package com.powerfactor.power;

/**
 * Per-tick electrical state of a bus (a wire grid, or a generator's terminals), in per-unit:
 * 1.0 V is nominal voltage and 1.0 I is the conductor's full rated current.
 *
 * <p>The model is the textbook short-line approximation, dV ~= R*P + X*Q. Real power (FE)
 * sags voltage a little, while lagging reactive power (EE) sags it a lot because X is three times
 * R. Capacitors supply EE locally, in proportion to V^2 like real ones. Over-correcting pushes
 * the flow negative (leading) and voltage rises. Synchronous generators (turbines) can make or
 * absorb EE up to their spare CE; any EE nobody can supply collapses voltage much faster.
 *
 * <p>Line loss is R*I^2, and current is CE / V. At the same FE, a poor power factor means more
 * current, more heat in the wire, and less FE delivered.
 */
public final class BusSolver {
    /** Per-unit resistance and reactance of a fully loaded conductor. */
    public static final double R = 0.04, X = 0.12;
    /** How hard unsupplied (or unabsorbed) EE moves voltage, per unit of rating. */
    public static final double DEFICIT_GAIN = 0.6;
    public static final double MIN_VOLTS = 0.5, MAX_VOLTS = 1.25;
    /** Share of their spare capacity generators can absorb when running under-excited. */
    public static final double ABSORB_FRACTION = 0.5;

    /**
     * @param volts        per-unit voltage
     * @param current      per-unit current (1.0 is the conductor's rating)
     * @param fe           real power carried
     * @param netEe        reactive power the conductor carries: load EE minus capacitor EE (negative = leading)
     * @param generatorEe  EE generators supply (negative = they absorb)
     * @param lossFe       FE lost as heat in the conductor
     */
    public record Result(double volts, double current, double fe, double netEe, double generatorEe, double lossFe) {
        public static final Result NOMINAL = new Result(1.0, 0, 0, 0, 0, 0);

        public double ce() {
            return Math.hypot(fe, netEe);
        }

        /** FE / CE; 1.0 when idle. */
        public double powerFactor() {
            double ce = ce();
            return ce == 0 ? 1.0 : fe / ce;
        }

        /** Current lags voltage (inductive) rather than leading it (capacitive). */
        public boolean lagging() {
            return netEe >= 0;
        }
    }

    /**
     * @param ratingCe       conductor rating, CE/t
     * @param fe             real power flowing, FE/t
     * @param loadEe         motor EE demand at nominal voltage
     * @param capacitorEe    capacitor EE at nominal voltage
     * @param generatorEeCap EE synchronous generators could still supply
     * @param previousVolts  last tick's voltage, for the V^2 dependence of capacitors
     */
    public static Result solve(double ratingCe, double fe, double loadEe, double capacitorEe, double generatorEeCap, double previousVolts) {
        return solve(ratingCe, R, fe, loadEe, capacitorEe, generatorEeCap, previousVolts);
    }

    /** As above, for a conductor with per-unit resistance {@code r} (reactance 3r). */
    public static Result solve(double ratingCe, double r, double fe, double loadEe, double capacitorEe, double generatorEeCap, double previousVolts) {
        double x = 3 * r;
        if (ratingCe <= 0) {
            return Result.NOMINAL;
        }
        double v0 = Math.clamp(previousVolts, MIN_VOLTS, MAX_VOLTS);
        double netEe = loadEe - capacitorEe * v0 * v0;
        double genEe = Math.clamp(netEe, -generatorEeCap * ABSORB_FRACTION, generatorEeCap);
        double unsupplied = netEe - genEe;

        double p = fe / ratingCe;
        double q = netEe / ratingCe;
        double volts = Math.clamp(1.0 - r * p - x * q - DEFICIT_GAIN * unsupplied / ratingCe, MIN_VOLTS, MAX_VOLTS);
        double current = Math.hypot(fe, netEe) / (volts * ratingCe);
        double loss = r * current * current * ratingCe;
        return new Result(volts, current, fe, netEe, genEe, loss);
    }

    /** Induction-motor speed: torque goes with V^2 and shaft speed with frequency. */
    public static double motorSpeed(double volts, double hertz) {
        return Math.clamp(volts * volts, 0, 1) * Math.clamp(hertz / 60.0, 0, 1.05);
    }

    private BusSolver() {}
}
