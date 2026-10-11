package com.powerfactor.api;

import net.minecraft.core.Direction;

/**
 * A block entity that plays different roles on different faces, like a transformer: a load on its
 * primary, a source on its secondary. Grids ask with the face that touches them.
 */
public interface SidedGridDevice {
    enum Role { NONE, LOAD, SOURCE, BOTH }

    Role role(Direction face);
}
