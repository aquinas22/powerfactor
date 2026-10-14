package com.powerfactor.exotic;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

/**
 * A paxel: pickaxe, axe, shovel and hoe in one head. Only the Starforge can fuse one, from the three
 * tools of a material, so they cost far more than the tools they replace. Right-click strips logs
 * like an axe, then makes paths like a shovel, then tills like a hoe.
 */
public class PaxelItem extends Item {
    public PaxelItem(ToolMaterial material, float damage, Properties properties) {
        super(properties.pickaxe(material, damage, -2.8F).component(DataComponents.TOOL, tool(material)));
    }

    static Tool tool(ToolMaterial m) {
        var blocks = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK);
        return new Tool(List.of(
                Tool.Rule.deniesDrops(blocks.getOrThrow(m.incorrectBlocksForDrops())),
                Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_PICKAXE), m.speed()),
                Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_AXE), m.speed()),
                Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_SHOVEL), m.speed()),
                Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_HOE), m.speed())), 1.0F, 1, true);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        // Borrow the vanilla behaviours; durability and sounds come out of this stack.
        InteractionResult r = Items.NETHERITE_AXE.useOn(context);
        if (r == InteractionResult.PASS) r = Items.NETHERITE_SHOVEL.useOn(context);
        if (r == InteractionResult.PASS) r = Items.NETHERITE_HOE.useOn(context);
        return r;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.powerfactor.paxel").withStyle(ChatFormatting.GRAY));
    }
}
