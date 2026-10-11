package com.powerfactor.client;

import com.powerfactor.item.ClampMeterItem;
import com.powerfactor.item.ToolPayloads;
import com.powerfactor.registry.ModSounds;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * The clamp meter's readout: while it's held, whatever the crosshair is on gets measured twice a
 * second and shown beside the crosshair, with a phasor diagram of FE (real) against EE (reactive).
 */
public final class ClampMeterHud {
    private static ToolPayloads.@Nullable MeterReading reading;
    private static @Nullable BlockPos target;
    private static int age, ticks;

    public static void onReading(ToolPayloads.MeterReading r) {
        reading = r;
        age = 0;
    }

    private static boolean holding() {
        var p = Minecraft.getInstance().player;
        return p != null && (p.getMainHandItem().getItem() instanceof ClampMeterItem || p.getOffhandItem().getItem() instanceof ClampMeterItem);
    }

    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        age++;
        if (!holding() || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            target = null;
            return;
        }
        BlockPos pos = hit.getBlockPos();
        if (!pos.equals(target)) {
            target = pos.immutable();
            ticks = 0;
            if (mc.player != null) {
                mc.player.playSound(ModSounds.METER_BEEP.get(), 0.25F, 1.0F);
            }
        }
        if (ticks++ % 10 == 0) {
            ClientPacketDistributor.sendToServer(new ToolPayloads.MeterRequest(pos));
        }
    }

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        ToolPayloads.MeterReading r = reading;
        if (target == null || r == null || !r.pos().equals(target) || age > 40) {
            return;
        }
        var font = mc.font;
        int width = Math.max(110, font.width(r.title()) + 8);
        for (Component line : r.lines()) {
            width = Math.max(width, font.width(line) + 8);
        }
        boolean phasor = r.volts() >= 0 || r.fe() != 0 || r.ee() != 0;
        int panelW = width + (phasor ? 48 : 0), panelH = 16 + r.lines().size() * 10 + (phasor ? 12 : 0);
        panelH = Math.max(panelH, phasor ? 58 : 0);
        int x = g.guiWidth() / 2 + 14, y = g.guiHeight() / 2 - panelH / 2;
        int accent = switch (r.status()) {
            case 1 -> 0xFF55FF55;
            case 2 -> 0xFFFFB020;
            case 3 -> 0xFFFF4A3A;
            default -> 0xFF9AA3AB;
        };
        g.fill(x, y, x + panelW, y + panelH, 0xC0101418);
        g.fill(x, y, x + 2, y + panelH, accent);
        g.text(font, r.title(), x + 6, y + 4, 0xFFFFD27A, false);
        int ly = y + 16;
        for (Component line : r.lines()) {
            g.text(font, line, x + 6, ly, 0xFFE6E6E6, false);
            ly += 10;
        }
        if (!phasor) {
            return;
        }
        double ce = Math.hypot(r.fe(), r.ee());
        String totals = String.format("%d FE  %d EE  %d CE", Math.round(r.fe()), Math.round(r.ee()), Math.round(ce));
        g.text(font, Component.literal(totals), x + 6, ly + 1, 0xFFB8C4CC, false);
        // Phasor: FE along +x, EE up for lagging (inductive), down for leading.
        int cx = x + width + 8, cy = y + panelH / 2, rad = 20;
        g.fill(cx - 2, cy, cx + rad + 2, cy + 1, 0xFF3A4248);
        g.fill(cx, cy - rad - 2, cx + 1, cy + rad + 2, 0xFF3A4248);
        if (ce > 0.5) {
            double scale = rad / ce;
            int ex = cx + (int) Math.round(r.fe() * scale), ey = cy - (int) Math.round(r.ee() * scale);
            line(g, cx, cy, ex, cy, 0xFFFF5A4A);
            line(g, ex, cy, ex, ey, 0xFF4FD8FF);
            line(g, cx, cy, ex, ey, 0xFFFFFFFF);
            double pf = r.fe() / ce;
            g.text(font, Component.literal(String.format("PF %.2f", pf)), cx - 4, y + panelH - 10, pf > 0.9 ? 0xFF55FF55 : pf > 0.7 ? 0xFFFFB020
                    : 0xFFFF4A3A, false);
        }
    }

    private static void line(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i++) {
            int x = steps == 0 ? x0 : Math.round(Mth.lerp((float) i / steps, x0, x1));
            int y = steps == 0 ? y0 : Math.round(Mth.lerp((float) i / steps, y0, y1));
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    private ClampMeterHud() {}
}
