package com.powerfactor.client.blackhole;

import com.powerfactor.blackhole.BlackHoleBlockEntity;
import com.powerfactor.blackhole.BlackHoleCore;
import com.powerfactor.blackhole.BlackHoleMenu;
import com.powerfactor.blackhole.BlackHolePayloads;
import com.powerfactor.client.Skin;
import com.powerfactor.client.fx.Fx;
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
 * Black Hole console. Left, the hole itself: its shadow sized by mass, the photon ring, the accretion disk
 * turning. Right, the instruments, and under them the plant's one law drawn as a curve: net power against
 * mass (small holes blaze but need feeding hard), with a marker where the hole sits now. Below, the matter
 * hopper and the controls.
 */
public class BlackHoleScreen extends AbstractContainerScreen<BlackHoleMenu> {
    public static final int W = 256, H = 236;
    private static double @Nullable [] latest;
    private static net.minecraft.core.@Nullable BlockPos latestPos;

    private record Btn(int x, int y, int w, int h, Component label, Component tip, int color, boolean on, Runnable action) {}

    private final List<Btn> buttons = new ArrayList<>();

    public BlackHoleScreen(BlackHoleMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, W, H);
    }

    public static void accept(BlackHolePayloads.Snapshot s) {
        latest = s.v();
        latestPos = s.pos();
    }

    private double v(int i) {
        return latest == null || !menu.pos.equals(latestPos) ? 0 : latest[i];
    }

    private void send(int op, double value) {
        ClientPacketDistributor.sendToServer(new BlackHolePayloads.Command(menu.pos, op, value));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractBackground(g, mx, my, a);
        buttons.clear();
        int x = leftPos, y = topPos;
        Skin.panel(g, x, y, W, H);
        Skin.title(g, font, title, x, y, W);
        hero(g, x + 8, y + 20, 120, 90);
        readouts(g, x + 134, y + 20, 114, 58);
        curve(g, x + 134, y + 81, 114, 29);
        for (var s : menu.slots) {
            if (s.container instanceof Inventory) continue;
            Skin.slot(g, x + s.x - 1, y + s.y - 1, Skin.INPUT);
        }
        controls(x, y);
        Skin.inventory(g, font, x + 8, y + 154);
        for (Btn b : buttons) {
            boolean hover = mx >= b.x && mx < b.x + b.w && my >= b.y && my < b.y + b.h;
            Skin.control(g, b.x, b.y, b.w, b.h, b.on, hover);
            Skin.condensed(g, font, b.label, b.x + (b.w - Math.min(b.w - 4, font.width(b.label))) / 2, b.y + (b.h - 8) / 2, b.w - 4, b.color);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {}

    private void hero(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF05060A);
        long t = Util.getMillis();
        // A few stars, bent toward the hole.
        java.util.Random r = new java.util.Random(7);
        int cx = x + w / 2, cy = y + h / 2;
        int state = (int) v(BlackHolePayloads.STATE);
        double mass = v(BlackHolePayloads.MASS);
        int rad = state == BlackHoleBlockEntity.STATE_RUNNING ? (int) Math.min(17, 5 + 12 * Math.cbrt(mass / 1000)) : 0;
        for (int i = 0; i < 40; i++) {
            int sx = x + r.nextInt(w), sy = y + r.nextInt(h);
            double dx = sx - cx, dy = sy - cy, d = Math.max(1, Math.hypot(dx, dy));
            double pull = rad > 0 ? Math.min(0.5, rad * rad / (d * d) * 2) : 0;
            g.fill((int) (sx - dx * pull), (int) (sy - dy * pull), (int) (sx - dx * pull) + 1, (int) (sy - dy * pull) + 1, 0xFF8FA6B5);
        }
        if (state == BlackHoleBlockEntity.STATE_IGNITING) {
            double f = v(BlackHolePayloads.IGNITION);
            int gr = (int) (2 + 10 * f);
            disc(g, cx, cy, gr, Fx.alpha(0xFFB18CFF, 0.5F + 0.5F * (float) Math.sin(t / 120.0)));
            Skin.progress(g, x + 10, y + h - 10, w - 20, (float) f, 0xFFB18CFF);
            return;
        }
        if (state != BlackHoleBlockEntity.STATE_RUNNING) {
            // Dormant: an empty dashed cage where the hole would hang, and the hint.
            for (int k = 0; k < 48; k += 2) {
                double ang = k * Math.PI * 2 / 48;
                g.fill(cx + (int) (Math.cos(ang) * 14), cy + (int) (Math.sin(ang) * 14), cx + (int) (Math.cos(ang) * 14) + 1,
                        cy + (int) (Math.sin(ang) * 14) + 1, 0xFF4A3A6A);
            }
            var lines = font.split(Component.translatable("gui.powerfactor.black_hole.dormant"), w - 12);
            for (int i = 0; i < lines.size() && i < 3; i++) g.centeredText(font, lines.get(i), cx, y + h - 30 + i * 9, 0xFF8FA6B5);
            return;
        }
        // Accretion disk (an ellipse of dots, spinning), photon ring, then the shadow on top.
        double spin = t / 300.0;
        for (int ring = 0; ring < 6; ring++) {
            double rr = rad * (1.5 + ring * 0.22);
            int col = ring < 2 ? 0xFFFFF0B0 : ring < 4 ? 0xFFFFB347 : 0xFFC0301A;
            int n = (int) (rr * 4);
            for (int k = 0; k < n; k++) {
                double ang = k * Math.PI * 2 / n + spin / (0.5 + ring * 0.3);
                int px = cx + (int) Math.round(Math.cos(ang) * rr), py = cy + (int) Math.round(Math.sin(ang) * rr * 0.28);
                if (Math.sin(ang) < 0 && Math.hypot(px - cx, py - cy) < rad + 1) continue;   // the far side hides behind the hole
                g.fill(px, py, px + 1, py + 1, col);
            }
        }
        disc(g, cx, cy, rad + 2, 0xFFFFF4D6);
        disc(g, cx, cy, rad, 0xFF000000);
        // The near side of the disk crosses in front of the shadow.
        for (int k = 0; k < rad * 6; k++) {
            double ang = Math.PI * k / (rad * 6) + 0.0;
            int px = cx + (int) Math.round(Math.cos(ang) * rad * 1.6), py = cy + (int) Math.round(Math.sin(ang) * rad * 1.6 * 0.28);
            g.fill(px, py, px + 1, py + 1, 0xFFFFE07A);
        }
    }

    private static void disc(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt(r * r - dy * dy);
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    private void readouts(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        Skin.lcd(g, x, y, w, h);
        double net = v(BlackHolePayloads.NET);
        Skin.lcdRows(g, font, x, y, w, h, List.of(
                new Skin.Row(Skin.Icon.MATTER, Component.literal(String.format("%,.0f", v(BlackHolePayloads.MASS))), 0xFFE8DCFF,
                        Component.translatable("gui.powerfactor.black_hole.point.mass")),
                new Skin.Row(Skin.Icon.HEAT, Component.literal(Skin.compact(v(BlackHolePayloads.HAWKING)) + " FE/t"), 0xFFFFE07A,
                        Component.translatable("gui.powerfactor.black_hole.point.hawking")),
                new Skin.Row(Skin.Icon.INPUT, Component.literal(Skin.compact(v(BlackHolePayloads.ACCRETION)) + " FE/t"), 0xFFFFB347,
                        Component.translatable("gui.powerfactor.black_hole.point.accretion")),
                new Skin.Row(Skin.Icon.SHIELD, Component.literal("−" + Skin.compact(v(BlackHolePayloads.CONTAINMENT)) + " FE/t"), 0xFF6FE3FF,
                        Component.translatable("gui.powerfactor.black_hole.point.containment")),
                new Skin.Row(Skin.Icon.FE, Component.literal(Skin.compact(net) + " FE/t"), net >= 0 ? 0xFF7CFF8A : 0xFFFF4A3A,
                        Component.translatable("gui.powerfactor.black_hole.point.net"))));
    }

    /** Net power against mass, log-log, with the hole's position marked; holding cost (feed) shades the curve. */
    private void curve(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        Skin.lcd(g, x, y, w, h);
        double lo = Math.log10(100), hi = Math.log10(20_000);
        int prevY = -1;
        for (int i = 0; i < w - 4; i++) {
            double m = Math.pow(10, lo + (hi - lo) * i / (w - 5));
            double haw = BlackHoleCore.HAWKING / (m * m);
            double hold = haw / BlackHoleCore.C2 / (1 - BlackHoleCore.ACCRETION_EFFICIENCY);
            double net = haw + hold * BlackHoleCore.C2 * BlackHoleCore.ACCRETION_EFFICIENCY - BlackHoleCore.CONTAIN_PER_MASS * m;
            double f = net <= 0 ? 0 : Mth.clamp((Math.log10(net) - 4) / 5, 0, 1);
            int py = y + h - 3 - (int) ((h - 6) * f);
            int col = net <= 0 ? 0xFFFF4A3A : Fx.mix(0xFF7CFF8A, 0xFFFFB347, (float) Mth.clamp(hold * 20 / 10, 0, 1));
            if (prevY >= 0) g.fill(x + 2 + i, Math.min(prevY, py), x + 3 + i, Math.max(prevY, py) + 1, col);
            prevY = py;
        }
        double mass = v(BlackHolePayloads.MASS);
        if (mass > 0) {
            int mx = x + 2 + (int) ((w - 5) * Mth.clamp((Math.log10(mass) - lo) / (hi - lo), 0, 1));
            g.fill(mx, y + 2, mx + 1, y + h - 2, 0xFFFFFFFF);
        }
        double target = v(BlackHolePayloads.TARGET);
        int tx = x + 2 + (int) ((w - 5) * Mth.clamp((Math.log10(Math.max(1, target)) - lo) / (hi - lo), 0, 1));
        g.fill(tx, y + 2, tx + 1, y + 5, 0xFF6FE3FF);
    }

    private void controls(int x, int y) {
        int state = (int) v(BlackHolePayloads.STATE);
        boolean auto = v(BlackHolePayloads.AUTO) > 0;
        int bx = x + 174, by = y + 115;
        buttons.add(new Btn(bx, by, 74, 16, Component.translatable("gui.powerfactor.black_hole.ignite"),
                Component.translatable("gui.powerfactor.black_hole.ignite.tip"), state == BlackHoleBlockEntity.STATE_DORMANT ? 0xFFB18CFF : Skin.LCD_DIM, false,
                () -> send(BlackHolePayloads.IGNITE, 0)));
        buttons.add(new Btn(x + 8, y + 136, 40, 14, Component.translatable("gui.powerfactor.black_hole.auto"),
                Component.translatable("gui.powerfactor.black_hole.auto.tip", String.format("%.1f", v(BlackHolePayloads.HOLD))), auto ? 0xFF7CFF8A : Skin.LCD_DIM,
                auto, () -> send(BlackHolePayloads.SET_AUTO, auto ? 0 : 1)));
        double target = v(BlackHolePayloads.TARGET), rate = v(BlackHolePayloads.RATE);
        // Target mass (auto) or feed rate (manual), stepped by ×/÷ 1.25 or ±1.
        Component value = auto ? Component.translatable("gui.powerfactor.black_hole.target_value", String.format("%,.0f", target))
                : Component.translatable("gui.powerfactor.black_hole.rate_value", (int) rate);
        buttons.add(new Btn(x + 52, y + 136, 14, 14, Component.literal("−"), Component.translatable(auto ? "gui.powerfactor.black_hole.target" : "gui.powerfactor.black_hole.rate"),
                0xFF6FE3FF, false, () -> {
                    if (auto) send(BlackHolePayloads.SET_TARGET, target / 1.25);
                    else send(BlackHolePayloads.SET_RATE, rate - 1);
                }));
        valueText = value;
        buttons.add(new Btn(x + 156, y + 136, 14, 14, Component.literal("+"), Component.translatable(auto ? "gui.powerfactor.black_hole.target" : "gui.powerfactor.black_hole.rate"),
                0xFF6FE3FF, false, () -> {
                    if (auto) send(BlackHolePayloads.SET_TARGET, target * 1.25);
                    else send(BlackHolePayloads.SET_RATE, rate + 1);
                }));
        // Feed in vs. what holding the mass needs.
        double feed = v(BlackHolePayloads.FEED), hold = v(BlackHolePayloads.HOLD);
        Component fl = Component.translatable("gui.powerfactor.black_hole.feed", String.format("%.1f", feed), String.format("%.1f", hold));
        stored = (long) v(BlackHolePayloads.STORED);
        feedLine = fl;
        feedShort = feed + 0.05 < hold;
    }

    private long stored;
    private Component valueText = Component.empty();
    private Component feedLine = Component.empty();
    private boolean feedShort;

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        int x = leftPos, y = topPos;
        Skin.lcd(g, x + 68, y + 136, 86, 14);
        g.centeredText(font, valueText, x + 111, y + 139, Skin.LCD_TEXT);
        Skin.condensed(g, font, feedLine, x + 174, y + 140, 74, feedShort ? 0xFFFF4A3A : Skin.TEXT);
        Skin.bar(g, x + 174, y + 133, 74, stored / (float) BlackHoleBlockEntity.CAPACITY, Skin.FE);
        for (Btn b : buttons) {
            if (mx >= b.x && mx < b.x + b.w && my >= b.y && my < b.y + b.h) {
                g.setTooltipForNextFrame(font, b.tip, mx, my);
                break;
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (Btn b : List.copyOf(buttons)) {
            if (event.x() >= b.x && event.x() < b.x + b.w && event.y() >= b.y && event.y() < b.y + b.h) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.4F, 0.35F));
                b.action.run();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
