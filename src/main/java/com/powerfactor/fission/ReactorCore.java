package com.powerfactor.fission;

/**
 * The reactor's physics, free of Minecraft so it can be unit-tested. One delayed-neutron group point
 * kinetics drives the neutron population; fuel temperature feeds back through Doppler broadening and the
 * coolant's steam voids; iodine-135 decays into xenon-135, which soaks up neutrons (so a reactor that has
 * just been shut down from high power can't restart for a while: the iodine pit); and decay heat keeps
 * warming the core after a SCRAM. Heat leaves through the coolant: water boils straight into steam;
 * heavy water, liquid sodium and molten salt run round a closed loop and come out hot, for a Heat
 * Exchanger to raise steam from (see {@link Coolant}).
 *
 * <p>Units are game units: power and heat in FE/t (thermal), temperatures in kelvin, fluids in mB.
 */
public final class ReactorCore {
    // Kinetics (shortened timescales so things happen in game minutes, not hours).
    public static final double BETA = 0.0065, GEN_TIME = 0.02, DECAY_C = 0.08, SOURCE = 1e-6;
    // Reactivity: per-column rod worth, Doppler and void coefficients, xenon worth at equilibrium full power.
    public static final double ROD_WORTH = 0.12, DOPPLER = 2.5e-5, VOID_WATER = 0.04, XENON_WORTH = 0.028;
    // Iodine/xenon (per tick): yields per unit power, decay constants, burn-out by flux.
    public static final double I_YIELD = 1.0, X_YIELD = 0.1, I_DECAY = 1.0 / 6000, X_DECAY = 1.0 / 9000, X_BURN = 1.0 / 900;
    // Heat.
    public static final double AMBIENT = 300, FE_PER_STEAM = 20, BOIL_K = 373, HEAT_CAP = 40_000, COOLING = 0.3;
    public static final double DECAY_HEAT = 0.065, DECAY_HEAT_TAU = 2400;
    public static final double DAMAGE_K = 1200, MELT = 100;

    /** Per-assembly thermal power at n = 1 (FE/t). */
    public static final double ASSEMBLY_POWER = 4000;

    public double n = 0, precursors = 0, iodine = 0, xenon = 0;
    public double fuelTemp = AMBIENT, decayHeat = 0, damage = 0;
    public double reactivity, k, power, steamMade, heatToCoolant;
    public double voidFraction;
    /** Coolant used this tick (mB), and what it is. */
    public double coolantUsed;
    public Coolant coolant = Coolant.WATER;

    /**
     * What carries the heat away. Water boils in the core: its steam voids push reactivity down, so a
     * hot water reactor holds itself back. The loop coolants stay liquid and leave hot, carrying more heat
     * per mB; they only "void" when the loop runs short, and each changes the core:
     * <ul>
     * <li>Heavy water moderates better (+2% reactivity) and wastes fewer neutrons (fuel lasts a third longer),
     * but losing it adds a little reactivity (as in a CANDU).</li>
     * <li>Liquid sodium takes heat three times as well, so a core runs far denser; but losing sodium
     * <em>adds</em> reactivity, and its harder spectrum costs a little moderation.</li>
     * <li>Molten salt takes heat twice as well and lets the fuel run 400 K hotter before it is damaged.</li>
     * </ul>
     */
    public enum Coolant {
        WATER(1.0, VOID_WATER, 0, 1.0, 0, false, 200),
        HEAVY_WATER(1.0, -0.002, 0.02, 0.75, 0, true, 200),
        LIQUID_SODIUM(3.0, -0.004, -0.01, 1.0, 0, true, 300),
        MOLTEN_SALT(2.0, 0, 0, 1.0, 400, true, 400);

        /** Heat-transfer multiple, void coefficient (positive: steam lowers reactivity), reactivity bonus, burn-rate multiple,
         *  extra damage margin (K), whether it loops (comes out hot instead of boiling), heat carried per mB (FE). */
        public final double cooling, voidCoefficient, moderation, burn, marginK;
        public final boolean loop;
        public final int heatPerMb;

        Coolant(double cooling, double voidCoefficient, double moderation, double burn, double marginK, boolean loop, int heatPerMb) {
            this.cooling = cooling;
            this.voidCoefficient = voidCoefficient;
            this.moderation = moderation;
            this.burn = burn;
            this.marginK = marginK;
            this.loop = loop;
            this.heatPerMb = heatPerMb;
        }

        /** Output made per mB of coolant: steam for water (10 mB), the same mB of hot coolant for a loop. */
        public double outputPerMb() {
            return loop ? 1 : 10;
        }
    }

