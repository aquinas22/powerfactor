package com.powerfactor.client;

import com.powerfactor.scada.ScadaPayloads;
import com.powerfactor.scada.ScadaReading;
import com.powerfactor.scada.ScadaTerminalMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * A control-room HMI: system totals across the top, then one row per device with a status lamp,
 * live FE/EE, what it's doing, and an ON/OFF switch. Drawn entirely in code.
 */
public class ScadaTerminalScreen extends AbstractContainerScreen<ScadaTerminalMenu> {
    // 222 tall so it fits the 240-unit minimum GUI height.
    private static final int W = 256, H = 222;
    private static final int TREND_Y = 46, TREND_H = 38;
    private static final int LIST_Y = 98, ROW_H = 22, ROWS = 5;
    /** One sample per snapshot (twice a second): a minute of history. */
    private static final int HISTORY = 120;
    private static final int BTN_X = 216, BTN_W = 32, BTN_H = 12;

    private static final int BG = Skin.INK, PANEL = Skin.SURFACE, GRID = 0xFF28424C;
    private static final int TEXT = Skin.LCD_TEXT, DIM = Skin.LCD_DIM, AMBER = Skin.OUTPUT;
    private static final int[] LAMP = {0xFF3A3F3C, 0xFF55FF55, 0xFFFFB020, 0xFFFF4040};

    private List<ScadaReading> readings = List.of();
    private long lastAlarms;
    private final java.util.ArrayDeque<double[]> history = new java.util.ArrayDeque<>();
    private int scroll;
    private long lastUpdate;
    /** The device whose detail view is open (null: the list). */
    private net.minecraft.core.@org.jspecify.annotations.Nullable BlockPos detail;
    private boolean alarmsOnly;
    private String armed = "";
    private long armedAt;

    /** A clickable area in screen-local coordinates. */
    private record Hit(int x, int y, int w, int h, Runnable action) {}

    private final java.util.List<Hit> hits = new java.util.ArrayList<>();

    /** What the list shows: everything, or only devices raising a warning or alarm. */
    private List<ScadaReading> shown() {
        return alarmsOnly ? readings.stream().filter(r -> r.status() == ScadaReading.Status.ALARM || r.status() == ScadaReading.Status.WARNING).toList()
                : readings;
    }

