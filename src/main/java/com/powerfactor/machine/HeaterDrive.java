package com.powerfactor.machine;

import com.powerfactor.power.UpgradeEffects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Electric heating. Heat output goes with V^2 (P = V^2 / R), so a sagging grid makes it work slower
 * rather than trip, and frequency doesn't matter.
 * <ul>
 *   <li>Resistive elements (electric furnace): eeRatio 0, a clean PF 1.0 load.</li>
 *   <li>Induction heating (alloy furnace): the work coil is mostly reactive, about PF 0.6.</li>
 *   <li>Electric arc (arc furnace): about PF 0.75, and the arc's EE jumps around tick to tick,
 *       which shows up as flicker on everyone else's voltage.</li>
 * </ul>
 */
public final class HeaterDrive implements Drive {
    private final int ratedFe;
    private final double eeRatio;
    private final boolean flicker;
    private State state = State.OFF;
    private double speed, ee;

    public HeaterDrive() {
        this(0, 0, false);
    }

    public HeaterDrive(int ratedFe, double eeRatio, boolean flicker) {
        this.ratedFe = ratedFe;
        this.eeRatio = eeRatio;
        this.flicker = flicker;
    }

    @Override
    public boolean tick(double volts, double hertz, boolean hasWork, boolean enabled, boolean powered, UpgradeEffects up) {
        state = hasWork && enabled && powered && volts > 0.05 ? State.RUNNING : State.OFF;
        double v2 = Math.min(1.1, volts * volts);
        speed = state == State.RUNNING ? v2 * up.speedMultiplier() : 0;
        if (state == State.RUNNING && eeRatio > 0) {
            double swing = flicker ? 0.55 + ThreadLocalRandom.current().nextDouble() * 0.9 : 1.0;
            ee = ratedFe * up.powerMultiplier() * eeRatio * v2 * swing - up.capacitorEe(volts);
        } else {
            ee = 0;
        }
        return false;
    }

    @Override
    public State state() {
        return state;
    }

    @Override
    public double speed() {
        return speed;
    }

    @Override
    public double ee() {
        return ee;
    }

    @Override
    public int idleFe() {
        return 0;
    }

    @Override
    public void load(int state, int timer) {
        this.state = State.values()[Math.clamp(state, 0, 3)];
    }

    @Override
    public int timer() {
        return 0;
    }
}
