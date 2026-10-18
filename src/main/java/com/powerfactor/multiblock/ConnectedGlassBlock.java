package com.powerfactor.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** Structural glass whose frame disappears between panes once its structure forms. */
public class ConnectedGlassBlock extends TransparentBlock implements Connected.Part {
    private final Connected.Style style;

    public ConnectedGlassBlock(Connected.Style style, Properties properties) {
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

    /** Hides the face against a liquid as well as against its own panes: the liquid draws that face itself, and two
     * translucent faces in one plane flicker (a reactor pool behind its glass). */
    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
        return !adjacent.getFluidState().isEmpty() || super.skipRendering(state, adjacent, direction);
    }

    /** A formed fission reactor draws its own pool behind its glass (see ReactorRenderer), so the water's faces against it are hidden. */
    @Override
    public boolean shouldHideAdjacentFluidFace(BlockState state, Direction selfFace, net.minecraft.world.level.material.FluidState adjacentFluid) {
        return state.getValue(CasingBlock.FORMED) && "fission".equals(style.group()) || super.shouldHideAdjacentFluidFace(state, selfFace, adjacentFluid);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return Connected.update(state, direction, neighbourState);
    }
}
