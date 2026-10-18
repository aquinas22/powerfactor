package com.powerfactor.redstone;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A gate's item: says what the gate does. */
public class GateItem extends BlockItem {
    private final GateBlock.Type type;

    public GateItem(GateBlock block, Properties properties) {
        super(block, properties);
        this.type = block.type();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.powerfactor.gate." + type.id()).withStyle(ChatFormatting.GRAY));
    }
}
