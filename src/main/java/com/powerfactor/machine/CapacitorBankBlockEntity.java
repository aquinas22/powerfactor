package com.powerfactor.machine;

import com.powerfactor.PowerFactorConfig;
import com.powerfactor.api.Bus;
import com.powerfactor.api.ReactiveLoad;
import com.powerfactor.grid.BusLocator;
import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.scada.ScadaReading;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fixed power-factor correction. Like a real capacitor its EE output goes with V^2, so it helps
 * most when voltage is healthy. Over-correcting pushes voltage up; above 1.12 pu it trips out on
 * overvoltage protection and recloses once voltage is back under 1.05.
 */
public class CapacitorBankBlockEntity extends MachineBlockEntity implements ReactiveLoad {
    private static final double TRIP_VOLTS = 1.12, RECLOSE_VOLTS = 1.05;
    private static final int RECLOSE_TICKS = 200;

    private boolean switchedIn;
    private boolean tripped;
    private int recloseTimer;
    private double volts = 1.0;

    public CapacitorBankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CAPACITOR_BANK.get(), pos, state);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        Bus bus = BusLocator.find(level, pos);
        volts = bus.volts();
        if (tripped) {
            if (volts < RECLOSE_VOLTS && --recloseTimer <= 0) {
                tripped = false;
            }
        } else if (switchedIn && volts > TRIP_VOLTS) {
            tripped = true;
            recloseTimer = RECLOSE_TICKS;
            level.playSound(null, pos, com.powerfactor.registry.ModSounds.CAPACITOR_STEP_OUT.get(), SoundSource.BLOCKS, 1.0F, 0.7F);
        }
        switchedIn = enabled(level, pos) && !tripped && volts > 0;
        setRunning(level, pos, state, switchedIn);
    }

    public static double ratedEe() {
        return PowerFactorConfig.SPEC.isLoaded() ? PowerFactorConfig.CAPACITOR_BANK_EE.getAsInt() : 60;
    }

    /** Negative: it supplies EE. */
    @Override
    public double eeDemand() {
        return switchedIn ? -ratedEe() : 0;
    }

    @Override
    public ScadaReading scadaReading() {
        ScadaReading.Status status = tripped ? ScadaReading.Status.ALARM : switchedIn ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF;
        Component detail = Component.translatable(tripped ? "scada.powerfactor.capacitor.tripped"
                : switchedIn ? "scada.powerfactor.capacitor.in" : "scada.powerfactor.capacitor.out");
        return new ScadaReading(worldPosition, getDisplayName(), status, 0, (int) Math.round(eeDemand() * volts * volts), detail, true,
                remoteEnabled(), false, (float) volts, 60F);
    }
}
