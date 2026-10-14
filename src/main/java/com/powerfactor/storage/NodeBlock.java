package com.powerfactor.storage;

import com.mojang.serialization.MapCodec;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Every storage-network device: six-way facing, an ACTIVE state for the online lights, a block
 * entity, and a GUI on use. Ports face the block they were placed against; the rest face the player.
 */
public class NodeBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public enum Placement { PLAYER, HORIZONTAL, AGAINST }

    private final Supplier<? extends BlockEntityType<? extends NodeBlockEntity>> type;
    private final Placement placement;
    private final VoxelShape[] shapes;

    public NodeBlock(Supplier<? extends BlockEntityType<? extends NodeBlockEntity>> type, Placement placement, @Nullable VoxelShape northShape,
            Properties properties) {
        super(properties);
        this.type = type;
        this.placement = placement;
        this.shapes = new VoxelShape[6];
        for (Direction d : Direction.values()) {
            shapes[d.ordinal()] = northShape == null ? Shapes.block() : rotate(northShape, d);
        }
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }

    /** Rotates a shape authored facing north (toward -Z) to face {@code d}. */
    static VoxelShape rotate(VoxelShape north, Direction d) {
        VoxelShape[] out = {Shapes.empty()};
        north.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
            double[] a = map(x0, y0, z0, d), b = map(x1, y1, z1, d);
            out[0] = Shapes.or(out[0], Shapes.box(Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
                    Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2])));
        });
        return out[0];
    }

    private static double[] map(double x, double y, double z, Direction d) {
        return switch (d) {
            case NORTH -> new double[] {x, y, z};
            case SOUTH -> new double[] {1 - x, y, 1 - z};
            case EAST -> new double[] {1 - z, y, x};
            case WEST -> new double[] {z, y, 1 - x};
            case UP -> new double[] {x, 1 - z, y};
            case DOWN -> new double[] {x, z, 1 - y};
        };
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        throw new UnsupportedOperationException("not data-driven");
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return type.get().create(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> t) {
        if (t != type.get()) return null;
        if (level.isClientSide()) {
            return (l, p, s, be) -> ((NodeBlockEntity) be).clientTick();
        }
        return (l, p, s, be) -> {
            if (be instanceof StorageControllerBlockEntity c) c.serverTick((ServerLevel) l);
        };
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = switch (placement) {
            case PLAYER -> context.getNearestLookingDirection().getOpposite();
            case HORIZONTAL -> context.getHorizontalDirection().getOpposite();
            case AGAINST -> context.getClickedFace().getOpposite();
        };
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof NodeBlockEntity node && node.hasMenu()) {
            player.openMenu(node, buf -> node.writeMenuData(buf));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        StorageNetworks.invalidate(level, pos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes[state.getValue(FACING).ordinal()];
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }
}
