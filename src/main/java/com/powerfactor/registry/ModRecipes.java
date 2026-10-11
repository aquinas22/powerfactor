package com.powerfactor.registry;

import com.powerfactor.PowerFactor;
import com.powerfactor.recipe.CrushingRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipes {
    public static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, PowerFactor.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, PowerFactor.MODID);
    public static final DeferredRegister<RecipeBookCategory> CATEGORIES = DeferredRegister.create(Registries.RECIPE_BOOK_CATEGORY, PowerFactor.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<CrushingRecipe>> CRUSHING = TYPES.register("crushing",
            () -> RecipeType.simple(PowerFactor.id("crushing")));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<CrushingRecipe>> CRUSHING_SERIALIZER = SERIALIZERS.register("crushing",
            () -> CrushingRecipe.SERIALIZER);
    public static final DeferredHolder<RecipeBookCategory, RecipeBookCategory> CRUSHING_CATEGORY = CATEGORIES.register("crushing", RecipeBookCategory::new);

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        SERIALIZERS.register(modBus);
        CATEGORIES.register(modBus);
    }

    private ModRecipes() {}
}
