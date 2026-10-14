package com.powerfactor.item;

import com.powerfactor.registry.ModEntities;
import com.powerfactor.vehicle.HoverBikeEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/** Deployable hover-bike; wrench packing keeps its battery and hull. */
public final class HoverBikeItem extends PoweredItem {
    public HoverBikeItem(Properties properties) {
        super(properties.rarity(net.minecraft.world.item.Rarity.EPIC));
    }

    @Override
    public int capacity() {
        return HoverBikeEntity.CAPACITY;
    }

    @Override
    public int capacity(ItemStack stack) {
        return capacity();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
            java.util.function.Consumer<Component> builder, net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        builder.accept(Component.translatable("tooltip.powerfactor.hover_bike.controls").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.powerfactor.rover.pack").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        var level = context.getLevel();
        if (player == null) return InteractionResult.PASS;
        var bike = new HoverBikeEntity(ModEntities.HOVER_BIKE.get(), level);
        bike.setPos(context.getClickLocation().add(0, 0.6, 0));
        bike.setYRot(player.getYRot());
        bike.restore(context.getItemInHand());
        if (!level.noCollision(bike, bike.getBoundingBox()) || !level.getWorldBorder().isWithinBounds(bike.getBoundingBox())
                || !level.mayInteract(player, context.getClickedPos())) return InteractionResult.FAIL;
        if (!level.isClientSide()) {
            level.addFreshEntity(bike);
            context.getItemInHand().consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }
}
