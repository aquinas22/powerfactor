package com.powerfactor.compat.jei;

import com.powerfactor.PowerFactor;
import com.powerfactor.client.Skin;
import com.powerfactor.quantum.ColliderRecipes;
import com.powerfactor.quantum.StarforgeBlockEntity;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/** JEI pages for the Particle Collider and the Starforge, each with a small animated instrument. */
public final class QuantumCategories {
    public static final IRecipeType<ColliderRecipes.Recipe> COLLIDER = IRecipeType.create(PowerFactor.id("particle_collider"),
            ColliderRecipes.Recipe.class);
    public static final IRecipeType<StarforgeBlockEntity.Recipe> STARFORGE = IRecipeType.create(PowerFactor.id("starforge"),
            StarforgeBlockEntity.Recipe.class);
    static final int VIOLET = 0xFFB18CFF, CYAN = 0xFF6FE3FF, ORANGE = 0xFFFFB347;

    /** Every registered item a predicate-based collider recipe accepts, for JEI's slot cycle. */
    static List<ItemStack> accepted(ColliderRecipes.Recipe r) {
        return BuiltInRegistries.ITEM.stream().filter(i -> i != net.minecraft.world.item.Items.AIR).map(i -> new ItemStack(i, r.count()))
                .filter(r.input()).limit(24).toList();
    }

    static Component energy(long fe) {
        return Component.translatable("jei.powerfactor.energy", Skin.compact(fe)).withStyle(ChatFormatting.DARK_GRAY);
    }

    public static class Collider implements IRecipeCategory<ColliderRecipes.Recipe> {
        private final IDrawable icon;

        public Collider(IGuiHelper gui, ItemLike block) {
            this.icon = gui.createDrawableItemLike(block);
        }

        @Override
        public IRecipeType<ColliderRecipes.Recipe> getRecipeType() {
            return COLLIDER;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("jei.powerfactor.particle_collider");
        }

        @Override
        public int getWidth() {
            return 150;
        }

        @Override
        public int getHeight() {
            return 52;
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public Identifier getIdentifier(ColliderRecipes.Recipe recipe) {
            return PowerFactor.id("collider/" + recipe.id());
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, ColliderRecipes.Recipe recipe, IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.INPUT, 6, 18).setStandardSlotBackground().addItemStacks(accepted(recipe));
            builder.addSlot(RecipeIngredientRole.OUTPUT, 126, 18).setOutputSlotBackground().add(recipe.output().get());
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder extras, ColliderRecipes.Recipe recipe, IFocusGroup focuses) {
            extras.addText(energy(recipe.energy()), 150, 10).setPosition(0, 43)
                    .setTextAlignment(mezz.jei.api.gui.placement.HorizontalAlignment.CENTER);
        }

        @Override
        public void draw(ColliderRecipes.Recipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor g, double mx, double my) {
            // The accelerator ring with two counter-rotating bunches that meet in a flash.
            int cx = 75, cy = 25, r = 17;
            double t = (Util.getMillis() % 2400) / 2400.0;
            for (int i = 0; i < 40; i++) {
                double a = i * Math.PI * 2 / 40;
                int px = cx + (int) Math.round(Math.cos(a) * r), py = cy + (int) Math.round(Math.sin(a) * r);
                g.fill(px, py, px + 1, py + 1, 0xFF7A8C99);
            }
            for (int b = 0; b < 2; b++) {
                for (int tail = 0; tail < 7; tail++) {
                    double a = (b == 0 ? 1 : -1) * (t * Math.PI * 2 - tail * 0.09) + Math.PI / 2;
                    int px = cx + (int) Math.round(Math.cos(a) * r), py = cy + (int) Math.round(Math.sin(a) * r);
                    int alpha = 255 - tail * 34;
                    int s = tail == 0 ? 1 : 0;
                    g.fill(px - s, py - s, px + 1 + s, py + 1 + s, alpha << 24 | (b == 0 ? CYAN : VIOLET) & 0xFFFFFF);
                }
            }
            // Meeting point (bottom of the ring): flash as the bunches cross.
            double meet = Math.min(t, 1 - t) * 2;
            if (meet < 0.08) {
                int a = (int) (255 * (1 - meet / 0.08));
                g.fill(cx - 3, cy - r - 3, cx + 4, cy - r + 4, a << 24 | 0xFFFFFF);
            }
            g.fill(30, 24, 54, 26, 0xFF8CA0AE);
            g.fill(97, 24, 121, 26, 0xFF8CA0AE);
            g.fill(119, 22, 120, 28, 0xFF8CA0AE);
            g.fill(120, 23, 121, 27, 0xFF8CA0AE);
        }
    }

