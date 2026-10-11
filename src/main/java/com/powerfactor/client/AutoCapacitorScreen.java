package com.powerfactor.client;

import com.powerfactor.machine.AutoCapacitorBankBlockEntity;
import com.powerfactor.machine.AutoCapacitorMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Controller face on the left (six capacitor steps that light as they switch in, a power-factor
 * meter, and a countdown to the next switching decision), live grid analyzer on the right.
 */
public class AutoCapacitorScreen extends AbstractContainerScreen<AutoCapacitorMenu> {
    private static final int LEFT_W = 176, H = ElectricalPanel.HEIGHT;
    private static final int BG = 0xFF0A100D, TEXT = 0xFFB8F5C0, DIM = 0xFF5F8A66;
    private final ElectricalPanel panel = new ElectricalPanel();
    private final float[] glow = new float[AutoCapacitorBankBlockEntity.STEPS];
    private double needle = 1.0;
    private long lastNanos = System.nanoTime();

    public AutoCapacitorScreen(AutoCapacitorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, LEFT_W + 2 + ElectricalPanel.WIDTH, H);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        long now = System.nanoTime();
        float dt = Math.min(0.1F, (now - lastNanos) / 1e9F);
        lastNanos = now;

        int x = leftPos, y = topPos;
        g.fill(x, y, x + LEFT_W, y + H, 0xFF2B3036);
        g.fill(x + 1, y + 1, x + LEFT_W - 1, y + H - 1, 0xFF6F767D);
        g.fill(x + 2, y + 2, x + LEFT_W - 2, y + H - 2, BG);

        // Capacitor steps: glow fades in and out as contactors close and open.
        int steps = menu.steps();
        for (int i = 0; i < glow.length; i++) {
            float target = i < steps ? 1 : 0;
            glow[i] += (target - glow[i]) * Math.min(1, dt * 6);
            int cx = x + 10 + i * 27, cy = y + 20;
            int body = lerpColor(0xFF163A30, 0xFF2FD6A0, glow[i]);
            g.fill(cx, cy + 4, cx + 20, cy + 36, 0xFF0E1512);
            g.fill(cx + 1, cy + 5, cx + 19, cy + 35, body);
            g.fill(cx + 4, cy + 6, cx + 6, cy + 34, lerpColor(0xFF1E4A3D, 0xFFB8FFE8, glow[i]));
            g.fill(cx + 2, cy, cx + 18, cy + 5, 0xFFB9C2C9);
            g.fill(cx + 5, cy - 2, cx + 7, cy, 0xFF8A9298);
            g.fill(cx + 13, cy - 2, cx + 15, cy, 0xFF8A9298);
            int lamp = glow[i] > 0.5F ? 0xFF55FF55 : 0xFF2A3A2F;
            g.fill(cx + 8, cy + 39, cx + 12, cy + 43, lamp);
        }

        // Power factor meter: lagging on the left, unity in the middle, leading on the right.
        int mx = x + 10, my = y + 74, mw = LEFT_W - 20;
        g.fill(mx, my, mx + mw, my + 10, 0xFF060A08);
        zone(g, mx, my, mw, 0.0, 0.3, 0xFF5A1C1C);
        zone(g, mx, my, mw, 0.3, 0.42, 0xFF5A4A1C);
        zone(g, mx, my, mw, 0.42, 0.58, 0xFF1C5A2A);
        zone(g, mx, my, mw, 0.58, 0.7, 0xFF5A4A1C);
        zone(g, mx, my, mw, 0.7, 1.0, 0xFF5A1C1C);
        double ce = Math.hypot(menu.fe(), menu.ee());
        double pf = ce < 0.5 ? 1 : menu.fe() / ce;
        // Map PF 0.5 lag .. 1.0 .. 0.5 lead onto 0 .. 0.5 .. 1.
        double pos = 0.5 + (menu.ee() >= 0 ? -1 : 1) * (1 - pf);
        needle += (Math.clamp(pos, 0, 1) - needle) * Math.min(1, dt * 5);
        int nx = mx + (int) Math.round(needle * (mw - 1));
        g.fill(nx - 1, my - 3, nx + 2, my + 13, 0xFFFFFFFF);
        g.fill(mx + mw / 2, my + 10, mx + mw / 2 + 1, my + 13, TEXT);

        // Countdown to the next switching decision.
        int bx = x + 10, by = y + 140, bw = LEFT_W - 20;
        g.fill(bx, by, bx + bw, by + 3, 0xFF16261D);
        g.fill(bx, by, bx + (int) (bw * (1 - menu.nextSwitch())), by + 3, DIM);

        Component status;
        int color;
        if (!menu.enabled()) {
            status = Component.translatable("gui.powerfactor.autocap.disabled");
            color = DIM;
        } else if (!menu.onGrid()) {
            status = Component.translatable("gui.powerfactor.autocap.no_grid");
            color = 0xFFFFB020;
        } else if (menu.tripped()) {
            status = Component.translatable("gui.powerfactor.autocap.tripped");
            color = 0xFFFF4040;
        } else {
            status = Component.translatable("gui.powerfactor.autocap.auto");
            color = 0xFF55FF55;
        }
        panel.draw(g, font, x + LEFT_W + 2, y, menu.onGrid() ? menu.volts() : 0, menu.onGrid() ? menu.hertz() : 0,
                menu.fe(), menu.ee(), 512, status, color);
    }

    private static void zone(GuiGraphicsExtractor g, int x, int y, int w, double from, double to, int color) {
        g.fill(x + (int) (w * from), y + 1, x + (int) (w * to), y + 9, color);
    }

    private static int lerpColor(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int gg = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return 0xFF000000 | r << 16 | gg << 8 | bl;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
        g.text(font, title, 6, 6, TEXT, false);
        int steps = menu.steps();
        g.text(font, Component.translatable("gui.powerfactor.autocap.steps", steps, AutoCapacitorBankBlockEntity.STEPS,
                steps * AutoCapacitorBankBlockEntity.STEP_EE), 8, 90, TEXT, false);
        g.text(font, "0.5 lag", 8, 62, DIM, false);
        g.text(font, "1.0", LEFT_W / 2 - font.width("1.0") / 2, 62, DIM, false);
        g.text(font, "0.5 lead", LEFT_W - 8 - font.width("0.5 lead"), 62, DIM, false);
        double ce = Math.hypot(menu.fe(), menu.ee());
        String pf = ce < 0.5 ? "---" : String.format("%.2f %s", menu.fe() / ce, menu.ee() >= 0 ? "lag" : "lead");
        g.text(font, Component.translatable("gui.powerfactor.autocap.grid_pf", pf), 8, 102, TEXT, false);
        g.text(font, Component.translatable("gui.powerfactor.autocap.target"), 8, 114, DIM, false);
        g.text(font, Component.translatable("gui.powerfactor.autocap.next"), 8, 130, DIM, false);
    }
}
