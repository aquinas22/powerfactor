package com.powerfactor.powernet;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jspecify.annotations.Nullable;

/**
 * A wireless power network device. Plugs and Points are small nodes that reach an arm out to every neighbour that
 * handles power; Cells and the Controller are full blocks.
 */
public class PowerNodeBlock extends BaseEntityBlock {
    public static final Map<Direction, BooleanProperty> SIDES = PipeBlock.PROPERTY_BY_DIRECTION;
    private static final VoxelShape CORE = Block.box(4, 4, 4, 12, 12, 12);
    private static final Map<Direction, VoxelShape> ARMS = Map.of(
            Direction.DOWN, Block.box(6, 0, 6, 10, 4, 10), Direction.UP, Block.box(6, 12, 6, 10, 16, 10),
            Direction.NORTH, Block.box(6, 6, 0, 10, 10, 4), Direction.SOUTH, Block.box(6, 6, 12, 10, 10, 16),
            Direction.WEST, Block.box(0, 6, 6, 4, 10, 10), Direction.EAST, Block.box(12, 6, 6, 16, 10, 10));
    private final PowerNodeBlockEntity.Kind kind;

    public PowerNodeBlock(PowerNodeBlockEntity.Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        BlockState s = stateDefinition.any();
        for (var p : SIDES.values()) s = s.setValue(p, false);
        registerDefaultState(s);
    }

    public PowerNodeBlockEntity.Kind kind() {
        return kind;
    }

    private boolean node() {
        return kind == PowerNodeBlockEntity.Kind.PLUG || kind == PowerNodeBlockEntity.Kind.POINT;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        throw new UnsupportedOperationException("not data-driven");
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        SIDES.values().forEach(builder::add);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PowerNodeBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    private boolean connects(Level level, BlockPos pos, Direction d) {
        BlockPos at = pos.relative(d);
        return !(level.getBlockEntity(at) instanceof PowerNodeBlockEntity) && level.getCapability(Capabilities.Energy.BLOCK, at, d.getOpposite()) != null;
    }

    private BlockState connections(BlockState state, Level level, BlockPos pos) {
        if (!node()) return state;
        for (var e : SIDES.entrySet()) state = state.setValue(e.getValue(), connects(level, pos, e.getKey()));
        return state;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return connections(defaultBlockState(), ctx.getLevel(), ctx.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (!node() || !(level instanceof Level l)) return state;
        return state.setValue(SIDES.get(direction), connects(l, pos, direction));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        // Block entities next door may only now have their capabilities: look again.
        if (!level.isClientSide() && node()) {
            BlockState now = connections(state, level, pos);
            if (now != state) level.setBlock(pos, now, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        // A new device joins the network its placer last used, if they may.
        if (placer instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof PowerNodeBlockEntity d) {
            int last = PowerNetPayloads.lastNetwork(sp);
            var net = PowerNets.get(sp.level().getServer()).net(last);
            if (net != null && net.canUse(sp)) d.setNetwork(last);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof PowerNodeBlockEntity d) {
            if (player instanceof ServerPlayer sp) sp.openMenu(d, buf -> buf.writeBlockPos(pos));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!node()) return Shapes.block();
        VoxelShape s = CORE;
        for (var e : SIDES.entrySet()) if (state.getValue(e.getValue())) s = Shapes.or(s, ARMS.get(e.getKey()));
        return s;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return node();
    }
}
