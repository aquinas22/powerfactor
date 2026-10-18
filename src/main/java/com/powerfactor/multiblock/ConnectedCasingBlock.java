package com.powerfactor.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A casing that joins its neighbours into one seamless shell once its structure forms (see {@link Connected}). */
public class ConnectedCasingBlock extends CasingBlock implements Connected.Part {
    private final Connected.Style style;

    public ConnectedCasingBlock(Connected.Style style, VoxelShape shape, Properties properties) {
        super(shape, properties);
        this.style = style;
        registerDefaultState(Connected.unconnected(defaultBlockState()));
    }

    @Override
    public Connected.Style style() {
        return style;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        Connected.addProperties(builder);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return Connected.update(state, direction, neighbourState);
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
        return Connected.hidesFace(state, adjacent) || super.skipRendering(state, adjacent, direction);
    }
}