    /**
     * One tick.
     *
     * @param kInf          the core's multiplication with rods out and cold (fuel, moderation)
     * @param rodInsertion  mean control-rod insertion, 0..1
     * @param assemblies    fuel assemblies holding a rod (scales power)
     * @param water         coolant water available this tick (mB)
     * @param mox           fraction of MOX rods (burn hotter, less delayed neutrons)
     * @return water used (mB)
     */
    public double tick(double kInf, double rodInsertion, int assemblies, double water, double mox) {
        return tick(kInf, rodInsertion, assemblies, water, mox, Coolant.WATER);
    }

    /**
     * One tick with the given coolant; {@code available} is the coolant that can pass through the core this tick (mB).
     * Afterwards {@link #steamMade} holds the output made: steam for water, hot coolant for a loop.
     *
     * @return coolant used (mB)
     */
    public double tick(double kInf, double rodInsertion, int assemblies, double available, double mox, Coolant c) {
        coolant = c;
        double water = available;
        double beta = BETA * (1 - 0.35 * mox);
        double xenonEq = xenonWorth();
        reactivity = (kInf - 1) / Math.max(kInf, 1e-6) + (assemblies > 0 ? c.moderation : 0) - ROD_WORTH * rodInsertion - xenonEq
                - DOPPLER * (fuelTemp - AMBIENT) - c.voidCoefficient * voidFraction;
        k = 1 / (1 - reactivity);
        if (assemblies == 0) reactivity = -1;
        // Point kinetics, sub-stepped for stability.
        for (int s = 0; s < 10; s++) {
            double dt = 0.1;
            double dn = ((reactivity - beta) / GEN_TIME) * n + DECAY_C * precursors + SOURCE * assemblies;
            double dc = (beta / GEN_TIME) * n - DECAY_C * precursors;
            n = Math.max(0, n + dn * dt);
            precursors = Math.max(0, precursors + dc * dt);
            n = Math.min(n, 50);   // physical runaway cap; the heat will do the rest
        }
        power = n * ASSEMBLY_POWER * assemblies;
        // Iodine and xenon follow the power level.
        double rel = n;
        iodine += I_YIELD * rel * 0.001 - I_DECAY * iodine;
        xenon += X_YIELD * rel * 0.001 + I_DECAY * iodine - X_DECAY * xenon - X_BURN * rel * xenon;
        // Decay heat follows recent fission power, fading after shutdown.
        decayHeat += (DECAY_HEAT * power - decayHeat) / DECAY_HEAT_TAU * (power * DECAY_HEAT > decayHeat ? 20 : 1);
        double heat = power + decayHeat;
        // Coolant takes heat by temperature difference: water boils (1 mB → 10 mB steam at 20 FE each),
        // a loop coolant leaves hot carrying its heat per mB.
        double canTake = Math.max(0, (fuelTemp - BOIL_K) * COOLING * c.cooling * Math.max(1, assemblies) * 40);
        double maxByCoolant = water * c.heatPerMb;
        heatToCoolant = Math.min(canTake, maxByCoolant);
        coolantUsed = heatToCoolant / c.heatPerMb;
        steamMade = coolantUsed * c.outputPerMb();
        if (c.loop) {
            // A loop never boils; it voids only when there isn't enough of it to carry the heat away.
            double needed = canTake / c.heatPerMb;
            voidFraction = needed <= 1e-9 ? (water <= 0 && heat > 1 ? 1 : 0) : Math.clamp(1 - water / needed, 0, 1);
        } else {
            double steamPossible = water * 10;
            voidFraction = Math.min(1, water <= 0 ? 1 : steamMade / Math.max(1, steamPossible) * 0.6);
        }
        fuelTemp += (heat - heatToCoolant) / HEAT_CAP - (fuelTemp - AMBIENT) * 1e-4;
        fuelTemp = Math.max(AMBIENT, fuelTemp);
        double damageK = DAMAGE_K + c.marginK;
        if (fuelTemp > damageK) damage = Math.min(MELT, damage + (fuelTemp - damageK) * 0.0005);
        else damage = Math.max(0, damage - 0.002);
        return coolantUsed;
    }

    /** Xenon reactivity: XENON_WORTH at the full-power equilibrium, saturating at twice that (the deepest iodine pit). */
    public double xenonWorth() {
        double eq = (X_YIELD * 0.001 + I_YIELD * 0.001) / (X_DECAY + X_BURN);
        double x = xenon / eq;
        return XENON_WORTH * 2 * x / (x + 1);
    }

    /** Reactor period in seconds (e-folding of power), infinite when steady; negative when falling. */
    public double period() {
        double rho = reactivity;
        if (Math.abs(rho) < 1e-5) return Double.POSITIVE_INFINITY;
        return rho > 0 ? (BETA - rho) / (DECAY_C * rho) / 20 : (BETA - rho) / (DECAY_C * rho) / 20;
    }

    public void scramDecay() {}

    public boolean meltdown() {
        return damage >= MELT;
    }
}
