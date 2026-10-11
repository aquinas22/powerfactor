package com.powerfactor.item;

import com.powerfactor.registry.ModItems;
import com.powerfactor.world.RubberLogBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Taps a weeping rubber log for latex. */
public class TreeTapItem extends Item {
    public TreeTapItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof RubberLogBlock) || !state.getValue(RubberLogBlock.LATEX)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(RubberLogBlock.LATEX, false), Block.UPDATE_ALL);
            int count = 1 + level.getRandom().nextInt(3);
            var drop = context.getClickedFace();
            ItemEntity item = new ItemEntity(level, pos.getX() + 0.5 + drop.getStepX() * 0.6, pos.getY() + 0.5,
                    pos.getZ() + 0.5 + drop.getStepZ() * 0.6, new ItemStack(ModItems.LATEX.get(), count));
            level.addFreshEntity(item);
            level.playSound(null, pos, SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.BLOCKS, 0.8F, 1.3F);
            if (context.getPlayer() != null) {
                context.getItemInHand().hurtAndBreak(1, context.getPlayer(),
                        context.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
