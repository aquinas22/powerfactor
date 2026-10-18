package com.powerfactor.fission;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Tells every port and logic adapter in a formed reactor's shell which controller it belongs to. */
final class ReactorPorts {
    static void link(ServerLevel level, ReactorStructure.Shape shape, BlockPos controller) {
        for (BlockPos p : shape.ports()) if (level.getBlockEntity(p) instanceof ReactorPartBlockEntity part) part.link(controller);
        for (BlockPos p : shape.adapters()) if (level.getBlockEntity(p) instanceof ReactorPartBlockEntity part) part.link(controller);
    }

    static void unlink(ServerLevel level, ReactorStructure.Shape shape) {
        for (BlockPos p : shape.ports()) if (level.getBlockEntity(p) instanceof ReactorPartBlockEntity part) part.link(null);
        for (BlockPos p : shape.adapters()) if (level.getBlockEntity(p) instanceof ReactorPartBlockEntity part) part.link(null);
    }

    private ReactorPorts() {}
}
