package com.powerfactor.client;

import com.powerfactor.machine.RecipeMachine;
import com.powerfactor.machine.RecipeMachineBlockEntity;
import com.powerfactor.machine.RecipeMachineMenu;
import com.powerfactor.registry.ModBlocks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Recipe machines; the ore washer also shows its water tank. */
public class RecipeMachineScreen extends ProcessingScreen<RecipeMachineMenu> {
    private static final int TANK_X = 8, TANK_Y = 16, TANK_W = 16, TANK_H = 54;

    public RecipeMachineScreen(RecipeMachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu.machine().guiTexture(), menu.machine().arrowX, menu.machine().arrowY, menu.machine().ratedCe(),
                !menu.machine().heater, ModBlocks.RECIPE_MACHINES.get(menu.machine()).get());
    }

    @Override
    protected void extractMain(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractMain(g, mouseX, mouseY);
        if (menu.machine() == RecipeMachine.ORE_WASHER) {
            GuiUtil.fluidGauge(g, leftPos + TANK_X, topPos + TANK_Y, TANK_W, TANK_H, menu.tank(), RecipeMachineBlockEntity.TANK_CAPACITY, 0xFF3F76E4);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        if (menu.machine() == RecipeMachine.ORE_WASHER && isHovering(TANK_X, TANK_Y, TANK_W, TANK_H, mouseX, mouseY)) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.powerfactor.water", String.format("%,d", menu.tank()),
                    String.format("%,d", RecipeMachineBlockEntity.TANK_CAPACITY)), mouseX, mouseY);
        }
    }
}
