package com.powerfactor.api;

/**
 * A synchronous generator on a grid. It sets the grid's frequency and supplies (or absorbs) the
 * grid's net EE, shared out by spare capacity.
 */
public interface GridGenerator {
    double ratedCe();

    /** Electrical frequency; 0 while offline. */
    double hertz();

    /** EE it could still supply on top of its current FE output; 0 while offline. */
    double eeCapacity();
}
