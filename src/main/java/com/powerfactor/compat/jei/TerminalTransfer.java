package com.powerfactor.compat.jei;

import com.powerfactor.registry.ModStorage;
import com.powerfactor.storage.GridPayloads;
import com.powerfactor.storage.TerminalBlockEntity;
import com.powerfactor.storage.TerminalMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * JEI's "+" button on terminals. On a pattern terminal any recipe becomes a pattern: crafting
 * recipes as crafting patterns, everything else as a processing pattern with its amounts. On a
 * crafting terminal a crafting recipe fills the grid from the network (shift fills a stack).
 */
public class TerminalTransfer implements IUniversalRecipeTransferHandler<TerminalMenu> {
    private final IRecipeTransferHandlerHelper helper;

    public TerminalTransfer(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    @Override
    public Class<? extends TerminalMenu> getContainerClass() {
        return TerminalMenu.class;
    }

    @Override
    public Optional<MenuType<TerminalMenu>> getMenuType() {
        return Optional.of(ModStorage.TERMINAL_MENU.get());
    }

    @Override
    public IRecipeTransferError transferRecipe(TerminalMenu menu, Object recipe, IRecipeSlotsView slots, Player player, boolean maxTransfer,
            boolean doTransfer) {
        boolean crafting = recipe instanceof RecipeHolder<?> h && h.value() instanceof CraftingRecipe;
        TerminalBlockEntity.Kind kind = menu.kind();
        if (kind == TerminalBlockEntity.Kind.GRID) return helper.createUserErrorWithTooltip(Component.translatable("jei.powerfactor.transfer.grid"));
        if (kind == TerminalBlockEntity.Kind.CRAFTING && !crafting) {
            return helper.createUserErrorWithTooltip(Component.translatable("jei.powerfactor.transfer.not_crafting"));
        }
        List<IRecipeSlotView> in = slots.getSlotViews(RecipeIngredientRole.INPUT);
        List<IRecipeSlotView> out = slots.getSlotViews(RecipeIngredientRole.OUTPUT);
        if (!crafting && in.stream().noneMatch(v -> v.getItemStacks().findAny().isPresent())) {
            return helper.createUserErrorWithTooltip(Component.translatable("jei.powerfactor.transfer.no_items"));
        }
        if (!doTransfer) return null;
        List<List<ItemStack>> inputs = new ArrayList<>();
        for (IRecipeSlotView v : in) {
            // The displayed stack first so the player's chosen variant wins ties.
            List<ItemStack> alts = new ArrayList<>();
            v.getDisplayedItemStack().ifPresent(s -> alts.add(s.copy()));
            v.getItemStacks().limit(32).forEach(s -> {
                if (alts.stream().noneMatch(a -> ItemStack.isSameItemSameComponents(a, s))) alts.add(s.copy());
            });
            inputs.add(alts);
            if (inputs.size() >= 81) break;
        }
        List<ItemStack> outputs = new ArrayList<>();
        for (IRecipeSlotView v : out) {
            v.getDisplayedItemStack().ifPresent(s -> outputs.add(s.copy()));
            if (outputs.size() >= 9) break;
        }
        ClientPacketDistributor.sendToServer(new GridPayloads.Transfer(menu.containerId, !crafting, maxTransfer, inputs, outputs));
        return null;
    }
}