    public ScadaTerminalScreen(ScadaTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H);
    }

    /** Called from the network thread hand-off with the server's latest poll. */
    public static void receive(ScadaPayloads.Snapshot snapshot) {
        var screen = net.minecraft.client.Minecraft.getInstance().gui.screen();
        if (screen instanceof ScadaTerminalScreen s && s.menu.terminal().equals(snapshot.terminal())) {
            long alarms = snapshot.readings().stream().filter(r -> r.status() == ScadaReading.Status.ALARM).count();
            if (alarms > s.lastAlarms) {
                net.minecraft.client.Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        com.powerfactor.registry.ModSounds.SCADA_ALARM.get(), 1.0F, 0.6F));
            }
            s.lastAlarms = alarms;
            s.readings = snapshot.readings();
            s.record();
            s.lastUpdate = System.currentTimeMillis();
            s.scroll = Mth.clamp(s.scroll, 0, Math.max(0, s.shown().size() - ROWS));
        }
    }

    /** System frequency and voltage, taken from the generators (or any device if there are none). */
    private void record() {
        double hz = 0, v = 0;
        int n = 0;
        for (ScadaReading r : readings) {
            if (r.generator() && r.hertz() > 0) {
                hz += r.hertz();
                v += r.volts();
                n++;
            }
        }
        if (n == 0) {
            for (ScadaReading r : readings) {
                if (r.volts() > 0 && r.ee() != 0) {
                    hz += r.hertz();
                    v += r.volts();
                    n++;
                }
            }
        }
        history.addLast(n == 0 ? new double[] {0, 0} : new double[] {hz / n, v / n});
        while (history.size() > HISTORY) {
            history.removeFirst();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        int x = leftPos, y = topPos;
        // The control-room monitor sits in the same bright chassis as every other screen.
        Skin.panel(g, x - 7, y - 7, W + 14, H + 14);
        g.fill(x - 2, y - 2, x + W + 2, y + H + 2, 0xFF6F8797);
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, 0xFF0A1016);
        g.fill(x, y, x + W, y + H, BG);
        // Header and totals panels.
        g.fill(x, y, x + W, y + 14, 0xFF244940);
        g.fill(x + 4, y + 18, x + W - 4, y + 42, PANEL);
        // Faint scanlines.
        for (int sy = y + 46; sy < y + H; sy += 2) {
            g.fill(x, sy, x + W, sy + 1, 0xFF13232C);
        }
        if (detail != null) return;
        trend(g, x + 4, y + TREND_Y, W - 8, TREND_H);
        for (int i = 0; i < ROWS; i++) {
            int ry = y + LIST_Y + i * ROW_H;
            g.fill(x + 4, ry + ROW_H - 1, x + W - 4, ry + ROW_H, GRID);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
        g.text(font, Component.translatable("gui.powerfactor.scada.title"), 6, 3, TEXT, false);
        boolean live = System.currentTimeMillis() - lastUpdate < 2_000;
        Component state = live ? Component.translatable("gui.powerfactor.scada.live", readings.size())
                : Component.translatable("gui.powerfactor.scada.no_link");
        g.text(font, state, W - 6 - font.width(state), 3, live ? TEXT : AMBER, false);

        int genFe = 0, genEe = 0, loadFe = 0, loadEe = 0;
        for (ScadaReading r : readings) {
            if (r.generator()) {
                genFe += r.fe();
                genEe += r.ee();
            } else {
                loadFe += r.fe();
                loadEe += r.ee();
            }
        }
        double ce = Math.hypot(genFe, genEe);
        g.text(font, Component.translatable("gui.powerfactor.scada.generation"), 8, 21, DIM, false);
        unitPair(g, genFe, genEe, 76, 21);
        g.text(font, ce == 0 ? "PF ---" : String.format("PF %.2f", genFe / ce), W - 8 - font.width("PF 0.00"), 21, TEXT, false);
        g.text(font, Component.translatable("gui.powerfactor.scada.load"), 8, 31, DIM, false);
        unitPair(g, loadFe, loadEe, 76, 31);
        String ceText = Math.round(ce) + " CE/t";
        g.text(font, ceText, W - 8 - font.width(ceText), 31, GuiUtil.CE_COLOR, false);

        hits.clear();
        if (detail != null) {
            ScadaReading r = readings.stream().filter(d -> d.pos().equals(detail)).findFirst().orElse(null);
            if (r == null) {
                detail = null;
            } else {
                detailView(g, r, xm - leftPos, ym - topPos);
                return;
            }
        }
        double[] latest = history.isEmpty() ? new double[] {0, 0} : history.getLast();
        g.text(font, String.format("%.2f Hz", latest[0]), 8, TREND_Y + 2, AMBER, false);
        String vText = Math.round(latest[1] * 480) + " V";
        g.text(font, vText, W - 8 - font.width(vText), TREND_Y + 2, 0xFF55FF55, false);
        g.text(font, Component.translatable("gui.powerfactor.scada.trend"), W / 2 - font.width(Component.translatable("gui.powerfactor.scada.trend")) / 2,
                TREND_Y + TREND_H - 10, DIM, false);
        g.text(font, Component.translatable("gui.powerfactor.scada.device"), 18, LIST_Y - 11, DIM, false);
        g.text(font, "FE/t", 132 - font.width("FE/t"), LIST_Y - 11, DIM, false);
        g.text(font, "EE/t", 170 - font.width("EE/t"), LIST_Y - 11, DIM, false);

        // Filter chip: all devices, or only those in alarm.
        long alarmCount = readings.stream().filter(r -> r.status() == ScadaReading.Status.ALARM || r.status() == ScadaReading.Status.WARNING).count();
        Component chip = alarmsOnly ? Component.translatable("gui.powerfactor.scada.filter.alarms", alarmCount)
                : Component.translatable("gui.powerfactor.scada.filter.all", alarmCount);
        int cw = font.width(chip) + 8, cx = W - 6 - cw, cy = LIST_Y - 13;
        g.fill(cx, cy, cx + cw, cy + 11, alarmsOnly ? 0xFF6B1F1F : alarmCount > 0 ? 0xFF6B4A1F : 0xFF1E3A30);
        g.outline(cx, cy, cw, 11, 0xFF8A9A90);
        g.text(font, chip, cx + 4, cy + 2, 0xFFFFFFFF, false);
        hits.add(new Hit(cx, cy, cw, 11, () -> {
            alarmsOnly = !alarmsOnly;
            scroll = 0;
        }));
        List<ScadaReading> list = shown();
        if (readings.isEmpty()) {
            Component hint = Component.translatable(live ? "gui.powerfactor.scada.empty" : "gui.powerfactor.scada.connecting");
            // Wrapped to the panel, so long translations never run into the border.
            var lines = font.split(hint, W - 24);
            for (int i = 0; i < lines.size(); i++) {
                g.centeredText(font, lines.get(i), W / 2, LIST_Y + 26 + i * 10, DIM);
            }
        }
        for (int i = 0; i < ROWS && scroll + i < list.size(); i++) {
            row(g, list.get(scroll + i), LIST_Y + i * ROW_H, xm - leftPos, ym - topPos);
        }
        if (list.size() > ROWS) {
            int trackH = ROWS * ROW_H;
            int thumbH = Math.max(8, trackH * ROWS / list.size());
            int thumbY = LIST_Y + (trackH - thumbH) * scroll / (list.size() - ROWS);
            g.fill(W - 3, thumbY, W - 1, thumbY + thumbH, DIM);
        }
    }

    /** Frequency (amber, 57-61 Hz) and voltage (green, 0.8-1.2 pu) over the last minute. */
    private void trend(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF060A08);
        for (int gy = 1; gy < 4; gy++) {
            int ly = y + h * gy / 4;
            for (int gx = x; gx < x + w; gx += 3) {
                g.fill(gx, ly, gx + 1, ly + 1, GRID);
            }
        }
        int nominal = y + h / 2;
        for (int gx = x; gx < x + w; gx += 2) {
            g.fill(gx, nominal, gx + 1, nominal + 1, 0xFF24402F);
        }
        int i = 0, prevHz = -1, prevV = -1;
        int n = history.size();
        for (double[] sample : history) {
            int px = x + w - 1 - (n - 1 - i) * (w - 1) / (HISTORY - 1);
            int hy = y + (int) Math.round(Mth.clamp((61 - sample[0]) / 4.0, 0, 1) * (h - 1));
            int vy = y + (int) Math.round(Mth.clamp((1.2 - sample[1]) / 0.4, 0, 1) * (h - 1));
            if (prevHz >= 0) {
                int prevX = x + w - 1 - (n - i) * (w - 1) / (HISTORY - 1);
                segment(g, prevX, prevV, px, vy, 0xFF55FF55);
                segment(g, prevX, prevHz, px, hy, AMBER);
            }
            prevHz = hy;
            prevV = vy;
            i++;
        }
    }

    private static void segment(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int s = 0; s <= steps; s++) {
            int px = steps == 0 ? x0 : x0 + (x1 - x0) * s / steps, py = steps == 0 ? y0 : y0 + (y1 - y0) * s / steps;
            g.fill(px, py, px + 1, py + 1, color);
        }
    }

    private void row(GuiGraphicsExtractor g, ScadaReading r, int y, int mx, int my) {
        g.fill(7, y + 3, 13, y + 9, LAMP[r.status().ordinal()]);
        g.text(font, font.substrByWidth(r.name(), 104).getString(), 18, y + 2, TEXT, false);
        String fe = Integer.toString(r.fe());
        String ee = Integer.toString(r.ee());
        g.text(font, fe, 132 - font.width(fe), y + 2, GuiUtil.FE_COLOR, false);
        g.text(font, ee, 170 - font.width(ee), y + 2, GuiUtil.EE_COLOR, false);
        g.text(font, font.substrByWidth(r.detail(), 192).getString(), 18, y + 11, DIM, false);
        if (r.detailed()) g.text(font, "›", BTN_X - 8, y + 6, TEXT, false);

        if (r.controllable()) {
            boolean hover = mx >= BTN_X && mx < BTN_X + BTN_W && my >= y + 2 && my < y + 2 + BTN_H;
            int color = r.enabled() ? 0xFF1F6B3A : 0xFF6B1F1F;
            g.fill(BTN_X, y + 2, BTN_X + BTN_W, y + 2 + BTN_H, hover ? brighten(color) : color);
            g.outline(BTN_X, y + 2, BTN_W, BTN_H, 0xFF8A9A90);
            Component label = Component.translatable(r.enabled() ? "gui.powerfactor.scada.on" : "gui.powerfactor.scada.off");
            g.centeredText(font, label, BTN_X + BTN_W / 2, y + 4, 0xFFFFFFFF);
        }
    }

    private void unitPair(GuiGraphicsExtractor g, int fe, int ee, int x, int y) {
        String feText = fe + " FE/t";
        g.text(font, feText, x, y, GuiUtil.FE_COLOR, false);
        g.text(font, ee + " EE/t", x + font.width(feText) + 8, y, GuiUtil.EE_COLOR, false);
    }

    private static int brighten(int argb) {
        return 0xFF000000 | Math.min(255, ((argb >> 16) & 0xFF) + 40) << 16 | Math.min(255, ((argb >> 8) & 0xFF) + 40) << 8 | Math.min(255, (argb & 0xFF) + 40);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x() - leftPos, my = (int) event.y() - topPos;
        for (Hit h : java.util.List.copyOf(hits)) {
            if (mx >= h.x && mx < h.x + h.w && my >= h.y && my < h.y + h.h) {
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        com.powerfactor.registry.ModSounds.SCADA_CLICK.get(), 1.0F));
                h.action.run();
                return true;
            }
        }
        if (detail != null) return super.mouseClicked(event, doubleClick);
        List<ScadaReading> list = shown();
        for (int i = 0; i < ROWS && scroll + i < list.size(); i++) {
            ScadaReading r = list.get(scroll + i);
            int rowY = LIST_Y + i * ROW_H;
            if (mx >= 4 && mx < BTN_X - 2 && my >= rowY && my < rowY + ROW_H) {
                detail = r.pos();
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        com.powerfactor.registry.ModSounds.SCADA_CLICK.get(), 1.2F));
                return true;
            }
            int y = LIST_Y + i * ROW_H + 2;
            if (r.controllable() && mx >= BTN_X && mx < BTN_X + BTN_W && my >= y && my < y + BTN_H) {
                ClientPacketDistributor.sendToServer(new ScadaPayloads.Command(menu.terminal(), r.pos(), !r.enabled()));
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        com.powerfactor.registry.ModSounds.SCADA_CLICK.get(), 1.0F));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, Math.max(0, shown().size() - ROWS));
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        int mx = mouseX - leftPos, my = mouseY - topPos;
        if (detail != null) {
            if (pointTip != null) g.setComponentTooltipForNextFrame(font, pointTip, mouseX, mouseY);
            return;
        }
        List<ScadaReading> list = shown();
        for (int i = 0; i < ROWS && scroll + i < list.size(); i++) {
            int y = LIST_Y + i * ROW_H;
            if (mx >= 4 && mx < BTN_X - 2 && my >= y && my < y + ROW_H) {
                ScadaReading r = list.get(scroll + i);
                g.setComponentTooltipForNextFrame(font, List.of(r.name(), r.detail(),
                        Component.literal(r.pos().toShortString()).withColor(DIM & 0xFFFFFF)), mouseX, mouseY);
            }
        }
    }

    private java.util.@org.jspecify.annotations.Nullable List<Component> pointTip;

    private static final int[] STATE = {0xFF55FF55, 0xFFFFB020, 0xFFFF4040};

    /** One device up close: its gauges in two columns, then its controls. */
    private void detailView(GuiGraphicsExtractor g, ScadaReading r, int mx, int my) {
        pointTip = null;
        long t = System.currentTimeMillis();
        if (!armed.isEmpty() && t - armedAt > 2500) armed = "";
        // Header: back, lamp, name, position.
        g.fill(4, 46, W - 4, 60, 0xFF16303A);
        boolean backHover = mx >= 6 && mx < 38 && my >= 48 && my < 58;
        g.fill(6, 48, 38, 58, backHover ? 0xFF2E5A6A : 0xFF1E3A48);
        g.text(font, Component.translatable("gui.powerfactor.scada.back"), 9, 49, TEXT, false);
        hits.add(new Hit(6, 48, 32, 10, () -> detail = null));
        g.fill(44, 50, 50, 56, LAMP[r.status().ordinal()]);
        g.text(font, font.substrByWidth(r.name(), 120).getString(), 54, 49, TEXT, false);
        String where = r.pos().toShortString();
        g.text(font, where, W - 8 - font.width(where), 49, DIM, false);
        // Gauges.
        int y0 = 64;
        if (r.points().isEmpty()) {
            g.text(font, font.substrByWidth(r.detail(), W - 16).getString(), 8, y0, DIM, false);
            y0 += 14;
        }
        for (int k = 0; k < r.points().size(); k++) {
            var p = r.points().get(k);
            int cx = 6 + (k % 2) * 124, cy = y0 + (k / 2) * 19;
            int col = STATE[Math.clamp(p.state(), 0, 2)];
            boolean flash = p.state() == 2 && (t / 300) % 2 == 0;
            g.fill(cx, cy, cx + 120, cy + 17, flash ? 0xFF3A1414 : 0xFF0E1C22);
            Skin.condensed(g, font, p.label(), cx + 3, cy + 2, 64, DIM);
            String v = format(p.value(), p.unit());
            g.text(font, v, cx + 117 - font.width(v), cy + 2, col, false);
            float f = p.hi() > p.lo() ? Mth.clamp((p.value() - p.lo()) / (p.hi() - p.lo()), 0, 1) : 0;
            g.fill(cx + 3, cy + 12, cx + 117, cy + 14, 0xFF24343C);
            g.fill(cx + 3, cy + 12, cx + 3 + (int) (114 * f), cy + 14, col);
            if (mx >= cx && mx < cx + 120 && my >= cy && my < cy + 17) {
                pointTip = java.util.List.of(p.label(), Component.literal(v + "   (" + format(p.lo(), p.unit()) + " – " + format(p.hi(), p.unit()) + ")")
                        .withColor(DIM & 0xFFFFFF));
            }
        }
        int cy = y0 + ((r.points().size() + 1) / 2) * 19 + 4;
        // Buttons and toggles in a row, then setpoints in two columns.
        int bx = 6;
        for (var c : r.controls()) {
            if (c.kind() == ScadaReading.SETPOINT) continue;
            boolean isArmed = c.danger() && armed.equals(c.id());
            boolean on = c.kind() == ScadaReading.TOGGLE && c.value() > 0;
            int bw = Math.max(38, font.width(c.label()) + 10);
            boolean hover = mx >= bx && mx < bx + bw && my >= cy && my < cy + 14;
            int base = c.danger() ? (isArmed && (t / 200) % 2 == 0 ? 0xFFFF4040 : 0xFF8A1F1F) : on ? 0xFF1F6B3A : 0xFF1E3A48;
            g.fill(bx, cy, bx + bw, cy + 14, hover ? brighten(base) : base);
            g.outline(bx, cy, bw, 14, 0xFF8A9A90);
            Component label = isArmed ? Component.translatable("gui.powerfactor.scada.confirm") : c.label();
            g.centeredText(font, label, bx + bw / 2, cy + 3, 0xFFFFFFFF);
            hits.add(new Hit(bx, cy, bw, 14, () -> {
                if (c.danger() && !armed.equals(c.id())) {
                    armed = c.id();
                    armedAt = System.currentTimeMillis();
                    return;
                }
                armed = "";
                send(r, c.id(), c.kind() == ScadaReading.TOGGLE ? (c.value() > 0 ? 0 : 1) : 0);
            }));
            bx += bw + 4;
        }
        int sy = cy + 18, k = 0;
        for (var c : r.controls()) {
            if (c.kind() != ScadaReading.SETPOINT) continue;
            int sx = 6 + (k % 2) * 124, yy = sy + (k / 2) * 16;
            g.fill(sx, yy, sx + 120, yy + 14, 0xFF0E1C22);
            Skin.condensed(g, font, c.label(), sx + 3, yy + 3, 50, DIM);
            String v = format(c.value(), "");
            g.centeredText(font, v, sx + 88, yy + 3, TEXT);
            for (int d = 0; d < 2; d++) {
                int ax = sx + (d == 0 ? 56 : 106);
                boolean hover = mx >= ax && mx < ax + 12 && my >= yy + 1 && my < yy + 13;
                g.fill(ax, yy + 1, ax + 12, yy + 13, hover ? 0xFF2E5A6A : 0xFF1E3A48);
                g.centeredText(font, d == 0 ? "−" : "+", ax + 6, yy + 3, TEXT);
                float nv = Mth.clamp(c.value() + (d == 0 ? -c.step() : c.step()), c.min(), c.max());
                hits.add(new Hit(ax, yy + 1, 12, 12, () -> send(r, c.id(), nv)));
            }
            k++;
        }
    }

    private void send(ScadaReading r, String id, float value) {
        ClientPacketDistributor.sendToServer(new ScadaPayloads.ControlCommand(menu.terminal(), r.pos(), id, value));
    }

    private static String format(float v, String unit) {
        String n = Math.abs(v) >= 10_000 ? Skin.compact(v) : Math.abs(v) >= 100 || v == Math.round(v) ? String.valueOf(Math.round(v)) : String.format("%.1f", v);
        return unit.isEmpty() ? n : n + " " + unit;
    }
}
