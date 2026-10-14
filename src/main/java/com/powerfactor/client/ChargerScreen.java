package com.powerfactor.client;

import com.powerfactor.PowerFactor;
import com.powerfactor.machine.ChargerMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class ChargerScreen extends AbstractContainerScreen<ChargerMenu> {
    private static final Identifier TEXTURE = PowerFactor.id("textures/gui/charger.png");
    private static final int BAR_X = 156, BAR_Y = 21, BAR_W = 12, BAR_H = 49;
    private static final int ITEM_BAR_X = 62, ITEM_BAR_Y = 56, ITEM_BAR_W = 52, ITEM_BAR_H = 6;

    public ChargerScreen(ChargerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        Skin.machineBackground(graphics, font, title, leftPos, topPos, imageWidth, imageHeight, menu.slots);
        Skin.progress(graphics, leftPos + ITEM_BAR_X, topPos + ITEM_BAR_Y, ITEM_BAR_W, menu.itemCharge(), Skin.TEAL);
        GuiUtil.energyBar(graphics, leftPos + BAR_X, topPos + BAR_Y, BAR_W, BAR_H, menu.stored(), menu.capacity());

    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        String rate = menu.fe() + " FE/t";
        graphics.text(font, rate, 88 - font.width(rate) / 2, 20, Skin.FE, false);
        graphics.text(font, Math.round(menu.itemCharge() * 100) + "%", 120, 56, Skin.TEXT, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        if (isHovering(BAR_X, BAR_Y, BAR_W, BAR_H, mouseX, mouseY)) {
            GuiUtil.energyTooltip(graphics, font, menu.stored(), menu.capacity(), mouseX, mouseY);
        } else if (isHovering(ITEM_BAR_X, ITEM_BAR_Y, ITEM_BAR_W, ITEM_BAR_H, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(font, Component.literal(Math.round(menu.itemCharge() * 100) + "%"), mouseX, mouseY);
        }
    }
}
