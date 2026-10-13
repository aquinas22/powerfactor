package com.powerfactor.grid;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A thin cable that connects on each side to its own kind or to something it can talk to. */
public abstract class ConnectorBlock extends Block {
    public static final Map<Direction, BooleanProperty> CONNECTIONS = PipeBlock.PROPERTY_BY_DIRECTION;

    private final VoxelShape[] shapes = new VoxelShape[64];

    protected ConnectorBlock(Properties properties, int thickness) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (BooleanProperty p : CONNECTIONS.values()) {
            state = state.setValue(p, false);
        }
        registerDefaultState(state);

        double lo = 8 - thickness / 2.0, hi = 8 + thickness / 2.0;
        VoxelShape core = Block.box(lo, lo, lo, hi, hi, hi);
        for (int mask = 0; mask < 64; mask++) {
            VoxelShape shape = core;
            for (Direction d : Direction.values()) {
                if ((mask & (1 << d.ordinal())) != 0) {
                    shape = Shapes.or(shape, Block.box(
                            d.getStepX() < 0 ? 0 : d.getStepX() > 0 ? hi : lo,
                            d.getStepY() < 0 ? 0 : d.getStepY() > 0 ? hi : lo,
                            d.getStepZ() < 0 ? 0 : d.getStepZ() > 0 ? hi : lo,
                            d.getStepX() < 0 ? lo : d.getStepX() > 0 ? 16 : hi,
                            d.getStepY() < 0 ? lo : d.getStepY() > 0 ? 16 : hi,
                            d.getStepZ() < 0 ? lo : d.getStepZ() > 0 ? 16 : hi));
                }
            }
            shapes[mask] = shape;
        }
    }

    /** Whether to connect toward {@code side}. {@code level} may not be a full Level during worldgen. */
    protected abstract boolean connectsTo(BlockGetter level, BlockPos pos, Direction side);

    /** Called when this cable or its connections change, so networks can rebuild. */
    protected abstract void onTopologyChanged(Level level, BlockPos pos);

    public BlockState withConnections(BlockGetter level, BlockPos pos, BlockState state) {
        for (Direction d : Direction.values()) {
            state = state.setValue(CONNECTIONS.get(d), connectsTo(level, pos, d));
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withConnections(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        BlockState updated = state.setValue(CONNECTIONS.get(direction), connectsTo(level, pos, direction));
        if (updated != state && level instanceof Level l && !l.isClientSide()) onTopologyChanged(l, pos);
        return updated;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide()) {
            onTopologyChanged(level, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        onTopologyChanged(level, pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
            net.minecraft.world.level.redstone.@org.jspecify.annotations.Nullable Orientation orientation, boolean movedByPiston) {
        // A block entity appearing next to us may not have changed our shape; re-check. Changes
        // that leave our connections alone (a machine toggling its lamp) don't touch the network:
        // capability caches and per-tick lookups already follow block entities.
        BlockState updated = withConnections(level, pos, state);
        if (updated != state) {
            if (!level.isClientSide()) onTopologyChanged(level, pos);
            level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int mask = 0;
        for (Direction d : Direction.values()) {
            if (state.getValue(CONNECTIONS.get(d))) {
                mask |= 1 << d.ordinal();
            }
        }
        return shapes[mask];
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CONNECTIONS.values().toArray(BooleanProperty[]::new));
    }
}
