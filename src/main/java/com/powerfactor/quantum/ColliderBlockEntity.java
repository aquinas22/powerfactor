package com.powerfactor.quantum;

import com.powerfactor.api.ReactiveLoad;
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
 * Particle Collider: a superconducting ring that accelerates a charge of matter for as long as it
 * takes to bank the collision energy, then smashes it into something rarer. Draws up to 250,000 FE/t.
 */
public class ColliderBlockEntity extends MachineBlockEntity implements ReactiveLoad {
    public static final int CAPACITY = 100_000_000, MAX_INPUT = 1_000_000, MAX_RATE = 250_000;
    public static final int DATA_PROGRESS = 0, DATA_RATE_HI = 1, DATA_RATE_LO = 2, DATA_ENERGY_HI = 3, DATA_ENERGY_LO = 4, DATA_RECIPE = 5,
            DATA_FLASH = 6, DATA_COUNT = 7;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CAPACITY, MAX_INPUT, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private final SimpleContainer slots = new SimpleContainer(2) {
        @Override
        public boolean canPlaceItem(int index, ItemStack stack) {
            return index == 0 && ColliderRecipes.find(stack) != null;
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(ColliderBlockEntity.this, player);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            ColliderBlockEntity.this.setChanged();
        }
    };
    private final ResourceHandler<ItemResource> items = VanillaContainerWrapper.of(slots);
    private long banked;
    private int rate, recipe = -1, flash;
    /** Client: 0..1 how far the current run has got, synced in the update tag for the renderer. */
    private float clientProgress;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            ColliderRecipes.Recipe r = recipe < 0 ? null : ColliderRecipes.ALL.get(recipe);
            return switch (i) {
                case DATA_PROGRESS -> r == null ? 0 : (int) (1000 * Math.min(1, (double) banked / r.energy()));
                case DATA_RATE_HI -> rate >>> 16;
                case DATA_RATE_LO -> rate & 0xFFFF;
                case DATA_ENERGY_HI -> (energy.getAmountAsInt() >>> 12) & 0xFFFF;
                case DATA_ENERGY_LO -> energy.getAmountAsInt() & 0xFFF;
                case DATA_RECIPE -> recipe + 1;
                case DATA_FLASH -> flash;
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

    public ColliderBlockEntity(BlockPos pos, BlockState state) {
        super(ModQuantum.COLLIDER_BE.get(), pos, state);
    }

    public SimpleEnergyHandler getEnergy() {
        return energy;
    }

    public ResourceHandler<ItemResource> getItems() {
        return items;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        rate = 0;
        if (flash > 0) flash--;
        ItemStack in = slots.getItem(0);
        ColliderRecipes.Recipe r = ColliderRecipes.find(in);
        int index = r == null ? -1 : ColliderRecipes.ALL.indexOf(r);
        if (index != recipe) {
            recipe = index;
            banked = 0;
        }
        boolean ready = r != null && in.getCount() >= r.count() && fits(r.output().get()) && enabled(level, pos);
        if (ready) {
            int draw = (int) Math.min(Math.min(MAX_RATE, energy.getAmountAsInt()), r.energy() - banked);
            energy.set(energy.getAmountAsInt() - draw);
            banked += draw;
            rate = draw;
            if (banked >= r.energy()) collide(level, pos, r);
        }
        setRunning(level, pos, state, rate > 0);
        if (level.getGameTime() % 5 == 0) level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }

    private boolean fits(ItemStack out) {
        ItemStack have = slots.getItem(1);
        return have.isEmpty() || ItemStack.isSameItemSameComponents(have, out) && have.getCount() + out.getCount() <= have.getMaxStackSize();
    }

    private void collide(ServerLevel level, BlockPos pos, ColliderRecipes.Recipe r) {
        slots.getItem(0).shrink(r.count());
        ItemStack out = r.output().get();
        ItemStack have = slots.getItem(1);
        if (have.isEmpty()) slots.setItem(1, out);
        else have.grow(out.getCount());
        banked = 0;
        flash = 20;
        double x = pos.getX() + .5, y = pos.getY() + 1.2, z = pos.getZ() + .5;
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 60, .1, .1, .1, .35);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 40, .3, .3, .3, .6);
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.8F);
        level.playSound(null, pos, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.BLOCKS, 0.7F, 1.6F);
        level.sendBlockUpdated(pos, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    public float progress() {
        return clientProgress;
    }

    public int flash() {
        return flash;
    }

    @Override
    protected double animationRate() {
        return isRunning() ? 1 : 0.02;
    }

    @Override
    public double eeDemand() {
        // Superconducting magnets: a large reactive draw while ramping the field.
        return rate * 0.15;
    }

    @Override
    public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), rate > 0 ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF, rate,
                (int) eeDemand(), Component.translatable("scada.powerfactor.collider", recipe < 0 ? 0 : (int) (100 * clamp())), true,
                remoteEnabled(), false, 1F, 60F);
    }

    private double clamp() {
        return recipe < 0 ? 0 : Math.min(1, (double) banked / ColliderRecipes.ALL.get(recipe).energy());
    }

    @Override
    protected Container inventory() {
        return slots;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ColliderMenu(containerId, inventory, this, slots, data);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        slots.clearContent();
        ContainerHelper.loadAllItems(input, slots.getItems());
        banked = input.getLongOr("banked", 0);
        recipe = input.getIntOr("recipe", -1);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        ContainerHelper.saveAllItems(output, slots.getItems());
        output.putLong("banked", banked);
        output.putInt("recipe", recipe);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putFloat("progress", (float) clamp());
        tag.putInt("flash", flash);
        return tag;
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        clientProgress = input.getFloatOr("progress", 0);
        flash = input.getIntOr("flash", 0);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ValueInput input) {
        handleUpdateTag(input);
    }

    @Override
    public void clientTick() {
        super.clientTick();
        if (flash > 0) flash--;
    }
}
