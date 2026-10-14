package com.powerfactor.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * PowerFactor's GUI look, "bright high-tech": a pearl-white chassis with chamfered corners and a
 * cyan accent line, crisp light slot wells, and dark-glass holographic displays and controls set
 * into it for contrast. Drawn in code so every machine shares one resolution-crisp style.
 *
 * <p>Text colours: {@link #TEXT}/{@link #DIM} are for the bright chassis; {@link #LCD_TEXT}/{@link #LCD_DIM}
 * (and the unit colours) are for dark glass: displays, title-free readouts and controls.
 */
public final class Skin {
    public static final int TEXT = 0xFF1A2A36, DIM = 0xFF5B7080,
            LCD_TEXT = 0xFFCDF6FF, LCD_DIM = 0xFF7FA4B4,
            TEAL = 0xFF19C6D8, INPUT = 0xFF2E9BE6, OUTPUT = 0xFFF09A2A, FE = 0xFFFF7F66, EE = 0xFF4FE0C0,
            BORDER = 0xFF8FA6B5, SURFACE = 0xFF1C2A35, INK = 0xFF0F1C26,
            ACCENT = 0xFF19C6D8, ACCENT_2 = 0xFF8C6CFF;

    private static final int CHASSIS_TOP = 0xFFF7FAFC, CHASSIS_BOTTOM = 0xFFDCE5EC, OUTLINE = 0xFF6F8797, SHADOW = 0x40102030;

    /** The chassis: pearl gradient, chamfered corners, a white top highlight, a cyan accent and a faint tech grid. */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        int c = 4;
        // Soft drop shadow.
        g.fill(x + c, y + h, x + w + 2, y + h + 3, SHADOW);
        g.fill(x + w, y + c, x + w + 2, y + h, SHADOW);
        // Outline and body with chamfers (stepped diagonals).
        for (int i = 0; i < c; i++) {
            g.fill(x + c - i, y + i, x + w - c + i, y + i + 1, OUTLINE);
            g.fill(x + c - i, y + h - 1 - i, x + w - c + i, y + h - i, OUTLINE);
        }
        g.fill(x, y + c, x + w, y + h - c, OUTLINE);
        for (int i = 1; i < c; i++) {
            g.fill(x + c - i + 1, y + i, x + w - c + i - 1, y + i + 1, CHASSIS_TOP);
            g.fill(x + c - i + 1, y + h - 1 - i, x + w - c + i - 1, y + h - i, CHASSIS_BOTTOM);
        }
        g.fillGradient(x + 1, y + c, x + w - 1, y + h - c, CHASSIS_TOP, CHASSIS_BOTTOM);
        // Highlight and faint grid.
        g.fill(x + c, y + 1, x + w - c, y + 2, 0xFFFFFFFF);
        for (int gx = x + 8; gx < x + w - 4; gx += 8) g.fill(gx, y + 4, gx + 1, y + h - 4, 0x08305070);
        for (int gy = y + 8; gy < y + h - 4; gy += 8) g.fill(x + 4, gy, x + w - 4, gy + 1, 0x08305070);
        // Accent: a cyan line under the top edge, brighter at its leading end.
        g.fillGradient(x + c + 2, y + 2, x + w / 2, y + 3, ACCENT, ACCENT);
        g.fill(x + w / 2, y + 2, x + w - c - 2, y + 3, 0x6019C6D8);
        // Corner brackets: the high-tech frame marks.
        bracket(g, x + 3, y + h - 7, false);
        bracket(g, x + w - 7, y + h - 7, true);
    }

    private static void bracket(GuiGraphicsExtractor g, int x, int y, boolean right) {
        g.fill(x, y + 3, x + 4, y + 4, 0xFF9DB3C2);
        g.fill(right ? x + 3 : x, y, right ? x + 4 : x + 1, y + 4, 0xFF9DB3C2);
    }

    /** Kept for callers that want a small mark: now a bright stud with a cyan core. */
    public static void rivet(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 3, y + 3, 0xFF9DB3C2);
        g.fill(x + 1, y + 1, x + 2, y + 2, ACCENT);
    }

    /** Title: dark slate text with a cyan accent pill and a fading divider. */
    public static void title(GuiGraphicsExtractor g, Font font, Component title, int x, int y, int w) {
        g.fill(x + 6, y + 6, x + 8, y + 14, ACCENT);
        g.fill(x + 6, y + 6, x + 8, y + 7, 0xFFB8F6FF);
        g.text(font, fit(font, title, w - 30), x + 12, y + 6, TEXT, false);
        g.fillGradient(x + 6, y + 16, x + w - 6, y + 17, 0x8019C6D8, 0x8019C6D8);
        g.fill(x + w / 2, y + 16, x + w - 6, y + 17, 0x308FA6B5);
    }

    /** Keep translated titles and live readouts inside their panel. */
    public static Component fit(Font font, Component text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return Component.literal(font.substrByWidth(text, Math.max(0, width - font.width("…"))).getString() + "…");
    }

    /** A recessed well in the bright chassis: soft inner shadow top-left, white lip bottom-right. */
    public static void well(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF8AA0AF);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFC4D2DC);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFFA9BBC8);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFFB2C3CF);
        g.fill(x, y + h - 1, x + w, y + h, 0xFFFFFFFF);
        g.fill(x + w - 1, y + 1, x + w, y + h, 0xFFF2F7FA);
    }

    /** An 18x18 item slot (item at x+1, y+1): bright inset with a glowing role underline (0 for none). */
    public static void slot(GuiGraphicsExtractor g, int x, int y, int ring) {
        well(g, x, y, 18, 18);
        g.fillGradient(x + 2, y + 2, x + 16, y + 16, 0xFFE4ECF2, 0xFFD2DDE6);
        g.fill(x + 2, y + 2, x + 16, y + 3, 0xFFB9C9D5);
        g.fill(x + 2, y + 3, x + 3, y + 16, 0xFFC3D2DD);
        if (ring != 0) {
            g.fill(x + 2, y + 15, x + 16, y + 16, ring);
            g.fill(x + 4, y + 14, x + 14, y + 15, 0x50000000 | (ring & 0xFFFFFF));
            g.fill(x + 1, y + 1, x + 3, y + 2, ring);
            g.fill(x + 1, y + 1, x + 2, y + 3, ring);
        }
    }

    /** A big output slot (26x26, item at x+5, y+5) with a framed glow. */
    public static void bigSlot(GuiGraphicsExtractor g, int x, int y, int ring) {
        well(g, x, y, 26, 26);
        g.fillGradient(x + 2, y + 2, x + 24, y + 24, 0xFFF2F7FA, 0xFFD9E3EA);
        g.fill(x + 2, y + 2, x + 24, y + 3, ring);
        g.fill(x + 2, y + 23, x + 24, y + 24, ring);
        g.fill(x + 3, y + 4, x + 23, y + 5, 0x30000000 | (ring & 0xFFFFFF));
    }

    /** The player's inventory: label, a 9x3 grid and the hotbar (item origin at x, y). */
    public static void inventory(GuiGraphicsExtractor g, Font font, int x, int y) {
        g.fillGradient(x - 1, y - 13, x + 80, y - 12, 0x8019C6D8, 0x8019C6D8);
        g.fill(x + 80, y - 13, x + 161, y - 12, 0x408FA6B5);
        g.text(font, Component.translatable("container.inventory"), x, y - 10 - 1, DIM, false);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                slot(g, x - 1 + c * 18, y - 1 + r * 18, 0);
            }
        }
        for (int c = 0; c < 9; c++) {
            slot(g, x - 1 + c * 18, y + 57, 0);
        }
    }

    /** A dark holographic display set into the chassis: deep glass, a cyan rim glow and faint scanlines. */
    public static void lcd(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF56707F);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF0D2231, 0xFF0A1A26);
        for (int i = y + 2; i < y + h - 1; i += 2) {
            g.fill(x + 1, i, x + w - 1, i + 1, 0x0A6FE3FF);
        }
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x406FE3FF);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, 0x206FE3FF);
        g.fill(x, y + h, x + w, y + h + 1, 0xFFFFFFFF);
    }

    /** Glass energy tube with a segmented, glowing FE fill and tick marks. */
    public static void energyTube(GuiGraphicsExtractor g, int x, int y, int w, int h, long stored, long capacity, boolean charging) {
        well(g, x, y, w, h);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF20262A);
        float frac = capacity <= 0 ? 0 : Mth.clamp((float) stored / capacity, 0, 1);
        int fill = Math.round((h - 2) * frac), top = y + h - 1 - fill;
        if (fill > 0) {
            g.fillGradient(x + 1, top, x + w - 1, y + h - 1, 0xFFFFC49A, 0xFFE2553C);
            for (int s = y + h - 4; s > top; s -= 4) {
                g.fill(x + 1, s, x + w - 1, s + 1, 0x50000000);
            }
            g.fill(x + 1, top, x + w - 1, top + 1, 0xFFFFF0DA);
            if (charging) {
                int pulseY = y + h - 2 - (int) (Util.getMillis() / 45 % Math.max(1, fill));
                g.fill(x + 1, pulseY, x + w - 1, Math.min(y + h - 1, pulseY + 2), 0x60FFFFFF);
            }
        }
        g.fill(x + 2, y + 2, x + 3, y + h - 2, 0x50FFFFFF);
        for (int t = 1; t < 4; t++) {
            int ty = y + h * t / 4;
            g.fill(x + w - 3, ty, x + w - 1, ty + 1, 0x90FFFFFF);
        }
    }

    /** Process progress as a row of chevrons that light up in turn, pulsing at the head while running. */
    public static void chevrons(GuiGraphicsExtractor g, int x, int y, int count, float progress, boolean running) {
        long t = Util.getMillis();
        int lit = (int) (Mth.clamp(progress, 0, 1) * count);
        for (int i = 0; i < count; i++) {
            int cx = x + i * 7;
            boolean on = i < lit;
            int color = on ? ACCENT : 0xFFA9BBC8;
            if (running && i == lit) {
                float pulse = (float) (0.5 + 0.5 * Math.sin(t / 140.0));
                color = 0xFF000000 | ((int) (25 + 40 * pulse) << 16) | ((int) (150 + 70 * pulse) << 8) | (int) (190 + 60 * pulse);
            }
            for (int k = 0; k < 4; k++) {
                g.fill(cx + k, y + k, cx + k + 2, y + k + 1, color);
                g.fill(cx + k, y + 7 - k, cx + k + 2, y + 8 - k, color);
            }
        }
    }

    /** A round status lamp with a soft glow. */
    public static void lamp(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x - 1, y - 1, x + 6, y + 6, 0x50000000 | (color & 0xFFFFFF));
        g.fill(x, y, x + 5, y + 5, 0xFF223038);
        g.fill(x + 1, y + 1, x + 4, y + 4, color);
        g.fill(x + 1, y + 1, x + 2, y + 2, 0xD0FFFFFF);
    }

    /**
     * An oscilloscope display: three phase waves (red, yellow, blue, 120 degrees apart) scrolling while
     * the machine runs, their height following the load.
     */
    public static void scope(GuiGraphicsExtractor g, int x, int y, int w, int h, float load, boolean running) {
        lcd(g, x, y, w, h);
        int mid = y + h / 2;
        for (int gx = x + 6; gx < x + w - 2; gx += 8) {
            g.fill(gx, y + 2, gx + 1, y + h - 2, 0x184D9F91);
        }
        g.fill(x + 2, mid, x + w - 2, mid + 1, 0x3055C1AC);
        if (!running) {
            g.fill(x + 2, mid, x + w - 2, mid + 1, LCD_DIM);
            return;
        }
        float amp = Math.max(0.15F, Math.min(1F, load)) * (h / 2F - 3);
        double t = Util.getMillis() / 200.0;
        int[] colors = {0xFFFF5A4A, 0xFFFFD040, 0xFF4FA8FF};
        for (int ph = 0; ph < 3; ph++) {
            int prev = Integer.MIN_VALUE;
            for (int px = x + 2; px < x + w - 2; px++) {
                double a = (px - x) / 7.0 - t + ph * 2.0943951;
                int py = mid - (int) Math.round(Math.sin(a) * amp);
                int lo = prev == Integer.MIN_VALUE ? py : Math.min(prev, py), hi = prev == Integer.MIN_VALUE ? py : Math.max(prev, py);
                g.fill(px, lo, px + 1, hi + 1, colors[ph]);
                prev = py;
            }
        }
    }

    /** Thin bar for a 0..1 value, in a light track. */
    public static void bar(GuiGraphicsExtractor g, int x, int y, int w, float value, int color) {
        g.fill(x, y, x + w, y + 3, 0xFFB7C7D3);
        g.fill(x, y, x + Math.round(w * Mth.clamp(value, 0, 1)), y + 3, color);
        g.fill(x, y, x + Math.round(w * Mth.clamp(value, 0, 1)), y + 1, 0x60FFFFFF);
    }

    /** Compact engineering units for small instrument faces. Full values remain in hover readouts. */
    public static String compact(double value) {
        double magnitude = Math.abs(value);
        if (magnitude >= 1_000_000) return String.format(java.util.Locale.ROOT, "%.1fM", value / 1_000_000);
        if (magnitude >= 1_000) return String.format(java.util.Locale.ROOT, "%.1fk", value / 1_000);
        return Long.toString(Math.round(value));
    }

    /** Floating instrumentation (HUD overlays): dark glass so it reads over any world. */
    public static void hud(GuiGraphicsExtractor g,int x,int y,int w,int h) {
        g.fill(x+1,y+1,x+w+1,y+h+1,0x30000000);
        g.fill(x,y,x+w,y+h,0xD90F1C26);
        g.fill(x,y,x+1,y+h,0xFF19C6D8);
        g.fill(x,y,x+w,y+1,0xFF4E6A7A);
    }

    /** A labelled value fitted between the ends of a readout; never overwrites its label. */
    public static void metric(GuiGraphicsExtractor g, Font font, int x, int y, int w, Component label, Component value, int color) {
        int labelWidth = Math.min(font.width(label), Math.max(0, w / 3));
        g.text(font, fit(font, label, labelWidth), x, y, LCD_DIM, false);
        Component fitted = fit(font, value, Math.max(0, w - labelWidth - 5));
        g.text(font, fitted, x + w - font.width(fitted), y, color, false);
    }

    /** Broad, calibrated progress rail; the mark shows partial cycles accurately. */
    public static void progress(GuiGraphicsExtractor g, int x, int y, int w, float value, int color) {
        well(g, x, y, w, 6);
        g.fill(x + 1, y + 1, x + w - 1, y + 5, 0xFFDDE6EC);
        int n = Math.round((w - 2) * Mth.clamp(value, 0, 1));
        if (n > 0) {
            g.fill(x + 1, y + 1, x + 1 + n, y + 5, color);
            g.fill(x + 1, y + 1, x + 1 + n, y + 2, 0x80FFFFFF);
        }
        for (int tick = 1; tick < 4; tick++) {
            int tx = x + 1 + (w - 2) * tick / 4;
            g.fill(tx, y + 4, tx + 1, y + 5, 0xA06F8797);
        }
    }

    /**
     * Controls are dark-glass chips set into the bright chassis, so their (light) labels stay crisp:
     * cyan rim on hover, a filled cyan underline when selected.
     */
    public static void control(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean selected, boolean hover) {
        g.fill(x, y, x + w, y + h, hover ? ACCENT : selected ? 0xFF0F8FA0 : 0xFF4B6271);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, hover ? 0xFF2A4555 : selected ? 0xFF16404C : 0xFF243440,
                hover ? 0xFF1C3240 : selected ? 0xFF0F2E38 : 0xFF17242E);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, hover ? 0x606FE3FF : 0x30FFFFFF);
        if (selected) g.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, ACCENT);
    }

    /** Background for a classic-layout container screen: chassis, title, slot wells by role, player inventory. */
    public static void machineBackground(GuiGraphicsExtractor g, Font font, Component title, int x, int y, int w, int h,
            Iterable<net.minecraft.world.inventory.Slot> slots) {
        panel(g, x, y, w, h);
        title(g, font, title, x, y, w);
        for (var slot : slots) {
            if (slot instanceof com.powerfactor.machine.ProcessingMenu.UpgradeSlot
                    || slot.container instanceof net.minecraft.world.entity.player.Inventory) {
                continue;
            }
            slot(g, x + slot.x - 1, y + slot.y - 1, slot instanceof com.powerfactor.machine.MachineMenu.OutputSlotMarker ? OUTPUT : INPUT);
        }
        inventory(g, font, x + 8, y + h - 82);
    }

    private Skin() {}
}
