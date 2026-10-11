package com.powerfactor.registry;

import com.powerfactor.PowerFactor;
import com.powerfactor.recipe.CrushingRecipe;
import com.powerfactor.recipe.MachineRecipe;
import com.powerfactor.recipe.MachineRecipeKind;
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

    public static final java.util.Map<MachineRecipeKind, DeferredHolder<RecipeType<?>, RecipeType<MachineRecipe>>> MACHINE_TYPES =
            new java.util.EnumMap<>(MachineRecipeKind.class);
    public static final java.util.Map<MachineRecipeKind, DeferredHolder<RecipeSerializer<?>, RecipeSerializer<MachineRecipe>>> MACHINE_SERIALIZERS =
            new java.util.EnumMap<>(MachineRecipeKind.class);

    static {
        for (MachineRecipeKind kind : MachineRecipeKind.values()) {
            MACHINE_TYPES.put(kind, TYPES.register(kind.id, () -> RecipeType.simple(PowerFactor.id(kind.id))));
            MACHINE_SERIALIZERS.put(kind, SERIALIZERS.register(kind.id,
                    () -> new RecipeSerializer<>(MachineRecipe.codec(kind), MachineRecipe.streamCodec(kind))));
        }
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        SERIALIZERS.register(modBus);
        CATEGORIES.register(modBus);
    }

    private ModRecipes() {}
}
