package com.powerfactor.building;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A structural I-beam, oriented along the face used for placement. */
public final class SteelBeamBlock extends RotatedPillarBlock {
    public static final MapCodec<SteelBeamBlock> CODEC = simpleCodec(SteelBeamBlock::new);
    private static final VoxelShape[] SHAPES = new VoxelShape[3];
    static {
        for (Direction.Axis axis : Direction.Axis.values()) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : new double[][] {{3, 0, 3, 13, 16, 5}, {3, 0, 11, 13, 16, 13}, {7, 0, 5, 9, 16, 11}}) {
                if (axis == Direction.Axis.X) {
                    shape = Shapes.or(shape, Block.box(b[1], b[0], b[2], b[4], b[3], b[5]));
                } else if (axis == Direction.Axis.Z) {
                    shape = Shapes.or(shape, Block.box(b[0], b[2], b[1], b[3], b[5], b[4]));
                } else {
                    shape = Shapes.or(shape, Block.box(b[0], b[1], b[2], b[3], b[4], b[5]));
                }
            }
            SHAPES[axis.ordinal()] = shape;
        }
    }

    public SteelBeamBlock(Properties properties) { super(properties); }

    @Override
    public MapCodec<SteelBeamBlock> codec() { return CODEC; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(AXIS).ordinal()];
    }
}
