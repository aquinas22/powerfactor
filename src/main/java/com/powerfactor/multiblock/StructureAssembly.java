package com.powerfactor.multiblock;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

/** Exclusive ownership and lifecycle of a controller's formed casing ports. */
public final class StructureAssembly {
    private final Set<BlockPos> linked = new LinkedHashSet<>();
    private boolean initialized, formed;

    public boolean formed() { return formed; }
    public Set<BlockPos> ports() { return java.util.Collections.unmodifiableSet(linked); }

    public void tick(ServerLevel level, BlockPos origin, Direction facing, Structure structure) {
        if (initialized && level.getGameTime() % 20 != Math.floorMod(origin.asLong(), 20)) return;
        initialized = true;
        boolean complete = true;
        Set<BlockPos> parts = new LinkedHashSet<>();
        for (var part : structure.parts()) {
            BlockPos at = structure.worldPos(origin, facing, part);
            if (!level.hasChunkAt(at) || !part.matches(level.getBlockState(at))
                    || !(level.getBlockEntity(at) instanceof CasingBlockEntity casing)
                    || casing.controllerPos() != null && !origin.equals(casing.controllerPos())) {
                complete = false;
            }
            parts.add(at);
        }
        if (!complete) parts.clear();
        for (BlockPos at : linked) {
            if (!parts.contains(at) && level.hasChunkAt(at) && level.getBlockEntity(at) instanceof CasingBlockEntity casing
                    && origin.equals(casing.controllerPos())) casing.link(null);
        }
        for (BlockPos at : parts) {
            if (level.getBlockEntity(at) instanceof CasingBlockEntity casing) casing.link(origin);
        }
        linked.clear();
        linked.addAll(parts);
        formed = complete;
        var current = level.getBlockState(origin);
        if (current.getValue(CasingBlock.FORMED) != formed) {
            level.setBlock(origin, current.setValue(CasingBlock.FORMED, formed), Block.UPDATE_ALL);
        }
    }

    public void release(ServerLevel level, BlockPos origin) {
        for (BlockPos at : linked) {
            if (level.hasChunkAt(at) && level.getBlockEntity(at) instanceof CasingBlockEntity casing
                    && origin.equals(casing.controllerPos())) casing.link(null);
        }
        linked.clear();
        formed = initialized = false;
    }
}
