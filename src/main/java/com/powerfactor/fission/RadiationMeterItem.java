package com.powerfactor.fission;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Geiger Counter (clicks faster as the dose rate climbs, and shows it) or Dosimeter (shows the dose you've taken
 * in all, kept with you). Either works in either hand.
 */
public class RadiationMeterItem extends Item {
    private final boolean dosimeter;

    public RadiationMeterItem(boolean dosimeter, Properties properties) {
        super(properties);
        this.dosimeter = dosimeter;
    }

    public boolean dosimeter() {
        return dosimeter;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable(dosimeter ? "tooltip.powerfactor.dosimeter" : "tooltip.powerfactor.geiger_counter").withStyle(ChatFormatting.GRAY));
    }
}
