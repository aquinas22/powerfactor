package com.powerfactor.compat.jei;

import com.powerfactor.PowerFactor;
import com.powerfactor.client.MachineScreen;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;

/** Loaded by JEI only when JEI is installed. Keeps JEI's item list off our side tabs. */
@JeiPlugin
public class PowerFactorJeiPlugin implements IModPlugin {
    @Override
    public Identifier getPluginUid() {
        return PowerFactor.id("jei");
    }

    @Override
    public void registerCategories(mezz.jei.api.registration.IRecipeCategoryRegistration registration) {
        var gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new PowerFactorCategories.Crushing(gui, com.powerfactor.registry.ModBlocks.CRUSHER.get()));
        for (var m : com.powerfactor.machine.RecipeMachine.values()) {
            registration.addRecipeCategories(new PowerFactorCategories.Machine(gui, m.kind, com.powerfactor.registry.ModBlocks.RECIPE_MACHINES.get(m).get()));
        }
        registration.addRecipeCategories(new QuantumCategories.Collider(gui, com.powerfactor.registry.ModQuantum.PARTICLE_COLLIDER.get()),
                new QuantumCategories.Starforge(gui, com.powerfactor.registry.ModQuantum.STARFORGE.get()));
    }

    @Override
    public void registerRecipeTransferHandlers(mezz.jei.api.registration.IRecipeTransferRegistration registration) {
        registration.addUniversalRecipeTransferHandler(new TerminalTransfer(registration.getTransferHelper()));
    }

    @Override
    public void registerRecipes(mezz.jei.api.registration.IRecipeRegistration registration) {
        registration.addRecipes(PowerFactorCategories.CRUSHING, ClientRecipes.map.byType(com.powerfactor.registry.ModRecipes.CRUSHING.get())
                .stream().map(h -> h.value()).toList());
        for (var kind : com.powerfactor.recipe.MachineRecipeKind.values()) {
            registration.addRecipes(PowerFactorCategories.type(kind), ClientRecipes.map.byType(kind.type()).stream().map(h -> h.value()).toList());
        }
        registration.addRecipes(QuantumCategories.COLLIDER, com.powerfactor.quantum.ColliderRecipes.ALL);
        registration.addRecipes(QuantumCategories.STARFORGE, com.powerfactor.quantum.StarforgeBlockEntity.RECIPES);
    }

    @Override
    public void registerRecipeCatalysts(mezz.jei.api.registration.IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalysts(PowerFactorCategories.CRUSHING, com.powerfactor.registry.ModBlocks.CRUSHER.get());
        for (var m : com.powerfactor.machine.RecipeMachine.values()) {
            registration.addRecipeCatalysts(PowerFactorCategories.type(m.kind), com.powerfactor.registry.ModBlocks.RECIPE_MACHINES.get(m).get());
        }
        registration.addRecipeCatalysts(QuantumCategories.COLLIDER, com.powerfactor.registry.ModQuantum.PARTICLE_COLLIDER.get());
        registration.addRecipeCatalysts(QuantumCategories.STARFORGE, com.powerfactor.registry.ModQuantum.STARFORGE.get());
    }

    @Override
    public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime runtime) {
        ClientRecipes.show = (stack, uses) -> runtime.getRecipesGui().show(runtime.getJeiHelpers().getFocusFactory().createFocus(
                uses ? mezz.jei.api.recipe.RecipeIngredientRole.INPUT : mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT,
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK, stack));
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler((Class) MachineScreen.class, new IGuiContainerHandler<MachineScreen<?>>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(MachineScreen<?> screen) {
                return screen.extraAreas();
            }
        });
    }
}
