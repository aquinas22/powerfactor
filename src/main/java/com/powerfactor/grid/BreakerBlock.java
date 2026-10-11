package com.powerfactor.grid;

import com.mojang.serialization.MapCodec;
import com.powerfactor.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * A circuit breaker. Closed, it joins the wires on either side into one grid; open, it splits them.
 * Right-click to operate it; a redstone signal holds it open; it trips by itself on sustained
 * overcurrent. SCADA can open and close it remotely.
 */
public class BreakerBlock extends BaseEntityBlock {
    public static final MapCodec<BreakerBlock> CODEC = simpleCodec(BreakerBlock::new);
    public static final BooleanProperty CLOSED = BooleanProperty.create("closed");
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

    public BreakerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CLOSED, true).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BreakerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel server
                ? createTickerHelper(type, ModBlockEntities.BREAKER.get(), (l, pos, s, be) -> be.serverTick(server, pos, s)) : null;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof BreakerBlockEntity breaker) {
            breaker.operate(server, !state.getValue(CLOSED), false);
            player.sendOverlayMessage(Component.translatable(level.getBlockState(pos).getValue(CLOSED)
                    ? "message.powerfactor.breaker.closed" : "message.powerfactor.breaker.open"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server) {
            GridManager.get(server).invalidate(pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        GridManager.get(level).invalidate(pos);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CLOSED, FACING);
    }
}
