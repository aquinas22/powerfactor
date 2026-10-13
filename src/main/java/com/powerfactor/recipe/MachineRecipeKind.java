package com.powerfactor.recipe;

import com.powerfactor.registry.ModRecipes;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

public enum MachineRecipeKind {
    ALLOYING("alloying", 200),
    ARC_SMELTING("arc_smelting", 300),
    WIRE_DRAWING("wire_drawing", 100),
    ROLLING("rolling", 100),
    WASHING("washing", 160),
    LASER_ENGRAVING("laser_engraving", 200),
    PRESSING("pressing", 80),
    MIXING("mixing", 120),
    CENTRIFUGING("centrifuging", 160),
    SAWING("sawing", 80);

    public final String id;
    public final int defaultTime;

    MachineRecipeKind(String id, int defaultTime) {
        this.id = id;
        this.defaultTime = defaultTime;
    }

    public RecipeType<MachineRecipe> type() {
        return ModRecipes.MACHINE_TYPES.get(this).get();
    }

    public RecipeSerializer<MachineRecipe> serializer() {
        return ModRecipes.MACHINE_SERIALIZERS.get(this).get();
    }
}
