package com.powerfactor.client;

import com.powerfactor.item.DrillMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class DrillScreen extends AbstractContainerScreen<DrillMenu> {
    private static final Identifier TEXTURE = com.powerfactor.PowerFactor.id("textures/gui/drill_modules.png");

    public DrillScreen(DrillMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        Skin.machineBackground(g, font, title, leftPos, topPos, imageWidth, imageHeight, menu.slots);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
        g.text(font, Component.translatable("gui.powerfactor.drill.hint"), 8, 60, Skin.DIM, false);
    }
}
