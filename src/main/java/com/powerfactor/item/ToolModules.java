package com.powerfactor.item;

import com.powerfactor.registry.ModComponents;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Upgrade modules installed in a powered tool (stored in the tool's modules component), and what
 * they do to its numbers. Every tool asks here for its capacity, its cost per use and its bonuses.
 */
public final class ToolModules {
    public static final int SLOTS = 6;

    public static NonNullList<ItemStack> list(ItemStack tool) {
        NonNullList<ItemStack> list = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        tool.getOrDefault(ModComponents.DRILL_MODULES.get(), ItemContainerContents.EMPTY).copyInto(list);
        return list;
    }

    public static int count(ItemStack tool, DrillModule.Type type) {
        if (!tool.has(ModComponents.DRILL_MODULES.get())) {
            return 0;
        }
        int n = 0;
        for (ItemStack m : list(tool)) {
            if (m.getItem() instanceof DrillModule module && module.type() == type) {
                n += m.getCount();
            }
        }
        return Math.min(n, type.max);
    }

    /** Battery modules add the base capacity again, each. */
    public static int capacity(ItemStack tool, int base) {
        return base * (1 + count(tool, DrillModule.Type.BATTERY));
    }

    /** Efficiency modules take 20% off each use, compounding. */
    public static int cost(ItemStack tool, int base) {
        return Math.max(1, (int) Math.round(base * Math.pow(0.8, count(tool, DrillModule.Type.EFFICIENCY))));
    }

    /** Extra damage from overcharge modules. */
    public static float overcharge(ItemStack tool, float perModule) {
        return perModule * count(tool, DrillModule.Type.OVERCHARGE);
    }

    /** One line per installed module type. */
    public static void tooltip(ItemStack tool, java.util.function.Consumer<net.minecraft.network.chat.Component> builder) {
        for (DrillModule.Type t : DrillModule.Type.values()) {
            int n = count(tool, t);
            if (n > 0) {
                builder.accept(net.minecraft.network.chat.Component.translatable("tooltip.powerfactor.drill.module",
                        net.minecraft.network.chat.Component.translatable("item.powerfactor." + t.id), n)
                        .withStyle(net.minecraft.ChatFormatting.AQUA));
            }
        }
    }

    /** Pull nearby drops toward the player. */
    public static void magnet(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.player.Player player) {
        for (var item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(6))) {
            if (!item.hasPickUpDelay()) {
                var pull = player.position().add(0, 0.5, 0).subtract(item.position()).normalize().scale(0.35);
                item.setDeltaMovement(item.getDeltaMovement().scale(0.6).add(pull));
            }
        }
    }

    /** Store a new module list on the tool and apply what can't be computed on the fly (enchantments, armour). */
    public static void install(ItemStack tool, java.util.List<ItemStack> modules, net.minecraft.core.RegistryAccess registries) {
        tool.set(ModComponents.DRILL_MODULES.get(), ItemContainerContents.fromItems(modules));
        ToolKind kind = ToolKind.of(tool);
        if (kind == ToolKind.DRILL) {
            DrillItem.applyModules(tool, registries);
        }
        if (kind == ToolKind.JETPACK) {
            int plates = count(tool, DrillModule.Type.PLATING);
            tool.set(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                    net.minecraft.world.item.component.ItemAttributeModifiers.builder()
                            .add(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR,
                                    new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                                            net.minecraft.resources.Identifier.fromNamespaceAndPath("powerfactor", "jetpack_armor"), 3 + 2 * plates,
                                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE),
                                    net.minecraft.world.entity.EquipmentSlotGroup.CHEST)
                            .add(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS,
                                    new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                                            net.minecraft.resources.Identifier.fromNamespaceAndPath("powerfactor", "jetpack_toughness"), plates,
                                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE),
                                    net.minecraft.world.entity.EquipmentSlotGroup.CHEST)
                            .build());
        }
        // Removing battery modules can leave more charge than now fits.
        int cap = switch (kind) {
            case null -> Integer.MAX_VALUE;
            case DRILL -> DrillItem.capacity(tool);
            case SABER -> ArcSaberItem.capacity(tool);
            case ARC_GUN -> ArcGunItem.capacity(tool);
            default -> tool.getItem() instanceof PoweredItem p ? p.capacity(tool) : Integer.MAX_VALUE;
        };
        Integer energy = tool.get(ModComponents.ENERGY.get());
        if (energy != null && energy > cap) {
            tool.set(ModComponents.ENERGY.get(), cap);
        }
    }

    private ToolModules() {}
}
