package com.powerfactor.quantum;

import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.registry.ModQuantum;
import com.powerfactor.scada.ScadaReading;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Space Elevator anchor: the capstone. Feed it Nanotube Tether segments and power; it spools them up
 * into the sky, one segment every few seconds. With all 64 segments raised and the sky open above, the
 * orbital station at the top beams down 120,000 FE/t of space solar power, day and night. Use the
 * finished anchor to ride the climber up the tether (slow fall brings you home).
 */
public class SpaceElevatorBlockEntity extends MachineBlockEntity {
    public static final int SEGMENTS = 64, SPOOL_TICKS = 60, SPOOL_FE = 50_000, CAPACITY = 2_000_000, MAX_INPUT = 50_000, OUTPUT = 120_000;
    public static final int DATA_SEGMENTS = 0, DATA_SPOOL = 1, DATA_ENERGY = 2, DATA_OUT_HI = 3, DATA_OUT_LO = 4, DATA_SKY = 5, DATA_COUNT = 6;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CAPACITY, MAX_INPUT, OUTPUT) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private final SimpleContainer tether = new SimpleContainer(1) {
        @Override
        public boolean canPlaceItem(int index, ItemStack stack) {
            return stack.is(ModQuantum.NANOTUBE_TETHER.get());
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(SpaceElevatorBlockEntity.this, player);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            SpaceElevatorBlockEntity.this.setChanged();
        }
    };
    private final ResourceHandler<ItemResource> items = VanillaContainerWrapper.of(tether);
    private int segments, spool, out;
    private boolean sky;
    private int clientSegments;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case DATA_SEGMENTS -> segments;
                case DATA_SPOOL -> Math.round(1000F * spool / SPOOL_TICKS);
                case DATA_ENERGY -> energy.getAmountAsInt() / 1000;
                case DATA_OUT_HI -> out >>> 16;
                case DATA_OUT_LO -> out & 0xFFFF;
                case DATA_SKY -> sky ? 1 : 0;
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

    public SpaceElevatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModQuantum.SPACE_ELEVATOR_BE.get(), pos, state);
    }

    public SimpleEnergyHandler getEnergy() {
        return energy;
    }

    public ResourceHandler<ItemResource> getItems() {
        return items;
    }

    public Container tetherSlot() {
        return tether;
    }

    public boolean complete() {
        return segments >= SEGMENTS;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        sky = level.canSeeSky(pos.above());
        out = 0;
        boolean on = enabled(level, pos) && sky;
        int was = segments;
        if (on && !complete()) {
            if (!tether.getItem(0).isEmpty() && energy.getAmountAsInt() >= SPOOL_FE / SPOOL_TICKS) {
                energy.set(energy.getAmountAsInt() - SPOOL_FE / SPOOL_TICKS);
                if (++spool >= SPOOL_TICKS) {
                    spool = 0;
                    tether.getItem(0).shrink(1);
                    tether.setChanged();
                    segments++;
                    level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1F, 0.6F);
                    if (complete()) {
                        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 2F, 0.5F);
                        level.playSound(null, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1F, 1F);
                    }
                }
            }
        } else if (on) {
            out = OUTPUT;
            energy.set((int) Math.min(CAPACITY, (long) energy.getAmountAsInt() + out));
            push(level, pos);
        }
        setRunning(level, pos, state, on && (complete() || spool > 0));
        if (segments != was || level.getGameTime() % 40 == 0) level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }

    private void push(ServerLevel level, BlockPos pos) {
        List<EnergyHandler> targets = new ArrayList<>();
        for (Direction d : Direction.values()) {
            if (d == Direction.UP) continue;
            EnergyHandler h = level.getCapability(Capabilities.Energy.BLOCK, pos.relative(d), d.getOpposite());
            if (h != null) targets.add(h);
        }
        for (int i = 0; i < targets.size() && energy.getAmountAsInt() > 0; i++) {
            int share = Math.max(1, energy.getAmountAsInt() / (targets.size() - i));
            try (Transaction tx = Transaction.openRoot()) {
                int moved = targets.get(i).insert(Math.min(share, OUTPUT), tx);
                tx.commit();
                energy.set(energy.getAmountAsInt() - moved);
            }
        }
    }

    /** Tests: a full build takes minutes, so jump the tether count directly. */
    public void setSegmentsForTest(int n) {
        segments = Math.clamp(n, 0, SEGMENTS);
        setChanged();
    }

    public int segments() {
        return segments;
    }

    /** Ride the climber: a long, steady lift up the tether, then a slow fall home. */
    public boolean ride(Player player) {
        if (!complete()) return false;
        player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 200, 14, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 900, 0, false, false, true));
        if (level != null) level.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.5F, 0.6F);
        player.sendOverlayMessage(Component.translatable("gui.powerfactor.elevator.ride").withStyle(ChatFormatting.AQUA));
        return true;
    }

    public int clientSegments() {
        return clientSegments;
    }

    @Override
    public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), complete() && sky ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF,
                -out, 0, Component.translatable("gui.powerfactor.elevator.segments", segments, SEGMENTS), true, remoteEnabled(), true, 1F, 60F);
    }

    @Override
    protected Container inventory() {
        return tether;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new SpaceElevatorMenu(containerId, inventory, this, tether, data);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        tether.clearContent();
        ContainerHelper.loadAllItems(input, tether.getItems());
        segments = Math.clamp(input.getIntOr("segments", 0), 0, SEGMENTS);
        spool = input.getIntOr("spool", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        ContainerHelper.saveAllItems(output, tether.getItems());
        output.putInt("segments", segments);
        output.putInt("spool", spool);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("segments", segments);
        return tag;
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        clientSegments = input.getIntOr("segments", 0);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ValueInput input) {
        handleUpdateTag(input);
    }
}
