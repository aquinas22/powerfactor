package com.powerfactor.building;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Thin steel floor; solid footing over a visibly open grid of bars. */
public final class CatwalkBlock extends Block {
    public static final MapCodec<CatwalkBlock> CODEC = simpleCodec(CatwalkBlock::new);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 2, 16);
    public CatwalkBlock(Properties properties) { super(properties); }
    @Override
    protected MapCodec<CatwalkBlock> codec() { return CODEC; }
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
}
