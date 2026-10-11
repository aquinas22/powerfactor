package com.powerfactor.registry;

import com.powerfactor.PowerFactor;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class ModTags {
    /** What the coal turbine will burn. Burn time comes from the item's normal furnace fuel value. */
    public static final TagKey<Item> TURBINE_FUELS = TagKey.create(Registries.ITEM, PowerFactor.id("turbine_fuels"));

    private ModTags() {}
}
