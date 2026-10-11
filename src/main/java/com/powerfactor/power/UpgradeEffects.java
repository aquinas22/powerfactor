package com.powerfactor.power;

/**
 * What a machine's installed upgrade cards do, as numbers. Counts are how many of each card are
 * installed (Speed, Efficiency, Capacitor and Capacity stack to 4; VFD and Surge Protector are
 * single cards).
 *
 * <ul>
 *   <li><b>Speed</b>: +50% speed per card, but a bigger motor: FE per operation goes up 15% per card
 *       and the motor's EE rises with its size.</li>
 *   <li><b>Efficiency</b>: -15% FE per operation per card (compounding).</li>
 *   <li><b>Capacitor</b> (built-in power factor correction): each card cancels 5 EE of the
 *       machine's own motor EE (scaled by V^2, like any capacitor). Three cards exactly cancel
 *       a stock crusher; the fourth covers a Speed-upgraded motor's extra EE. Too many over-correct
 *       to a leading power factor, same as on the grid.</li>
 *   <li><b>VFD</b> (variable frequency drive): the motor runs from a DC link, so its speed no longer
 *       follows grid frequency or voltage (down to 0.8 pu), it draws a quarter of the EE, and it
 *       stops instead of idling between jobs.</li>
 *   <li><b>Surge Protector</b>: undervoltage trip drops from 0.75 to 0.6 pu with a 3 s ride-through.</li>
 *   <li><b>Capacity</b>: +100% FE buffer per card.</li>
 * </ul>
 */
public record UpgradeEffects(int speed, int efficiency, int capacitor, boolean vfd, boolean surge, int capacity) {
    public static final UpgradeEffects NONE = new UpgradeEffects(0, 0, 0, false, false, 0);
    public static final int CAPACITOR_EE_PER_CARD = 5;

    public double speedMultiplier() {
        return 1 + 0.5 * speed;
    }

    /** FE per operation relative to stock. */
    public double energyPerOperation() {
        return (1 + 0.15 * speed) * Math.pow(0.85, efficiency);
    }

    /** FE per tick relative to stock, while working at full speed. */
    public double powerMultiplier() {
        return speedMultiplier() * energyPerOperation();
    }

    /** Motor EE relative to stock. */
    public double reactiveMultiplier() {
        return (1 + 0.5 * speed) * (vfd ? 0.25 : 1.0);
    }

    /** EE the built-in capacitors supply at the given voltage. */
    public double capacitorEe(double volts) {
        return capacitor * CAPACITOR_EE_PER_CARD * volts * volts;
    }

    public double bufferMultiplier() {
        return 1 + capacity;
    }

    public double tripVolts() {
        return surge ? 0.6 : 0.75;
    }

    public int tripDelayTicks() {
        return surge ? 60 : 10;
    }

    /** Motor speed fraction (before the Speed multiplier) on a bus at this voltage and frequency. */
    public double motorSpeed(double volts, double hertz) {
        if (vfd) {
            return Math.clamp((volts - 0.6) / 0.2, 0, 1);
        }
        return BusSolver.motorSpeed(volts, hertz);
    }
}
