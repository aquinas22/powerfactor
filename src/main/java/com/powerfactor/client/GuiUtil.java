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

    /** Fills a gauge frame (1px inset) from the bottom. */
    public static void energyBar(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int stored, int capacity) {
        if (capacity <= 0 || stored <= 0) {
            return;
        }
        int filled = (int) ((long) (h - 2) * Math.min(stored, capacity) / capacity);
        int bottom = y + h - 1;
        graphics.fillGradient(x + 1, bottom - filled, x + w - 1, bottom, FE_COLOR, FE_DARK);
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
