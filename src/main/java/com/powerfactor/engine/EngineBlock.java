package com.powerfactor.engine;

import com.mojang.serialization.MapCodec;
import com.powerfactor.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jspecify.annotations.Nullable;

/**
 * BuildCraft-style engine. Needs a redstone signal to run, pushes FE out of its plate face only,
 * and changes color as it heats up. Placing it against a machine points it at that machine;
 * sneak-right-click turns it to the next face that accepts power.
 */
public class EngineBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final MapCodec<EngineBlock> REDSTONE_CODEC = simpleCodec(p -> new EngineBlock(EngineKind.REDSTONE, p));
    public static final MapCodec<EngineBlock> STIRLING_CODEC = simpleCodec(p -> new EngineBlock(EngineKind.STIRLING, p));
    private static final VoxelShape SHAPE = Shapes.block();

    private final EngineKind kind;

    public EngineBlock(EngineKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    public EngineKind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return kind == EngineKind.REDSTONE ? REDSTONE_CODEC : STIRLING_CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EngineBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level instanceof ServerLevel serverLevel) {
            return createTickerHelper(type, ModBlockEntities.ENGINE.get(), (l, pos, s, engine) -> engine.serverTick(serverLevel, pos, s));
        }
        return createTickerHelper(type, ModBlockEntities.ENGINE.get(), (l, pos, s, engine) -> engine.clientTick());
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Point at the block we were placed against, so it powers it.
        return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            level.setBlock(pos, state.setValue(FACING, nextFacing(level, pos, state.getValue(FACING))), Block.UPDATE_ALL);
        } else if (kind.burnsFuel && level.getBlockEntity(pos) instanceof EngineBlockEntity engine) {
            player.openMenu(engine, pos);
        }
        return InteractionResult.SUCCESS;
    }

    /** The next face (in Direction order) with something that accepts FE, or just the next face. */
    private static Direction nextFacing(Level level, BlockPos pos, Direction current) {
        Direction[] all = Direction.values();
        for (int i = 1; i <= all.length; i++) {
            Direction d = all[(current.ordinal() + i) % all.length];
            if (level.getCapability(Capabilities.Energy.BLOCK, pos.relative(d), d.getOpposite()) != null) {
                return d;
            }
        }
        return all[(current.ordinal() + 1) % all.length];
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
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
        builder.add(FACING);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof EngineBlockEntity engine && engine.heatStage() >= EngineBlockEntity.STAGE_RED
                && random.nextFloat() < 0.4F) {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + random.nextDouble(), pos.getY() + 0.9, pos.getZ() + random.nextDouble(), 0, 0.03, 0);
        }
    }
}
