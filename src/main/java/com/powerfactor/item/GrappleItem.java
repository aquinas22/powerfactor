package com.powerfactor.item;

import com.powerfactor.movement.GrappleServer;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** Wrist launcher with an electric reel. The cable keeps tangential momentum when released. */
public final class GrappleItem extends PoweredItem {
    public static final int CAPACITY = 120000, FIRE_FE = 100, REEL_FE = 12;
    public static final double RANGE = 64;
    public GrappleItem(Properties properties) { super(properties); }
    @Override public int capacity() { return CAPACITY; }
    public static double reelMultiplier(ItemStack stack) { return 1 + 0.2 * ToolModules.count(stack, DrillModule.Type.SPEED); }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && !GrappleServer.toggle(player)) return InteractionResult.FAIL;
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }
    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72000; }
    @Override public ItemUseAnimation getUseAnimation(ItemStack stack) { return ItemUseAnimation.NONE; }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        builder.accept(Component.translatable("tooltip.powerfactor.grapple.range", (int) RANGE).withStyle(ChatFormatting.AQUA));
        builder.accept(Component.translatable("tooltip.powerfactor.grapple.controls").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.powerfactor.grapple.swing").withStyle(ChatFormatting.DARK_GRAY));
    }
}
