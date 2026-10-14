package com.powerfactor.api;

import net.minecraft.core.Direction;

/** Storage charges from a grid's surplus after ordinary loads; source faces receive no grid FE. */
public interface GridStorage extends SidedGridDevice {
    /** Remaining FE this storage may export this tick; zero disables automatic discharge. */
    default int gridOutputLimit(Direction face) { return 0; }
    /** Called after a committed withdrawal, including this storage's share of line heat. */
    default void onGridExport(int amount) {}
}
