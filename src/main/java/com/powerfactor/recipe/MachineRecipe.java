package com.powerfactor.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/**
 * One recipe format for every multi-input machine: counted item inputs (in any slot order), an
 * optional fluid, outputs (the first is the main product; later ones may have chances), and time
 * at stock speed. Each machine gets its own recipe type so JSON says which machine runs it:
 * {@code powerfactor:alloying}, {@code arc_smelting}, {@code wire_drawing}, {@code rolling}, {@code washing}.
 */
public record MachineRecipe(MachineRecipeKind kind, List<SizedIngredient> inputs, Optional<SizedFluidIngredient> fluid,
        List<CrushingOutput> results, int time, Optional<net.minecraft.world.item.crafting.Ingredient> tool) implements Recipe<MachineRecipeInput> {

    /** Without a tool (the item in the machine's tool slot that each recipe needs but only wears down). */
    public MachineRecipe(MachineRecipeKind kind, List<SizedIngredient> inputs, Optional<SizedFluidIngredient> fluid,
            List<CrushingOutput> results, int time) {
        this(kind, inputs, fluid, results, time, Optional.empty());
    }

    public static MapCodec<MachineRecipe> codec(MachineRecipeKind kind) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                SizedIngredient.NESTED_CODEC.listOf(1, 3).fieldOf("inputs").forGetter(MachineRecipe::inputs),
                SizedFluidIngredient.CODEC.optionalFieldOf("fluid").forGetter(MachineRecipe::fluid),
                CrushingOutput.CODEC.listOf(1, 4).fieldOf("results").forGetter(MachineRecipe::results),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("time", kind.defaultTime).forGetter(MachineRecipe::time),
                net.minecraft.world.item.crafting.Ingredient.CODEC.optionalFieldOf("tool").forGetter(MachineRecipe::tool)
        ).apply(i, (inputs, fluid, results, time, tool) -> new MachineRecipe(kind, inputs, fluid, results, time, tool)));
    }

    public static StreamCodec<RegistryFriendlyByteBuf, MachineRecipe> streamCodec(MachineRecipeKind kind) {
        return StreamCodec.composite(
                SizedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list(3)), MachineRecipe::inputs,
                ByteBufCodecs.optional(SizedFluidIngredient.STREAM_CODEC), MachineRecipe::fluid,
                CrushingOutput.STREAM_CODEC.apply(ByteBufCodecs.list(4)), MachineRecipe::results,
                ByteBufCodecs.VAR_INT, MachineRecipe::time,
                ByteBufCodecs.optional(net.minecraft.world.item.crafting.Ingredient.CONTENTS_STREAM_CODEC), MachineRecipe::tool,
                (inputs, fluid, results, time, tool) -> new MachineRecipe(kind, inputs, fluid, results, time, tool));
    }

    @Override
    public boolean matches(MachineRecipeInput input, Level level) {
        if (fluid.isPresent() && !fluid.get().test(input.fluid())) {
            return false;
        }
        if (tool.isPresent() && !tool.get().test(input.tool())) {
            return false;
        }
        return slotsFor(input) != null;
    }

    /** Which input slot satisfies each ingredient (in order), or null if they can't all be met. */
    public int[] slotsFor(MachineRecipeInput input) {
        int[] chosen = new int[inputs.size()];
        return assign(input, 0, chosen, new boolean[input.size()]) ? chosen : null;
    }

    private boolean assign(MachineRecipeInput input, int ingredient, int[] chosen, boolean[] used) {
        if (ingredient == inputs.size()) {
            // Every non-empty slot must be used, so stray items don't ride along.
            for (int s = 0; s < input.size(); s++) {
                if (!used[s] && !input.getItem(s).isEmpty()) {
                    return false;
                }
            }
            return true;
        }
        SizedIngredient need = inputs.get(ingredient);
        for (int s = 0; s < input.size(); s++) {
            if (!used[s] && need.test(input.getItem(s))) {
                used[s] = true;
                chosen[ingredient] = s;
                if (assign(input, ingredient + 1, chosen, used)) {
                    return true;
                }
                used[s] = false;
            }
        }
        return false;
    }

    public List<ItemStack> guaranteedOutputs() {
        List<ItemStack> out = new ArrayList<>();
        for (CrushingOutput o : results) {
            if (o.chance() >= 1) {
                out.add(o.resolve());
            }
        }
        return out;
    }

    @Override
    public ItemStack assemble(MachineRecipeInput input) {
        return results.getFirst().resolve();
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    @SuppressWarnings("unchecked")
    public RecipeSerializer<MachineRecipe> getSerializer() {
        return kind.serializer();
    }

    @Override
    public RecipeType<MachineRecipe> getType() {
        return kind.type();
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return com.powerfactor.registry.ModRecipes.CRUSHING_CATEGORY.get();
    }
}
