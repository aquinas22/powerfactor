package com.powerfactor.grid;

import com.mojang.serialization.MapCodec;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A power transformer between two grids of any tiers. Its back face (red, primary) draws from one
 * grid; its front face (blue, secondary) feeds the other. Right-click for a reading, or to reset it
 * after an overload trip.
 */
public class TransformerBlock extends MachineBlock {
    public static final MapCodec<TransformerBlock> CODEC = simpleCodec(TransformerBlock::new);

    public TransformerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected BlockEntityType<? extends MachineBlockEntity> blockEntityType() {
        return ModBlockEntities.TRANSFORMER.get();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TransformerBlockEntity t) {
            t.reset();
            player.sendOverlayMessage(t.describe());
        }
        return InteractionResult.SUCCESS;
    }
}
