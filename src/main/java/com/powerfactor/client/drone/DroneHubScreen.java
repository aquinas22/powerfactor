package com.powerfactor.client.drone;

import com.powerfactor.client.Skin;
import com.powerfactor.client.fx.Fx;
import com.powerfactor.drone.DroneHubBlockEntity;
import com.powerfactor.drone.DroneHubMenu;
import com.powerfactor.drone.DronePayloads;
import com.powerfactor.drone.MiningDroneEntity;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/**
 * Drone Hub console. Left, the AI's map of the ground: the hub at the centre, every vein it has found as a
 * square in its ore's colour (ringed when claimed, crossed red near lava), each drone a coloured dot with a
 * trail, and a radar sweep while it surveys. Right, the AI's log, newest at the bottom. Below, the drone
 * bay, the haul and the stats.
 */
public class DroneHubScreen extends AbstractContainerScreen<DroneHubMenu> {
    public static final int W = 256, H = 240, MAP = 96;
    private static final int[] ACCENT = {0xFF6FE3FF, 0xFF7CFF8A, 0xFFFFD84A, 0xFFB18CFF};
    private static @Nullable CompoundTag latest;
    private static net.minecraft.core.@Nullable BlockPos latestPos;
    private static final Map<Integer, ArrayDeque<int[]>> TRAILS = new HashMap<>();

