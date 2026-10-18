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
    /** One row, 8 px gaps: fuel column (8-26), thermometer (34-50), readout glass (58-148), FE tube (156-168). */
    private static final int HEAT_X = 34, HEAT_Y = Skin.Layout.CONTENT_TOP, HEAT_W = 16, HEAT_H = Skin.Layout.contentHeight(166);
    private static final int LCD_X = 58, LCD_W = 90;
    private static final int BAR_X = 156, BAR_Y = Skin.Layout.CONTENT_TOP, BAR_W = 12, BAR_H = HEAT_H;
    private static final int[] STAGE_COLORS = {0xFF4A7BFF, 0xFF3CC94A, 0xFFF2D030, 0xFFE0302A};

    public EngineScreen(EngineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        int x = leftPos, y = topPos;
        Skin.machineBackground(g, font, title, x, y, imageWidth, imageHeight, menu.slots);
        Skin.well(g, x + HEAT_X, y + HEAT_Y, HEAT_W, HEAT_H);
        Skin.well(g, x + 9, y + 26, 16, 16);
        if (menu.burnProgress() > 0) {
            int h = Mth.ceil(menu.burnProgress() * 13F) + 1;
            g.blitSprite(RenderPipelines.GUI_TEXTURED, LIT_SPRITE, 14, 14, 0, 14 - h, x + 10, y + 27 + 14 - h, 14, h);
        }
        Skin.lcd(g, x + LCD_X, y + HEAT_Y, LCD_W, HEAT_H);
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
        Component status = menu.seized() ? Component.translatable("gui.powerfactor.engine.seized")
                : menu.running() ? Component.literal(menu.fe() + " FE/t")
                : Component.translatable("gui.powerfactor.engine.needs_redstone");
        int color = menu.seized() ? 0xFFFF6A5A : menu.running() ? GuiUtil.FE_COLOR : 0xFFFFB347;
        int heat = menu.heat() * 100 / EngineHeat.MAX;
        int stage = menu.heat() >= EngineHeat.RED ? 3 : menu.heat() >= EngineHeat.YELLOW ? 2 : menu.heat() >= EngineHeat.GREEN ? 1 : 0;
        Skin.lcdRows(g, font, LCD_X, HEAT_Y, LCD_W, HEAT_H, List.of(new Skin.Row(menu.running() && !menu.seized() ? Skin.Icon.FE : Skin.statusIcon(color),
                status, color), new Skin.Row(Skin.Icon.HEAT, Component.literal(heat + "%"), STAGE_COLORS[stage], Component.translatable("gui.powerfactor.engine.heat", heat))));
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
