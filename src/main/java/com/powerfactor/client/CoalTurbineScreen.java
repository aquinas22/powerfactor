package com.powerfactor.client;

import com.powerfactor.PowerFactor;
import com.powerfactor.machine.CoalTurbineMenu;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/** Fuel slot on the left, a SCADA-style readout in the middle, the FE buffer on the right. */
public class CoalTurbineScreen extends MachineScreen<CoalTurbineMenu> {
    private static final Identifier TEXTURE = PowerFactor.id("textures/gui/coal_turbine.png");
    private static final Identifier LIT_SPRITE = Identifier.withDefaultNamespace("container/furnace/lit_progress");

    // Layout, matching tools/gen_textures.py.
    private static final int FLAME_X = 19, FLAME_Y = 40;
    private static final int LCD_X = 40, LCD_Y = 16, LCD_W = 110, LCD_H = 64;
    private static final int BAR_X = 156, BAR_Y = 16, BAR_W = 12, BAR_H = 64;

    public static final int FE_COLOR = 0xFFFF5A4A;
    public static final int EE_COLOR = 0xFF33E0B0;
    public static final int CE_COLOR = 0xFFE070FF;
    private static final int LCD_TEXT = 0xFFB8F5C0;
    private static final int LCD_DIM = 0xFF5F8A66;
    private static final int GREEN = 0xFF55FF55, AMBER = 0xFFFFB020, RED = 0xFFFF5555;

    public CoalTurbineScreen(CoalTurbineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176);
    }

    @Override
    protected java.util.List<Tab> tabs() {
        return java.util.List.of(Tab.ELECTRICAL, Tab.CONFIG);
    }

    @Override
    protected com.powerfactor.machine.MachineBlockEntity.RedstoneMode redstoneMode() {
        return menu.redstoneMode();
    }

    @Override
    protected Electrical electrical() {
        Component status = menu.overloaded() ? Component.translatable("gui.powerfactor.panel.overloaded")
                : menu.breakerClosed() ? Component.translatable("gui.powerfactor.panel.generating")
                : Component.translatable("gui.powerfactor.panel.breaker_open");
        int statusColor = menu.overloaded() ? 0xFFFF4040 : menu.breakerClosed() ? 0xFF55FF55 : 0xFFFFB020;
        return new Electrical(menu.breakerClosed() ? menu.volts() : 0, menu.breakerClosed() ? menu.hertz() : 0, menu.fe(), menu.ee(), 250,
                status, statusColor);
    }

    @Override
    protected void extractMain(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0, 0, 176, imageHeight, 256, 256);

        if (menu.steam()) {
            int h = Mth.ceil(menu.fuelProgress() * 13F) + 1;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LIT_SPRITE, 14, 14, 0, 14 - h, x + FLAME_X, y + FLAME_Y + 14 - h, 14, h);
        }

        int capacity = menu.capacity();
        if (capacity > 0) {
            int h = (int) ((long) (BAR_H - 2) * menu.stored() / capacity);
            int bottom = y + BAR_Y + BAR_H - 1;
            graphics.fillGradient(x + BAR_X + 1, bottom - h, x + BAR_X + BAR_W - 1, bottom, FE_COLOR, 0xFF9A2018);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        super.extractLabels(graphics, xm, ym);
        int x = LCD_X + 4;
        int y = LCD_Y + 4;
        int right = LCD_X + LCD_W - 4;

        Component status;
        int statusColor;
        if (menu.breakerClosed()) {
            status = Component.translatable("gui.powerfactor.turbine.online");
            statusColor = GREEN;
        } else if (menu.steam()) {
            status = Component.translatable("gui.powerfactor.turbine.syncing");
            statusColor = AMBER;
        } else if (menu.rpm() > 0) {
            status = Component.translatable("gui.powerfactor.turbine.coasting");
            statusColor = AMBER;
        } else {
            status = Component.translatable("gui.powerfactor.turbine.offline");
            statusColor = RED;
        }
        graphics.text(font, status, x, y, statusColor, false);
        String hz = String.format("%.1f Hz", menu.hertz());
        graphics.text(font, hz, right - font.width(hz), y, LCD_TEXT, false);

        y += 10;
        row(graphics, "RPM", Integer.toString(menu.rpm()), "", x, right, y, LCD_TEXT);
        y += 10;
        int fe = menu.fe(), ee = menu.ee();
        double ce = Math.hypot(fe, ee);
        row(graphics, "FE", Integer.toString(fe), "/t", x, right, y, FE_COLOR);
        y += 9;
        row(graphics, "EE", Integer.toString(ee), "/t", x, right, y, EE_COLOR);
        y += 9;
        row(graphics, "CE", Integer.toString((int) Math.round(ce)), "/t", x, right, y, CE_COLOR);
        y += 10;
        String pf = ce == 0 ? "---" : String.format("%.2f", fe / ce);
        row(graphics, "PF", pf, "", x, right, y, LCD_TEXT);
    }

    private void row(GuiGraphicsExtractor graphics, String label, String value, String unit, int x, int right, int y, int color) {
        graphics.text(font, label, x, y, color, false);
        int unitW = font.width("/t");
        graphics.text(font, value, right - unitW - 2 - font.width(value), y, color, false);
        if (!unit.isEmpty()) {
            graphics.text(font, unit, right - unitW, y, LCD_DIM, false);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        if (isHovering(BAR_X, BAR_Y, BAR_W, BAR_H, mouseX, mouseY)) {
            graphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable("gui.powerfactor.buffer", String.format("%,d", menu.stored()), String.format("%,d", menu.capacity()))),
                    mouseX, mouseY);
        } else if (isHovering(LCD_X, LCD_Y + 23, LCD_W, 9, mouseX, mouseY)) {
            unitTooltip(graphics, "fe", FE_COLOR, mouseX, mouseY);
        } else if (isHovering(LCD_X, LCD_Y + 32, LCD_W, 9, mouseX, mouseY)) {
            unitTooltip(graphics, "ee", EE_COLOR, mouseX, mouseY);
        } else if (isHovering(LCD_X, LCD_Y + 41, LCD_W, 9, mouseX, mouseY)) {
            unitTooltip(graphics, "ce", CE_COLOR, mouseX, mouseY);
        } else if (isHovering(LCD_X, LCD_Y + 51, LCD_W, 9, mouseX, mouseY)) {
            unitTooltip(graphics, "pf", LCD_TEXT, mouseX, mouseY);
        }
    }

    private void unitTooltip(GuiGraphicsExtractor graphics, String unit, int color, int mouseX, int mouseY) {
        graphics.setComponentTooltipForNextFrame(font, List.of(
                Component.translatable("gui.powerfactor.unit." + unit).withColor(color & 0xFFFFFF),
                Component.translatable("gui.powerfactor.unit." + unit + ".desc").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
    }
}
