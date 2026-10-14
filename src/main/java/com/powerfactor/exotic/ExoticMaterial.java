package com.powerfactor.exotic;

import com.powerfactor.PowerFactor;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;

/**
 * The exotic tool metals, in ascending tier. Each tier keeps the lower tiers' character and adds its
 * own: Adamantium excavates 3x3, Faradaenium arcs lightning and pulls drops into your pack,
 * Cortanium never breaks and fells whole veins, and Quantum Alloy does all of it.
 */
public enum ExoticMaterial {
    ADAMANTIUM("adamantium", 4200, 10F, 5F, 12, 0xFF9FB6C8, Rarity.RARE, 0),
    FARADAENIUM("faradaenium", 2600, 14F, 4.5F, 22, 0xFF3FF2E0, Rarity.RARE, 1),
    CORTANIUM("cortanium", 9999, 11F, 7F, 15, 0xFFE0303C, Rarity.EPIC, 2),
    QUANTUM("quantum", 9999, 20F, 9F, 25, 0xFFB18CFF, Rarity.EPIC, 3);

    public final String id;
    public final ToolMaterial tool;
    public final int color;
    public final Rarity rarity;
    public final int tier;

    ExoticMaterial(String id, int durability, float speed, float damage, int enchant, int color, Rarity rarity, int tier) {
        this.id = id;
        this.color = color;
        this.rarity = rarity;
        this.tier = tier;
        TagKey<Item> repair = TagKey.create(Registries.ITEM, PowerFactor.id("repairs/" + id));
        this.tool = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, durability, speed, damage, enchant, repair);
    }

    /** 3x3 excavation with pickaxes and shovels. */
    public boolean excavates() {
        return this == ADAMANTIUM || this == QUANTUM;
    }

    /** Drops go straight into the inventory. */
    public boolean magnetic() {
        return this == FARADAENIUM || this == QUANTUM;
    }

    /** Ores and logs come down as whole veins with pickaxes and axes. */
    public boolean veinMines() {
        return this == CORTANIUM || this == QUANTUM;
    }

    public boolean unbreakable() {
        return this == CORTANIUM || this == QUANTUM;
    }
}
