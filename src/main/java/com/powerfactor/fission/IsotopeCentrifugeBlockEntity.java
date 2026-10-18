package com.powerfactor.fission;

import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.registry.ModFission;
import com.powerfactor.scada.ScadaReading;
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
import net.minecraft.world.item.Item;
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
 * Isotope Centrifuge: a tall glass column whose rotor spins up to a scream. It enriches uranium (9 natural
 * ingots → 1 enriched + 8 depleted) and reprocesses spent fuel (a spent rod → plutonium, depleted uranium
 * and nuclear waste). Speed and Efficiency cards apply.
 */
public class IsotopeCentrifugeBlockEntity extends MachineBlockEntity implements com.powerfactor.upgrade.Upgradeable, com.powerfactor.api.ReactiveLoad, com.powerfactor.api.MachineAudio.Looping {
    public static final int CAPACITY = 2_000_000, MAX_INPUT = 20_000, FE_PER_TICK = 600, TIME = 400, OUT = 4;
    public static final int DATA_PROGRESS = 0, DATA_ENERGY = 1, DATA_STATE = 2, DATA_COUNT = 3;
    public static final int STATE_IDLE = 0, STATE_RUNNING = 1, STATE_NO_POWER = 2, STATE_FULL = 3;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CAPACITY, MAX_INPUT, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    /** 0 input, 1..4 output. */
    private final SimpleContainer slots = new SimpleContainer(1 + OUT) {
        @Override
        public boolean canPlaceItem(int index, ItemStack stack) {
            return index == 0 && recipe(stack) != null;
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(IsotopeCentrifugeBlockEntity.this, player);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            IsotopeCentrifugeBlockEntity.this.setChanged();
        }
    };
    private final ResourceHandler<ItemResource> items = new net.neoforged.neoforge.transfer.DelegatingResourceHandler<>(VanillaContainerWrapper.of(slots)) {
        @Override
        public int insert(int index, ItemResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
            return index == 0 ? super.insert(index, resource, amount, tx) : 0;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
            return index > 0 ? super.extract(index, resource, amount, tx) : 0;
        }
    };
    private final com.powerfactor.upgrade.UpgradeInventory upgrades = new com.powerfactor.upgrade.UpgradeInventory(
            com.powerfactor.upgrade.UpgradeInventory.CONSOLE, this::setChanged, p -> Container.stillValidBlockEntity(this, p));
    private float work;
    private int state;
    private float clientSpin;

    public record Recipe(Item input, int count, ItemStack[] outputs) {}

    public static @Nullable Recipe recipe(ItemStack in) {
        if (in.is(ModFission.URANIUM_INGOT.get())) {
            return new Recipe(ModFission.URANIUM_INGOT.get(), 9,
                    new ItemStack[] {new ItemStack(ModFission.ENRICHED_URANIUM.get()), new ItemStack(ModFission.DEPLETED_URANIUM.get(), 8)});
        }
        if (in.is(net.minecraft.world.item.Items.WATER_BUCKET)) {
            // Deuterium enrichment: spin the heavy water out of a bucket of ordinary water.
            return new Recipe(net.minecraft.world.item.Items.WATER_BUCKET, 1, new ItemStack[] {new ItemStack(com.powerfactor.registry.ModFluids.HEAVY_WATER.bucket().get())});
        }
        if (in.is(ModFission.SPENT_FUEL_ROD.get()) || in.is(ModFission.SPENT_MOX_ROD.get())) {
            boolean mox = in.is(ModFission.SPENT_MOX_ROD.get());
            return new Recipe(in.getItem(), 1, new ItemStack[] {new ItemStack(ModFission.PLUTONIUM.get(), mox ? 1 : 3),
                    new ItemStack(ModFission.DEPLETED_URANIUM.get(), 3), new ItemStack(ModFission.NUCLEAR_WASTE.get(), mox ? 3 : 2)});
        }
        return null;
    }

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case DATA_PROGRESS -> (int) (1000 * work / TIME);
                case DATA_ENERGY -> energy.getAmountAsInt() / 100;
                case DATA_STATE -> state;
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

