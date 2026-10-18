package com.powerfactor.compat.jei;

import com.powerfactor.PowerFactor;
import com.powerfactor.chem.ChemicalReactorBlockEntity;
import com.powerfactor.chem.OreDissolverBlockEntity;
import com.powerfactor.chem.OreMetal;
import com.powerfactor.registry.ModChem;
import com.powerfactor.registry.ModFluids;
import com.powerfactor.registry.ModWash;
import com.powerfactor.wash.WashPlantBlockEntity;
import java.util.ArrayList;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluids;

/** JEI pages for ore processing: the Wash Plant, the Chemical Reactor and the Ore Dissolver. */
public final class ProcessCategories {
    /** One metal's (or gravel's) wash: what goes in, the main product and its count, and the byproducts with chances. */
    public record Wash(String id, List<ItemStack> inputs, ItemStack main, List<ItemStack> byproducts, List<String> chances) {}

    /** The dissolver for one metal: ore in, crystals out, with or without the catalyst. */
    public record Dissolve(OreMetal metal, boolean catalyst) {}

    public record React(int dummy) {}

    public static final IRecipeType<Wash> WASH = IRecipeType.create(PowerFactor.id("wash_plant"), Wash.class);
    public static final IRecipeType<Dissolve> DISSOLVE = IRecipeType.create(PowerFactor.id("ore_dissolver"), Dissolve.class);
    public static final IRecipeType<React> REACT = IRecipeType.create(PowerFactor.id("chemical_reactor"), React.class);
    static final int ACID = 0xFFE4E25A, WATER = 0xFF3F76E4;

    static List<ItemStack> tagged(net.minecraft.tags.TagKey<Item> tag) {
        List<ItemStack> out = new ArrayList<>();
        BuiltInRegistries.ITEM.getTagOrEmpty(tag).forEach(h -> out.add(new ItemStack(h.value())));
        return out;
    }

    public static List<Wash> washes() {
        List<Wash> list = new ArrayList<>();
        for (OreMetal m : OreMetal.values()) {
            List<ItemStack> in = new ArrayList<>(tagged(m.raw));
            in.addAll(tagged(m.ore));
            list.add(new Wash(m.id(), in, new ItemStack(ModWash.CONCENTRATES.get(m).get(), WashPlantBlockEntity.YIELD),
                    List.of(new ItemStack(Items.GOLD_NUGGET, 2), new ItemStack(Items.FLINT), new ItemStack(Items.LAPIS_LAZULI), new ItemStack(Items.DIAMOND),
                            new ItemStack(Items.EMERALD)), List.of("20%", "8%", "3%", "0.6%", "0.4%")));
        }
        list.add(new Wash("gravel", List.of(new ItemStack(Items.GRAVEL), new ItemStack(Items.SAND), new ItemStack(Items.RED_SAND)), new ItemStack(Items.GOLD_NUGGET),
                List.of(new ItemStack(Items.IRON_NUGGET, 2), new ItemStack(Items.FLINT), new ItemStack(Items.DIAMOND)), List.of("30%", "10%", "15%", "0.2%")));
        return list;
    }

    public static List<Dissolve> dissolves() {
        List<Dissolve> list = new ArrayList<>();
        for (OreMetal m : OreMetal.values()) {
            list.add(new Dissolve(m, false));
            list.add(new Dissolve(m, true));
        }
        return list;
    }

