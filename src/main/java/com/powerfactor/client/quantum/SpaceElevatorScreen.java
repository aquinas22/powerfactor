package com.powerfactor.client.quantum;

import com.powerfactor.client.Skin;
import com.powerfactor.client.storage.StorageScreens;
import com.powerfactor.quantum.SpaceElevatorBlockEntity;
import com.powerfactor.quantum.SpaceElevatorMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;

/**
 * Space Elevator console: the planet's curve, the tether rising with every segment raised, the
 * station waiting at the top, and once complete a climber running up and down the line.
 */
public class SpaceElevatorScreen extends StorageScreens.Base<SpaceElevatorMenu> {
    private static final int CYAN = 0xFF6FE3FF, VIOLET = 0xFFB18CFF;

    public SpaceElevatorScreen(SpaceElevatorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void main(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        long ms = Util.getMillis();
        int sx = x + 8, sy = y + 20, w = 88, h = 72;   // hero: the view up the tether
        Skin.lcd(g, sx, sy, w, h);
        // Stars.
        for (int i = 0; i < 30; i++) {
            int px = sx + 2 + (i * 29 + 5) % (w - 4), py = sy + 2 + (i * 41 + 3) % (h - 24);
            int a = (int) (90 + 120 * (0.5 + 0.5 * Math.sin(ms / 500.0 + i)));
            g.fill(px, py, px + 1, py + 1, a << 24 | 0xE8F2FF);
        }
        // Planet limb: an arc of atmosphere and ground along the bottom.
        int cx = sx + w / 2;
        for (int dx = -w / 2 + 1; dx < w / 2 - 1; dx++) {
            int yy = sy + h - 10 + (dx * dx) / 220;
            g.fill(cx + dx, yy - 2, cx + dx + 1, yy - 1, 0x806FE3FF);
            g.fill(cx + dx, yy - 1, cx + dx + 1, sy + h - 1, dx % 7 == 0 ? 0xFF2A6A3A : 0xFF1E4A2C);
        }
        int ground = sy + h - 11, topY = sy + 10;
        int segs = menu.segments();
        float frac = (float) segs / SpaceElevatorBlockEntity.SEGMENTS;
        int tetherTop = Math.round(ground - (ground - topY) * frac);
        // Tether with segment ticks.
        g.fill(cx, tetherTop, cx + 1, ground, 0xFFC8D2DA);
        for (int k = 0; k <= segs; k += 8) {
            int ty = Math.round(ground - (ground - topY) * (float) k / SpaceElevatorBlockEntity.SEGMENTS);
            g.fill(cx - 1, ty, cx + 2, ty + 1, 0xFF8CA0AE);
        }
        // Anchor.
        g.fill(cx - 4, ground, cx + 5, ground + 2, 0xFFC8D2DA);
        boolean complete = segs >= SpaceElevatorBlockEntity.SEGMENTS;
        if (complete) {
            // Station: a ring with solar wings, and a climber travelling the tether.
            g.fill(cx - 14, topY - 1, cx - 4, topY + 1, 0xFF3F8FC8);
            g.fill(cx + 5, topY - 1, cx + 15, topY + 1, 0xFF3F8FC8);
            g.fill(cx - 3, topY - 3, cx + 4, topY + 4, 0xFFE6FCFF);
            double t = (ms % 6000) / 6000.0, f = t < 0.5 ? t * 2 : 2 - t * 2;
            int climb = (int) Math.round(ground - 4 - (ground - topY - 8) * f);
            g.fill(cx - 2, climb, cx + 3, climb + 3, CYAN);
            // Beamed power down the line.
            int beam = (int) ((ms / 40) % (ground - topY));
            g.fill(cx - 1, topY + beam, cx + 2, topY + beam + 2, VIOLET);
        } else if (segs > 0) {
            g.fill(cx - 2, tetherTop - 1, cx + 3, tetherTop + 1, 0xFFFFB347);
        }
        // Readout column (x 102-150): status glass over the tether slot; FE tube on the right edge.
        int tx = x + 102, tw = 48;
        Skin.lcd(g, tx, y + 20, tw, 48);
        Skin.icon(g, !menu.sky() ? Skin.Icon.ERROR : complete ? Skin.Icon.OK : Skin.Icon.WAIT, tx + 2, y + 22);
        Component state = Component.translatable(!menu.sky() ? "gui.powerfactor.elevator.no_sky"
                : complete ? "gui.powerfactor.elevator.online" : "gui.powerfactor.elevator.building");
        Skin.condensed(g, font, state, tx + 12, y + 23, tw - 15, Skin.LCD_TEXT);
        Skin.iconValue(g, font, Skin.Icon.LINK, Component.literal(segs + "/" + SpaceElevatorBlockEntity.SEGMENTS), tx + 4, y + 33, Skin.LCD_DIM,
                Component.translatable("gui.powerfactor.elevator.segments", segs, SpaceElevatorBlockEntity.SEGMENTS));
        Skin.bar(g, tx + 4, y + 43, tw - 8, frac, CYAN);
        if (!complete) {
            Skin.icon(g, Skin.Icon.ROTOR, tx + 4, y + 49, Component.translatable("gui.powerfactor.elevator.spool"));
            Skin.bar(g, tx + 4, y + 60, tw - 8, menu.spool(), 0xFFFFB347);
        } else {
            Skin.iconValue(g, font, Skin.Icon.FE, Component.literal("+" + Skin.compact(menu.output()) + "/t"), tx + 4, y + 52, Skin.FE, null);
        }
        Skin.energyTube(g, x + 156, y + 20, 12, 72, menu.energy(), SpaceElevatorBlockEntity.CAPACITY, menu.output() > 0);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        if (mx >= leftPos + 156 && mx < leftPos + 168 && my >= topPos + 20 && my < topPos + 92) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.powerfactor.buffer", String.format("%,d", menu.energy()),
                    String.format("%,d", SpaceElevatorBlockEntity.CAPACITY)), mx, my);
        }
    }
}
