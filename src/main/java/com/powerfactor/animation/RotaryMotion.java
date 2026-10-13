package com.powerfactor.animation;

/** Tick-driven rotation with inertia and continuous interpolation through a full revolution. */
public final class RotaryMotion {
    private static final double TURN = Math.PI * 2;
    private double phase, previous, speed;

    public void tick(double target, double acceleration, double braking) {
        previous = phase;
        speed += (target - speed) * Math.clamp(target > speed ? acceleration : braking, 0, 1);
        if (Math.abs(speed - target) < 0.0001) {
            speed = target;
        }
        phase += speed;
        if (phase >= TURN) {
            phase -= TURN;
            previous -= TURN;
        }
    }

    public float phase(float partialTick) {
        return (float) (previous + (phase - previous) * Math.clamp(partialTick, 0, 1));
    }

    public double speed() {
        return speed;
    }
}
