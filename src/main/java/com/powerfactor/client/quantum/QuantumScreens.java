package com.powerfactor.client.quantum;

import com.powerfactor.client.Skin;
import com.powerfactor.client.storage.StorageScreens;
import com.powerfactor.quantum.ColliderBlockEntity;
import com.powerfactor.quantum.ColliderMenu;
import com.powerfactor.quantum.ColliderRecipes;
import com.powerfactor.quantum.ZeroPointBlockEntity;
import com.powerfactor.quantum.ZeroPointMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;

/** Screens for the Particle Collider and Zero-Point Generator, each with a live instrument at its heart. */
public final class QuantumScreens {
    static final int VIOLET = 0xFFB18CFF, CYAN = 0xFF6FE3FF, ORANGE = 0xFFFFB347, WHITE = 0xFFFFFFFF;

    // ------------------------------------------------------------------ collider

    public static class Collider extends StorageScreens.Base<ColliderMenu> {
        private float spin;

        public Collider(ColliderMenu menu, Inventory inv, Component title) {
            super(menu, inv, title);
        }

        @Override
        protected void main(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
            float p = menu.progress();
            boolean running = menu.rate() > 0;
            // The ring: an LCD with the accelerator loop; bunches race faster as energy banks.
            // Hero: the ring on the screen's centre line; input and output slots mirror it (docs/gui-style-guide.md).
            int cx = x + 88, cy = y + 47, r = 21;
            Skin.lcd(g, x + 54, y + 20, 68, 72);
            spin += running ? 0.02F + p * 0.35F : 0.003F;
            for (int i = 0; i < 48; i++) {
                double a = i * Math.PI * 2 / 48;
                int px = cx + (int) Math.round(Math.cos(a) * r), py = cy + (int) Math.round(Math.sin(a) * r);
                g.fill(px, py, px + 1, py + 1, 0xFF2E4650);
            }
            int bunches = running ? 2 : 0;
            for (int b = 0; b < bunches; b++) {
                for (int tail = 0; tail < 8; tail++) {
                    double a = spin * (b == 0 ? 1 : -1) + b * Math.PI - tail * (0.05 + 0.1 * p) * (b == 0 ? 1 : -1);
                    int px = cx + (int) Math.round(Math.cos(a) * r), py = cy + (int) Math.round(Math.sin(a) * r);
                    int col = b == 0 ? CYAN : VIOLET;
                    int alpha = 255 - tail * 30;
                    g.fill(px - (tail == 0 ? 1 : 0), py - (tail == 0 ? 1 : 0), px + (tail == 0 ? 2 : 1), py + (tail == 0 ? 2 : 1),
                            alpha << 24 | col & 0xFFFFFF);
                }
            }
            // Magnets around the ring light up with progress.
            for (int k = 0; k < 8; k++) {
                double a = k * Math.PI / 4 + Math.PI / 8;
                int px = cx + (int) Math.round(Math.cos(a) * (r + 5)), py = cy + (int) Math.round(Math.sin(a) * (r + 5));
                g.fill(px - 1, py - 1, px + 2, py + 2, k < Math.round(p * 8) ? ORANGE : 0xFF3A3020);
            }
            int flash = menu.flash();
            if (flash > 0) {
                int s = (20 - flash) + 2;
                g.fill(cx - s, cy - s, cx + s, cy + s, (flash * 12) << 24 | 0xFFFFFF);
            }
            g.text(font, Component.literal(Math.round(p * 100) + "%"), cx - font.width(Math.round(p * 100) + "%") / 2, cy - 4, running ? WHITE : Skin.DIM, false);
            // Readout on the ring's glass, under the loop: what it's making and how fast it banks.
            int recipe = menu.recipe();
            Component what = recipe < 0 ? Component.translatable("gui.powerfactor.collider.idle")
                    : Component.translatable("gui.powerfactor.collider.recipe." + ColliderRecipes.ALL.get(recipe).id());
            Component rate = Component.literal(Skin.compact(menu.rate()) + " FE/t");
            int w1 = Math.min(60, font.width(what)), w2 = font.width(rate);
            Skin.condensed(g, font, what, cx - w1 / 2, y + 73, 60, running ? Skin.LCD_TEXT : Skin.LCD_DIM);
            Skin.iconValue(g, font, Skin.Icon.FE, Component.literal(Skin.compact(menu.rate()) + "/t"), cx - (w2 + 11) / 2, y + 82,
                    running ? Skin.FE : Skin.LCD_DIM, null);
            Skin.energyTube(g, x + 156, y + 20, 12, 72, menu.energy(), ColliderBlockEntity.CAPACITY, running);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
            super.extractRenderState(g, mx, my, a);
            if (mx >= leftPos + 156 && mx < leftPos + 168 && my >= topPos + 20 && my < topPos + 92) {
                g.setTooltipForNextFrame(font, Component.translatable("gui.powerfactor.buffer", String.format("%,d", menu.energy()),
                        String.format("%,d", ColliderBlockEntity.CAPACITY)), mx, my);
            } else if (menu.recipe() >= 0 && mx >= leftPos + 54 && mx < leftPos + 122 && my >= topPos + 20 && my < topPos + 92) {
                var r = ColliderRecipes.ALL.get(menu.recipe());
                g.setTooltipForNextFrame(font, Component.translatable("gui.powerfactor.collider.needs", r.count(), Skin.compact(r.energy())), mx, my);
            }
        }
    }

