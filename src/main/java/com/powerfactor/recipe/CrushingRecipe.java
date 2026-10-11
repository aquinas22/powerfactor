package com.powerfactor.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.powerfactor.registry.ModRecipes;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/**
 * {@code powerfactor:crushing}: one input, a main output, optional bonus outputs with chances, and
 * how many ticks it takes at full power.
 */
public record CrushingRecipe(Ingredient ingredient, CrushingOutput result, List<CrushingOutput> bonus, int time) implements Recipe<SingleRecipeInput> {
    public static final int DEFAULT_TIME = 100;

    public static final MapCodec<CrushingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.fieldOf("ingredient").forGetter(CrushingRecipe::ingredient),
            CrushingOutput.CODEC.fieldOf("result").forGetter(CrushingRecipe::result),
            CrushingOutput.CODEC.listOf().optionalFieldOf("bonus", List.of()).forGetter(CrushingRecipe::bonus),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(CrushingRecipe::time)
    ).apply(i, CrushingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CrushingRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, CrushingRecipe::ingredient,
            CrushingOutput.STREAM_CODEC, CrushingRecipe::result,
            CrushingOutput.STREAM_CODEC.apply(ByteBufCodecs.list()), CrushingRecipe::bonus,
            ByteBufCodecs.VAR_INT, CrushingRecipe::time,
            CrushingRecipe::new);

    public static final RecipeSerializer<CrushingRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return ingredient.test(input.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input) {
        return result.resolve();
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
    public RecipeSerializer<CrushingRecipe> getSerializer() {
        return ModRecipes.CRUSHING_SERIALIZER.get();
    }

    @Override
    public RecipeType<CrushingRecipe> getType() {
        return ModRecipes.CRUSHING.get();
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return ModRecipes.CRUSHING_CATEGORY.get();
    }
}
