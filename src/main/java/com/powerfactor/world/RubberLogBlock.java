package com.powerfactor.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Rubber wood. Logs grown by a tree are {@code natural}; natural logs slowly weep latex, shown as a
 * pale drip on the bark, which a Tree Tap collects. Player-placed logs never refill.
 */
public class RubberLogBlock extends RotatedPillarBlock {
    public static final BooleanProperty NATURAL = BooleanProperty.create("natural");
    public static final BooleanProperty LATEX = BooleanProperty.create("latex");

    public RubberLogBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(NATURAL, false).setValue(LATEX, false));
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(NATURAL) && !state.getValue(LATEX);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(6) == 0) {
            level.setBlock(pos, state.setValue(LATEX, true), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(NATURAL, LATEX);
    }
}
