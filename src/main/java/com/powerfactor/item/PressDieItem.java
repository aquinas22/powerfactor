package com.powerfactor.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Replaceable tooling selects the press's product without ambiguous bulk-input recipes. */
public final class PressDieItem extends Item {
    private final String pattern;

    public PressDieItem(String pattern, Properties properties) {
        super(properties.durability(512));
        this.pattern = pattern;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.powerfactor.press_die." + pattern).withStyle(ChatFormatting.AQUA));
        tooltip.accept(Component.translatable("tooltip.powerfactor.press_die").withStyle(ChatFormatting.DARK_GRAY));
    }
}
