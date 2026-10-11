package com.powerfactor.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A photomask: the stencil a laser engraver shines through. Which mask sits in the engraver decides
 * which chip it makes; the mask isn't used up, but every die wears it a little.
 */
public class PhotomaskItem extends Item {
    private final String pattern;

    public PhotomaskItem(String pattern, Properties properties) {
        super(properties.durability(256));
        this.pattern = pattern;
    }

    public String pattern() {
        return pattern;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.powerfactor.photomask." + pattern).withStyle(ChatFormatting.AQUA));
        builder.accept(Component.translatable("tooltip.powerfactor.photomask").withStyle(ChatFormatting.DARK_GRAY));
    }
}
