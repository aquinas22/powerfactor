package com.powerfactor.redstone;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Project-Red-style wire laid flat on the floor, climbing blocks like redstone dust. Three kinds:
 * <ul>
 * <li>Red Alloy: carries full strength along its whole run (no fade), powers what it points at and sits on;</li>
 * <li>Insulated (16 colours): joins only its own colour and red alloy, and doesn't power the block under it, so
 * different colours can run side by side;</li>
 * <li>Bundled Cable: sixteen insulated channels in one, joined to insulated wire by colour.</li>
 * </ul>
 * A connected run is solved as one network by {@link WireNet}.
 */
public class WireBlock extends Block {
    public enum Kind { ALLOY, INSULATED, BUNDLED }

    public static final EnumProperty<RedstoneSide> NORTH = BlockStateProperties.NORTH_REDSTONE, EAST = BlockStateProperties.EAST_REDSTONE,
            SOUTH = BlockStateProperties.SOUTH_REDSTONE, WEST = BlockStateProperties.WEST_REDSTONE;
    public static final Map<Direction, EnumProperty<RedstoneSide>> SIDES = Map.of(Direction.NORTH, NORTH, Direction.EAST, EAST, Direction.SOUTH, SOUTH,
            Direction.WEST, WEST);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final VoxelShape FLAT = Block.box(0, 0, 0, 16, 2, 16), DOT = Block.box(5, 0, 5, 11, 2, 11);

    private final Kind kind;
    private final int color;

    public WireBlock(Kind kind, int color, Properties properties) {
        super(properties);
        this.kind = kind;
        this.color = color;
        BlockState s = stateDefinition.any().setValue(POWERED, false);
        for (var p : SIDES.values()) s = s.setValue(p, RedstoneSide.NONE);
        registerDefaultState(s);
    }

    public Kind kind() {
        return kind;
    }

