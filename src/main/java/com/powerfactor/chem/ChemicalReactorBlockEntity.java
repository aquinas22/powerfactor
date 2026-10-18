package com.powerfactor.chem;

import com.powerfactor.api.ReactiveLoad;
import com.powerfactor.device.HydrogenTanks;
import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.registry.ModChem;
import com.powerfactor.registry.ModFluids;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import org.jspecify.annotations.Nullable;

/**
 * Chemical Reactor: the contact process in a box. Sulfur burns to SO₂ and is oxidised and absorbed
 * into water; hydrogen from an Electrolyser fires the burner. One sulfur, 500 mB water and 250 mB
 * hydrogen make 500 mB sulfuric acid in five seconds at 120 FE/t.
 */
public class ChemicalReactorBlockEntity extends MachineBlockEntity implements com.powerfactor.upgrade.Upgradeable, ReactiveLoad, com.powerfactor.api.MachineAudio.Looping {
    public static final int CAPACITY = 200_000, MAX_INPUT = 2_000, FE_PER_TICK = 120, TIME = 100, TANK = 8_000;
    public static final int WATER_PER = 500, HYDROGEN_PER = 250, ACID_PER = 500;
    public static final int WATER = 0, HYDROGEN = 1, ACID = 2;
    public static final int DATA_PROGRESS = 0, DATA_WATER = 1, DATA_HYDROGEN = 2, DATA_ACID = 3, DATA_ENERGY = 4, DATA_STATE = 5, DATA_COUNT = 6;
    public static final int STATE_IDLE = 0, STATE_RUNNING = 1, STATE_NO_POWER = 2, STATE_FULL = 3, STATE_NEEDS = 4;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CAPACITY, MAX_INPUT, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private final HydrogenTanks tanks = new HydrogenTanks(TANK, new net.minecraft.world.level.material.Fluid[] {Fluids.WATER, ModFluids.HYDROGEN.get(),
            ModFluids.SULFURIC_ACID.source().get()}, new boolean[] {false, false, true}, this::setChanged);
    private final SimpleContainer slots = new SimpleContainer(1) {
        @Override
        public boolean canPlaceItem(int index, ItemStack stack) {
            return stack.is(ModChem.SULFUR_DUST.get());
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(ChemicalReactorBlockEntity.this, player);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            ChemicalReactorBlockEntity.this.setChanged();
        }
    };
    private final ResourceHandler<ItemResource> items = VanillaContainerWrapper.of(slots);
    private int progress, state, synced = -1;
    private int clientAcid, clientState;

    private final com.powerfactor.upgrade.UpgradeInventory upgrades = new com.powerfactor.upgrade.UpgradeInventory(
            java.util.EnumSet.of(com.powerfactor.upgrade.UpgradeType.SPEED, com.powerfactor.upgrade.UpgradeType.EFFICIENCY), this::setChanged,
            p -> Container.stillValidBlockEntity(this, p));
    /** Fractional progress: Speed cards add more than one tick of work per tick. */
    private float work;

    public com.powerfactor.upgrade.UpgradeInventory upgrades() {
        return upgrades;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && !level.isClientSide()) net.minecraft.world.Containers.dropContents(level, pos, upgrades);
    }

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case DATA_PROGRESS -> progress;
                case DATA_WATER -> tanks.amount(WATER);
                case DATA_HYDROGEN -> tanks.amount(HYDROGEN);
                case DATA_ACID -> tanks.amount(ACID);
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

    public ChemicalReactorBlockEntity(BlockPos pos, BlockState state) {
        super(ModChem.CHEMICAL_REACTOR_BE.get(), pos, state);
    }

    public SimpleEnergyHandler getEnergy() {
        return energy;
    }

    public HydrogenTanks getTanks() {
        return tanks;
    }

    public ResourceHandler<ItemResource> getItems() {
        return items;
    }

    public SimpleContainer slots() {
        return slots;
    }

    @Override
    protected Container inventory() {
        return slots;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState st) {
        boolean inputs = slots.getItem(0).is(ModChem.SULFUR_DUST.get()) && tanks.amount(WATER) >= WATER_PER && tanks.amount(HYDROGEN) >= HYDROGEN_PER;
        boolean room = tanks.amount(ACID) + ACID_PER <= TANK;
        var fx = upgrades.effects();
        int fe = (int) Math.ceil(FE_PER_TICK * fx.powerMultiplier());
        boolean power = energy.getAmountAsInt() >= fe;
        state = !inputs ? (progress > 0 ? STATE_NEEDS : STATE_IDLE) : !room ? STATE_FULL : !power ? STATE_NO_POWER : STATE_RUNNING;
        boolean on = state == STATE_RUNNING && enabled(level, pos);
        if (on) {
            energy.set(energy.getAmountAsInt() - fe);
            work += (float) fx.speedMultiplier();
            progress = (int) work;
            if (work >= TIME) {
                work = 0;
                progress = 0;
                slots.getItem(0).shrink(1);
                tanks.add(WATER, -WATER_PER);
                tanks.add(HYDROGEN, -HYDROGEN_PER);
                tanks.add(ACID, ACID_PER);
            }
        }
        setRunning(level, pos, st, on);
        int sync = (tanks.amount(ACID) * 16 / TANK) * 8 + state;
        if (sync != synced) {
            synced = sync;
            level.sendBlockUpdated(pos, st, st, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public boolean soundActive() {
        return isRunning();
    }

    @Override
    public net.minecraft.sounds.SoundEvent loopSound() {
        return com.powerfactor.registry.ModSounds.CHEMICAL_REACTOR_RUN.get();
    }

    @Override
    public double eeDemand() {
        return isRunning() ? FE_PER_TICK * 0.6 : 0;
    }

    /** Client: acid fill 0..1 for the vessel renderer. */
    public float clientAcid() {
        return clientAcid / (float) TANK;
    }

    @Override
    public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), isRunning() ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF,
                isRunning() ? FE_PER_TICK : 0, 0, Component.translatable("gui.powerfactor.reactor.scada", tanks.amount(ACID)), true, remoteEnabled(), false, 0, 0);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ChemicalReactorMenu(id, inventory, this, slots, data);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        tanks.deserialize(input.childOrEmpty("tanks"));
        slots.clearContent();
        ContainerHelper.loadAllItems(input, slots.getItems());
        progress = input.getIntOr("progress", 0);
        work = progress;
        upgrades.load(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        tanks.serialize(output.child("tanks"));
        ContainerHelper.saveAllItems(output, slots.getItems());
        output.putInt("progress", progress);
        upgrades.save(output);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("acid", tanks.amount(ACID));
        tag.putInt("state", state);
        return tag;
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        clientAcid = input.getIntOr("acid", 0);
        clientState = input.getIntOr("state", 0);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ValueInput input) {
        handleUpdateTag(input);
    }
}
