package com.powerfactor.client.quantum;

import com.powerfactor.client.Skin;
import com.powerfactor.client.storage.StorageScreens;
import com.powerfactor.quantum.OrbitalCollectorBlockEntity;
import com.powerfactor.quantum.OrbitalCollectorMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;

/**
 * Orbital Collector console: a night-sky window with twinkling stars and the collector's satellite on
 * its orbit; while collecting, a beam drops from the satellite to the dish and a progress arc fills.
 */
public class OrbitalCollectorScreen extends StorageScreens.Base<OrbitalCollectorMenu> {
    private static final int VIOLET = 0xFFB18CFF, CYAN = 0xFF6FE3FF;

    public OrbitalCollectorScreen(OrbitalCollectorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void main(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        long ms = Util.getMillis();
        boolean working = menu.state() == OrbitalCollectorBlockEntity.WORKING;
        int sx = x + 8, sy = y + 20, w = 84, h = 54;
        Skin.lcd(g, sx, sy, w, h);
        // Stars: fixed positions from a hash, each twinkling on its own phase.
        for (int i = 0; i < 40; i++) {
            int px = sx + 2 + (i * 37 + 11) % (w - 4), py = sy + 2 + (i * 53 + 7) % (h - 14);
            float tw = 0.5F + 0.5F * (float) Math.sin(ms / 400.0 + i * 1.7);
            int a = (int) (80 + 175 * tw);
            g.fill(px, py, px + 1, py + 1, a << 24 | (i % 5 == 0 ? 0xB18CFF : 0xE8F2FF));
        }
        // Horizon and the dish.
        int cx = sx + w / 2, ground = sy + h - 8;
        g.fill(sx + 1, ground, sx + w - 1, sy + h - 1, 0xFF0A1410);
        g.fill(cx - 5, ground - 3, cx + 6, ground - 2, 0xFFC8D2DA);
        g.fill(cx - 3, ground - 5, cx + 4, ground - 3, 0xFFC8D2DA);
        g.fill(cx, ground - 2, cx + 1, ground, 0xFF7A8C99);
        // The satellite on an elliptical orbit across the sky.
        double a = ms / 3000.0;
        int ox = cx + (int) Math.round(Math.cos(a) * 34), oy = sy + 16 + (int) Math.round(Math.sin(a) * 10);
        for (int k = 0; k < 64; k++) {
            double t = k * Math.PI * 2 / 64;
            g.fill(cx + (int) Math.round(Math.cos(t) * 34), sy + 16 + (int) Math.round(Math.sin(t) * 10), cx + (int) Math.round(Math.cos(t) * 34) + 1,
                    sy + 16 + (int) Math.round(Math.sin(t) * 10) + 1, 0x403F8FA8);
        }
        g.fill(ox - 3, oy, ox + 4, oy + 1, 0xFF6F8FA8);
        g.fill(ox - 1, oy - 1, ox + 2, oy + 2, working ? CYAN : 0xFF8A9AA8);
        if (working) {
            // Collection beam: from the satellite down to the dish, shimmering.
            for (int k = 0; k < 24; k++) {
                float f = k / 23F;
                int bx = Math.round(ox + (cx - ox) * f), by = Math.round(oy + (ground - 5 - oy) * f);
                int al = (int) (120 + 100 * Math.sin(ms / 90.0 - k * 0.6));
                g.fill(bx, by, bx + 1, by + 1, Math.max(0, Math.min(255, al)) << 24 | (k % 2 == 0 ? 0x6FE3FF : 0xB18CFF));
            }
        }
        // Status and progress.
        int state = menu.state();
        int lamp = working ? 0xFF5CFF8A : state == OrbitalCollectorBlockEntity.DAY ? 0xFFFFB347 : 0xFFC8402A;
        Skin.lamp(g, x + 8, y + 79, lamp);
        g.text(font, Skin.fit(font, Component.translatable("gui.powerfactor.orbital.state." + state), 76), x + 16, y + 78, Skin.TEXT, false);
        Skin.bar(g, x + 8, y + 90, 84, menu.progress(), VIOLET);
        Skin.energyTube(g, x + 156, y + 20, 12, 74, menu.energy(), OrbitalCollectorBlockEntity.CAPACITY, working);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        if (mx >= leftPos + 156 && mx < leftPos + 168 && my >= topPos + 20 && my < topPos + 94) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.powerfactor.buffer", String.format("%,d", menu.energy()),
                    String.format("%,d", OrbitalCollectorBlockEntity.CAPACITY)), mx, my);
        } else if (mx >= leftPos + 8 && mx < leftPos + 92 && my >= topPos + 88 && my < topPos + 94) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.powerfactor.orbital.pass", Math.round(menu.progress() * 100)), mx, my);
        }
    }
}
