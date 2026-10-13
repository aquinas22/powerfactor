package com.powerfactor.item;

import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** The powered tools the upgrade table can modify. */
public enum ToolKind {
    DRILL, CHAINSAW, JETPACK, SABER, ARC_GUN, POWER_HELMET, POWER_CHEST, POWER_LEGS, POWER_BOOTS, GRAPPLE;

    public static @Nullable ToolKind of(ItemStack stack) {
        var item = stack.getItem();
        if (item instanceof GrappleItem) return GRAPPLE;
        if (item instanceof PowerArmorItem armor) return armor.part().toolKind;
        if (item instanceof DrillItem) {
            return DRILL;
        }
        if (item instanceof ChainsawItem) {
            return CHAINSAW;
        }
        if (item instanceof JetpackItem) {
            return JETPACK;
        }
        if (item instanceof ArcSaberItem) {
            return SABER;
        }
        if (item instanceof ArcGunItem) {
            return ARC_GUN;
        }
        return null;
    }
}
