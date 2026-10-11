package com.powerfactor.machine;

import com.mojang.serialization.MapCodec;
import com.powerfactor.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Power factor correction. Supplies EE to its grid so motors don't have to pull it from the
 * generators, freeing their capacity for FE. Right-click toggles it in and out.
 */
public class CapacitorBankBlock extends MachineBlock {
    public static final MapCodec<CapacitorBankBlock> CODEC = simpleCodec(CapacitorBankBlock::new);

    public CapacitorBankBlock(Properties properties) {
        super(properties);
    }

    /** Base, cans and top frame; leaves the open sides walk-through-looking but still solid. */
    public static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 2, 16), Block.box(1, 2, 1, 15, 15, 15), Block.box(0, 15, 0, 16, 16, 16));

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected BlockEntityType<? extends MachineBlockEntity> blockEntityType() {
        return ModBlockEntities.CAPACITOR_BANK.get();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CapacitorBankBlockEntity bank) {
            bank.setRemoteEnabled(!bank.remoteEnabled());
            player.sendOverlayMessage(Component.translatable(bank.remoteEnabled() ? "message.powerfactor.capacitor.on" : "message.powerfactor.capacitor.off",
                    (int) -bank.eeDemand()));
        }
        return InteractionResult.SUCCESS;
    }
}
