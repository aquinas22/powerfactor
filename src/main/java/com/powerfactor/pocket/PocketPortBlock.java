package com.powerfactor.pocket;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jspecify.annotations.Nullable;

/**
 * Pocket Port: placed inside a pocket room, facing a machine or inventory. Whatever is piped into the
 * Dimensional Pocket outside (items, fluids, energy) goes to what the port faces, and can be pulled back out.
 */
public class PocketPortBlock extends DirectionalBlock {
    public PocketPortBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        throw new UnsupportedOperationException("not data-driven");
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Faces the block it was placed against. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getClickedFace().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        if (!(level instanceof ServerLevel s)) return;
        if (!PocketRooms.isPocket(level)) {
            if (by instanceof net.minecraft.world.entity.player.Player p) p.sendOverlayMessage(Component.translatable("gui.powerfactor.pocket.port_outside"));
            return;
        }
        PocketData data = PocketData.get(s.getServer());
        data.room(PocketRooms.roomAt(pos)).ifPresent(r -> {
            r.portPos = pos.asLong();
            r.portFace = state.getValue(FACING).get3DDataValue();
            data.changed();
        });
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        // Covers placement by machines and /setblock, which don't call setPlacedBy.
        if (level instanceof ServerLevel s && PocketRooms.isPocket(level)) setPlacedBy(level, pos, state, null, ItemStack.EMPTY);
    }
}