    // ------------------------------------------------------------------ zero-point

    public static class ZeroPoint extends StorageScreens.Base<ZeroPointMenu> {
        public ZeroPoint(ZeroPointMenu menu, Inventory inv, Component title) {
            super(menu, inv, title);
        }

        @Override
        protected void main(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
            long t = Util.getMillis();
            float s = menu.stability();
            // Readouts on the left glass; the vacuum pocket is the hero, centred over its fuel slot; FE tube on the right.
            Skin.lcd(g, x + 8, y + 20, 40, 72);
            int cx = x + 101, cy = y + 44;
            Skin.lcd(g, x + 54, y + 20, 94, 50);
            // The vacuum pocket: a black core with an orange rim, orbited by qubits that speed up as it stabilises.
            int core = 6 + Math.round(4 * s);
            for (int dy = -core - 3; dy <= core + 3; dy++) {
                for (int dx = -core - 3; dx <= core + 3; dx++) {
                    double d = Math.sqrt(dx * dx + dy * dy);
                    if (d <= core) g.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, 0xFF000000);
                    else if (d <= core + 1.2) g.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, s > 0.05F ? ORANGE : 0xFF3A2A1A);
                    else if (d <= core + 3 && s > 0.05F) g.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, ((int) (60 * s)) << 24 | 0xB18CFF);
                }
            }
            for (int k = 0; k < 3; k++) {
                double a = t / (900.0 - 600 * s) * (k % 2 == 0 ? 1 : -1) + k * 2.1;
                double rx = 20 + k * 4, ry = 8 + k * 3;
                int px = cx + (int) Math.round(Math.cos(a) * rx), py = cy + (int) Math.round(Math.sin(a) * ry);
                g.fill(px - 1, py - 1, px + 1, py + 1, s > 0.05F ? (k == 1 ? CYAN : VIOLET) : 0xFF404850);
            }
            // Left glass: label over value over bar, twice (field, then exotic matter), with the output between.
            int lx = x + 12, lw = 32;
            Skin.iconValue(g, font, Skin.Icon.RANGE, Component.literal(Math.round(s * 100) + "%"), lx, y + 26, s > 0.95F ? 0xFF7CFF8A : Skin.LCD_TEXT,
                    Component.translatable("gui.powerfactor.zero_point.stability", Math.round(s * 100)));
            Skin.bar(g, lx, y + 43, lw, s, VIOLET);
            Skin.iconValue(g, font, Skin.Icon.FE, Component.literal(Skin.compact(menu.output()) + "/t"), lx, y + 53, menu.output() > 0 ? Skin.FE : Skin.LCD_DIM, null);
            Skin.icon(g, Skin.Icon.MATTER, lx, y + 68, Component.translatable("gui.powerfactor.zero_point.fuel"));
            Skin.bar(g, lx, y + 80, lw, menu.fuel(), ORANGE);
            Skin.energyTube(g, x + 156, y + 20, 12, 72, menu.stored(), ZeroPointBlockEntity.BUFFER, menu.output() > 0);
        }
    }

    private QuantumScreens() {}
}
