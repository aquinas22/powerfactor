package com.powerfactor.machine;

import com.powerfactor.power.UpgradeEffects;

/** What turns electricity into work in a processing machine: an induction motor or a heater. */
public interface Drive {
    enum State { OFF, IDLE, RUNNING, TRIPPED }

    /**
     * @param volts   bus voltage, pu
     * @param hertz   bus frequency
     * @param hasWork a job is ready
     * @param enabled redstone/SCADA allow running
     * @param powered the buffer holds enough FE to run
     * @return true if the drive tripped this tick (for a sound)
     */
    boolean tick(double volts, double hertz, boolean hasWork, boolean enabled, boolean powered, UpgradeEffects upgrades);

    State state();

    /** Work rate, 1.0 = stock machine at nominal voltage. */
    double speed();

    /** Net EE drawn after any built-in capacitors (negative if over-corrected). */
    double ee();

    /** FE drawn this tick when not doing work (idling motor friction), else 0. */
    int idleFe();

    void load(int state, int timer);

    int timer();
}
