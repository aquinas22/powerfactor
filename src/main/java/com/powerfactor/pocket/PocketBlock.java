package com.powerfactor.pocket;

import com.mojang.serialization.MapCodec;
import com.powerfactor.registry.ModPocket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** Dimensional Pocket: use it to step inside; use a Spatial Expander on it to make the room bigger. */
public class PocketBlock extends BaseEntityBlock {
    public PocketBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        throw new UnsupportedOperationException("not data-driven");
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PocketBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel s ? createTickerHelper(type, ModPocket.POCKET_BE.get(), (l, p, st, be) -> {
            if (s.getGameTime() % 20 == 0) be.serverTick(s);
        }) : null;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModPocket.SPATIAL_EXPANDER.get())) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level instanceof ServerLevel s && level.getBlockEntity(pos) instanceof PocketBlockEntity be) {
            if (be.grow(s)) {
                stack.consume(1, player);
                s.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1F, 0.6F);
                s.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 60, 0.4, 0.4, 0.4, 0.15);
            }
            int n = PocketRooms.size(be.tier());
            player.sendOverlayMessage(Component.translatable("gui.powerfactor.pocket.size", n, n, n));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp && level instanceof ServerLevel s && level.getBlockEntity(pos) instanceof PocketBlockEntity be) {
            if (PocketRooms.level(s.getServer()) == null) return InteractionResult.FAIL;
            PocketRooms.enter(sp, be.ensureRoom(s));
        }
        return InteractionResult.SUCCESS;
    }
}
