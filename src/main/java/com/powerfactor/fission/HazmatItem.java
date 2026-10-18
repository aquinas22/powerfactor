package com.powerfactor.fission;

import com.powerfactor.PowerFactor;
import java.util.EnumMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;

/** Hazmat Suit: lead-lined rubber. Little protection from blows; all four pieces together keep radiation out. */
public class HazmatItem extends Item {
    public static final ArmorMaterial MATERIAL;

    static {
        var defense = new EnumMap<ArmorType, Integer>(ArmorType.class);
        defense.put(ArmorType.BOOTS, 1);
        defense.put(ArmorType.LEGGINGS, 2);
        defense.put(ArmorType.CHESTPLATE, 3);
        defense.put(ArmorType.HELMET, 1);
        defense.put(ArmorType.BODY, 3);
        MATERIAL = new ArmorMaterial(20, defense, 5, SoundEvents.ARMOR_EQUIP_LEATHER, 0, 0,
                TagKey.create(Registries.ITEM, PowerFactor.id("repairs/hazmat")), ResourceKey.create(EquipmentAssets.ROOT_ID, PowerFactor.id("hazmat")));
    }

    public HazmatItem(ArmorType type, Properties properties) {
        super(properties.humanoidArmor(MATERIAL, type));
    }
}
