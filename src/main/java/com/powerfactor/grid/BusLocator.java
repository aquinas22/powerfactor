package com.powerfactor.grid;

import com.powerfactor.api.Bus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/** Finds the bus a machine is fed from: an adjacent wire grid first, then an adjacent turbine. */
public final class BusLocator {
    public static Bus find(ServerLevel level, BlockPos pos) {
        Bus turbine = null;
        for (Direction d : Direction.values()) {
            BlockPos other = pos.relative(d);
            if (level.getBlockState(other).getBlock() instanceof WireBlock) {
                PowerGrid grid = GridManager.get(level).gridAt(other);
                if (grid != null) {
                    return grid;
                }
            } else if (turbine == null) {
                var entity = level.getBlockEntity(other);
                if (entity instanceof com.powerfactor.multiblock.MultiblockPart part) entity = part.controller();
                if (entity instanceof Bus bus) turbine = bus;
            }
        }
        return turbine != null ? turbine : Bus.NOMINAL;
    }

    private BusLocator() {}
}
