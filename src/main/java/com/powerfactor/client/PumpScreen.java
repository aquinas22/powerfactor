package com.powerfactor.client;

import com.powerfactor.PowerFactor;
import com.powerfactor.logistics.PumpBlockEntity;
import com.powerfactor.logistics.PumpMenu;
import com.powerfactor.registry.ModBlocks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class PumpScreen extends ProcessingScreen<PumpMenu> {
    private static final int TANK_X = 72, TANK_Y = 22, TANK_W = 76, TANK_H = 54;

    public PumpScreen(PumpMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PowerFactor.id("textures/gui/electric_pump.png"), -100, -100, 17, true, ModBlocks.ELECTRIC_PUMP.get());
    }

    @Override
    protected void extractMain(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractMain(g, mouseX, mouseY);
        GuiUtil.fluidGauge(g, leftPos + TANK_X, topPos + TANK_Y, TANK_W, TANK_H, menu.tank(), PumpBlockEntity.CAPACITY, 0xFF3F76E4);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        if (isHovering(TANK_X, TANK_Y, TANK_W, TANK_H, mouseX, mouseY)) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.powerfactor.water", String.format("%,d", menu.tank()),
                    String.format("%,d", PumpBlockEntity.CAPACITY)), mouseX, mouseY);
        }
    }
}
