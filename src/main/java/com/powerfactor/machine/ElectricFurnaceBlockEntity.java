package com.powerfactor.machine;

import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.upgrade.UpgradeType;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Smelts anything a furnace can, a quarter faster, on resistive elements: a clean PF 1.0 load
 * that draws no EE. Heat goes with V^2, so on a sagging grid it slows down rather than trips.
 */
public class ElectricFurnaceBlockEntity extends ProcessingBlockEntity {
    public static final int INPUT = 0, OUTPUT = 1;
    public static final int FE_PER_TICK = 16;
    private static final double SPEEDUP = 0.75;

    private final RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe> recipes = RecipeManager.createCheck(RecipeType.SMELTING);

    public ElectricFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ELECTRIC_FURNACE.get(), pos, state, new int[] {INPUT}, new int[] {OUTPUT}, 6_000, FE_PER_TICK,
                new HeaterDrive(), EnumSet.of(UpgradeType.SPEED, UpgradeType.EFFICIENCY, UpgradeType.CAPACITY));
    }

    @Override
    protected @Nullable Job findJob(ServerLevel level) {
        ItemStack input = items.getItem(INPUT);
        if (input.isEmpty()) {
            return null;
        }
        SingleRecipeInput recipeInput = new SingleRecipeInput(input);
        RecipeHolder<SmeltingRecipe> holder = recipes.getRecipeFor(recipeInput, level).orElse(null);
        if (holder == null) {
            return null;
        }
        ItemStack result = holder.value().assemble(recipeInput);
        if (result.isEmpty() || !fits(result, OUTPUT)) {
            return null;
        }
        int time = Math.max(1, (int) Math.round(holder.value().cookingTime() * SPEEDUP));
        return new Job(time, input.getHoverName(), () -> {
            input.shrink(1);
            merge(result, OUTPUT);
        });
    }

    @Override
    public net.minecraft.sounds.SoundEvent loopSound() {
        return com.powerfactor.registry.ModSounds.ELECTRIC_FURNACE_RUN.get();
    }

    @Override
    public float loopVolume() {
        return 0.45F;
    }

    @Override
    protected boolean isValidInput(int slot, ItemStack stack) {
        return true;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ElectricFurnaceMenu(containerId, inventory, items, upgrades, data, this);
    }
}
