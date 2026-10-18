package com.powerfactor.client.fission;

import com.powerfactor.client.Skin;
import com.powerfactor.fission.ReactorControllerBlockEntity;
import com.powerfactor.fission.ReactorCore;
import com.powerfactor.fission.ReactorMenu;
import com.powerfactor.fission.ReactorPayloads;
import com.powerfactor.fission.ReactorPayloads.Command;
import com.powerfactor.fission.ReactorPayloads.Snapshot;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * The reactor control console. Left, a live plan of the core: each fuel column coloured by temperature
 * with its rod insertion as a bar (click one to drive it alone). Centre, the instruments. Right, the
 * controls: a guarded SCRAM, reset and acknowledge, AUTO with its power target, and rod drives. Below,
 * the annunciator (flashing until acknowledged), the trip setpoints and a minute of trend.
 */
public class ReactorScreen extends AbstractContainerScreen<ReactorMenu> {
    public static final int W = 320, H = 236;
    private static final int RED = 0xFFFF4A3A, GREEN = 0xFF7CFF8A, AMBER = 0xFFFFB347, CYAN = 0xFF6FE3FF, VIOLET = 0xFFB18CFF;
    private static @Nullable Snapshot latest;
    private static final ArrayDeque<float[]> HISTORY = new ArrayDeque<>();
    private int selected = -1;
    private boolean armed;
    private long armedAt;

    private record Btn(int x, int y, int w, int h, Component label, Component tip, int color, boolean on, Runnable action) {}

    private final List<Btn> buttons = new ArrayList<>();
    private boolean purgeArmed;

