package com.powerfactor.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * A three-phase power analyzer, drawn in code and animated every frame:
 * <ul>
 *   <li>An oscilloscope with the A/B/C voltage waves (solid) and current waves (dotted). Current is
 *       shifted from voltage by the power-factor angle, so you can see the power factor directly.
 *       Wave height follows voltage; scroll speed follows frequency (slowed down so the eye can
 *       follow it).</li>
 *   <li>Rotating phasors: three voltage arrows 120 degrees apart, with the current arrows trailing
 *       (lagging) or leading them.</li>
 *   <li>The power triangle: FE across, EE up (or down when leading), CE the hypotenuse.</li>
 *   <li>Readouts: line volts and amps (480 V nominal), frequency, PF lead/lag, FE/EE/CE.</li>
 * </ul>
 * Values glide toward each new server reading instead of jumping.
 */
public final class ElectricalPanel {
    public static final int WIDTH = 124, HEIGHT = 166;
    private static final double NOMINAL_VOLTS = 480;
    /** Visible wave cycles per second at 60 Hz. */
    private static final double VISUAL_HZ = 0.6;

    private static final int BG = 0xFF0A100D, GRID = 0xFF16261D, AXIS = 0xFF24402F;
    private static final int TEXT = 0xFFB8F5C0, DIM = 0xFF5F8A66;
    private static final int[] PHASE = {0xFFFF5A4A, 0xFFFFD24A, 0xFF4AA8FF};
    private static final int[] PHASE_DIM = {0xFFB0453A, 0xFFB0913A, 0xFF3A78B0};
    private static final int GOOD = 0xFF55FF55, FAIR = 0xFFFFB020, BAD = 0xFFFF4040;

    private double volts = 1, hertz = 60, fe, ee;
    private double phase;
    private long lastNanos = System.nanoTime();

    /**
     * @param volts   per-unit bus voltage (0 = dead)
     * @param hertz   frequency
     * @param fe      real power through this device
     * @param ee      reactive power, positive lagging
     * @param ratedCe what counts as full current for the wave and arrow sizes
     * @param status  bottom line, e.g. motor state
     */
    public void draw(GuiGraphicsExtractor g, Font font, int x, int y, double volts, double hertz, double fe, double ee, double ratedCe,
            Component status, int statusColor) {
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;
        double k = 1 - Math.exp(-dt * 8);
        this.volts += (volts - this.volts) * k;
        this.hertz += (hertz - this.hertz) * k;
        this.fe += (fe - this.fe) * k;
        this.ee += (ee - this.ee) * k;
        phase = (phase + dt * VISUAL_HZ * (this.hertz / 60.0) * Math.PI * 2) % (Math.PI * 2);

        double v = this.volts;
        double ce = Math.hypot(this.fe, this.ee);
        double phi = Math.atan2(this.ee, Math.max(0.0001, this.fe));
        double iFrac = Mth.clamp(ce / Math.max(1, ratedCe), 0, 1.3);
        boolean dead = v < 0.05 || this.hertz < 1;

        // Frame.
        g.fill(x, y, x + WIDTH, y + HEIGHT, 0xFF2B3036);
        g.fill(x + 1, y + 1, x + WIDTH - 1, y + HEIGHT - 1, 0xFF6F767D);
        g.fill(x + 2, y + 2, x + WIDTH - 2, y + HEIGHT - 2, BG);

        // Header.
        g.text(font, "3φ " + Math.round(v * NOMINAL_VOLTS) + " V", x + 5, y + 5, voltColor(v), false);
        String hz = String.format("%.2f Hz", this.hertz);
        g.text(font, hz, x + WIDTH - 5 - font.width(hz), y + 5, freqColor(this.hertz), false);

        scope(g, font, x + 4, y + 16, WIDTH - 8, 46, v, phi, iFrac, dead);
        phasors(g, x + 27, y + 90, 21, v, phi, iFrac, dead);
        triangle(g, font, x + 54, y + 68, WIDTH - 58, 44);

        // Readouts.
        int ry = y + 116;
        double amps = dead ? 0 : ce * 1000 / (Math.sqrt(3) * NOMINAL_VOLTS * Math.max(0.05, v));
        g.text(font, String.format("%.0f A", amps), x + 5, ry, TEXT, false);
        double pf = ce < 0.5 ? 1 : this.fe / ce;
        String pfText = ce < 0.5 ? "PF ---" : String.format("PF %.2f %s", pf, this.ee >= 0 ? "lag" : "lead");
        g.text(font, pfText, x + WIDTH - 5 - font.width(pfText), ry, ce < 0.5 ? DIM : pfColor(pf), false);
        ry += 11;
        String feText = Math.round(this.fe) + " FE";
        String eeText = Math.round(this.ee) + " EE";
        String ceText = Math.round(ce) + " CE";
        g.text(font, feText, x + 5, ry, GuiUtil.FE_COLOR, false);
        g.text(font, eeText, x + 5 + font.width(feText) + 6, ry, GuiUtil.EE_COLOR, false);
        g.text(font, ceText, x + WIDTH - 5 - font.width(ceText), ry, GuiUtil.CE_COLOR, false);
        ry += 13;
        g.fill(x + 4, ry - 3, x + WIDTH - 4, ry - 2, GRID);
        g.text(font, font.substrByWidth(status, WIDTH - 10).getString(), x + 5, ry, statusColor, false);
    }

