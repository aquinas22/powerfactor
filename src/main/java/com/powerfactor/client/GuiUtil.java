package com.powerfactor.client;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Unit colors and the vertical FE gauge every machine GUI shares. */
public final class GuiUtil {
    public static final int FE_COLOR = 0xFFFF5A4A;
    public static final int FE_DARK = 0xFF9A2018;
    public static final int EE_COLOR = 0xFF33E0B0;
    public static final int CE_COLOR = 0xFFE070FF;

    /** The v2 glass energy tube. */
    public static void energyBar(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int stored, int capacity) {
        Skin.energyTube(graphics, x, y, w, h, stored, capacity, false);
    }

    /** A liquid gauge: fills from the bottom with a wavy surface. */
    public static void fluidGauge(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int amount, int capacity, int color) {
        // Glass tank: recessed, with tick marks and a highlight down the side.
        Skin.well(graphics, x, y, w, h);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF0B1418);
        for (int t = 1; t < 4; t++) {
            graphics.fill(x + w - 4, y + h * t / 4, x + w - 1, y + h * t / 4 + 1, 0x70FFFFFF);
        }
        graphics.fill(x + 2, y + 2, x + 3, y + h - 2, 0x30FFFFFF);
        if (capacity <= 0 || amount <= 0) {
            return;
        }
        int filled = (int) ((long) (h - 2) * Math.min(amount, capacity) / capacity);
        int bottom = y + h - 1;
        graphics.fillGradient(x + 1, bottom - filled, x + w - 1, bottom, color, darker(color));
        long t = System.currentTimeMillis() / 120;
        for (int i = x + 1; i < x + w - 1; i++) {
            if ((i + t) % 4 == 0) {
                graphics.fill(i, bottom - filled, i + 1, bottom - filled + 1, 0xFFFFFFFF);
            }
        }
    }

    private static int darker(int argb) {
        return 0xFF000000 | ((argb >> 17) & 0x7F) << 16 | ((argb >> 9) & 0x7F) << 8 | ((argb >> 1) & 0x7F);
    }

    public static void energyTooltip(GuiGraphicsExtractor graphics, Font font, int stored, int capacity, int mouseX, int mouseY) {
        graphics.setComponentTooltipForNextFrame(font, List.of(
                Component.translatable("gui.powerfactor.buffer", String.format("%,d", stored), String.format("%,d", capacity))), mouseX, mouseY);
    }

    /** "20 FE/t · 15 EE/t · PF 0.80", each part in its unit color. */
    public static void powerLine(GuiGraphicsExtractor graphics, Font font, int fe, int ee, int x, int y) {
        String feText = fe + " FE/t";
        String eeText = ee + " EE/t";
        double ce = Math.hypot(fe, ee);
        String pfText = ce == 0 ? "PF ---" : String.format("PF %.2f", fe / ce);
        graphics.text(font, feText, x, y, FE_COLOR, false);
        x += font.width(feText) + 8;
        graphics.text(font, eeText, x, y, EE_COLOR, false);
        x += font.width(eeText) + 8;
        graphics.text(font, pfText, x, y, 0xFF404040, false);
    }

    private GuiUtil() {}
}