    public static class Starforge implements IRecipeCategory<StarforgeBlockEntity.Recipe> {
        private final IDrawable icon;

        public Starforge(IGuiHelper gui, ItemLike block) {
            this.icon = gui.createDrawableItemLike(block);
        }

        @Override
        public IRecipeType<StarforgeBlockEntity.Recipe> getRecipeType() {
            return STARFORGE;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("jei.powerfactor.starforge");
        }

        @Override
        public int getWidth() {
            return 150;
        }

        @Override
        public int getHeight() {
            return 74;
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public Identifier getIdentifier(StarforgeBlockEntity.Recipe recipe) {
            return PowerFactor.id("starforge/" + recipe.id());
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, StarforgeBlockEntity.Recipe recipe, IFocusGroup focuses) {
            // Tools on three sides of the crucible, catalyst beneath, paxel out to the right.
            builder.addSlot(RecipeIngredientRole.INPUT, 30, 4).setStandardSlotBackground().add(recipe.pick().get());
            builder.addSlot(RecipeIngredientRole.INPUT, 8, 26).setStandardSlotBackground().add(recipe.axe().get());
            builder.addSlot(RecipeIngredientRole.INPUT, 52, 26).setStandardSlotBackground().add(recipe.shovel().get());
            builder.addSlot(RecipeIngredientRole.INPUT, 30, 48).setStandardSlotBackground().add(recipe.catalyst().get())
                    .addRichTooltipCallback((v, tip) -> tip.add(Component.translatable("jei.powerfactor.starforge.catalyst")
                            .withStyle(ChatFormatting.GOLD)));
            builder.addSlot(RecipeIngredientRole.OUTPUT, 120, 26).setOutputSlotBackground().add(recipe.paxel().get());
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder extras, StarforgeBlockEntity.Recipe recipe, IFocusGroup focuses) {
            extras.addText(energy(recipe.energy()), 72, 10).setPosition(76, 62);
            extras.addTooltipArea(28, 24, 22, 22).setTooltip(List.of(Component.translatable("jei.powerfactor.starforge.merge")));
        }

        @Override
        public void draw(StarforgeBlockEntity.Recipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor g, double mx, double my) {
            long ms = Util.getMillis();
            int cx = 39, cy = 35;
            // Crucible: a hot core that breathes, with arcs from the three tool slots.
            float pulse = 0.5F + 0.5F * (float) Math.sin(ms / 220.0);
            for (int dy = -7; dy <= 7; dy++) {
                for (int dx = -7; dx <= 7; dx++) {
                    double d = Math.sqrt(dx * dx + dy * dy);
                    if (d > 7) continue;
                    float k = (float) (1 - d / 7);
                    int a = (int) (255 * Math.min(1, k * (1.1F + 0.6F * pulse)));
                    int col = d < 2.5 ? 0xFFF0D8 : d < 4.5 ? 0xFFB347 : 0xFF7A1A;
                    g.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, a << 24 | col);
                }
            }
            // Arrow charging up towards the paxel.
            float f = (ms % 3000) / 3000F;
            g.fill(80, 33, 112, 37, 0xFFC8D2DA);
            g.fill(80, 33, 80 + Math.round(32 * f), 37, 0xFFFF9A3C);
            for (int k = 0; k < 4; k++) g.fill(112 + k, 31 + k, 113 + k, 39 - k, f > 0.98F ? 0xFFFFFFFF : 0xFFC8D2DA);
        }
    }

    private QuantumCategories() {}
}