    private void scope(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, double v, double phi, double iFrac, boolean dead) {
        g.fill(x, y, x + w, y + h, 0xFF060A08);
        int mid = y + h / 2;
        for (int gx = x; gx < x + w; gx += 2) {
            g.fill(gx, mid, gx + 1, mid + 1, AXIS);
        }
        for (int gx = x + 14; gx < x + w; gx += 14) {
            for (int gy = y + 2; gy < y + h; gy += 3) {
                g.fill(gx, gy, gx + 1, gy + 1, GRID);
            }
        }
        if (dead) {
            Component text = Component.translatable("gui.powerfactor.panel.dead");
            g.centeredText(font, text, x + w / 2, mid - 4, BAD);
            return;
        }
        double ampV = (h / 2.0 - 3) * Mth.clamp(v / 1.25, 0, 1);
        double ampI = (h / 2.0 - 5) * Math.min(1, iFrac / 1.3);
        double period = w / 2.0;
        for (int p = 0; p < 3; p++) {
            double offset = -p * 2 * Math.PI / 3;
            int prevV = Integer.MIN_VALUE;
            for (int i = 0; i < w; i++) {
                double theta = phase - i * 2 * Math.PI / period + offset;
                // Current: dotted, shifted by the PF angle.
                if (ampI > 0.5 && (i & 1) == 0) {
                    int yi = (int) Math.round(mid - ampI * Math.sin(theta - phi));
                    g.fill(x + i, yi, x + i + 1, yi + 1, PHASE_DIM[p]);
                }
                int yv = (int) Math.round(mid - ampV * Math.sin(theta));
                int top = prevV == Integer.MIN_VALUE ? yv : Math.min(yv, prevV);
                int bottom = prevV == Integer.MIN_VALUE ? yv : Math.max(yv, prevV);
                g.fill(x + i, top, x + i + 1, bottom + 1, PHASE[p]);
                prevV = yv;
            }
        }
    }

    private void phasors(GuiGraphicsExtractor g, int cx, int cy, int r, double v, double phi, double iFrac, boolean dead) {
        for (int a = 0; a < 48; a++) {
            double t = a * Math.PI * 2 / 48;
            int px = cx + (int) Math.round(Math.cos(t) * r), py = cy + (int) Math.round(Math.sin(t) * r);
            g.fill(px, py, px + 1, py + 1, AXIS);
        }
        g.fill(cx - r, cy, cx + r + 1, cy + 1, GRID);
        g.fill(cx, cy - r, cx + 1, cy + r + 1, GRID);
        if (dead) {
            return;
        }
        double lenV = (r - 2) * Mth.clamp(v / 1.25, 0, 1);
        double lenI = (r - 4) * Math.min(1, iFrac / 1.3);
        for (int p = 0; p < 3; p++) {
            // Counter-clockwise rotation, screen y down.
            double a = phase - p * 2 * Math.PI / 3;
            if (lenI > 1) {
                line(g, cx, cy, cx + Math.cos(a - phi) * lenI, cy - Math.sin(a - phi) * lenI, PHASE_DIM[p], true);
            }
            line(g, cx, cy, cx + Math.cos(a) * lenV, cy - Math.sin(a) * lenV, PHASE[p], false);
        }
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, TEXT);
    }

    private void triangle(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF060A08);
        double ce = Math.hypot(fe, ee);
        if (ce < 0.5) {
            g.centeredText(font, "△", x + w / 2, y + h / 2 - 4, DIM);
            return;
        }
        double scale = Math.min((w - 8) / Math.max(1, Math.abs(fe)), (h - 8) / Math.max(1, Math.abs(ee)));
        scale = Math.min(scale, (w - 8) / ce);
        double ox = x + 4, oy = ee >= 0 ? y + h - 4 : y + 4;
        double fx = ox + fe * scale, fy = oy - ee * scale;
        line(g, ox, oy, fx, oy, GuiUtil.FE_COLOR, false);
        line(g, fx, oy, fx, fy, GuiUtil.EE_COLOR, false);
        line(g, ox, oy, fx, fy, GuiUtil.CE_COLOR, false);
        // The angle between FE and CE is the power factor angle.
        g.text(font, "φ", (int) ox + 6, ee >= 0 ? (int) oy - 10 : (int) oy + 2, DIM, false);
    }

    private static void line(GuiGraphicsExtractor g, double x0, double y0, double x1, double y1, int color, boolean dashed) {
        double len = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        int steps = Math.max(1, (int) Math.ceil(len));
        for (int i = 0; i <= steps; i++) {
            if (dashed && (i / 2) % 2 == 1) {
                continue;
            }
            int px = (int) Math.round(x0 + (x1 - x0) * i / steps), py = (int) Math.round(y0 + (y1 - y0) * i / steps);
            g.fill(px, py, px + 1, py + 1, color);
        }
    }

    public static int voltColor(double v) {
        return v < 0.05 ? BAD : v < 0.9 || v > 1.1 ? (v < 0.8 || v > 1.12 ? BAD : FAIR) : TEXT;
    }

    public static int freqColor(double hz) {
        return hz < 1 ? BAD : Math.abs(hz - 60) > 2 ? BAD : Math.abs(hz - 60) > 0.9 ? FAIR : TEXT;
    }

    public static int pfColor(double pf) {
        return pf >= 0.95 ? GOOD : pf >= 0.85 ? FAIR : BAD;
    }
}
