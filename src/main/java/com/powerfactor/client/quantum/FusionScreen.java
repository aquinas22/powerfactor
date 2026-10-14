package com.powerfactor.client.quantum;

import com.powerfactor.client.Skin;
import com.powerfactor.client.storage.StorageScreens;
import com.powerfactor.quantum.FusionMenu;
import com.powerfactor.quantum.FusionReactorBlockEntity;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;

/**
 * Fusion console: a live cross-section of the tokamak (the plasma torus brightens and shifts from
 * red through violet to white-blue as it heats), plasma temperature, hydrogen and output.
 */
public class FusionScreen extends StorageScreens.Base<FusionMenu> {
    public FusionScreen(FusionMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    /** Plasma colour by temperature: dull red, orange, violet, then white-blue. */
    static int plasma(float t) {
        int[] stops = {0xFF5A1010, 0xFFFF6A2A, 0xFFB18CFF, 0xFFE8F6FF};
        float f = Mth.clamp(t, 0, 1) * (stops.length - 1);
        int i = Math.min(stops.length - 2, (int) f);
        return lerp(stops[i], stops[i + 1], f - i);
    }

    /** The coloured sheath around the white-hot core: the plasma colour, cooling to violet and cyan at full heat. */
    static int halo(float t) {
        return t > 0.75F ? lerp(0xFFB18CFF, 0xFF6FE3FF, (t - 0.75F) * 4) : plasma(t);
    }

    static int lerp(int a, int b, float t) {
        int r = (int) Mth.lerp(t, a >> 16 & 255, b >> 16 & 255), g = (int) Mth.lerp(t, a >> 8 & 255, b >> 8 & 255),
                bl = (int) Mth.lerp(t, a & 255, b & 255);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    @Override
    protected void main(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        float t = menu.temperature();
        int state = menu.state();
        long ms = Util.getMillis();
        // Tokamak cross-section: magnet ring, vacuum vessel, plasma torus.
        int sx = x + 8, sy = y + 20, w = 84, h = 74, cx = sx + w / 2, cy = sy + h / 2;
        Skin.lcd(g, sx, sy, w, h);
        for (int k = 0; k < 16; k++) {
            double a = k * Math.PI * 2 / 16;
            int px = cx + (int) Math.round(Math.cos(a) * 36), py = cy + (int) Math.round(Math.sin(a) * 30);
            boolean lit = menu.formed() && (state != FusionReactorBlockEntity.STATE_COLD || t > 0);
            g.fill(px - 2, py - 2, px + 3, py + 3, lit ? 0xFFC86A36 : 0xFF5A3A2A);
            g.fill(px - 1, py - 1, px + 2, py + 2, lit ? 0xFF48C4F0 : 0xFF2A3A44);
        }
        int col = plasma(t);
        int halo = halo(t);
        double spin = ms / (900.0 - 700 * t);
        if (t > 0.01F) {
            for (int k = 0; k < 220; k++) {
                double a = k * Math.PI * 2 / 220;
                double wobble = 1 + 0.06 * Math.sin(a * 5 + spin * 3) * (1 - t);
                for (int ring = 0; ring < 3; ring++) {
                    double rx = (21 + ring * 3) * wobble, ry = (16 + ring * 2) * wobble;
                    int px = cx + (int) Math.round(Math.cos(a) * rx), py = cy + (int) Math.round(Math.sin(a) * ry);
                    float bright = (float) (0.55 + 0.45 * Math.sin(a * 3 - spin * 4 + ring));
                    int alpha = (int) (255 * Mth.clamp(t * 1.4F, 0, 1) * (ring == 1 ? 1 : 0.55F) * bright);
                    g.fill(px, py, px + 1, py + 1, alpha << 24 | (ring == 1 ? col : halo) & 0xFFFFFF);
                }
            }
            if (state == FusionReactorBlockEntity.STATE_BURNING) {
                // Fusion events: brief white sparks inside the torus.
                for (int k = 0; k < 6; k++) {
                    double a = (ms / 70 + k * 37) % 360 * Math.PI / 180;
                    int px = cx + (int) Math.round(Math.cos(a) * 24), py = cy + (int) Math.round(Math.sin(a) * 18);
                    g.fill(px - 1, py, px + 2, py + 1, 0xFFFFFFFF);
                    g.fill(px, py - 1, px + 1, py + 2, 0xFFFFFFFF);
                }
            }
        }
        // Central solenoid.
        g.fill(cx - 3, cy - 9, cx + 4, cy + 10, 0xFF3A4652);
        g.fill(cx - 2, cy - 8, cx + 3, cy + 9, menu.formed() ? 0xFF7A8C99 : 0xFF4A5560);
        // Readouts.
        int tx = x + 96;
        String key = !menu.formed() ? "unformed" : switch (state) {
            case FusionReactorBlockEntity.STATE_BURNING -> "burning";
            case FusionReactorBlockEntity.STATE_IGNITING -> "igniting";
            default -> "cold";
        };
        int lamp = !menu.formed() ? 0xFFC8402A : state == FusionReactorBlockEntity.STATE_BURNING ? 0xFF5CFF8A
                : state == FusionReactorBlockEntity.STATE_IGNITING ? 0xFFFFB347 : 0xFF7FA4B4;
        Skin.lamp(g, tx, y + 22, lamp);
        g.text(font, Skin.fit(font, Component.translatable("gui.powerfactor.fusion." + key), 46), tx + 8, y + 21, Skin.TEXT, false);
        g.text(font, Component.translatable("gui.powerfactor.fusion.temp", Math.round(t * 150)), tx, y + 33, t > 0 ? Skin.TEXT : Skin.DIM, false);
        Skin.bar(g, tx, y + 43, 52, t, halo(t));
        g.text(font, Skin.fit(font, Component.translatable("gui.powerfactor.fusion.output", Skin.compact(menu.output())), 54), tx, y + 50,
                menu.output() > 0 ? 0xFFD0503A : Skin.DIM, false);
        g.text(font, Skin.fit(font, Component.translatable("gui.powerfactor.fusion.fuel", Skin.compact(menu.fuel())), 54), tx, y + 62, Skin.DIM, false);
        Skin.bar(g, tx, y + 72, 52, menu.fuel() / (float) FusionReactorBlockEntity.TANK, 0xFF48C4F0);
        if (menu.formed() && state == FusionReactorBlockEntity.STATE_COLD) {
            g.text(font, Skin.fit(font, Component.translatable("gui.powerfactor.fusion.ignite_hint"), 54), tx, y + 80, Skin.DIM, false);
        }
        Skin.energyTube(g, x + 152, y + 20, 14, 74, menu.energy(), FusionReactorBlockEntity.CAPACITY, menu.output() > 0);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        if (mx >= leftPos + 152 && mx < leftPos + 166 && my >= topPos + 20 && my < topPos + 94) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.powerfactor.buffer", String.format("%,d", menu.energy()),
                    String.format("%,d", FusionReactorBlockEntity.CAPACITY)), mx, my);
        } else if (mx >= leftPos + 96 && mx < leftPos + 150 && my >= topPos + 60 && my < topPos + 90) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.powerfactor.fusion.fuel_tip", String.format("%,d", menu.fuel()),
                    String.format("%,d", FusionReactorBlockEntity.TANK)), mx, my);
        }
    }
}
