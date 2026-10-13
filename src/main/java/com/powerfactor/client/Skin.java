package com.powerfactor.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * PowerFactor's GUI look: brushed gunmetal panels with bevelled edges and rivets, recessed wells,
 * colour-ringed slots (blue in, orange out, like side config), a glass energy tube, LCD readouts.
 * Drawn in code so every machine shares one consistent, resolution-crisp style.
 */
public final class Skin {
    public static final int TEXT = 0xFFE4ECF0, DIM = 0xFF8C9AA4, LCD_TEXT = 0xFF7CFFB0, TEAL = 0xFF2FD0A8, INPUT = 0xFF3F9CFF,
            OUTPUT = 0xFFFF9A3C, FE = 0xFFFF5A4A, EE = 0xFF33E0B0;

    /** The main body: gunmetal gradient, light top/left edge, dark bottom/right, black outline, rivets. */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x + 2, y + 3, x + w + 2, y + h + 3, 0x60000000);
        g.fill(x, y, x + w, y + h, 0xFF0B0D0F);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF4A535B, 0xFF2E353B);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFF7C8790);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFF6A747D);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0xFF181C20);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, 0xFF1C2125);
        // Brushed-metal streaks.
        for (int i = 0; i < h - 4; i += 3) {
            int a = ((i * 37) % 7) * 2;
            g.fill(x + 2, y + 2 + i, x + w - 2, y + 3 + i, (a << 24) | 0xFFFFFF);
        }
        for (int[] c : new int[][] {{x + 4, y + 4}, {x + w - 7, y + 4}, {x + 4, y + h - 7}, {x + w - 7, y + h - 7}}) {
            rivet(g, c[0], c[1]);
        }
    }

    public static void rivet(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 3, y + 3, 0xFF1A1E22);
        g.fill(x, y, x + 2, y + 2, 0xFF9AA5AE);
        g.fill(x + 1, y + 1, x + 2, y + 2, 0xFF5B646C);
    }

    /** Title strip with a teal underline and the title in an engraved plate. */
    public static void title(GuiGraphicsExtractor g, Font font, Component title, int x, int y, int w) {
        g.fill(x + 3, y + 3, x + w - 3, y + 15, 0xFF1B2024);
        g.fill(x + 3, y + 15, x + w - 3, y + 16, TEAL);
        g.fill(x + 3, y + 16, x + w - 3, y + 17, 0x6030D0A8);
        g.text(font, fit(font, title, w - 32), x + 10, y + 5, TEXT, false);
    }

    /** Keep translated titles and live readouts inside their panel. */
    public static Component fit(Font font, Component text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return Component.literal(font.substrByWidth(text, Math.max(0, width - font.width("…"))).getString() + "…");
    }

    /** A recessed well: dark inside, shadowed top/left, catching light bottom/right. */
    public static void well(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF0C1013);
        g.fill(x, y, x + w, y + 1, 0xFF05070A);
        g.fill(x, y, x + 1, y + h, 0xFF05070A);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF67727B);
        g.fill(x + w - 1, y, x + w, y + h, 0xFF56616A);
    }

    /** An 18x18 item slot (item at x+1, y+1) with a role ring: 0 none, else the ring colour. */
    public static void slot(GuiGraphicsExtractor g, int x, int y, int ring) {
        well(g, x, y, 18, 18);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF1A2026);
        if (ring != 0) {
            int r = ring & 0xFFFFFF;
            g.fill(x + 1, y + 1, x + 17, y + 2, 0xA0000000 | r);
            g.fill(x + 1, y + 16, x + 17, y + 17, 0xA0000000 | r);
            g.fill(x + 1, y + 1, x + 2, y + 17, 0xA0000000 | r);
            g.fill(x + 16, y + 1, x + 17, y + 17, 0xA0000000 | r);
        }
    }

    /** A big output slot (26x26, item at x+5, y+5), for a machine's main product. */
    public static void bigSlot(GuiGraphicsExtractor g, int x, int y, int ring) {
        well(g, x, y, 26, 26);
        g.fill(x + 1, y + 1, x + 25, y + 25, 0xFF1A2026);
        g.fill(x + 2, y + 2, x + 24, y + 3, 0x70000000 | (ring & 0xFFFFFF));
        g.fill(x + 2, y + 23, x + 24, y + 24, 0x70000000 | (ring & 0xFFFFFF));
    }

    /** The player's inventory: a 9x3 grid plus the hotbar, item origin at (x, y). */
    public static void inventory(GuiGraphicsExtractor g, Font font, int x, int y) {
        g.text(font, Component.translatable("container.inventory"), x, y - 10, DIM, false);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                slot(g, x - 1 + c * 18, y - 1 + r * 18, 0);
            }
        }
        for (int c = 0; c < 9; c++) {
            slot(g, x - 1 + c * 18, y + 57, 0);
        }
    }

    /** LCD panel: green-black glass with faint scanlines. */
    public static void lcd(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        well(g, x, y, w, h);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF06150E, 0xFF0B2016);
        for (int i = y + 2; i < y + h - 1; i += 2) {
            g.fill(x + 1, i, x + w - 1, i + 1, 0x18000000);
        }
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x2030FF90);
    }

    /** Glass energy tube with a segmented, glowing FE fill and tick marks. */
    public static void energyTube(GuiGraphicsExtractor g, int x, int y, int w, int h, long stored, long capacity, boolean charging) {
        well(g, x, y, w, h);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF140809);
        float frac = capacity <= 0 ? 0 : Mth.clamp((float) stored / capacity, 0, 1);
        int fill = Math.round((h - 2) * frac), top = y + h - 1 - fill;
        if (fill > 0) {
            g.fillGradient(x + 1, top, x + w - 1, y + h - 1, 0xFFFF7A5A, 0xFFB0281E);
            // Segments.
            for (int s = y + h - 4; s > top; s -= 4) {
                g.fill(x + 1, s, x + w - 1, s + 1, 0x60000000);
            }
            // Bright liquid surface, shimmering while charging.
            g.fill(x + 1, top, x + w - 1, top + 1, 0xFFFFD0B0);
            if (charging) {
                int pulseY = y + h - 2 - (int) (Util.getMillis() / 45 % Math.max(1, fill));
                g.fill(x + 1, pulseY, x + w - 1, Math.min(y + h - 1, pulseY + 2), 0x50FFE4C0);
            }
        }
        // Glass highlight down the left.
        g.fill(x + 2, y + 2, x + 3, y + h - 2, 0x40FFFFFF);
        for (int t = 1; t < 4; t++) {
            int ty = y + h * t / 4;
            g.fill(x + w - 3, ty, x + w - 1, ty + 1, 0x80FFFFFF);
        }
    }

    /** Process progress as a row of chevrons that light up in turn, pulsing at the head while running. */
    public static void chevrons(GuiGraphicsExtractor g, int x, int y, int count, float progress, boolean running) {
        long t = Util.getMillis();
        int lit = (int) (Mth.clamp(progress, 0, 1) * count);
        for (int i = 0; i < count; i++) {
            int cx = x + i * 7;
            boolean on = i < lit;
            int color = on ? 0xFF3FE0FF : 0xFF27313A;
            if (running && i == lit) {
                float pulse = (float) (0.5 + 0.5 * Math.sin(t / 140.0));
                color = 0xFF000000 | ((int) (28 + 32 * pulse) << 16) | ((int) (90 + 100 * pulse) << 8) | (int) (108 + 116 * pulse);
            }
            for (int k = 0; k < 4; k++) {
                g.fill(cx + k, y + k, cx + k + 2, y + k + 1, color);
                g.fill(cx + k, y + 7 - k, cx + k + 2, y + 8 - k, color);
            }
        }
    }

    /** A round status lamp with a soft glow. */
    public static void lamp(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x - 1, y - 1, x + 6, y + 6, 0x40000000 | (color & 0xFFFFFF));
        g.fill(x, y, x + 5, y + 5, 0xFF0B0D0F);
        g.fill(x + 1, y + 1, x + 4, y + 4, color);
        g.fill(x + 1, y + 1, x + 2, y + 2, 0xC0FFFFFF);
    }

    /**
     * An oscilloscope LCD: three phase waves (red, yellow, blue, 120 degrees apart) scrolling while
     * the machine runs, their height following the load.
     */
    public static void scope(GuiGraphicsExtractor g, int x, int y, int w, int h, float load, boolean running) {
        lcd(g, x, y, w, h);
        int mid = y + h / 2;
        for (int gx = x + 6; gx < x + w - 2; gx += 8) {
            g.fill(gx, y + 2, gx + 1, y + h - 2, 0x1830FF90);
        }
        g.fill(x + 2, mid, x + w - 2, mid + 1, 0x3030FF90);
        if (!running) {
            g.fill(x + 2, mid, x + w - 2, mid + 1, DIM);
            return;
        }
        float amp = running ? Math.max(0.15F, Math.min(1F, load)) * (h / 2F - 3) : 0;
        double t = running ? Util.getMillis() / 200.0 : 0;
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

    /** Thin bar for a 0..1 value. */
    public static void bar(GuiGraphicsExtractor g, int x, int y, int w, float value, int color) {
        g.fill(x, y, x + w, y + 3, 0xFF0A0F12);
        g.fill(x, y, x + Math.round(w * Mth.clamp(value, 0, 1)), y + 3, color);
    }

    /** Background for a classic-layout container screen: panel, title, slot wells by role, player inventory. */
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
