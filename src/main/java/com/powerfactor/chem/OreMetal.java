package com.powerfactor.chem;

import java.util.Locale;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Metals the Ore Dissolver takes: any raw ore or ore block in the common c: tags. Each dissolves into
 * crystals of its own colour, which crush back to that metal's dust.
 */
public enum OreMetal {
    IRON(0xFFD8B7A0), GOLD(0xFFFFD84A), COPPER(0xFFE88A5A), ZINC(0xFFB8C8D0), TIN(0xFFD8E0E8), LEAD(0xFF7A80A8), SILVER(0xFFE8F0FF), ALUMINUM(0xFFC8D8E8);

    public final int color;
    public final TagKey<Item> raw, ore;

    OreMetal(int color) {
        this.color = color;
        String id = name().toLowerCase(Locale.ROOT);
        raw = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "raw_materials/" + id));
        ore = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "ores/" + id));
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static OreMetal of(ItemStack stack) {
        for (OreMetal m : values()) if (stack.is(m.raw) || stack.is(m.ore)) return m;
        return null;
    }
}
