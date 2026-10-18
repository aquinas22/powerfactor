package com.powerfactor.blackhole;

/**
 * A contained micro black hole, game-scaled but shaped like the real thing. Mass M (in "mass units"; one
 * ordinary item is one unit). Hawking radiation grows as the hole shrinks (P ∝ 1/M²) and evaporates it
 * (dM/dt = −P/c²); infalling matter adds mass and, on the way down, releases a fixed fraction of its rest
 * energy as accretion light. Holding it costs a containment field that grows with the horizon (∝ M).
 * Too small and it flashes away in a burst; let containment fail and it escapes.
 */
public final class BlackHoleCore {
    /** FE per mass unit of rest energy. */
    public static final double C2 = 20_000_000;
    /** Hawking scale: P = HAWKING / M² (FE/t). */
    public static final double HAWKING = 1.0e12;
    public static final double ACCRETION_EFFICIENCY = 0.3;
    /** Containment cost per mass unit per tick. */
    public static final double CONTAIN_PER_MASS = 100;
    public static final double MIN_MASS = 50, MAX_MASS = 100_000;

    public double mass;
    public double hawking, accretion, containment, net;
    public boolean burst, escaped;

    /** One tick: feed (mass units this tick), containment power available (FE). Returns gross output FE. */
    public double tick(double feed, double containmentAvailable) {
        if (mass <= 0 || burst || escaped) return 0;
        hawking = HAWKING / (mass * mass);
        accretion = feed * C2 * ACCRETION_EFFICIENCY;
        containment = CONTAIN_PER_MASS * mass;
        mass += feed * (1 - ACCRETION_EFFICIENCY) - hawking / C2;
        if (mass < MIN_MASS) burst = true;
        if (containmentAvailable < containment || mass > MAX_MASS) escaped = true;
        net = hawking + accretion - containment;
        return hawking + accretion;
    }

    /** Mass units per tick that hold the hole at its present mass (replacing what evaporates). */
    public double holdFeed() {
        return mass <= 0 ? 0 : HAWKING / (mass * mass) / C2 / (1 - ACCRETION_EFFICIENCY);
    }

    public double horizonRadius() {
        return 0.15 + 0.35 * Math.cbrt(mass / 1000);
    }
}
