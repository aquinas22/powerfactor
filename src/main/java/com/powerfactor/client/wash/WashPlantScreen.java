package com.powerfactor.client.wash;

import com.powerfactor.chem.OreMetal;
import com.powerfactor.client.GuiUtil;
import com.powerfactor.client.Skin;
import com.powerfactor.client.fx.Fx;
import com.powerfactor.client.storage.StorageScreens;
import com.powerfactor.wash.WashPlantBlockEntity;
import com.powerfactor.wash.WashPlantMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;

/**
 * Wash Plant console. The hero is a live side-view of the line across the top: feed hopper, the turning
 * trommel with ore tumbling in it, water sheeting down the sluice with concentrate settling behind the
 * riffles, and the shaker table chattering at the front. Feed (2x2) and catch (3x2) sit beneath it.
 */
public class WashPlantScreen extends StorageScreens.Base<WashPlantMenu> {
    private static final int TOP = 20, VIEW_H = 30, BOTTOM = 92, WATER = 0xFF3F76E4;

    public WashPlantScreen(WashPlantMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void main(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        long t = Util.getMillis();
        boolean on = menu.state() == WashPlantBlockEntity.STATE_RUNNING;
        OreMetal metal = menu.metal() < 0 ? null : OreMetal.values()[menu.metal()];
        int ore = metal == null ? 0xFFE8C860 : metal.color;
        // ---- schematic (x 8..152, y 20..50): the plant drawn right to left as it stands, feed at the back.
        int sx = x + 8, sy = y + TOP, sw = 144;
        Skin.lcd(g, sx, sy, sw, VIEW_H);
        int ground = sy + VIEW_H - 4;
        g.fill(sx + 1, ground, sx + sw - 1, sy + VIEW_H - 1, 0xFF1A2420);
        // Feed hopper (far right) on its column.
        int hx = sx + sw - 18;
        g.fill(hx + 4, sy + 9, hx + 10, ground, 0xFFE8BC2A);
        for (int r = 0; r < 6; r++) g.fill(hx + r, sy + 3 + r, hx + 14 - r, sy + 4 + r, 0xFFAAB4BA);
        // Trommel: a drum with rotating bands, tipped toward the front.
        int tx0 = sx + 70, tx1 = hx - 2, ty = sy + 9;
        g.fill(tx0, ty, tx1, ty + 9, 0xFF3E484E);
        int phase = on ? (int) (t / 70 % 6) : 0;
        for (int bx = tx0 + phase; bx < tx1; bx += 6) g.fill(bx, ty, bx + 2, ty + 9, 0xFF8C969C);
        g.fill(tx0, ty, tx1, ty + 1, 0xFFC8D0D4);
        if (on) {
            for (int k = 0; k < 5; k++) {
                int ox = tx1 - 4 - (int) ((t / 25 + k * 13) % (tx1 - tx0 - 6));
                int oy = ty + 5 + (int) (2 * Math.sin(t / 120.0 + k));
                g.fill(ox, oy, ox + 2, oy + 2, ore);
            }
            for (int k = 0; k < 6; k++) {   // spray
                int dx = tx0 + 6 + k * 9, dy = ty - 3 + (int) ((t / 40 + k * 3) % 3);
                g.fill(dx, dy, dx + 1, dy + 2, 0xFF9AD8FF);
            }
        }
        g.fill(tx0 + 4, ty + 9, tx0 + 6, ground, 0xFFE8BC2A);
        g.fill(tx1 - 6, ty + 9, tx1 - 4, ground, 0xFFE8BC2A);
        // Sluice: a sloped trough from under the drum to the table, riffles every 6 px, water streaming down it.
        int lx0 = sx + 22, lx1 = tx1;
        for (int px = lx0; px < lx1; px++) {
            int py = ground - 3 - (px - lx0) * 4 / Math.max(1, lx1 - lx0);
            g.fill(px, py, px + 1, py + 2, 0xFF5A6268);
            if ((px - lx0) % 6 == 0) g.fill(px, py - 1, px + 1, py, 0xFFC8D0D4);
            if (on && ((px + t / 30) % 5) < 3) g.fill(px, py - 1, px + 1, py, 0xC06AA0D0);
            if ((px - lx0) % 6 == 1 && (menu.washed() + px) % 3 == 0) g.fill(px, py - 1, px + 1, py, ore);
        }
        // Shaker table (front, far left), shaking.
        int jig = on ? (int) (Math.sin(t / 25.0) * 1.2) : 0;
        g.fill(sx + 4 + jig, ground - 6, sx + 22 + jig, ground - 4, 0xFF4A5850);
        g.fill(sx + 6, ground - 4, sx + 8, ground, 0xFFE8BC2A);
        g.fill(sx + 18, ground - 4, sx + 20, ground, 0xFFE8BC2A);
        if (on) for (int k = 0; k < 3; k++) g.fill(sx + 8 + k * 4 + jig, ground - 7, sx + 10 + k * 4 + jig, ground - 6, ore);
        if (!menu.formed()) {
            g.fill(sx + 1, sy + 1, sx + sw - 1, sy + VIEW_H - 1, 0xC0140A0C);
            g.centeredText(font, Component.translatable("gui.powerfactor.wash.state.5"), sx + sw / 2, sy + 11, 0xFFFF9A8A);
        }
        // ---- lower row: water tank, feed 2x2, the run arrow, catch 3x2.
        GuiUtil.fluidGauge(g, x + 8, y + 54, 16, BOTTOM - 54, menu.water(), WashPlantBlockEntity.TANK, WATER);
        Skin.icon(g, Skin.Icon.INPUT, x + 37, y + 44, Component.translatable("gui.powerfactor.wash.feed"));
        Skin.icon(g, Skin.Icon.OUTPUT, x + 121, y + 44, Component.translatable("gui.powerfactor.wash.yield"));
        Skin.chevrons(g, x + 71, y + 69, 3, menu.progress(), on);
        Skin.icon(g, statusIcon(menu.state()), x + 77, y + 56, Component.translatable("gui.powerfactor.wash.state." + menu.state()));
        Skin.iconValue(g, font, Skin.Icon.ITEM, Component.literal(String.valueOf(menu.washed())), x + 70, y + 82, Skin.DIM,
                Component.translatable("gui.powerfactor.wash.washed", menu.washed()));
        Skin.energyTube(g, x + 156, y + TOP, 12, BOTTOM - TOP, menu.energy(), WashPlantBlockEntity.CAPACITY, on);
    }

    private static Skin.Icon statusIcon(int state) {
        return switch (state) {
            case WashPlantBlockEntity.STATE_RUNNING -> Skin.Icon.OK;
            case WashPlantBlockEntity.STATE_NO_POWER, WashPlantBlockEntity.STATE_UNFORMED -> Skin.Icon.ERROR;
            case WashPlantBlockEntity.STATE_NO_WATER -> Skin.Icon.FLUID;
            case WashPlantBlockEntity.STATE_FULL -> Skin.Icon.WARNING;
            default -> Skin.Icon.WAIT;
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        int x = mx - leftPos, y = my - topPos;
        if (x >= 8 && x < 24 && y >= 54 && y < BOTTOM) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.powerfactor.fluid_amount", Component.translatable("block.minecraft.water"),
                    String.format("%,d", menu.water()), String.format("%,d", WashPlantBlockEntity.TANK)), mx, my);
        } else if (x >= 156 && x < 168 && y >= TOP && y < BOTTOM) {
            GuiUtil.energyTooltip(g, font, menu.energy(), WashPlantBlockEntity.CAPACITY, mx, my);
        }
    }
}
