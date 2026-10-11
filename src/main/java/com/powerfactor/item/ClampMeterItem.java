package com.powerfactor.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Clamp meter: hold it and look at a wire or machine for a live readout of voltage, frequency, FE,
 * EE, CE and power factor beside the crosshair. Sips FE from its own little battery.
 */
public class ClampMeterItem extends PoweredItem {
    public static final int CAPACITY = 20_000, PER_READING = 2;

    public ClampMeterItem(Properties properties) {
        super(properties);
    }

    @Override
    public int capacity() {
        return CAPACITY;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        builder.accept(Component.translatable("tooltip.powerfactor.clamp_meter").withStyle(ChatFormatting.GRAY));
    }
}
