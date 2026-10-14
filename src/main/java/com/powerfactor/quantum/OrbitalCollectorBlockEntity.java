package com.powerfactor.quantum;

import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.registry.ModQuantum;
import com.powerfactor.scada.ScadaReading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import org.jspecify.annotations.Nullable;

/**
 * Orbital Collector: a tracking dish that, at night under open sky, pulls drifting matter down a
 * collection beam. Each one-minute pass costs 3M FE and yields Cosmic Dust, sometimes qubit crystal,
 * raw faradaenium or Meteorite, and very rarely a Nether Star.
 */
public class OrbitalCollectorBlockEntity extends MachineBlockEntity {
    public static final int CAPACITY = 4_000_000, MAX_INPUT = 20_000, RATE = 2_500, PASS = 1200, SLOTS = 9;
    public static final int DATA_PROGRESS = 0, DATA_STATE = 1, DATA_ENERGY = 2, DATA_COUNT = 3;
    /** 0 working, 1 daytime, 2 no sky, 3 no power, 4 output full, 5 off. */
    public static final int WORKING = 0, DAY = 1, NO_SKY = 2, NO_POWER = 3, FULL = 4, OFF = 5;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CAPACITY, MAX_INPUT, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private final SimpleContainer out = new SimpleContainer(SLOTS) {
        @Override
        public boolean canPlaceItem(int index, ItemStack stack) {
            return false;
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(OrbitalCollectorBlockEntity.this, player);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            OrbitalCollectorBlockEntity.this.setChanged();
        }
    };
    private final ResourceHandler<ItemResource> items = VanillaContainerWrapper.of(out);
    private int progress, state = OFF;
    private int clientState = OFF;
    private float clientProgress;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case DATA_PROGRESS -> Math.round(1000F * progress / PASS);
                case DATA_STATE -> state;
                case DATA_ENERGY -> energy.getAmountAsInt() / 1000;
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

    public OrbitalCollectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModQuantum.ORBITAL_COLLECTOR_BE.get(), pos, state);
    }

    public SimpleEnergyHandler getEnergy() {
        return energy;
    }

    public ResourceHandler<ItemResource> getItems() {
        return items;
    }

    public Container output() {
        return out;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState bs) {
        int was = state;
        state = !enabled(level, pos) ? OFF : !level.canSeeSky(pos.above()) ? NO_SKY : !level.isDarkOutside() ? DAY
                : energy.getAmountAsInt() < RATE ? NO_POWER : !hasRoom() ? FULL : WORKING;
        if (state == WORKING) {
            energy.set(energy.getAmountAsInt() - RATE);
            if (++progress >= PASS) {
                progress = 0;
                collect(level, pos);
            }
            if (level.getGameTime() % 10 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 6 + level.getRandom().nextDouble() * 20, pos.getZ() + 0.5,
                        1, 0.1, 0, 0.1, -0.15);
            }
        }
        setRunning(level, pos, bs, state == WORKING);
        if (state != was || level.getGameTime() % 20 == 0) level.sendBlockUpdated(pos, bs, bs, Block.UPDATE_CLIENTS);
    }

    /** Finishes the current pass immediately (tests: a real pass takes a minute). */
    public void completePass(ServerLevel level) {
        progress = 0;
        collect(level, worldPosition);
    }

    public int state() {
        return state;
    }

    private boolean hasRoom() {
        for (int i = 0; i < SLOTS; i++) {
            ItemStack s = out.getItem(i);
            if (s.isEmpty() || s.getCount() < s.getMaxStackSize()) return true;
        }
        return false;
    }

    private void collect(ServerLevel level, BlockPos pos) {
        var r = level.getRandom();
        add(new ItemStack(ModQuantum.COSMIC_DUST.get(), 2 + r.nextInt(3)));
        if (r.nextFloat() < 0.25F) add(new ItemStack(ModQuantum.QUBIT_CRYSTAL.get(), 1 + r.nextInt(2)));
        if (r.nextFloat() < 0.15F) add(new ItemStack(ModQuantum.RAW_FARADAENIUM.get()));
        if (r.nextFloat() < 0.10F) add(new ItemStack(ModQuantum.METEORITE.get()));
        if (r.nextFloat() < 0.005F) add(new ItemStack(Items.NETHER_STAR));
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1F, 0.6F);
        level.sendParticles(ParticleTypes.GLOW, pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5, 20, 0.4, 0.2, 0.4, 0.05);
    }

    /** Adds to the output, dropping nothing: whatever doesn't fit is simply not collected. */
    private void add(ItemStack stack) {
        for (int i = 0; i < SLOTS && !stack.isEmpty(); i++) {
            ItemStack s = out.getItem(i);
            if (s.isEmpty()) {
                out.setItem(i, stack.copy());
                stack.setCount(0);
            } else if (ItemStack.isSameItemSameComponents(s, stack)) {
                int n = Math.min(stack.getCount(), s.getMaxStackSize() - s.getCount());
                s.grow(n);
                stack.shrink(n);
            }
        }
        out.setChanged();
    }

    public int clientState() {
        return clientState;
    }

    public float clientProgress() {
        return clientProgress;
    }

    @Override
    protected double animationRate() {
        return clientState == WORKING ? 0.4 : 0.05;
    }

    @Override
    public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), state == WORKING ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF,
                state == WORKING ? RATE : 0, 0, Component.translatable("gui.powerfactor.orbital.state." + state), true, remoteEnabled(), false, 0, 0);
    }

    @Override
    protected Container inventory() {
        return out;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new OrbitalCollectorMenu(containerId, inventory, this, out, data);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        out.clearContent();
        ContainerHelper.loadAllItems(input, out.getItems());
        progress = input.getIntOr("progress", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        ContainerHelper.saveAllItems(output, out.getItems());
        output.putInt("progress", progress);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("state", state);
        tag.putFloat("progress", (float) progress / PASS);
        return tag;
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        clientState = input.getIntOr("state", OFF);
        clientProgress = input.getFloatOr("progress", 0);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ValueInput input) {
        handleUpdateTag(input);
    }
}
