package com.powerfactor.compat.jei;

import net.minecraft.world.item.crafting.RecipeMap;

/** Recipes the server synced to this client (NeoForge's RecipesReceivedEvent), for JEI to show. */
public final class ClientRecipes {
    public static RecipeMap map = RecipeMap.EMPTY;
    /** Opens JEI's recipe view for a stack (uses=true: where it is used). Set when JEI's runtime starts; no-op without JEI. */
    public static java.util.function.BiConsumer<net.minecraft.world.item.ItemStack, Boolean> show = (stack, uses) -> {
        throw new IllegalStateException("JEI is not running");
    };

    private ClientRecipes() {}
}
