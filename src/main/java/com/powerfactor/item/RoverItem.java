package com.powerfactor.item;

import com.powerfactor.registry.ModEntities;
import com.powerfactor.vehicle.RoverEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/** Deployable vehicle; wrench packing preserves the same battery and hull. */
public final class RoverItem extends PoweredItem {
    public RoverItem(Properties properties) { super(properties); }
    @Override public int capacity() { return RoverEntity.CAPACITY; }
    @Override public int capacity(ItemStack stack) { return capacity(); }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
            java.util.function.Consumer<net.minecraft.network.chat.Component> builder, net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        builder.accept(net.minecraft.network.chat.Component.translatable("tooltip.powerfactor.rover.controls").withStyle(net.minecraft.ChatFormatting.GRAY));
        builder.accept(net.minecraft.network.chat.Component.translatable("tooltip.powerfactor.rover.pack").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        builder.accept(net.minecraft.network.chat.Component.translatable("tooltip.powerfactor.rover.hull",
                Math.round(stack.getOrDefault(com.powerfactor.registry.ModComponents.VEHICLE_HEALTH.get(), RoverEntity.MAX_HEALTH) * 100 / RoverEntity.MAX_HEALTH)).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer(); var level = context.getLevel(); if (player == null) return InteractionResult.PASS;
        var point = context.getClickLocation().add(0, 0.05, 0);
        var rover = new RoverEntity(ModEntities.ELECTRIC_ROVER.get(), level);
        rover.setPos(point); rover.setYRot(player.getYRot()); rover.restore(context.getItemInHand());
        if (!level.noCollision(rover, rover.getBoundingBox()) || !level.getWorldBorder().isWithinBounds(rover.getBoundingBox())
                || !level.mayInteract(player, context.getClickedPos())) return InteractionResult.FAIL;
        if (!level.isClientSide()) { level.addFreshEntity(rover); context.getItemInHand().consume(1, player); }
        return InteractionResult.SUCCESS;
    }
}
