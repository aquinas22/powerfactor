package com.powerfactor.power;

/**
 * A utility wind turbine's control logic, as real ones work:
 *
 * <ul>
 *   <li><b>Power curve.</b> Nothing below the cut-in speed; between cut-in and rated, power grows with
 *       the cube of wind speed (the energy in moving air); from rated up to cut-out, the blades pitch
 *       to spill the excess and output holds at rated.</li>
 *   <li><b>Storm cut-out.</b> Above cut-out speed the blades feather and the rotor brakes. It restarts
 *       only after the wind has stayed below the restart speed for a while (hysteresis, so gusty
 *       storms don't cycle it).</li>
 *   <li><b>Pitch.</b> Fine pitch (0 degrees) below rated, pitched out above it, about 65 degrees idling
 *       in light air, 88 degrees feathered when stopped.</li>
 * </ul>
 *
 * Speeds are m/s. Plain Java so it is unit-tested.
 */
public final class WindModel {
    public static final double CUT_IN = 3.0, RATED = 12.0, CUT_OUT = 25.0, RESTART = 20.0;
    public static final double MAX_RPM = 16.0;
    public static final double IDLE_PITCH = 65.0, FEATHER = 88.0;
    /** Ticks the wind must stay below {@link #RESTART} before a storm-stopped turbine restarts. */
    public static final int RESTART_TICKS = 600;

    public enum Mode {
        /** Producing. */
        RUNNING,
        /** Below cut-in: blades idle at coarse pitch, waiting. */
        LOW_WIND,
        /** Storm: feathered and braked until the wind drops. */
        CUT_OUT,
        /** Switched off, or the rotor is obstructed. */
        STOPPED
    }

    private Mode mode = Mode.LOW_WIND;
    private int calmTicks;

    /** Advance the controller by {@code ticks} with wind speed {@code v}; {@code available} false = switched off/obstructed. */
    public Mode update(double v, boolean available, int ticks) {
        if (mode == Mode.CUT_OUT) {
            calmTicks = v < RESTART ? calmTicks + ticks : 0;
            if (calmTicks < RESTART_TICKS) {
                return mode;
            }
        }
        if (v >= CUT_OUT) {
            mode = Mode.CUT_OUT;
            calmTicks = 0;
        } else if (!available) {
            mode = Mode.STOPPED;
        } else {
            mode = v < CUT_IN ? Mode.LOW_WIND : Mode.RUNNING;
        }
        return mode;
    }

    public Mode mode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    /** Fraction of rated power at wind speed {@code v} (ignores cut-out; see {@link #update}). */
    public static double powerFraction(double v) {
        if (v < CUT_IN) {
            return 0;
        }
        if (v >= RATED) {
            return 1;
        }
        double in3 = CUT_IN * CUT_IN * CUT_IN;
        return (v * v * v - in3) / (RATED * RATED * RATED - in3);
    }

    /** Rotor speed the controller holds at wind speed {@code v} while running. */
    public static double rpm(double v) {
        return v < CUT_IN ? 0 : Math.min(MAX_RPM, MAX_RPM * (0.35 + 0.65 * (v - CUT_IN) / (RATED - CUT_IN)));
    }

    /** Blade pitch for a mode and wind speed, in degrees (0 = fine, 90 = edge-on to the wind). */
    public static double pitch(Mode mode, double v) {
        return switch (mode) {
            case RUNNING -> v <= RATED ? 0 : Math.min(30, 2.2 * (v - RATED));
            case LOW_WIND -> IDLE_PITCH;
            case CUT_OUT, STOPPED -> FEATHER;
        };
    }
}
