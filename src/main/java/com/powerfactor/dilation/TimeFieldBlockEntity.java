package com.powerfactor.dilation;

import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.registry.ModTime;
import com.powerfactor.scada.ScadaReading;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import org.jspecify.annotations.Nullable;

/**
 * Time Dilation Field: runs everything inside its bubble faster. At xN, each block entity in range ticks
 * N-1 extra times a tick and random ticks (crops, saplings, cane) come N times as often. The price grows
 * with the square of the speed-up: (N² - 1) x 40 FE/t for the field, plus (N - 1)² x 8 FE/t for every
 * machine it accelerates. Range Upgrades widen it from a 5-block to a 13-block cube.
 */
public class TimeFieldBlockEntity extends MachineBlockEntity {
    public static final int CAPACITY = 20_000_000, MAX_INPUT = 400_000, MAX_CARDS = 3;
    public static final int[] SPEEDS = {1, 2, 4, 8, 16};
    public static final int DATA_SPEED = 0, DATA_ENERGY_HI = 1, DATA_ENERGY_LO = 2, DATA_COST_HI = 3, DATA_COST_LO = 4, DATA_TARGETS = 5,
            DATA_RADIUS = 6, DATA_ON = 7, DATA_COUNT = 8;
    public static final int BUTTON_SPEED = 0;   // + index into SPEEDS

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CAPACITY, MAX_INPUT, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private final SimpleContainer cards = new SimpleContainer(1) {
        @Override
        public boolean canPlaceItem(int index, ItemStack stack) {
            return stack.is(com.powerfactor.registry.ModUtility.RANGE_UPGRADE.get());
        }

        @Override
        public int getMaxStackSize() {
            return MAX_CARDS;
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(TimeFieldBlockEntity.this, player);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            TimeFieldBlockEntity.this.setChanged();
        }
    };
    private int speedIndex = 1, targets, cost, synced = -1;
    private boolean on;
    private int clientSpeed, clientRadius;
    private boolean clientOn;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case DATA_SPEED -> speedIndex;
                case DATA_ENERGY_HI -> energy.getAmountAsInt() >>> 16;
                case DATA_ENERGY_LO -> energy.getAmountAsInt() & 0xFFFF;
                case DATA_COST_HI -> cost >>> 16;
                case DATA_COST_LO -> cost & 0xFFFF;
                case DATA_TARGETS -> targets;
                case DATA_RADIUS -> radius();
                case DATA_ON -> on ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public TimeFieldBlockEntity(BlockPos pos, BlockState state) {
        super(ModTime.TIME_FIELD_BE.get(), pos, state);
    }

    public SimpleEnergyHandler getEnergy() {
        return energy;
    }

    public SimpleContainer cards() {
        return cards;
    }

    @Override
    protected Container inventory() {
        return cards;
    }

    public int radius() {
        return 2 + 2 * Math.min(MAX_CARDS, cards.getItem(0).getCount());
    }

    public int speed() {
        return SPEEDS[speedIndex];
    }

    public void setSpeed(int index) {
        speedIndex = Math.clamp(index, 0, SPEEDS.length - 1);
        setChanged();
    }

    public static int fieldCost(int n) {
        return (n * n - 1) * 40;
    }

    public static int targetCost(int n) {
        return (n - 1) * (n - 1) * 8;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        int n = speed(), r = radius();
        List<BlockEntity> found = new ArrayList<>();
        if (n > 1) {
            for (BlockPos p : BlockPos.betweenClosed(pos.offset(-r, -r, -r), pos.offset(r, r, r))) {
                if (p.equals(pos) || !level.isLoaded(p)) continue;
                BlockEntity be = level.getBlockEntity(p);
                if (be != null && !(be instanceof TimeFieldBlockEntity)) found.add(be);
            }
        }
        targets = found.size();
        cost = n > 1 ? fieldCost(n) + targets * targetCost(n) : 0;
        on = n > 1 && enabled(level, pos) && energy.getAmountAsInt() >= cost;
        if (on) {
            energy.set(energy.getAmountAsInt() - cost);
            for (BlockEntity be : found) accelerate(level, be, n - 1);
            randomTicks(level, pos, r, n - 1);
        }
        setRunning(level, pos, state, on);
        int sync = (on ? 1 : 0) | speedIndex << 1 | r << 5;
        if (sync != synced) {
            synced = sync;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void accelerate(ServerLevel level, BlockEntity be, int extra) {
        BlockState s = be.getBlockState();
        if (!(s.getBlock() instanceof EntityBlock eb)) return;
        BlockEntityTicker ticker = eb.getTicker(level, s, be.getType());
        if (ticker == null) return;
        for (int i = 0; i < extra && !be.isRemoved(); i++) ticker.tick(level, be.getBlockPos(), be.getBlockState(), be);
    }

    /** Extra random ticks at the world's rate (randomTickSpeed per 16x16x16 section), scaled by the speed-up. */
    private static void randomTicks(ServerLevel level, BlockPos pos, int r, int extra) {
        int perSection = level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.RANDOM_TICK_SPEED);
        int volume = (2 * r + 1) * (2 * r + 1) * (2 * r + 1);
        float expected = perSection * extra * volume / 4096F;
        int rolls = (int) expected + (level.getRandom().nextFloat() < expected - (int) expected ? 1 : 0);
        for (int i = 0; i < rolls; i++) {
            BlockPos p = pos.offset(level.getRandom().nextInt(2 * r + 1) - r, level.getRandom().nextInt(2 * r + 1) - r, level.getRandom().nextInt(2 * r + 1) - r);
            BlockState s = level.getBlockState(p);
            if (s.isRandomlyTicking()) s.randomTick(level, p, level.getRandom());
        }
    }

    public boolean clientOn() {
        return clientOn;
    }

    public int clientSpeed() {
        return clientSpeed;
    }

    public int clientRadius() {
        return clientRadius;
    }

    @Override
    public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), on ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF, on ? cost : 0, 0,
                Component.translatable("gui.powerfactor.time_field.scada", speed(), targets), true, remoteEnabled(), false, 0, 0);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new TimeFieldMenu(id, inventory, this, cards, data);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        cards.clearContent();
        ContainerHelper.loadAllItems(input, cards.getItems());
        speedIndex = Math.clamp(input.getIntOr("speed", 1), 0, SPEEDS.length - 1);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        ContainerHelper.saveAllItems(output, cards.getItems());
        output.putInt("speed", speedIndex);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putBoolean("on", on);
        tag.putInt("speed", speed());
        tag.putInt("radius", radius());
        return tag;
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        clientOn = input.getBooleanOr("on", false);
        clientSpeed = input.getIntOr("speed", 1);
        clientRadius = input.getIntOr("radius", 2);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ValueInput input) {
        handleUpdateTag(input);
    }
}
