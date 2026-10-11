package com.powerfactor.api;

import com.powerfactor.power.BusSolver;

/**
 * What a machine is electrically connected to: a wire grid, a turbine's terminals, or (for power
 * from other mods' generators and engines, which have no AC side) an ideal nominal supply.
 */
public interface Bus {
    /** An ideal 1.0 pu, 60 Hz supply. */
    Bus NOMINAL = new Bus() {
        @Override
        public BusSolver.Result state() {
            return BusSolver.Result.NOMINAL;
        }

        @Override
        public double hertz() {
            return 60.0;
        }
    };

    /** Last tick's solution: voltage, current, flows, losses. */
    BusSolver.Result state();

    double hertz();

    default double volts() {
        return state().volts();
    }
}
