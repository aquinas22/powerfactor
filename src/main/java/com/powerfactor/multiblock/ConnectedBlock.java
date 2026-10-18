package com.powerfactor.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** A plain structural block (no block entity) whose controller forms it directly; connects like {@link ConnectedCasingBlock}. */
public class ConnectedBlock extends Block implements Connected.Part {
    private final Connected.Style style;

    public ConnectedBlock(Connected.Style style, Properties properties) {
        super(properties);
        this.style = style;
        registerDefaultState(Connected.unconnected(stateDefinition.any().setValue(CasingBlock.FORMED, false)));
    }

    @Override
    public Connected.Style style() {
        return style;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CasingBlock.FORMED);
        Connected.addProperties(builder);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return Connected.update(state, direction, neighbourState);
    }
}