    public DroneHubScreen(DroneHubMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, W, H);
    }

    public static void accept(DronePayloads.Snapshot s) {
        latest = s.data();
        latestPos = s.pos();
        ListTag drones = latest.getListOrEmpty("drones");
        for (int i = 0; i < drones.size(); i++) {
            CompoundTag d = drones.getCompoundOrEmpty(i);
            var trail = TRAILS.computeIfAbsent(d.getIntOr("i", 0), k -> new ArrayDeque<>());
            trail.addLast(new int[] {Math.round(d.getFloatOr("x", 0) * 2), Math.round(d.getFloatOr("z", 0) * 2)});
            while (trail.size() > 24) trail.removeFirst();
        }
    }

    private @Nullable CompoundTag data() {
        return latest != null && menu.pos.equals(latestPos) ? latest : null;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractBackground(g, mx, my, a);
        int x = leftPos, y = topPos;
        boolean bay = menu.slots.stream().anyMatch(sl -> sl instanceof com.powerfactor.machine.MachineMenu.BaySlot);
        if (bay) Skin.panel(g, x + W - 4, y + 8, 32, 4 * 18 + 22);
        Skin.panel(g, x, y, W, H);
        if (bay) Skin.icon(g, Skin.Icon.UPGRADE, x + W + 9, y + 12, Component.translatable("gui.powerfactor.upgrades.bay"));
        Skin.title(g, font, title, x, y, W);
        for (var s : menu.slots) {
            if (s.container instanceof Inventory) continue;
            int ring = s instanceof com.powerfactor.machine.MachineMenu.BaySlot ? 0xFF9C74F0 : s.index < DroneHubBlockEntity.BAY ? Skin.INPUT : Skin.OUTPUT;
            Skin.slot(g, x + s.x - 1, y + s.y - 1, ring);
        }
        CompoundTag d = data();
        map(g, d, x + 8, y + 20, mx, my);
        log(g, d, x + 108, y + 20, 140, MAP);
        stats(g, d, x + 8, y + 141);
        Skin.inventory(g, font, x + 8, y + 164);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {}

    private void map(GuiGraphicsExtractor g, @Nullable CompoundTag d, int x, int y, int mx, int my) {
        g.fill(x, y, x + MAP, y + MAP, 0xFF07110C);
        int cx = x + MAP / 2, cy = y + MAP / 2;
        for (int r = 12; r <= 48; r += 12) ring(g, cx, cy, r, 0xFF16301F);
        g.fill(cx - 1, y + 1, cx, y + MAP - 1, 0xFF12261A);
        g.fill(x + 1, cy - 1, x + MAP - 1, cy, 0xFF12261A);
        if (d == null) return;
        long t = Util.getMillis();
        float survey = d.getFloatOr("survey", 1);
        if (survey < 1) {
            double a = (t / 600.0) % (Math.PI * 2);
            for (int k = 0; k < 46; k++) {
                for (int trail = 0; trail < 8; trail++) {
                    double aa = a - trail * 0.06;
                    int px = cx + (int) (Math.cos(aa) * k), py = cy + (int) (Math.sin(aa) * k);
                    g.fill(px, py, px + 1, py + 1, Fx.alpha(0xFF7CFF8A, 0.6F - trail * 0.07F));
                }
            }
        }
        ListTag veins = d.getListOrEmpty("veins");
        Component tip = null;
        for (int i = 0; i < veins.size(); i++) {
            CompoundTag v = veins.getCompoundOrEmpty(i);
            int px = cx + v.getIntOr("x", 0) * 2, py = cy + v.getIntOr("z", 0) * 2;
            int size = Math.min(4, 1 + v.getIntOr("n", 1) / 3);
            int col = 0xFF000000 | v.getIntOr("c", 0x888888);
            g.fill(px - size / 2, py - size / 2, px + size / 2 + 1, py + size / 2 + 1, col);
            if (v.getBooleanOr("a", false)) ring(g, px, py, size + 1, 0xFFFFFFFF);
            if (v.getBooleanOr("h", false)) {
                g.fill(px - 2, py - 2, px - 1, py - 1, 0xFFFF4A3A);
                g.fill(px + 2, py + 2, px + 3, py + 3, 0xFFFF4A3A);
            }
            if (Math.abs(mx - px) <= 2 && Math.abs(my - py) <= 2) {
                tip = Component.translatable("gui.powerfactor.drone.vein", v.getIntOr("n", 0), v.getIntOr("v", 0), v.getIntOr("x", 0), v.getIntOr("y", 0),
                        v.getIntOr("z", 0));
            }
        }
        for (var e : TRAILS.entrySet()) {
            int i = 0, n = e.getValue().size();
            for (int[] p : e.getValue()) {
                g.fill(cx + p[0], cy + p[1], cx + p[0] + 1, cy + p[1] + 1, Fx.alpha(ACCENT[e.getKey() & 3], 0.15F + 0.5F * i++ / n));
            }
        }
        ListTag drones = d.getListOrEmpty("drones");
        for (int i = 0; i < drones.size(); i++) {
            CompoundTag dr = drones.getCompoundOrEmpty(i);
            int px = cx + Math.round(dr.getFloatOr("x", 0) * 2), py = cy + Math.round(dr.getFloatOr("z", 0) * 2);
            int col = ACCENT[dr.getIntOr("i", 0) & 3];
            g.fill(px - 1, py - 1, px + 2, py + 2, col);
            if (dr.getIntOr("s", 0) == MiningDroneEntity.STATE_MINING && (t / 150) % 2 == 0) ring(g, px, py, 3, 0xFFFFB04A);
        }
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFFE8F0F4);
        if (tip != null) g.setTooltipForNextFrame(font, tip, mx, my);
    }

    private static void ring(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
        int n = Math.max(8, r * 6);
        for (int k = 0; k < n; k++) {
            double a = k * Math.PI * 2 / n;
            int px = cx + (int) Math.round(Math.cos(a) * r), py = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(px, py, px + 1, py + 1, color);
        }
    }

    /** The AI's log: the newest lines that fit, wrapped, bottom-aligned. */
    private void log(GuiGraphicsExtractor g, @Nullable CompoundTag d, int x, int y, int w, int h) {
        Skin.lcd(g, x, y, w, h);
        Skin.icon(g, Skin.Icon.NETWORK, x + 3, y + 3, Component.translatable("gui.powerfactor.drone.log"));
        g.text(font, Component.translatable("gui.powerfactor.drone.log_title"), x + 14, y + 4, Skin.LCD_DIM, false);
        if (d == null) return;
        ListTag log = d.getListOrEmpty("log");
        float scale = 0.75F;
        int lineH = 7, maxW = (int) ((w - 6) / scale), top = y + 14, bottom = y + h - 3;
        int cursor = bottom;
        g.pose().pushMatrix();
        for (int i = log.size() - 1; i >= 0 && cursor > top; i--) {
            CompoundTag e = log.getCompoundOrEmpty(i);
            ListTag args = e.getListOrEmpty("a");
            Object[] a = new Object[args.size()];
            for (int k = 0; k < a.length; k++) a[k] = args.getStringOr(k, "");
            List<FormattedCharSequence> lines = font.split(Component.translatable(e.getStringOr("k", ""), a), maxW);
            for (int l = lines.size() - 1; l >= 0 && cursor - lineH >= top; l--) {
                cursor -= lineH;
                g.pose().pushMatrix();
                g.pose().translate(x + 3, cursor);
                g.pose().scale(scale, scale);
                g.text(font, lines.get(l), 0, 0, e.getIntOr("c", Skin.LCD_TEXT), false);
                g.pose().popMatrix();
            }
        }
        g.pose().popMatrix();
    }

    private void stats(GuiGraphicsExtractor g, @Nullable CompoundTag d, int x, int y) {
        int energy = d == null ? 0 : d.getIntOr("energy", 0), mined = d == null ? 0 : d.getIntOr("mined", 0);
        int out = d == null ? 0 : d.getListOrEmpty("drones").size(), veins = d == null ? 0 : d.getListOrEmpty("veins").size();
        float survey = d == null ? 1 : d.getFloatOr("survey", 1);
        int cx = Skin.iconValue(g, font, Skin.Icon.FE, Component.literal(Skin.compact(energy)), x, y + 2, Skin.TEXT,
                Component.translatable("gui.powerfactor.buffer", String.format("%,d", energy), String.format("%,d", DroneHubBlockEntity.CAPACITY)));
        cx = Skin.iconValue(g, font, Skin.Icon.ROTOR, Component.literal(String.valueOf(out)), cx + 8, y + 2, Skin.TEXT,
                Component.translatable("gui.powerfactor.drone.out", out));
        cx = Skin.iconValue(g, font, Skin.Icon.DEPTH, Component.literal(String.valueOf(veins)), cx + 8, y + 2, Skin.TEXT,
                Component.translatable("gui.powerfactor.drone.veins", veins));
        cx = Skin.iconValue(g, font, Skin.Icon.MINED, Component.literal(String.valueOf(mined)), cx + 8, y + 2, Skin.TEXT,
                Component.translatable("gui.powerfactor.drone.mined", mined));
        if (survey < 1) {
            Skin.iconValue(g, font, Skin.Icon.RANGE, Component.literal(Math.round(survey * 100) + "%"), cx + 8, y + 2, 0xFF2E8A4A,
                    Component.translatable("gui.powerfactor.drone.surveying"));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        extractTooltip(g, mx, my);
    }
}
