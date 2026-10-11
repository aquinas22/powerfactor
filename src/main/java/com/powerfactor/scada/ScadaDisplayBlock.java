package com.powerfactor.scada;

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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * A SCADA display panel. Panels side by side form one screen that lays itself out to suit its size.
 * Connect any panel to fiber. Right-click to change what it shows.
 */
public class ScadaDisplayBlock extends BaseEntityBlock {
    public static final MapCodec<ScadaDisplayBlock> CODEC = simpleCodec(ScadaDisplayBlock::new);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

    public ScadaDisplayBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ScadaDisplayBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level instanceof ServerLevel server) {
            return createTickerHelper(type, ModBlockEntities.SCADA_DISPLAY.get(), (l, pos, s, be) -> be.serverTick(server, pos, s));
        }
        return createTickerHelper(type, ModBlockEntities.SCADA_DISPLAY.get(), (l, pos, s, be) -> be.clientTick(l, pos, s));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Face the player; snap to an adjacent display's facing so walls line up.
        Direction facing = context.getHorizontalDirection().getOpposite();
        for (Direction d : Direction.values()) {
            BlockState n = context.getLevel().getBlockState(context.getClickedPos().relative(d));
            if (n.getBlock() instanceof ScadaDisplayBlock) {
                facing = n.getValue(FACING);
                break;
            }
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            DisplayWall wall = DisplayWall.find(level, pos, state);
            if (level.getBlockEntity(wall.master()) instanceof ScadaDisplayBlockEntity master) {
                ScadaDisplayBlockEntity.Page page = master.cyclePage(player.isShiftKeyDown() ? -1 : 1);
                player.sendOverlayMessage(Component.translatable("message.powerfactor.display.page",
                        Component.translatable("gui.powerfactor.display.page." + page.name().toLowerCase(java.util.Locale.ROOT)),
                        wall.width(), wall.height()));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
