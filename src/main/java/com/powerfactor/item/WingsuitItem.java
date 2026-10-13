package com.powerfactor.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.Equippable;

/** Fabric glider with a deliberate deploy key, usable directly or as a removable chest socket. */
public final class WingsuitItem extends Item {
    public WingsuitItem(Properties p) {
        super(p.stacksTo(1).component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST)
                .setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER).setDamageOnHurt(false).build()));
    }
    public static ItemStack fitted(ItemStack chest) {
        if (chest.getItem() instanceof WingsuitItem) return chest;
        if (!(chest.getItem() instanceof PowerArmorItem armor) || armor.part() != PowerArmorItem.Part.CHEST) return ItemStack.EMPTY;
        for (ItemStack stack : ToolModules.list(chest)) if (stack.getItem() instanceof WingsuitItem) return stack;
        return ItemStack.EMPTY;
    }
    public static boolean worn(ItemStack chest) { return !fitted(chest).isEmpty(); }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.powerfactor.wingsuit.controls").withStyle(ChatFormatting.AQUA));
        builder.accept(Component.translatable("tooltip.powerfactor.wingsuit.momentum").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.powerfactor.wingsuit.socket").withStyle(ChatFormatting.DARK_GRAY));
    }
}
