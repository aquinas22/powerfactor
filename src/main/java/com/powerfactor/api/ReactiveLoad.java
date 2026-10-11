package com.powerfactor.api;

/**
 * A block entity that draws (positive) or supplies (negative) EE, reactive power.
 * Motors draw EE while running; capacitor banks supply it. PowerFactor generators and wires on the
 * same grid see the net demand and lose FE capacity to it.
 */
public interface ReactiveLoad {
    double eeDemand();
}
