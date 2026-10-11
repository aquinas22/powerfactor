package com.powerfactor.machine;

import com.powerfactor.PowerFactorConfig;
import com.powerfactor.recipe.CrushingOutput;
import com.powerfactor.recipe.CrushingRecipe;
import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.registry.ModRecipes;
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
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Jaw crusher: ores to two dusts, cobble to gravel to sand. Driven by an induction motor, so it
 * cares about voltage, frequency and power factor (see {@link MotorDrive}).
 */
public class CrusherBlockEntity extends ProcessingBlockEntity {
    public static final int INPUT = 0, OUTPUT = 1, BONUS = 2;

    private final RecipeManager.CachedCheck<SingleRecipeInput, CrushingRecipe> recipes = RecipeManager.createCheck(ModRecipes.CRUSHING.get());

    public CrusherBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRUSHER.get(), pos, state, new int[] {INPUT}, new int[] {OUTPUT, BONUS}, 8_000,
                PowerFactorConfig.SPEC.isLoaded() ? PowerFactorConfig.CRUSHER_FE_PER_TICK.getAsInt() : 20,
                new MotorDrive(PowerFactorConfig.SPEC.isLoaded() ? PowerFactorConfig.CRUSHER_EE_PER_TICK.getAsInt() : 15),
                EnumSet.allOf(UpgradeType.class));
    }

    @Override
    protected @Nullable Job findJob(ServerLevel level) {
        ItemStack input = items.getItem(INPUT);
        if (input.isEmpty()) {
            return null;
        }
        CrushingRecipe recipe = recipes.getRecipeFor(new SingleRecipeInput(input), level).map(RecipeHolder::value).orElse(null);
        if (recipe == null) {
            return null;
        }
        ItemStack result = recipe.result().resolve();
        if (result.isEmpty() || !fits(result, OUTPUT)) {
            return null;
        }
        for (CrushingOutput bonus : recipe.bonus()) {
            if (!fits(bonus.resolve(), BONUS)) {
                return null;
            }
        }
        return new Job(recipe.time(), input.getHoverName(), () -> {
            input.shrink(1);
            merge(result, OUTPUT);
            for (CrushingOutput bonus : recipe.bonus()) {
                if (level.getRandom().nextFloat() < bonus.chance()) {
                    merge(bonus.resolve(), BONUS);
                }
            }
        });
    }

    @Override
    protected boolean isValidInput(int slot, ItemStack stack) {
        return true;
    }

    @Override
    public net.minecraft.sounds.SoundEvent loopSound() {
        return com.powerfactor.registry.ModSounds.CRUSHER_RUN.get();
    }

    public Drive.State motor() {
        return drive.state();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new CrusherMenu(containerId, inventory, items, upgrades, data, this);
    }
}
