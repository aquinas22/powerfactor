package com.powerfactor.compat.jei;

import net.minecraft.world.item.crafting.RecipeMap;

/** Recipes the server synced to this client (NeoForge's RecipesReceivedEvent), for JEI to show. */
public final class ClientRecipes {
    public static RecipeMap map = RecipeMap.EMPTY;

    private ClientRecipes() {}
}
