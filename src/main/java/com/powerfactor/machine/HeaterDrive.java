package com.powerfactor.machine;

import com.powerfactor.power.UpgradeEffects;

/**
 * Resistive heating elements: a pure FE load, power factor 1.0, indifferent to frequency. Heat
 * output goes with V^2 (P = V^2 / R), so a sagging grid makes it cook slower rather than trip.
 */
public final class HeaterDrive implements Drive {
    private State state = State.OFF;
    private double speed;

    @Override
    public boolean tick(double volts, double hertz, boolean hasWork, boolean enabled, boolean powered, UpgradeEffects up) {
        state = hasWork && enabled && powered && volts > 0.05 ? State.RUNNING : State.OFF;
        speed = state == State.RUNNING ? Math.min(1.1, volts * volts) * up.speedMultiplier() : 0;
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
        return 0;
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
