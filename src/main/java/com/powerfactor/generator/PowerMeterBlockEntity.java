package com.powerfactor.generator;

import com.powerfactor.api.Bus;
import com.powerfactor.api.ReactiveLoad;
import com.powerfactor.grid.BusLocator;
import com.powerfactor.grid.PowerGrid;
import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.power.BusSolver;
import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.scada.ScadaReading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.ValueInput;
import org.jspecify.annotations.Nullable;

/** Clamps onto a wire and reports its grid: flow, power factor, voltage, frequency, losses, loading. */
public class PowerMeterBlockEntity extends MachineBlockEntity implements ReactiveLoad {
    public static final int DATA_VOLTS = 0, DATA_HZ = 1, DATA_FE = 2, DATA_EE = 3, DATA_LOSS = 4, DATA_LOAD = 5, DATA_TIER = 6,
            DATA_INTERFERENCE = 7, DATA_REDSTONE = 8;

    private BusSolver.Result state = BusSolver.Result.NOMINAL;
    private double hertz, interference;
    private int tier = -1;
    private float visualVolts, visualLoad, shownVolts, shownLoad, previousVolts, previousLoad;
    private boolean visualPowered, visualsInitialized;
    private int syncedVolts = -1, syncedLoad = -1;
    private boolean syncedPowered;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case DATA_VOLTS -> (int) Math.round(state.volts() * 1000);
                case DATA_HZ -> (int) Math.round(hertz * 100);
                case DATA_FE -> (int) Math.round(state.fe());
                case DATA_EE -> (int) Math.round(state.netEe());
                case DATA_LOSS -> (int) Math.round(state.lossFe() * 10);
                case DATA_LOAD -> (int) Math.round(state.current() * 1000);
                case DATA_TIER -> tier;
                case DATA_INTERFERENCE -> (int) Math.round(interference * 100);
                case DATA_REDSTONE -> redstoneMode().ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return DieselMenu.DATA_COUNT;
        }
    };

    public PowerMeterBlockEntity(BlockPos pos, BlockState s) {
        super(ModBlockEntities.POWER_METER.get(), pos, s);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState blockState) {
        Bus bus = BusLocator.find(level, pos);
        state = bus.state();
        hertz = bus.hertz();
        tier = bus instanceof PowerGrid grid ? grid.tier().ordinal() : -1;
        interference = bus instanceof PowerGrid grid ? grid.interference() : 0;
        boolean powered = tier >= 0 && hertz > 0 && state.volts() > 0;
        setRunning(level, pos, blockState, powered);
        int volts = powered ? (int) Math.round(state.volts() * 100) : 0;
        int load = powered ? (int) Math.round(state.current() * 100) : 0;
        if (powered != syncedPowered || level.getGameTime() % 4 == 0 && (volts != syncedVolts || load != syncedLoad)) {
            syncedVolts = volts; syncedLoad = load; syncedPowered = powered;
            level.sendBlockUpdated(pos, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override public void clientTick() {
        super.clientTick();
        previousVolts = shownVolts; previousLoad = shownLoad;
        shownVolts += (visualVolts - shownVolts) * 0.18F;
        shownLoad += (visualLoad - shownLoad) * 0.18F;
    }

    public float shownVoltage(float partial) { return previousVolts + (shownVolts - previousVolts) * Math.clamp(partial, 0, 1); }
    public float shownLoad(float partial) { return previousLoad + (shownLoad - previousLoad) * Math.clamp(partial, 0, 1); }
    public boolean visualPowered() { return visualPowered; }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup) {
        var tag = saveCustomOnly(lookup);
        boolean powered = tier >= 0 && hertz > 0 && state.volts() > 0;
        tag.putFloat("meter_volts", powered ? (float) state.volts() : 0);
        tag.putFloat("meter_load", powered ? (float) state.current() : 0);
        tag.putBoolean("meter_powered", powered);
        return tag;
    }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        visualVolts = Math.clamp(input.getFloatOr("meter_volts", 0), 0, 1.25F);
        visualLoad = Math.clamp(input.getFloatOr("meter_load", 0), 0, 1.25F);
        visualPowered = input.getBooleanOr("meter_powered", false);
        if (!visualsInitialized) {
            previousVolts = shownVolts = visualVolts; previousLoad = shownLoad = visualLoad;
            visualsInitialized = true;
        }
    }

    /** Draws nothing; it only implements this so wires connect to it. */
    @Override
    public double eeDemand() {
        return 0;
    }

    @Override
    public ScadaReading scadaReading() {
        double pf = state.powerFactor();
        ScadaReading.Status status = tier < 0 || hertz <= 0 ? ScadaReading.Status.OFF
                : state.current() > 1 || state.volts() < 0.9 || state.volts() > 1.1 ? ScadaReading.Status.WARNING : ScadaReading.Status.RUNNING;
        // An instrument, not a load: its grid totals go in the detail line so they aren't counted twice.
        return new ScadaReading(worldPosition, getDisplayName(), status, 0, 0,
                Component.translatable("scada.powerfactor.meter", (int) state.fe(), String.format("%.2f", pf), Math.round(state.current() * 100),
                        String.format("%.1f", state.lossFe())), false, true, false, (float) state.volts(), (float) hertz);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new DieselMenu.Meter(id, inventory, worldPosition, data, this);
    }
}
