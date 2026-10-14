package com.powerfactor.quantum;

import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.registry.ModQuantum;
import com.powerfactor.scada.ScadaReading;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
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
 * Zero-Point Energy Generator. Exotic Matter holds a pocket of false vacuum open; the generator taps
 * the energy difference. It takes thirty seconds to stabilise, then supplies up to 50,000 FE/t to
 * every adjacent receiver (and cables may pull from its buffer). One Exotic Matter lasts twenty minutes.
 */
public class ZeroPointBlockEntity extends MachineBlockEntity {
    public static final int RATED = 50_000, BUFFER = 5_000_000, FUEL_TICKS = 24_000, SPIN_UP = 600;
    public static final int DATA_STABILITY = 0, DATA_FUEL = 1, DATA_OUT_HI = 2, DATA_OUT_LO = 3, DATA_STORED = 4, DATA_COUNT = 5;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(BUFFER, 0, RATED * 2) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private final SimpleContainer fuel = new SimpleContainer(1) {
        @Override
        public boolean canPlaceItem(int index, ItemStack stack) {
            return stack.is(ModQuantum.EXOTIC_MATTER.get());
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(ZeroPointBlockEntity.this, player);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            ZeroPointBlockEntity.this.setChanged();
        }
    };
    private final ResourceHandler<ItemResource> items = VanillaContainerWrapper.of(fuel);
    private int burn, ticks, out;
    /** 0..1: how stable the vacuum pocket is (also drives the animation). */
    private float stability;
    private float clientStability;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case DATA_STABILITY -> Math.round(stability * 1000);
                case DATA_FUEL -> Math.round(1000F * burn / FUEL_TICKS);
                case DATA_OUT_HI -> out >>> 16;
                case DATA_OUT_LO -> out & 0xFFFF;
                case DATA_STORED -> energy.getAmountAsInt() / 1000;
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

    public ZeroPointBlockEntity(BlockPos pos, BlockState state) {
        super(ModQuantum.ZERO_POINT_BE.get(), pos, state);
    }

    public SimpleEnergyHandler getEnergy() {
        return energy;
    }

    public ResourceHandler<ItemResource> getItems() {
        return items;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        boolean on = enabled(level, pos);
        if (on && burn <= 0 && !fuel.getItem(0).isEmpty()) {
            fuel.getItem(0).shrink(1);
            fuel.setChanged();
            burn = FUEL_TICKS;
            level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1F, 0.6F);
        }
        boolean active = on && burn > 0;
        if (active) burn--;
        stability = Math.clamp(stability + (active ? 1F / SPIN_UP : -2F / SPIN_UP), 0, 1);
        out = Math.round(RATED * stability * stability);
        if (out > 0) energy.set(Math.min(BUFFER, energy.getAmountAsInt() + out));
        push(level, pos);
        setRunning(level, pos, state, stability > 0.05F);
        if (++ticks % 10 == 0) level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        if (active && ticks % 80 == 0) level.playSound(null, pos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.8F, 0.4F + stability * 0.5F);
    }

    private void push(ServerLevel level, BlockPos pos) {
        List<EnergyHandler> targets = new ArrayList<>();
        for (Direction d : Direction.values()) {
            EnergyHandler h = level.getCapability(Capabilities.Energy.BLOCK, pos.relative(d), d.getOpposite());
            if (h != null) targets.add(h);
        }
        for (int i = 0; i < targets.size() && energy.getAmountAsInt() > 0; i++) {
            int share = Math.max(1, energy.getAmountAsInt() / (targets.size() - i));
            try (Transaction tx = Transaction.openRoot()) {
                int moved = targets.get(i).insert(Math.min(share, RATED * 2), tx);
                tx.commit();
                energy.set(energy.getAmountAsInt() - moved);
            }
        }
    }

    public float stability() {
        return clientStability;
    }

    @Override
    protected double animationRate() {
        return 0.1 + clientStability * 1.5;
    }

    @Override
    public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), stability > 0.05F ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF, -out, 0,
                Component.translatable("scada.powerfactor.zero_point", Math.round(stability * 100), burn / 20), true, remoteEnabled(), true, 1F, 60F);
    }

    @Override
    protected Container inventory() {
        return fuel;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ZeroPointMenu(containerId, inventory, this, fuel, data);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        fuel.clearContent();
        ContainerHelper.loadAllItems(input, fuel.getItems());
        burn = input.getIntOr("burn", 0);
        stability = input.getFloatOr("stability", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        ContainerHelper.saveAllItems(output, fuel.getItems());
        output.putInt("burn", burn);
        output.putFloat("stability", stability);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putFloat("stability", stability);
        return tag;
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        clientStability = input.getFloatOr("stability", 0);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ValueInput input) {
        handleUpdateTag(input);
    }
}