    /** Dye colour index for insulated wire, -1 otherwise. */
    public int color() {
        return color;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        throw new UnsupportedOperationException("not data-driven");
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, POWERED);
    }

    /** Whether two wires join. */
    public static boolean compatible(WireBlock a, WireBlock b) {
        return switch (a.kind) {
            case ALLOY -> b.kind != Kind.BUNDLED;
            case INSULATED -> b.kind == Kind.ALLOY || b.kind == Kind.BUNDLED || b.color == a.color;
            case BUNDLED -> b.kind != Kind.ALLOY;
        };
    }

    private boolean joins(BlockState other) {
        return other.getBlock() instanceof WireBlock w && compatible(this, w);
    }

    /** Non-wire neighbours the wire turns toward: anything redstone connects to (bundled cable joins only wire and gates). */
    private boolean pointsAt(BlockGetter level, BlockPos at, BlockState other, Direction from) {
        if (other.getBlock() instanceof WireBlock) return false;
        if (kind == Kind.BUNDLED) return other.getBlock() instanceof GateBlock;
        return other.canRedstoneConnectTo(level, at, from);
    }

    public RedstoneSide side(BlockGetter level, BlockPos pos, Direction dir) {
        BlockPos rel = pos.relative(dir);
        BlockState relState = level.getBlockState(rel);
        boolean openAbove = !level.getBlockState(pos.above()).isRedstoneConductor(level, pos.above());
        if (openAbove && relState.isFaceSturdy(level, rel, Direction.UP) && joins(level.getBlockState(rel.above()))) {
            return RedstoneSide.UP;
        }
        if (joins(relState) || pointsAt(level, rel, relState, dir)) return RedstoneSide.SIDE;
        if (!relState.isRedstoneConductor(level, rel) && joins(level.getBlockState(rel.below()))) return RedstoneSide.SIDE;
        return RedstoneSide.NONE;
    }

    public BlockState connect(BlockGetter level, BlockPos pos, BlockState state) {
        for (var e : SIDES.entrySet()) state = state.setValue(e.getValue(), side(level, pos, e.getKey()));
        // As with redstone dust, a line that ends carries on straight, so it powers the block in front of it.
        boolean n = state.getValue(NORTH) != RedstoneSide.NONE, s = state.getValue(SOUTH) != RedstoneSide.NONE,
                e = state.getValue(EAST) != RedstoneSide.NONE, w = state.getValue(WEST) != RedstoneSide.NONE;
        if (n || s || e || w) {
            if (!w && !n && !s) state = state.setValue(WEST, RedstoneSide.SIDE);
            if (!e && !n && !s) state = state.setValue(EAST, RedstoneSide.SIDE);
            if (!n && !e && !w) state = state.setValue(NORTH, RedstoneSide.SIDE);
            if (!s && !e && !w) state = state.setValue(SOUTH, RedstoneSide.SIDE);
        }
        return state;
    }

    /** The positions of wire this one links to (same level, up a step or down a step). */
    public java.util.List<BlockPos> links(BlockGetter level, BlockPos pos, BlockState state) {
        java.util.List<BlockPos> out = new java.util.ArrayList<>(6);
        for (var e : SIDES.entrySet()) {
            RedstoneSide s = state.getValue(e.getValue());
            if (s == RedstoneSide.NONE) continue;
            BlockPos rel = pos.relative(e.getKey());
            if (s == RedstoneSide.UP && joins(level.getBlockState(rel.above()))) out.add(rel.above());
            else if (joins(level.getBlockState(rel))) out.add(rel);
            else if (joins(level.getBlockState(rel.below())) && !level.getBlockState(rel).isRedstoneConductor(level, rel)) out.add(rel.below());
        }
        return out;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return connect(ctx.getLevel(), ctx.getClickedPos(), defaultBlockState());
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (direction == Direction.DOWN) return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
        if (direction == Direction.UP) return connect(level, pos, state);
        return connect(level, pos, state);
    }

    @Override
    protected void updateIndirectNeighbourShapes(BlockState state, net.minecraft.world.level.LevelAccessor level, BlockPos pos, int flags, int limit) {
        // Wires one step up or down diagonally need to re-check their climb.
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (BlockPos p : new BlockPos[] {pos.relative(d).above(), pos.relative(d).below()}) {
                BlockState s = level.getBlockState(p);
                if (s.getBlock() instanceof WireBlock w) {
                    BlockState now = w.connect(level, p, s);
                    if (now != s) level.setBlock(p, now, flags & ~Block.UPDATE_NEIGHBORS, limit);
                }
            }
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (level.isClientSide() || old.is(this)) return;
        // Placed by code (structures, commands) skips getStateForPlacement: find our connections now.
        BlockState joined = connect(level, pos, state);
        if (joined != state) level.setBlock(pos, joined, Block.UPDATE_CLIENTS);
        WireNet.update((ServerLevel) level, pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        if (moved) return;
        level.updateNeighborsAt(pos, this);
        level.updateNeighborsAt(pos.below(), this);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (BlockPos p : new BlockPos[] {pos.relative(d), pos.relative(d).above(), pos.relative(d).below()}) {
                if (level.getBlockState(p).getBlock() instanceof WireBlock) WireNet.update(level, p);
            }
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean moved) {
        if (level instanceof ServerLevel s && !WireNet.solving()) {
            if (!canSurvive(state, level, pos)) {
                level.destroyBlock(pos, true);
                return;
            }
            WireNet.update(s, pos);
        }
    }

    // ---------------------------------------------------------------- signals

    @Override
    protected boolean isSignalSource(BlockState state) {
        return kind != Kind.BUNDLED;
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return kind != Kind.BUNDLED && direction != null && direction.getAxis().isHorizontal();
    }

    private static boolean isDot(BlockState s) {
        for (var p : SIDES.values()) if (s.getValue(p) != RedstoneSide.NONE) return false;
        return true;
    }

    /** {@code direction}: from the block asking, toward this wire (vanilla's convention). */
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (WireNet.suppressed() || kind == Kind.BUNDLED || !state.getValue(POWERED) || direction == Direction.DOWN) return 0;
        if (direction == Direction.UP) return kind == Kind.ALLOY ? 15 : 0;
        return isDot(state) || state.getValue(SIDES.get(direction.getOpposite())) != RedstoneSide.NONE ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return isDot(state) ? DOT : FLAT;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }
}