    public ReactorScreen(ReactorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, W, H);
    }

    /** Called with each snapshot from the server. */
    public static void accept(Snapshot s) {
        latest = s;
        HISTORY.addLast(new float[] {s.values()[Snapshot.POWER], s.values()[Snapshot.TEMP], s.values()[Snapshot.REACTIVITY]});
        while (HISTORY.size() > 240) HISTORY.removeFirst();
    }

    private void send(int op, int column, float value) {
        ClientPacketDistributor.sendToServer(new Command(menu.pos, op, column, value));
    }

    private float v(int i) {
        return latest == null ? 0 : latest.values()[i];
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractBackground(g, mx, my, a);
        buttons.clear();
        int x = leftPos, y = topPos;
        long t = Util.getMillis();
        Skin.panel(g, x, y, W, H);
        Skin.title(g, font, title, x, y, W);
        if (latest != null && latest.formed() && !latest.problem().getString().isEmpty()) {
            Component trip = Component.translatable("gui.powerfactor.fission.tripped", latest.problem());
            g.text(font, trip, x + W - 8 - font.width(trip), y + 6, RED, false);
        }
        Snapshot s = latest != null && latest.pos().equals(menu.pos) ? latest : null;
        coreMap(g, s, x + 8, y + 20, 120, 120, mx, my);
        readouts(g, s, x + 134, y + 20, 96, 120);
        controls(s, x, y, t);
        annunciator(g, s, x + 8, y + 146, t);
        trips(g, x + 252, y + 146);
        trend(g, x + 8, y + 186, 304, 44);
        for (Btn b : buttons) {
            boolean hover = mx >= b.x && mx < b.x + b.w && my >= b.y && my < b.y + b.h;
            Skin.control(g, b.x, b.y, b.w, b.h, b.on, hover);
            Skin.condensed(g, font, b.label, b.x + (b.w - Math.min(b.w - 4, font.width(b.label))) / 2, b.y + (b.h - 8) / 2, b.w - 4, b.color);
        }
    }

    // ---------------------------------------------------------------- core map

    private static int heat(float k) {
        // 300 K teal → 700 yellow → 1000 orange → 1200 red-white.
        float f = Mth.clamp((k - 300) / 900, 0, 1);
        if (f < 0.45F) return com.powerfactor.client.fx.Fx.mix(0xFF2E8A9A, 0xFFFFD84A, f / 0.45F);
        if (f < 0.8F) return com.powerfactor.client.fx.Fx.mix(0xFFFFD84A, 0xFFFF7A2A, (f - 0.45F) / 0.35F);
        return com.powerfactor.client.fx.Fx.mix(0xFFFF7A2A, 0xFFFFE0E0, (f - 0.8F) / 0.2F);
    }

    private void coreMap(GuiGraphicsExtractor g, @Nullable Snapshot s, int x, int y, int w, int h, int mx, int my) {
        Skin.lcd(g, x, y, w, h);
        if (s == null || !s.formed()) {
            Component msg = s == null ? Component.translatable("gui.powerfactor.fission.waiting") : s.problem();
            Skin.icon(g, Skin.Icon.WARNING, x + w / 2 - 4, y + h / 2 - 16, null);
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(msg, w - 12);
            for (int i = 0; i < lines.size() && i < 4; i++) g.centeredText(font, lines.get(i), x + w / 2, y + h / 2 - 2 + i * 10, AMBER);
            return;
        }
        float[] c = s.columns();
        int iw = (int) c[c.length - 2], id = (int) c[c.length - 1];
        int cell = Math.max(4, Math.min((w - 8) / Math.max(1, iw), (h - 8) / Math.max(1, id)));
        int ox = x + (w - cell * iw) / 2, oy = y + (h - cell * id) / 2;
        // Pool (water) behind everything, glowing blue with power.
        float glow = Mth.clamp(v(Snapshot.POWER), 0, 1.2F);
        int pool = com.powerfactor.client.fx.Fx.mix(0xFF143A5A, 0xFF3A8CFF, glow * 0.6F);
        g.fill(ox, oy, ox + cell * iw, oy + cell * id, pool);
        int n = (c.length - 2) / 6;
        int hovered = -1;
        // Cherenkov light: each column haloed blue in the pool by its flux, shimmering.
        float time = Util.getMillis() / 50F;
        for (int i = 0; i < n; i++) {
            int cx = ox + (int) c[i * 6] * cell, cy = oy + (int) c[i * 6 + 1] * cell;
            float k = Mth.clamp(Mth.sqrt(Math.max(0, glow * c[i * 6 + 5])), 0, 1) * (0.85F + 0.15F * Mth.sin(time * 0.3F + i * 1.7F));
            if (k < 0.02F) continue;
            int spread = Math.max(2, cell / 3);
            for (int ring = spread; ring >= 1; ring--) {
                int a = (int) (k * 110 * (1 - (ring - 1F) / spread));
                g.fill(cx - ring, cy - ring, cx + cell + ring, cy + cell + ring, a << 24 | 0x5AA8FF);
            }
        }
        for (int i = 0; i < n; i++) {
            int cx = ox + (int) c[i * 6] * cell, cy = oy + (int) c[i * 6 + 1] * cell;
            float rod = c[i * 6 + 2], temp = c[i * 6 + 4];
            g.fill(cx + 1, cy + 1, cx + cell - 1, cy + cell - 1, heat(temp));
            // Rod insertion: a dark bar from the top of the cell down.
            int bar = (int) ((cell - 4) * rod);
            g.fill(cx + cell / 2 - 1, cy + 2, cx + cell / 2 + 1, cy + 2 + bar, 0xE0101418);
            if (i == selected) {
                g.fill(cx, cy, cx + cell, cy + 1, 0xFFFFFFFF);
                g.fill(cx, cy + cell - 1, cx + cell, cy + cell, 0xFFFFFFFF);
                g.fill(cx, cy, cx + 1, cy + cell, 0xFFFFFFFF);
                g.fill(cx + cell - 1, cy, cx + cell, cy + cell, 0xFFFFFFFF);
            }
            if (mx >= cx && mx < cx + cell && my >= cy && my < cy + cell) hovered = i;
        }
        if (hovered >= 0) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.powerfactor.fission.column", hovered + 1, (int) c[hovered * 6 + 4],
                    Math.round(c[hovered * 6 + 2] * 100), Math.round(c[hovered * 6 + 5] * 100)), mx, my);
        }
    }

    private int columnAt(double mx, double my) {
        Snapshot s = latest;
        if (s == null || !s.formed()) return -2;
        int x = leftPos + 8, y = topPos + 20, w = 120, h = 120;
        if (mx < x || my < y || mx >= x + w || my >= y + h) return -2;
        float[] c = s.columns();
        int iw = (int) c[c.length - 2], id = (int) c[c.length - 1];
        int cell = Math.max(4, Math.min((w - 8) / Math.max(1, iw), (h - 8) / Math.max(1, id)));
        int ox = x + (w - cell * iw) / 2, oy = y + (h - cell * id) / 2;
        int n = (c.length - 2) / 6;
        for (int i = 0; i < n; i++) {
            int cx = ox + (int) c[i * 6] * cell, cy = oy + (int) c[i * 6 + 1] * cell;
            if (mx >= cx && mx < cx + cell && my >= cy && my < cy + cell) return i;
        }
        return -1;
    }

    // ---------------------------------------------------------------- instruments

    private void readouts(GuiGraphicsExtractor g, @Nullable Snapshot s, int x, int y, int w, int h) {
        Skin.lcd(g, x, y, w, h);
        // The headline: power as a percentage, large.
        float p = v(Snapshot.POWER);
        int pc = p > 1.05F ? RED : p > 0.01F ? GREEN : Skin.LCD_DIM;
        g.pose().pushMatrix();
        g.pose().translate(x + 6, y + 5);
        g.pose().scale(2F, 2F);
        g.text(font, Component.literal(String.format("%.1f%%", p * 100)), 0, 0, pc, false);
        g.pose().popMatrix();
        double rho = v(Snapshot.REACTIVITY) * 1e5, period = v(Snapshot.PERIOD);
        List<Skin.Row> rows = List.of(
                new Skin.Row(Skin.Icon.HEAT, Component.literal(Skin.compact(v(Snapshot.THERMAL)) + " FE/t"), AMBER,
                        Component.translatable("gui.powerfactor.fission.thermal")),
                new Skin.Row(Skin.Icon.TEMP, Component.literal(Math.round(v(Snapshot.TEMP)) + " K"), heat(v(Snapshot.TEMP)),
                        Component.translatable("gui.powerfactor.fission.temp")),
                new Skin.Row(Skin.Icon.PF, Component.literal(String.format("%+.0f pcm", rho)), rho > 0 ? AMBER : CYAN,
                        Component.translatable("gui.powerfactor.fission.reactivity", String.format("%.4f", v(Snapshot.K)))),
                new Skin.Row(Skin.Icon.CLOCK, Component.literal(Math.abs(period) > 999 || p < 1e-4F ? "∞" : String.format("%.0f s", period)),
                        period > 0 && period < 4 ? RED : period > 0 && period < 15 ? AMBER : Skin.LCD_TEXT, Component.translatable("gui.powerfactor.fission.period")),
                new Skin.Row(Skin.Icon.VOID, Component.literal(String.format("-%.0f pcm", v(Snapshot.XENON) * 1e5)), VIOLET,
                        Component.translatable("gui.powerfactor.fission.xenon")),
                new Skin.Row(coolant().loop ? Skin.Icon.HEAT : Skin.Icon.STEAM, Component.literal(Skin.compact(v(Snapshot.STEAM_RATE)) + " mB/t"),
                        hotColor(coolant()), Component.translatable(coolant().loop ? "gui.powerfactor.fission.hot" : "gui.powerfactor.fission.steam",
                                Skin.compact(v(Snapshot.STEAM)), Component.translatable(coolantKey(coolant())))),
                new Skin.Row(Skin.Icon.FLUID, Component.literal(Skin.compact(v(Snapshot.WATER)) + " mB"), v(Snapshot.WATER) < 1000 ? RED : coolColor(coolant()),
                        Component.translatable("gui.powerfactor.fission.coolant", Component.translatable(coolantKey(coolant())),
                                Component.translatable(coolantKey(coolant()) + ".tip"))),
                new Skin.Row(Skin.Icon.FUEL, Component.literal((s == null ? 0 : s.fuel()) + "/" + (s == null ? 0 : s.assemblies())), GREEN,
                        Component.translatable("gui.powerfactor.fission.fuel", s == null ? 0 : s.spent())));
        Skin.lcdRows(g, font, x, y + 22, w, h - 22, rows);
        if (v(Snapshot.DAMAGE) > 0) {
            g.fill(x + 2, y + h - 4, x + 2 + (int) ((w - 4) * v(Snapshot.DAMAGE) / 100), y + h - 2, RED);
        }
    }

    private ReactorCore.Coolant coolant() {
        return ReactorCore.Coolant.values()[Mth.clamp((int) v(Snapshot.COOLANT), 0, ReactorCore.Coolant.values().length - 1)];
    }

    private static String coolantKey(ReactorCore.Coolant c) {
        return "gui.powerfactor.fission.coolant." + c.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** The coolant's own colour, as in its tank and bucket. */
    private static int coolColor(ReactorCore.Coolant c) {
        return switch (c) {
            case WATER -> 0xFF3F76E4;
            case HEAVY_WATER -> 0xFF5E9CD8;
            case LIQUID_SODIUM -> 0xFFC4CAD2;
            case MOLTEN_SALT -> 0xFFDCEEE6;
        };
    }

    private static int hotColor(ReactorCore.Coolant c) {
        return switch (c) {
            case WATER -> 0xFFE8F0F4;
            case HEAVY_WATER -> 0xFFA8D8F4;
            case LIQUID_SODIUM -> 0xFFF2B456;
            case MOLTEN_SALT -> 0xFFFF6A2E;
        };
    }

    // ---------------------------------------------------------------- controls

    private void controls(@Nullable Snapshot s, int x, int y, long t) {
        boolean scrammed = v(Snapshot.SCRAMMED) > 0, auto = v(Snapshot.AUTO) > 0;
        if (armed && t - armedAt > 3000) armed = false;
        // SCRAM: lift the guard, then press.
        int sc = armed ? (t / 250 % 2 == 0 ? RED : 0xFFFFFFFF) : RED;
        buttons.add(new Btn(x + 236, y + 20, 76, 22, Component.translatable(armed ? "gui.powerfactor.fission.scram_now" : "gui.powerfactor.fission.scram"),
                Component.translatable("gui.powerfactor.fission.scram.tip"), sc, armed, () -> {
                    if (armed) {
                        send(Command.SCRAM, 0, 0);
                        armed = false;
                    } else {
                        armed = true;
                        armedAt = Util.getMillis();
                    }
                }));
        buttons.add(new Btn(x + 236, y + 46, 37, 14, Component.translatable("gui.powerfactor.fission.reset"), Component.translatable("gui.powerfactor.fission.reset.tip"),
                scrammed ? GREEN : Skin.LCD_DIM, false, () -> send(Command.RESET, 0, 0)));
        buttons.add(new Btn(x + 275, y + 46, 37, 14, Component.translatable("gui.powerfactor.fission.ack"), Component.translatable("gui.powerfactor.fission.ack.tip"),
                s != null && s.unacked() != 0 ? AMBER : Skin.LCD_DIM, false, () -> send(Command.ACK, 0, 0)));
        buttons.add(new Btn(x + 236, y + 64, 76, 14, Component.translatable("gui.powerfactor.fission.auto", Math.round(v(Snapshot.TARGET) * 100)),
                Component.translatable("gui.powerfactor.fission.auto.tip"), auto ? GREEN : Skin.LCD_DIM, auto, () -> send(Command.AUTO, 0, auto ? 0 : 1)));
        float target = v(Snapshot.TARGET);
        buttons.add(new Btn(x + 236, y + 82, 24, 14, Component.literal("−5"), Component.translatable("gui.powerfactor.fission.target"), CYAN, false,
                () -> send(Command.TARGET, 0, Math.max(0, target - 0.05F))));
        buttons.add(new Btn(x + 262, y + 82, 24, 14, Component.literal("−1"), Component.translatable("gui.powerfactor.fission.target"), CYAN, false,
                () -> send(Command.TARGET, 0, Math.max(0, target - 0.01F))));
        buttons.add(new Btn(x + 288, y + 82, 24, 14, Component.literal("+5"), Component.translatable("gui.powerfactor.fission.target"), CYAN, false,
                () -> send(Command.TARGET, 0, Math.min(1.1F, target + 0.05F))));
        // Rods: all, or the selected column. "Out" withdraws (adds power).
        float rod = selected >= 0 && s != null && s.formed() && selected * 6 + 3 < s.columns().length - 2 ? s.columns()[selected * 6 + 3] : v(Snapshot.ROD_MEAN);
        int col = selected;
        buttons.add(new Btn(x + 236, y + 100, 18, 14, Component.literal("▲▲"), Component.translatable("gui.powerfactor.fission.rods_out", 10), AMBER, false,
                () -> send(Command.RODS, col, Math.max(0, rod - 0.10F))));
        buttons.add(new Btn(x + 255, y + 100, 18, 14, Component.literal("▲"), Component.translatable("gui.powerfactor.fission.rods_out", 1), AMBER, false,
                () -> send(Command.RODS, col, Math.max(0, rod - 0.01F))));
        buttons.add(new Btn(x + 275, y + 100, 18, 14, Component.literal("▼"), Component.translatable("gui.powerfactor.fission.rods_in", 1), CYAN, false,
                () -> send(Command.RODS, col, Math.min(1, rod + 0.01F))));
        buttons.add(new Btn(x + 294, y + 100, 18, 14, Component.literal("▼▼"), Component.translatable("gui.powerfactor.fission.rods_in", 10), CYAN, false,
                () -> send(Command.RODS, col, Math.min(1, rod + 0.10F))));
        // Purge (to change coolant): only once shut down and cool, guarded like SCRAM.
        boolean cool = scrammed && v(Snapshot.TEMP) <= 450;
        buttons.add(new Btn(x + 276, y + 135, 36, 10, Component.translatable(purgeArmed ? "gui.powerfactor.fission.purge_now" : "gui.powerfactor.fission.purge"),
                Component.translatable(cool ? "gui.powerfactor.fission.purge.tip" : "gui.powerfactor.fission.purge.locked"), cool ? AMBER : Skin.LCD_DIM, purgeArmed, () -> {
                    if (!cool) return;
                    if (purgeArmed) {
                        send(Command.PURGE, 0, 0);
                        purgeArmed = false;
                    } else {
                        purgeArmed = true;
                    }
                }));
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        // Rod readout under the drive buttons.
        float rod = v(Snapshot.ROD_MEAN);
        Component which = selected >= 0 ? Component.translatable("gui.powerfactor.fission.rods_column", selected + 1)
                : Component.translatable("gui.powerfactor.fission.rods_all");
        Component pct = Component.literal(Math.round(rod * 100) + "%");
        Skin.condensed(g, font, which, 236, 118, 74 - font.width(pct), Skin.TEXT);
        g.text(font, pct, 312 - font.width(pct), 118, Skin.DIM, false);
        Skin.progress(g, 236, 127, 76, rod, 0xFF39424A);
        // Coolant in use, beside the purge button.
        Skin.icon(g, Skin.Icon.FLUID, 236, 136, null);
        g.fill(246, 135, 265, 145, 0xFF39424A);
        g.fill(247, 136, 255, 144, coolColor(coolant()));
        g.fill(256, 136, 264, 144, hotColor(coolant()));
    }

    // ---------------------------------------------------------------- annunciator and trips

    private void annunciator(GuiGraphicsExtractor g, @Nullable Snapshot s, int x, int y, long t) {
        var alarms = ReactorControllerBlockEntity.Alarm.values();
        for (int i = 0; i < alarms.length; i++) {
            int tx = x + (i % 5) * 48, ty = y + (i / 5) * 18;
            boolean on = s != null && (s.alarms() & alarms[i].bit()) != 0, unacked = s != null && (s.unacked() & alarms[i].bit()) != 0;
            // SCRAMMED and FUEL LOW are states, not alarms: they glow steadily in amber.
            boolean status = alarms[i] == ReactorControllerBlockEntity.Alarm.SCRAMMED || alarms[i] == ReactorControllerBlockEntity.Alarm.FUEL_LOW;
            boolean lit = on && (status || !unacked || t / 300 % 2 == 0);
            int col = status ? AMBER : RED;
            g.fill(tx, ty, tx + 46, ty + 16, lit ? col : 0xFF20282E);
            g.fill(tx + 1, ty + 1, tx + 45, ty + 15, lit ? com.powerfactor.client.fx.Fx.mix(col, 0xFFFFFFFF, 0.25F) : 0xFF2C363E);
            Component label = Component.translatable("gui.powerfactor.fission.tile." + alarms[i].name().toLowerCase(java.util.Locale.ROOT));
            g.centeredText(font, label, tx + 23, ty + 4, lit ? 0xFF1A1010 : 0xFF5B7080);
        }
    }

    private void trips(GuiGraphicsExtractor g, int x, int y) {
        Skin.icon(g, Skin.Icon.TEMP, x, y + 3, Component.translatable("gui.powerfactor.fission.trip_temp"));
        float tt = v(Snapshot.TRIP_TEMP), tp = v(Snapshot.TRIP_POWER);
        g.text(font, Component.literal(Math.round(tt) + "K"), x + 11, y + 4, Skin.TEXT, false);
        buttons.add(new Btn(x + 38, y, 11, 14, Component.literal("−"), Component.translatable("gui.powerfactor.fission.trip_temp"), CYAN, false,
                () -> send(Command.TRIP_TEMP, 0, tt - 50)));
        buttons.add(new Btn(x + 50, y, 11, 14, Component.literal("+"), Component.translatable("gui.powerfactor.fission.trip_temp"), CYAN, false,
                () -> send(Command.TRIP_TEMP, 0, tt + 50)));
        Skin.icon(g, Skin.Icon.POWER, x, y + 21, Component.translatable("gui.powerfactor.fission.trip_power"));
        g.text(font, Component.literal(Math.round(tp * 100) + "%"), x + 11, y + 22, Skin.TEXT, false);
        buttons.add(new Btn(x + 38, y + 18, 11, 14, Component.literal("−"), Component.translatable("gui.powerfactor.fission.trip_power"), CYAN, false,
                () -> send(Command.TRIP_POWER, 0, tp - 0.05F)));
        buttons.add(new Btn(x + 50, y + 18, 11, 14, Component.literal("+"), Component.translatable("gui.powerfactor.fission.trip_power"), CYAN, false,
                () -> send(Command.TRIP_POWER, 0, tp + 0.05F)));
    }

    // ---------------------------------------------------------------- trend

    private void trend(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        Skin.lcd(g, x, y, w, h);
        for (int k = 1; k < 4; k++) g.fill(x + 2, y + k * h / 4, x + w - 2, y + k * h / 4 + 1, 0x30FFFFFF);
        float[][] pts = HISTORY.toArray(new float[0][]);
        if (pts.length < 2) return;
        // Power (0..120%), temperature (300..1300 K), reactivity (±600 pcm about the middle).
        series(g, pts, 0, x, y, w, h, 0, 1.2F, GREEN);
        series(g, pts, 1, x, y, w, h, 300, 1300, AMBER);
        series(g, pts, 2, x, y, w, h, -0.006F, 0.006F, CYAN);
        Skin.icon(g, Skin.Icon.POWER, x + 3, y + 3, Component.translatable("gui.powerfactor.fission.trend.power"));
        Skin.icon(g, Skin.Icon.TEMP, x + 14, y + 3, Component.translatable("gui.powerfactor.fission.trend.temp"));
        Skin.icon(g, Skin.Icon.PF, x + 25, y + 3, Component.translatable("gui.powerfactor.fission.trend.reactivity"));
    }

    private static void series(GuiGraphicsExtractor g, float[][] pts, int k, int x, int y, int w, int h, float lo, float hi, int color) {
        int n = pts.length;
        int prevX = -1, prevY = -1;
        for (int i = 0; i < n; i++) {
            int px = x + 2 + (int) ((w - 4) * (i + 240 - n) / 239F);
            float f = Mth.clamp((pts[i][k] - lo) / (hi - lo), 0, 1);
            int py = y + h - 3 - (int) ((h - 6) * f);
            if (prevX >= 0) {
                int y0 = Math.min(prevY, py), y1 = Math.max(prevY, py);
                g.fill(prevX, y0, px + 1, y1 + 1, color);
            }
            prevX = px;
            prevY = py;
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (Btn b : List.copyOf(buttons)) {
            if (event.x() >= b.x && event.x() < b.x + b.w && event.y() >= b.y && event.y() < b.y + b.h) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.4F, 0.35F));
                b.action.run();
                return true;
            }
        }
        int col = columnAt(event.x(), event.y());
        if (col != -2) {
            selected = col == selected ? -1 : col;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        for (Btn b : buttons) {
            if (mx >= b.x && mx < b.x + b.w && my >= b.y && my < b.y + b.h) {
                g.setTooltipForNextFrame(font, b.tip, mx, my);
                break;
            }
        }
    }
}