    public IsotopeCentrifugeBlockEntity(BlockPos pos, BlockState state) {
        super(ModFission.CENTRIFUGE_BE.get(), pos, state);
    }

    public SimpleEnergyHandler getEnergy() {
        return energy;
    }

    public ResourceHandler<ItemResource> getItems() {
        return items;
    }

    public SimpleContainer slots() {
        return slots;
    }

    public com.powerfactor.upgrade.UpgradeInventory upgrades() {
        return upgrades;
    }

    @Override
    protected Container inventory() {
        return slots;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && !level.isClientSide()) net.minecraft.world.Containers.dropContents(level, pos, upgrades);
    }

    private boolean fits(ItemStack[] outs) {
        SimpleContainer sim = new SimpleContainer(OUT);
        for (int i = 0; i < OUT; i++) sim.setItem(i, slots.getItem(1 + i).copy());
        for (ItemStack o : outs) if (!sim.addItem(o.copy()).isEmpty()) return false;
        return true;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState st) {
        Recipe r = recipe(slots.getItem(0));
        boolean inputs = r != null && slots.getItem(0).getCount() >= r.count();
        var fx = upgrades.effects();
        int fe = (int) Math.ceil(FE_PER_TICK * fx.powerMultiplier());
        state = !inputs ? STATE_IDLE : !fits(r.outputs()) ? STATE_FULL : energy.getAmountAsInt() < fe ? STATE_NO_POWER : STATE_RUNNING;
        boolean on = state == STATE_RUNNING && enabled(level, pos);
        if (on) {
            energy.set(energy.getAmountAsInt() - fe);
            work += (float) fx.speedMultiplier();
            if (work >= TIME) {
                work = 0;
                slots.getItem(0).shrink(r.count());
                for (ItemStack o : r.outputs()) {
                    ItemStack rest = o.copy();
                    for (int i = 1; i <= OUT && !rest.isEmpty(); i++) {
                        ItemStack s = slots.getItem(i);
                        if (s.isEmpty()) {
                            slots.setItem(i, rest);
                            rest = ItemStack.EMPTY;
                        } else if (ItemStack.isSameItemSameComponents(s, rest)) {
                            int move = Math.min(rest.getCount(), s.getMaxStackSize() - s.getCount());
                            s.grow(move);
                            rest.shrink(move);
                        }
                    }
                }
                slots.setChanged();
            }
        } else if (!inputs) {
            work = 0;
        }
        boolean was = isRunning();
        setRunning(level, pos, st, on);
        if (was != on) level.sendBlockUpdated(pos, st, st, Block.UPDATE_CLIENTS);
    }

    @Override
    public boolean soundActive() {
        return isRunning();
    }

    @Override
    public net.minecraft.sounds.SoundEvent loopSound() {
        return com.powerfactor.registry.ModSounds.CENTRIFUGE_RUN.get();
    }

    @Override
    public double eeDemand() {
        return isRunning() ? FE_PER_TICK * 0.6 : 0;
    }

    /** Client: rotor spin, eased toward full speed while running. */
    public float clientSpin(float target) {
        clientSpin += (target - clientSpin) * 0.02F;
        return clientSpin;
    }

    @Override
    public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), isRunning() ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF,
                isRunning() ? FE_PER_TICK : 0, 0, Component.translatable("gui.powerfactor.centrifuge.scada", (int) (100 * work / TIME)), true, remoteEnabled(),
                false, 0, 0);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new IsotopeCentrifugeMenu(id, inventory, this, slots, data);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        slots.clearContent();
        ContainerHelper.loadAllItems(input, slots.getItems());
        upgrades.load(input);
        work = input.getFloatOr("work", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        ContainerHelper.saveAllItems(output, slots.getItems());
        upgrades.save(output);
        output.putFloat("work", work);
    }
}
