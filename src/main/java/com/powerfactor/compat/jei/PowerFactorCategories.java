package com.powerfactor.compat.jei;

import com.powerfactor.PowerFactor;
import com.powerfactor.recipe.CrushingOutput;
import com.powerfactor.recipe.CrushingRecipe;
import com.powerfactor.recipe.MachineRecipe;
import com.powerfactor.recipe.MachineRecipeKind;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/** JEI categories for the crusher and the five recipe machines. */
public final class PowerFactorCategories {
    public static final IRecipeType<CrushingRecipe> CRUSHING = IRecipeType.create(PowerFactor.id("crushing"), CrushingRecipe.class);

    public static IRecipeType<MachineRecipe> type(MachineRecipeKind kind) {
        return IRecipeType.create(PowerFactor.id(kind.id), MachineRecipe.class);
    }

    static void output(IRecipeSlotBuilder slot, CrushingOutput out) {
        ItemStack stack = out.resolve();
        slot.setOutputSlotBackground().add(stack);
        if (out.chance() < 1) {
            slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(
                    Component.translatable("jei.powerfactor.chance", Math.round(out.chance() * 100)).withStyle(ChatFormatting.GOLD)));
        }
    }

    public static class Crushing implements IRecipeCategory<CrushingRecipe> {
        private final IDrawable icon;

        public Crushing(IGuiHelper gui, ItemLike crusher) {
            this.icon = gui.createDrawableItemLike(crusher);
        }

        @Override
        public IRecipeType<CrushingRecipe> getRecipeType() {
            return CRUSHING;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("jei.powerfactor.crushing");
        }

        @Override
        public int getWidth() {
            return 130;
        }

        @Override
        public int getHeight() {
            return 26;
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, CrushingRecipe recipe, IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.INPUT, 4, 5).setStandardSlotBackground().add(recipe.ingredient());
            output(builder.addSlot(RecipeIngredientRole.OUTPUT, 64, 5), recipe.result());
            for (int i = 0; i < Math.min(2, recipe.bonus().size()); i++) {
                output(builder.addSlot(RecipeIngredientRole.OUTPUT, 86 + i * 20, 5), recipe.bonus().get(i));
            }
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder extras, CrushingRecipe recipe, IFocusGroup focuses) {
            extras.addAnimatedRecipeArrow(recipe.time()).setPosition(30, 5);
        }
    }

    public static class Machine implements IRecipeCategory<MachineRecipe> {
        private final MachineRecipeKind kind;
        private final IDrawable icon;

        public Machine(IGuiHelper gui, MachineRecipeKind kind, ItemLike machine) {
            this.kind = kind;
            this.icon = gui.createDrawableItemLike(machine);
        }

        @Override
        public IRecipeType<MachineRecipe> getRecipeType() {
            return type(kind);
        }

        @Override
        public Component getTitle() {
            return Component.translatable("jei.powerfactor." + kind.id);
        }

        @Override
        public int getWidth() {
            return 160;
        }

        @Override
        public int getHeight() {
            return 26;
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, MachineRecipe recipe, IFocusGroup focuses) {
            List<SizedIngredient> inputs = recipe.inputs();
            for (int i = 0; i < inputs.size(); i++) {
                SizedIngredient in = inputs.get(i);
                IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.INPUT, 4 + i * 18, 5).setStandardSlotBackground();
                in.ingredient().items().forEach(h -> slot.add(new ItemStack(h, in.count())));
            }
            recipe.fluid().ifPresent(f -> {
                IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.INPUT, 58, 5).setStandardSlotBackground()
                        .setFluidRenderer(f.amount(), false, 16, 16);
                f.ingredient().fluids().forEach(h -> slot.add(h.value(), f.amount()));
            });
            for (int i = 0; i < recipe.results().size(); i++) {
                output(builder.addSlot(RecipeIngredientRole.OUTPUT, 108 + i * 20, 5), recipe.results().get(i));
            }
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder extras, MachineRecipe recipe, IFocusGroup focuses) {
            extras.addAnimatedRecipeArrow(recipe.time()).setPosition(78, 5);
        }
    }

    private PowerFactorCategories() {}
}
