package com.powerfactor.logistics;

import com.mojang.serialization.MapCodec;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.registry.ModQuantum;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Robot arm base. Faces the inventory it delivers to; takes from the one behind. Use with an item to set the filter. */
public class RobotArmBlock extends MachineBlock {
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 6, 14);

    public RobotArmBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        throw new UnsupportedOperationException("not data-driven");
    }

    @Override
    protected BlockEntityType<? extends MachineBlockEntity> blockEntityType() {
        return ModQuantum.ROBOT_ARM_BE.get();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof RobotArmBlockEntity arm) {
            arm.setFilter(stack);
            player.sendOverlayMessage(Component.translatable("gui.powerfactor.robot_arm.filter", stack.getHoverName()).withStyle(ChatFormatting.AQUA));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown() && level.getBlockEntity(pos) instanceof RobotArmBlockEntity arm) {
            if (!level.isClientSide()) {
                arm.setFilter(ItemStack.EMPTY);
                player.sendOverlayMessage(Component.translatable("gui.powerfactor.robot_arm.cleared").withStyle(ChatFormatting.AQUA));
            }
            return InteractionResult.SUCCESS;
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }
}