    static Component note(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.DARK_GRAY);
    }

    abstract static class Base<T> implements IRecipeCategory<T> {
        private final IDrawable icon;

        Base(IGuiHelper gui, ItemLike block) {
            this.icon = gui.createDrawableItemLike(block);
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public int getWidth() {
            return 150;
        }
    }

    // ------------------------------------------------------------------ wash plant

    public static class WashPlant extends Base<Wash> {
        public WashPlant(IGuiHelper gui, ItemLike block) {
            super(gui, block);
        }

        @Override
        public IRecipeType<Wash> getRecipeType() {
            return WASH;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("block.powerfactor.wash_plant");
        }

        @Override
        public int getHeight() {
            return 58;
        }

        @Override
        public Identifier getIdentifier(Wash r) {
            return PowerFactor.id("wash/" + r.id());
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder b, Wash r, IFocusGroup focuses) {
            b.addSlot(RecipeIngredientRole.INPUT, 4, 4).setStandardSlotBackground().addItemStacks(r.inputs());
            b.addSlot(RecipeIngredientRole.INPUT, 4, 24).setStandardSlotBackground()
                    .add(Fluids.WATER, WashPlantBlockEntity.WATER_PER).setFluidRenderer(WashPlantBlockEntity.WATER_PER, false, 16, 16);
            b.addSlot(RecipeIngredientRole.OUTPUT, 74, 4).setOutputSlotBackground().add(r.main());
            for (int i = 0; i < r.byproducts().size(); i++) {
                String chance = r.chances().get(i);
                b.addSlot(RecipeIngredientRole.OUTPUT, 74 + (i % 4) * 18 - (i >= 4 ? 0 : 0), 24 + (i / 4) * 18).setStandardSlotBackground().add(r.byproducts().get(i))
                        .addRichTooltipCallback((view, tip) -> tip.add(Component.literal(chance).withStyle(ChatFormatting.GOLD)));
            }
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder extras, Wash r, IFocusGroup focuses) {
            extras.addText(note("jei.powerfactor.wash.cost", WashPlantBlockEntity.FE_PER_TICK, WashPlantBlockEntity.TIME / 20), 150, 10).setPosition(0, 48);
        }

        @Override
        public void draw(Wash r, IRecipeSlotsView slots, GuiGraphicsExtractor g, double mx, double my) {
            // A little sluice: water running down the riffles toward the catch.
            long t = Util.getMillis();
            for (int x = 26; x < 68; x++) {
                int y = 8 + (x - 26) / 7;
                g.fill(x, y + 4, x + 1, y + 5, 0xFF8C969C);
                if ((x - 26) % 6 == 0) g.fill(x, y + 3, x + 1, y + 4, 0xFFC8D0D4);
                if ((x + t / 40) % 5 < 3) g.fill(x, y + 2, x + 1, y + 3, 0xC06AA0D0);
            }
        }
    }

    // ------------------------------------------------------------------ chemical reactor

    public static class Reactor extends Base<React> {
        public Reactor(IGuiHelper gui, ItemLike block) {
            super(gui, block);
        }

        @Override
        public IRecipeType<React> getRecipeType() {
            return REACT;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("block.powerfactor.chemical_reactor");
        }

        @Override
        public int getHeight() {
            return 44;
        }

        @Override
        public Identifier getIdentifier(React r) {
            return PowerFactor.id("reactor/sulfuric_acid");
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder b, React r, IFocusGroup focuses) {
            b.addSlot(RecipeIngredientRole.INPUT, 4, 8).setStandardSlotBackground().add(new ItemStack(ModChem.SULFUR_DUST.get()));
            b.addSlot(RecipeIngredientRole.INPUT, 24, 8).setStandardSlotBackground()
                    .add(Fluids.WATER, ChemicalReactorBlockEntity.WATER_PER).setFluidRenderer(ChemicalReactorBlockEntity.WATER_PER, false, 16, 16);
            b.addSlot(RecipeIngredientRole.INPUT, 44, 8).setStandardSlotBackground()
                    .add(ModFluids.HYDROGEN.get(), ChemicalReactorBlockEntity.HYDROGEN_PER).setFluidRenderer(ChemicalReactorBlockEntity.HYDROGEN_PER, false, 16, 16);
            b.addSlot(RecipeIngredientRole.OUTPUT, 126, 8).setOutputSlotBackground()
                    .add(ModFluids.SULFURIC_ACID.source().get(), ChemicalReactorBlockEntity.ACID_PER)
                    .setFluidRenderer(ChemicalReactorBlockEntity.ACID_PER, false, 16, 16);
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder extras, React r, IFocusGroup focuses) {
            extras.addText(note("jei.powerfactor.energy_time", ChemicalReactorBlockEntity.FE_PER_TICK * ChemicalReactorBlockEntity.TIME,
                    ChemicalReactorBlockEntity.TIME / 20), 150, 10).setPosition(0, 32);
        }

        @Override
        public void draw(React r, IRecipeSlotsView slots, GuiGraphicsExtractor g, double mx, double my) {
            long t = Util.getMillis();
            int fill = (int) (t / 60 % 18);
            g.fill(72, 6, 108, 26, 0xFF0D2231);
            g.fill(73, 25 - fill, 107, 25, ACID);
            for (int k = 0; k < 5; k++) {
                int by = 24 - (int) ((t / 40 + k * 7) % 18);
                g.fill(76 + k * 6, by, 78 + k * 6, by + 2, 0xC0FFF6A0);
            }
        }
    }

    // ------------------------------------------------------------------ ore dissolver

    public static class Dissolver extends Base<Dissolve> {
        public Dissolver(IGuiHelper gui, ItemLike block) {
            super(gui, block);
        }

        @Override
        public IRecipeType<Dissolve> getRecipeType() {
            return DISSOLVE;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("block.powerfactor.ore_dissolver");
        }

        @Override
        public int getHeight() {
            return 44;
        }

        @Override
        public Identifier getIdentifier(Dissolve r) {
            return PowerFactor.id("dissolve/" + r.metal().id() + (r.catalyst() ? "_catalyst" : ""));
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder b, Dissolve r, IFocusGroup focuses) {
            List<ItemStack> in = new ArrayList<>(tagged(r.metal().raw));
            in.addAll(tagged(r.metal().ore));
            b.addSlot(RecipeIngredientRole.INPUT, 4, 8).setStandardSlotBackground().addItemStacks(in);
            b.addSlot(RecipeIngredientRole.INPUT, 24, 8).setStandardSlotBackground()
                    .add(ModFluids.SULFURIC_ACID.source().get(), OreDissolverBlockEntity.ACID_PER).setFluidRenderer(OreDissolverBlockEntity.ACID_PER, false, 16, 16);
            if (r.catalyst()) b.addSlot(RecipeIngredientRole.CRAFTING_STATION, 66, 8).setStandardSlotBackground().add(new ItemStack(ModChem.QUANTUM_CATALYST.get()));
            b.addSlot(RecipeIngredientRole.OUTPUT, 126, 8).setOutputSlotBackground()
                    .add(new ItemStack(ModChem.CRYSTALS.get(r.metal()).get(), r.catalyst() ? 5 : 4));
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder extras, Dissolve r, IFocusGroup focuses) {
            extras.addText(note("jei.powerfactor.energy_time", OreDissolverBlockEntity.FE_PER_TICK * OreDissolverBlockEntity.TIME,
                    OreDissolverBlockEntity.TIME / 20), 150, 10).setPosition(0, 32);
        }

        @Override
        public void draw(Dissolve r, IRecipeSlotsView slots, GuiGraphicsExtractor g, double mx, double my) {
            com.powerfactor.client.Skin.chevrons(g, 90, 12, 3, (Util.getMillis() % 2000) / 2000F, true);
        }
    }

    private ProcessCategories() {}
}
