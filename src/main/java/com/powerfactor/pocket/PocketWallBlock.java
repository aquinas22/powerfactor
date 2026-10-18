package com.powerfactor.pocket;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The shell of a pocket room: unbreakable, glowing; use any panel from inside to step back out. */
public class PocketWallBlock extends Block {
    public PocketWallBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp && PocketRooms.isPocket(level)) PocketRooms.leave(sp);
        return InteractionResult.SUCCESS;
    }
}
