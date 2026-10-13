package com.powerfactor.item;

import com.powerfactor.PowerFactor;
import com.powerfactor.registry.ModComponents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;

/** Rechargeable armored hull with a powered shield and six removable upgrade sockets. */
public final class PowerArmorItem extends PoweredItem {
    public enum Part {
        HELMET(EquipmentSlot.HEAD, 200000, 2, ToolKind.POWER_HELMET),
        CHEST(EquipmentSlot.CHEST, 600000, 6, ToolKind.POWER_CHEST),
        LEGS(EquipmentSlot.LEGS, 400000, 5, ToolKind.POWER_LEGS),
        BOOTS(EquipmentSlot.FEET, 200000, 2, ToolKind.POWER_BOOTS);
        public final EquipmentSlot slot;
        public final int capacity, armor;
        public final ToolKind toolKind;
        Part(EquipmentSlot slot, int capacity, int armor, ToolKind toolKind) {
            this.slot = slot; this.capacity = capacity; this.armor = armor; this.toolKind = toolKind;
        }
    }
    public static final ResourceKey<EquipmentAsset> ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, PowerFactor.id("power_armor"));
    private final Part part;

    public PowerArmorItem(Part part, Properties properties) {
        super(properties.enchantable(12).component(DataComponents.EQUIPPABLE, Equippable.builder(part.slot).setAsset(ASSET)
                .setEquipSound(SoundEvents.ARMOR_EQUIP_NETHERITE).setDamageOnHurt(false).build())
                .attributes(ItemAttributeModifiers.builder().add(Attributes.ARMOR,
                        new AttributeModifier(PowerFactor.id("power_armor_" + part.name().toLowerCase(java.util.Locale.ROOT)), part.armor,
                                AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.bySlot(part.slot)).build())
                .component(ModComponents.ARMOR_ASSIST.get(), true).component(ModComponents.ARMOR_VISOR.get(), true));
        this.part = part;
    }
    public Part part() { return part; }
    @Override public int capacity() { return part.capacity; }
    public static boolean assists(ItemStack stack) { return stack.getOrDefault(ModComponents.ARMOR_ASSIST.get(), true); }
    public static boolean visor(ItemStack stack) { return stack.getOrDefault(ModComponents.ARMOR_VISOR.get(), true); }
    public static boolean ready(ItemStack stack, int cost) { return energy(stack) >= ToolModules.cost(stack, cost); }
    public static double shieldFraction(ItemStack stack) { return 0.08 + 0.02 * ToolModules.count(stack, DrillModule.Type.PLATING); }
    public static ItemStack jetpack(ItemStack chest) {
        if (!(chest.getItem() instanceof PowerArmorItem armor) || armor.part != Part.CHEST) return ItemStack.EMPTY;
        for (ItemStack installed : ToolModules.list(chest)) if (installed.getItem() instanceof JetpackItem) return installed;
        return ItemStack.EMPTY;
    }
    public static boolean fitsSocket(ItemStack device, ItemStack module) {
        return device.getItem() instanceof PowerArmorItem armor && armor.part == Part.CHEST
                && (module.getItem() instanceof JetpackItem || module.getItem() instanceof WingsuitItem);
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        builder.accept(Component.translatable("tooltip.powerfactor.armor.shield", Math.round(shieldFraction(stack) * 100)).withStyle(ChatFormatting.AQUA));
        builder.accept(Component.translatable("tooltip.powerfactor.armor." + part.name().toLowerCase(java.util.Locale.ROOT)).withStyle(ChatFormatting.GRAY));
        if (!jetpack(stack).isEmpty()) builder.accept(Component.translatable("tooltip.powerfactor.armor.jetpack").withStyle(ChatFormatting.AQUA));
        if (WingsuitItem.worn(stack)) builder.accept(Component.translatable("tooltip.powerfactor.armor.wingsuit").withStyle(ChatFormatting.AQUA));
        builder.accept(Component.translatable("tooltip.powerfactor.armor.controls").withStyle(ChatFormatting.DARK_GRAY));
    }
}
