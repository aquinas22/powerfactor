package com.powerfactor.item;

import com.powerfactor.logistics.FluidTankBlockEntity;
import com.powerfactor.registry.ModComponents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/** Portable reservoir cell. Its fluid component survives dropping, inventory storage and placement. */
public final class FluidTankItem extends BlockItem {
    public FluidTankItem(Block block, Properties properties) { super(block, properties.stacksTo(1)); }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        var content = stack.get(ModComponents.FLUID.get());
        if (content != null && !content.isEmpty()) {
            var fluid = content.copy();
            lines.accept(Component.translatable("tooltip.powerfactor.reservoir.contents", fluid.getHoverName(),
                    String.format(java.util.Locale.ROOT, "%,d", fluid.getAmount()), String.format(java.util.Locale.ROOT, "%,d", FluidTankBlockEntity.CAPACITY))
                    .withStyle(ChatFormatting.AQUA));
        } else lines.accept(Component.translatable("tooltip.powerfactor.reservoir.empty", "32,000").withStyle(ChatFormatting.GRAY));
        lines.accept(Component.translatable("tooltip.powerfactor.reservoir.join").withStyle(ChatFormatting.GRAY));
        lines.accept(Component.translatable("tooltip.powerfactor.reservoir.keep").withStyle(ChatFormatting.DARK_GRAY));
    }
}
