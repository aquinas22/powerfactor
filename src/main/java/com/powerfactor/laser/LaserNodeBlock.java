package com.powerfactor.laser;

import com.mojang.serialization.MapCodec;
import com.powerfactor.registry.ModLaser;
import java.util.EnumMap;
import java.util.Map;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Laser Node: clings to the face of a machine, chest or tank (FACING points at that block) and links by
 * laser to other nodes. Use it to open its settings; link it with a Laser Wrench.
 */
public class LaserNodeBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        // A squat emitter: a base plate on the attached face and a lens dome toward open air.
        for (Direction d : Direction.values()) {
            VoxelShape base = box(d, 3, 0, 3, 13, 3, 13), dome = box(d, 5, 3, 5, 11, 7, 11);
            SHAPES.put(d, Shapes.or(base, dome));
        }
    }

    /** A box given as if the node faced DOWN (attached below); rotated to {@code facing}. */
    private static VoxelShape box(Direction facing, double x0, double y0, double z0, double x1, double y1, double z1) {
        return switch (facing) {
            case DOWN -> Block.box(x0, y0, z0, x1, y1, z1);
            case UP -> Block.box(x0, 16 - y1, z0, x1, 16 - y0, z1);
            case NORTH -> Block.box(x0, z0, y0, x1, z1, y1);
            case SOUTH -> Block.box(x0, z0, 16 - y1, x1, z1, 16 - y0);
            case WEST -> Block.box(y0, z0, x0, y1, z1, x1);
            case EAST -> Block.box(16 - y1, z0, x0, 16 - y0, z1, x1);
        };
    }

    public LaserNodeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        throw new UnsupportedOperationException("not data-driven");
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaserNodeBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel server
                ? createTickerHelper(type, ModLaser.LASER_NODE_BE.get(), (l, p, s, node) -> node.serverTick(server))
                : null;
    }

    /** A Laser Wrench links instead of opening the settings. */
    @Override
    protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        return stack.getItem() instanceof LaserWrenchItem ? InteractionResult.PASS : InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof LaserNodeBlockEntity node) player.openMenu(node, pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        net.minecraft.world.Containers.updateNeighboursAfterDestroy(state, level, pos);
    }
}
