package com.powerfactor.util;

import com.mojang.serialization.MapCodec;
import com.powerfactor.registry.ModUtility;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class ChunkLoaderBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 8, 15);

    public ChunkLoaderBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        throw new UnsupportedOperationException("not data-driven");
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** A Range Upgrade grows the loaded area; sneak-use with an empty hand takes one back. */
    @Override
    protected net.minecraft.world.InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (!stack.is(ModUtility.RANGE_UPGRADE.get())) return net.minecraft.world.InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ChunkLoaderBlockEntity be) {
            if (be.changeCards(false)) {
                stack.consume(1, player);
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.END_PORTAL_FRAME_FILL, net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.2F);
            }
            player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("gui.powerfactor.chunk_loader.area", be.radius() * 2 + 1, be.radius() * 2 + 1));
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player,
            net.minecraft.world.phys.BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ChunkLoaderBlockEntity be) {
            if (player.isShiftKeyDown() && be.changeCards(true)) {
                player.getInventory().placeItemBackInInventory(new net.minecraft.world.item.ItemStack(ModUtility.RANGE_UPGRADE.get()));
            }
            player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("gui.powerfactor.chunk_loader.area", be.radius() * 2 + 1, be.radius() * 2 + 1));
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChunkLoaderBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel server ? createTickerHelper(type, ModUtility.CHUNK_LOADER_BE.get(), (l, p, s, be) -> be.serverTick(server)) : null;
    }
}
