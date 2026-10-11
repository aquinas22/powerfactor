package com.powerfactor.item;

import com.powerfactor.registry.ModComponents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Electric jetpack: twin ducted fans worn on the back. Hold jump to climb; toggle hover (H) to hang
 * in the air, sneaking to sink. Thrust is hungry; hovering less so.
 */
public class JetpackItem extends PoweredItem {
    public static final int CAPACITY = 400_000, THRUST_FE = 50, HOVER_FE = 18;

    public JetpackItem(Properties properties) {
        super(properties);
    }

    public static Properties properties(Properties p) {
        return p.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST).setEquipSound(SoundEvents.ARMOR_EQUIP_IRON).build())
                .attributes(ItemAttributeModifiers.builder()
                        .add(Attributes.ARMOR, new AttributeModifier(Identifier.fromNamespaceAndPath("powerfactor", "jetpack_armor"), 3,
                                AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.CHEST)
                        .build());
    }

    @Override
    public int capacity() {
        return CAPACITY;
    }

    public static boolean hover(ItemStack stack) {
        return stack.getOrDefault(ModComponents.JETPACK_HOVER.get(), false);
    }

    public static boolean powered(ItemStack stack) {
        return energy(stack) >= ToolModules.cost(stack, THRUST_FE);
    }

    /** Thrust multiplier from thruster modules. */
    public static float thrust(ItemStack stack) {
        return 1 + 0.35F * ToolModules.count(stack, DrillModule.Type.THRUSTER);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        builder.accept(Component.translatable(hover(stack) ? "tooltip.powerfactor.jetpack.hover_on" : "tooltip.powerfactor.jetpack.hover_off")
                .withStyle(ChatFormatting.AQUA));
        builder.accept(Component.translatable("tooltip.powerfactor.jetpack.help").withStyle(ChatFormatting.DARK_GRAY));
    }
}
