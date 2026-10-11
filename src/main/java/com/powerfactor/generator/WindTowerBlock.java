package com.powerfactor.generator;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A section of tubular steel wind turbine tower. Sections taper as they rise: TAPER counts the tower
 * sections stacked above this one (0 = just under the nacelle, thinnest). The bottom section sits on
 * a concrete foundation with a service door. Both are worked out from the neighbours, so a tower
 * reshapes itself as it is built.
 */
public class WindTowerBlock extends Block {
    public static final MapCodec<WindTowerBlock> CODEC = simpleCodec(WindTowerBlock::new);
    public static final int MAX_TAPER = 7;
    public static final IntegerProperty TAPER = IntegerProperty.create("taper", 0, MAX_TAPER);
    public static final BooleanProperty BASE = BooleanProperty.create("base");
    private static final VoxelShape[] SHAPES = new VoxelShape[MAX_TAPER + 1];
    private static final VoxelShape FOOTING = Block.box(1, 0, 1, 15, 2, 15);

    static {
        for (int t = 0; t <= MAX_TAPER; t++) {
            double h = width(t) / 2;
            SHAPES[t] = Block.box(8 - h, 0, 8 - h, 8 + h, 16, 8 + h);
        }
    }

    /** Flat-to-flat width of the octagonal tube, in pixels. */
    public static double width(int taper) {
        return 7 + taper * 0.5;
    }

    public WindTowerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TAPER, 0).setValue(BASE, true));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TAPER, BASE);
    }

    private BlockState shaped(BlockState state, BlockState above, BlockState below) {
        int taper = above.getBlock() instanceof WindTowerBlock ? Math.min(MAX_TAPER, above.getValue(TAPER) + 1) : 0;
        return state.setValue(TAPER, taper).setValue(BASE, !(below.getBlock() instanceof WindTowerBlock));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        return shaped(defaultBlockState(), context.getLevel().getBlockState(pos.above()), context.getLevel().getBlockState(pos.below()));
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (direction.getAxis() != Direction.Axis.Y) {
            return state;
        }
        return shaped(state, level.getBlockState(pos.above()), level.getBlockState(pos.below()));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape tube = SHAPES[state.getValue(TAPER)];
        return state.getValue(BASE) ? Shapes.or(tube, FOOTING) : tube;
    }
}
