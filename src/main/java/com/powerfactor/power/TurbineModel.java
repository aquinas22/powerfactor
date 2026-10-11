package com.powerfactor.power;

/**
 * Steam turbine-generator physics, free of Minecraft so it can be unit tested.
 *
 * <p>Burning fuel makes steam. Steam spins the rotor up to synchronous speed (3600 RPM, which is
 * 60 Hz on a two-pole generator) over a few seconds; without steam it coasts down. The generator
 * breaker closes once the rotor reaches 57 Hz and trips open below 54 Hz, so a turbine that runs
 * out of fuel goes offline a few seconds later rather than instantly.
 *
 * <p>While synchronized the governor follows load: fuel is spent on the FE actually delivered plus
 * a small no-load loss to keep the rotor spinning. Like a real governor it has droop: speed (and so
 * grid frequency) sags 3% from no load to full CE load, and another 3% while the generator is
 * overloaded (asked for more than its rating allows). Motors on the grid slow down with it.
 */
public final class TurbineModel {
    public static final int SYNC_RPM = 3600;
    /** Breaker closes at 57 Hz. */
    public static final int CLOSE_RPM = 3420;
    /** Breaker trips at 54 Hz. */
    public static final int TRIP_RPM = 3240;
    public static final double DROOP = 0.03;
    public static final double OVERLOAD_SAG = 0.03;

    public record Spec(int ratedFe, int ratedCe, int idleLossFe, int spinUpTicks, int coastDownTicks) {
        public Spec {
            if (ratedFe <= 0 || ratedCe < ratedFe || idleLossFe < 0 || spinUpTicks <= 0 || coastDownTicks <= 0) {
                throw new IllegalArgumentException("Bad turbine spec: " + this);
            }
        }
    }

    private final Spec spec;
    private double rpm;
    private boolean breakerClosed;
    /** FE worth of steam left from the fuel item currently burning. */
    private long fuelEnergy;
    /** FE worth of steam the current fuel item started with, for the flame gauge. */
    private long fuelEnergyMax;
    private PowerTriangle lastOutput = PowerTriangle.ZERO;
    private boolean overloaded;

    public TurbineModel(Spec spec) {
        this.spec = spec;
    }

    public boolean needsFuel() {
        return fuelEnergy <= 0;
    }

    public void addFuel(long energy) {
        fuelEnergy += energy;
        fuelEnergyMax = fuelEnergy;
    }

    /**
     * Advances one tick.
     *
     * @param enabled  false when redstone has shut the turbine down (steam valve closed)
     * @param feRoom   how much FE the output can accept this tick
     * @param eeDemand reactive power the connected load wants this tick; negative means the grid is
     *                 over-corrected and the generator must absorb EE
     * @return FE generated this tick
     */
    public int tick(boolean enabled, int feRoom, double eeDemand) {
        boolean steam = enabled && fuelEnergy > 0;

        if (steam) {
            double rate = (double) SYNC_RPM / spec.spinUpTicks();
            double target = governorTarget();
            rpm = rpm < target ? Math.min(target, rpm + rate) : Math.max(target, rpm - rate);
            fuelEnergy = Math.max(0, fuelEnergy - spec.idleLossFe());
        } else {
            rpm = Math.max(0, rpm - (double) SYNC_RPM / spec.coastDownTicks());
        }

        if (breakerClosed && rpm < TRIP_RPM) {
            breakerClosed = false;
        } else if (!breakerClosed && rpm >= CLOSE_RPM) {
            breakerClosed = true;
        }

        if (!breakerClosed || !steam) {
            lastOutput = PowerTriangle.ZERO;
            overloaded = false;
            return 0;
        }

        double ee = Math.clamp(eeDemand, -spec.ratedCe(), spec.ratedCe());
        double feLimit = Math.min(spec.ratedFe(), PowerTriangle.maxFeFor(spec.ratedCe(), ee));
        // Overloaded: EE has pushed the CE limit below rated FE and more FE is still wanted.
        overloaded = feLimit < spec.ratedFe() - 1 && feRoom > feLimit + 1 && fuelEnergy > feLimit;
        int fe = (int) Math.min(Math.min(feLimit, Math.max(0, feRoom)), fuelEnergy);
        fuelEnergy -= fe;
        lastOutput = new PowerTriangle(fe, ee);
        return fe;
    }

    /** Governor speed setpoint from last tick's loading. */
    private double governorTarget() {
        double load = Math.min(1, lastOutput.ce() / spec.ratedCe());
        return SYNC_RPM * (1 - DROOP * load - (overloaded ? OVERLOAD_SAG : 0));
    }

    public boolean overloaded() {
        return overloaded;
    }

    public Spec spec() {
        return spec;
    }

    public int rpm() {
        return (int) Math.round(rpm);
    }

    public double hertz() {
        return rpm / 60.0;
    }

    public boolean breakerClosed() {
        return breakerClosed;
    }

    public long fuelEnergy() {
        return fuelEnergy;
    }

    public long fuelEnergyMax() {
        return fuelEnergyMax;
    }

    public PowerTriangle lastOutput() {
        return lastOutput;
    }

    /** Restores saved state. */
    public void load(double rpm, boolean breakerClosed, long fuelEnergy, long fuelEnergyMax) {
        this.rpm = Math.clamp(rpm, 0, SYNC_RPM);
        this.breakerClosed = breakerClosed;
        this.fuelEnergy = Math.max(0, fuelEnergy);
        this.fuelEnergyMax = Math.max(this.fuelEnergy, fuelEnergyMax);
    }

    public double rawRpm() {
        return rpm;
    }
}
