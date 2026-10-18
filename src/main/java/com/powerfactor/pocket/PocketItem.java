package com.powerfactor.pocket;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/** The pocket as an item: says which room it carries and how big it is. */
public class PocketItem extends BlockItem {
    public PocketItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        var t = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        int room = t.getIntOr("pocket_room", 0), n = PocketRooms.size(Math.clamp(t.getIntOr("pocket_tier", 1), 1, PocketRooms.MAX_TIER));
        out.accept(Component.translatable("tooltip.powerfactor.pocket.size", n, n, n).withStyle(ChatFormatting.LIGHT_PURPLE));
        out.accept((room == 0 ? Component.translatable("tooltip.powerfactor.pocket.new") : Component.translatable("tooltip.powerfactor.pocket.room", room))
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntOr("pocket_room", 0) > 0;
    }
}
