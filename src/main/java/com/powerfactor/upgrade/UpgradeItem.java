package com.powerfactor.upgrade;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** An upgrade card. Goes in a machine's Upgrades tab. */
public class UpgradeItem extends Item {
    private final UpgradeType type;

    public UpgradeItem(UpgradeType type, Properties properties) {
        super(properties.stacksTo(type.maxCount));
        this.type = type;
    }

    public UpgradeType type() {
        return type;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.powerfactor.upgrade." + type.getSerializedName()).withColor(type.color));
        builder.accept(Component.translatable("tooltip.powerfactor.upgrade.max", type.maxCount).withStyle(ChatFormatting.DARK_GRAY));
    }
}
