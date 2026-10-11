package com.powerfactor.recipe;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.neoforged.neoforge.fluids.FluidStack;

public record MachineRecipeInput(List<ItemStack> items, FluidStack fluid, ItemStack tool) implements RecipeInput {
    public MachineRecipeInput(List<ItemStack> items, FluidStack fluid) {
        this(items, fluid, ItemStack.EMPTY);
    }

    @Override
    public ItemStack getItem(int index) {
        return items.get(index);
    }

    @Override
    public int size() {
        return items.size();
    }
}
