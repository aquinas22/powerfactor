package com.powerfactor.machine;

import com.powerfactor.power.UpgradeEffects;

/**
 * A three-phase induction motor. Speed follows V^2 x frequency (unless a VFD is fitted). Loaded it
 * runs near PF 0.8; between jobs it idles for a few seconds drawing little FE but most of its EE,
 * PF about 0.25. Sustained undervoltage trips it; it retries after a cool-off.
 */
public final class MotorDrive implements Drive {
    private static final int IDLE_TICKS = 100, TRIP_TICKS = 100;
    private static final double IDLE_EE_FRACTION = 0.8;
    private static final int IDLE_FE = 3;

    private final int ratedEe;
    private State state = State.OFF;
    private int timer, undervolt;
    private double speed, ee;

    public MotorDrive(int ratedEe) {
        this.ratedEe = ratedEe;
    }

    @Override
    public boolean tick(double volts, double hertz, boolean hasWork, boolean enabled, boolean powered, UpgradeEffects up) {
        boolean tripped = false;
        if (state == State.TRIPPED) {
            if (--timer <= 0) {
                state = State.OFF;
            }
        } else if (!enabled || !powered) {
            state = State.OFF;
        } else if (hasWork) {
            state = State.RUNNING;
            timer = IDLE_TICKS;
        } else if (state != State.OFF && !up.vfd()) {
            state = --timer > 0 ? State.IDLE : State.OFF;
        } else {
            state = State.OFF;
        }

        boolean energized = state == State.RUNNING || state == State.IDLE;
        undervolt = energized && volts < up.tripVolts() ? undervolt + 1 : 0;
        if (undervolt >= up.tripDelayTicks()) {
            undervolt = 0;
            state = State.TRIPPED;
            timer = TRIP_TICKS;
            tripped = true;
            energized = false;
        }

        speed = energized ? up.motorSpeed(volts, hertz) * up.speedMultiplier() : 0;
        double motorEe = switch (state) {
            case RUNNING -> ratedEe * up.reactiveMultiplier() * volts * volts;
            case IDLE -> ratedEe * IDLE_EE_FRACTION * up.reactiveMultiplier() * volts * volts;
            default -> 0;
        };
        // Built-in capacitors are switched with the motor contactor.
        ee = energized ? motorEe - up.capacitorEe(volts) : 0;
        return tripped;
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
        return state == State.IDLE ? IDLE_FE : 0;
    }

    @Override
    public void load(int state, int timer) {
        this.state = State.values()[Math.clamp(state, 0, 3)];
        this.timer = timer;
    }

    @Override
    public int timer() {
        return timer;
    }
}
