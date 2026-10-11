package com.powerfactor.logistics;

import com.mojang.serialization.MapCodec;
import com.powerfactor.registry.ModBlockEntities;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * An item or fluid pipe. Arms reach other pipes of the same kind and anything with a matching
 * inventory. Endpoint arms insert by default; right-click an arm with an empty hand to make it
 * extract (pull from that block into the network), then disabled, then back.
 */
public class PipeBlock extends BaseEntityBlock {
    public static final Map<Direction, EnumProperty<PipeSide>> SIDES = new EnumMap<>(Direction.class);

    static {
        for (Direction d : Direction.values()) {
            SIDES.put(d, EnumProperty.create(d.getSerializedName(), PipeSide.class));
        }
    }

    private final PipeKind kind;
    private final MapCodec<PipeBlock> codec;
    private final VoxelShape[] shapes = new VoxelShape[64];

    public PipeBlock(PipeKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        this.codec = simpleCodec(p -> new PipeBlock(kind, p));
        BlockState state = stateDefinition.any();
        for (var p : SIDES.values()) {
            state = state.setValue(p, PipeSide.NONE);
        }
        registerDefaultState(state);
        VoxelShape core = Block.box(5, 5, 5, 11, 11, 11);
        for (int mask = 0; mask < 64; mask++) {
            VoxelShape s = core;
            for (Direction d : Direction.values()) {
                if ((mask & 1 << d.ordinal()) != 0) {
                    s = Shapes.or(s, Block.box(
                            d.getStepX() < 0 ? 0 : d.getStepX() > 0 ? 11 : 5, d.getStepY() < 0 ? 0 : d.getStepY() > 0 ? 11 : 5,
                            d.getStepZ() < 0 ? 0 : d.getStepZ() > 0 ? 11 : 5, d.getStepX() < 0 ? 5 : d.getStepX() > 0 ? 16 : 11,
                            d.getStepY() < 0 ? 5 : d.getStepY() > 0 ? 16 : 11, d.getStepZ() < 0 ? 5 : d.getStepZ() > 0 ? 16 : 11));
                }
            }
            shapes[mask] = s;
        }
    }

    public PipeKind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PipeBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!(level instanceof ServerLevel server) || !hasExtract(state)) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.PIPE.get(), (l, pos, s, pipe) -> pipe.serverTick(server, pos, s, kind));
    }

    public static boolean hasExtract(BlockState state) {
        for (var p : SIDES.values()) {
            if (state.getValue(p) == PipeSide.EXTRACT) {
                return true;
            }
        }
        return false;
    }

    /** What this side should be, keeping a player's extract choice on endpoints. */
    private PipeSide compute(BlockGetter level, BlockPos pos, Direction side, PipeSide current) {
        if (level.getBlockEntity(pos) instanceof PipeBlockEntity pipe && pipe.disabled(side)) {
            return PipeSide.NONE;
        }
        BlockPos other = pos.relative(side);
        BlockState neighbour = level.getBlockState(other);
        if (neighbour.getBlock() == this) {
            if (level.getBlockEntity(other) instanceof PipeBlockEntity n && n.disabled(side.getOpposite())) {
                return PipeSide.NONE;
            }
            return PipeSide.PIPE;
        }
        if (neighbour.getBlock() instanceof PipeBlock) {
            return PipeSide.NONE;
        }
        if (level instanceof Level l && l.getCapability(kind.capability, other, side.getOpposite()) != null) {
            return current == PipeSide.EXTRACT ? PipeSide.EXTRACT : PipeSide.INSERT;
        }
        return PipeSide.NONE;
    }

    private BlockState refresh(BlockGetter level, BlockPos pos, BlockState state) {
        for (Direction d : Direction.values()) {
            state = state.setValue(SIDES.get(d), compute(level, pos, d, state.getValue(SIDES.get(d))));
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return refresh(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        var p = SIDES.get(direction);
        return state.setValue(p, compute(level, pos, direction, state.getValue(p)));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
            net.minecraft.world.level.redstone.@Nullable Orientation orientation, boolean movedByPiston) {
        BlockState updated = refresh(level, pos, state);
        if (updated != state) {
            level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        Direction side = armAt(hit.getLocation().subtract(Vec3.atCenterOf(pos)), hit.getDirection());
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof PipeBlockEntity pipe)) {
            return InteractionResult.PASS;
        }
        PipeSide current = state.getValue(SIDES.get(side));
        String key;
        if (pipe.disabled(side)) {
            pipe.setDisabled(side, false);
            key = "message.powerfactor.pipe.enabled";
        } else if (current == PipeSide.INSERT) {
            level.setBlock(pos, state.setValue(SIDES.get(side), PipeSide.EXTRACT), Block.UPDATE_ALL);
            key = "message.powerfactor.pipe.extract";
        } else if (current.connected()) {
            pipe.setDisabled(side, true);
            key = "message.powerfactor.pipe.disabled";
        } else {
            return InteractionResult.PASS;
        }
        BlockState now = level.getBlockState(pos);
        level.setBlock(pos, refresh(level, pos, now), Block.UPDATE_ALL);
        // A neighbour pipe needs to drop or regain its arm toward us too.
        BlockPos other = pos.relative(side);
        BlockState otherState = level.getBlockState(other);
        if (otherState.getBlock() == this) {
            level.setBlock(other, ((PipeBlock) otherState.getBlock()).refresh(level, other, otherState), Block.UPDATE_ALL);
        }
        player.sendOverlayMessage(Component.translatable(key, Component.translatable("direction.powerfactor." + side.getSerializedName())));
        return InteractionResult.SUCCESS;
    }

    /** The arm a click landed on: the axis the hit point sticks out along most, else the clicked face. */
    private static Direction armAt(Vec3 local, Direction face) {
        double ax = Math.abs(local.x), ay = Math.abs(local.y), az = Math.abs(local.z);
        double max = Math.max(ax, Math.max(ay, az));
        if (max < 3.0 / 16) {
            return face;
        }
        if (max == ax) return local.x > 0 ? Direction.EAST : Direction.WEST;
        if (max == ay) return local.y > 0 ? Direction.UP : Direction.DOWN;
        return local.z > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int mask = 0;
        for (Direction d : Direction.values()) {
            if (state.getValue(SIDES.get(d)).connected()) {
                mask |= 1 << d.ordinal();
            }
        }
        return shapes[mask];
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SIDES.values().toArray(EnumProperty[]::new));
    }
}
