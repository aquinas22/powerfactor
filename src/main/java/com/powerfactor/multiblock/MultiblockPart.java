package com.powerfactor.multiblock;

import org.jspecify.annotations.Nullable;
import net.minecraft.world.level.block.entity.BlockEntity;

/** A block entity that is a piece of a larger machine; grids and pipes talk to its controller instead. */
public interface MultiblockPart {
    @Nullable BlockEntity controller();
}
