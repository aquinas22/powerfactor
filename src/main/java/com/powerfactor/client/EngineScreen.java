package com.powerfactor.client;

import com.powerfactor.PowerFactor;
import com.powerfactor.engine.EngineMenu;
import com.powerfactor.power.EngineHeat;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/** Fuel slot, a heat thermometer in BuildCraft's four colors, and the FE buffer. */
public class EngineScreen extends AbstractContainerScreen<EngineMenu> {
    private static final Identifier TEXTURE = PowerFactor.id("textures/gui/engine.png");
    private static final Identifier LIT_SPRITE = Identifier.withDefaultNamespace("container/furnace/lit_progress");
    private static final int HEAT_X = 80, HEAT_Y = 16, HEAT_W = 16, HEAT_H = 54;
    private static final int BAR_X = 156, BAR_Y = 16, BAR_W = 12, BAR_H = 54;
    private static final int[] STAGE_COLORS = {0xFF4A7BFF, 0xFF3CC94A, 0xFFF2D030, 0xFFE0302A};

    public EngineScreen(EngineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        int x = leftPos, y = topPos;
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 256, 256);
        if (menu.burnProgress() > 0) {
            int h = Mth.ceil(menu.burnProgress() * 13F) + 1;
            g.blitSprite(RenderPipelines.GUI_TEXTURED, LIT_SPRITE, 14, 14, 0, 14 - h, x + 45, y + 36 + 14 - h, 14, h);
        }
        // Thermometer: each heat band filled in its stage color up to the current heat.
        int heatPx = (HEAT_H - 2) * menu.heat() / EngineHeat.MAX;
        int bottom = y + HEAT_Y + HEAT_H - 1;
        int[] bands = {0, EngineHeat.GREEN, EngineHeat.YELLOW, EngineHeat.RED, EngineHeat.MAX};
        for (int i = 0; i < 4; i++) {
            int from = (HEAT_H - 2) * bands[i] / EngineHeat.MAX;
            int to = Math.min(heatPx, (HEAT_H - 2) * bands[i + 1] / EngineHeat.MAX);
            if (to > from) {
                g.fill(x + HEAT_X + 1, bottom - to, x + HEAT_X + HEAT_W - 1, bottom - from, STAGE_COLORS[i]);
            }
        }
        GuiUtil.energyBar(g, x + BAR_X, y + BAR_Y, BAR_W, BAR_H, menu.stored(), EngineMenu.CAPACITY);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
        super.extractLabels(g, xm, ym);
        Component status = menu.seized() ? Component.translatable("gui.powerfactor.engine.seized")
                : menu.running() ? Component.literal(menu.fe() + " FE/t")
                : Component.translatable("gui.powerfactor.engine.needs_redstone");
        g.text(font, status, 102, 38, menu.seized() ? 0xFFE0302A : menu.running() ? GuiUtil.FE_COLOR : 0xFF404040, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        if (isHovering(BAR_X, BAR_Y, BAR_W, BAR_H, mouseX, mouseY)) {
            GuiUtil.energyTooltip(g, font, menu.stored(), EngineMenu.CAPACITY, mouseX, mouseY);
        } else if (isHovering(HEAT_X, HEAT_Y, HEAT_W, HEAT_H, mouseX, mouseY)) {
            g.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable("gui.powerfactor.engine.heat", menu.heat() / 10),
                    Component.translatable("gui.powerfactor.engine.heat.desc").withColor(0xAAAAAA)), mouseX, mouseY);
        }
    }
}
