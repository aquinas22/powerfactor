package com.powerfactor.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import org.jspecify.annotations.Nullable;

/** Reusable early workshop tools: every crafting operation spends one durability. */
public final class CraftingToolItem extends Item {
    private final String operation;
    public CraftingToolItem(String operation, Properties properties) { super(properties.durability(128)); this.operation = operation; }
    @Override public @Nullable ItemStackTemplate getCraftingRemainder(ItemInstance instance) {
        int next = instance.getOrDefault(DataComponents.DAMAGE, 0) + 1;
        if (next >= instance.getOrDefault(DataComponents.MAX_DAMAGE, 128)) return null;
        ItemStack result = instance instanceof ItemStack stack ? stack.copyWithCount(1)
                : instance instanceof ItemStackTemplate template ? template.withCount(1).create() : new ItemStack(this);
        result.setDamageValue(next); return ItemStackTemplate.fromNonEmptyStack(result);
    }
    @Override public net.minecraft.world.InteractionResult onItemUseFirst(ItemStack stack, net.minecraft.world.item.context.UseOnContext context) {
        if (!operation.equals("wire_cutters") || !(context.getLevel().getBlockState(context.getClickedPos()).getBlock() instanceof com.powerfactor.grid.WireBlock)) return net.minecraft.world.InteractionResult.PASS;
        var player = context.getPlayer();
        if (player == null || !context.getLevel().mayInteract(player, context.getClickedPos())) return net.minecraft.world.InteractionResult.FAIL;
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            if (!serverPlayer.gameMode.destroyBlock(context.getClickedPos())) return net.minecraft.world.InteractionResult.FAIL;
            stack.hurtAndBreak(1, player, context.getHand().asEquipmentSlot());
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
            java.util.function.Consumer<Component> builder, net.minecraft.world.item.TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.powerfactor." + operation).withStyle(net.minecraft.ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.powerfactor.crafting_tool", stack.getMaxDamage() - stack.getDamageValue()).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
