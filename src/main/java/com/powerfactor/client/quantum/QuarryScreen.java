package com.powerfactor.client.quantum;

import com.powerfactor.client.Skin;
import com.powerfactor.client.storage.StorageScreens;
import com.powerfactor.quantum.QuarryBlockEntity;
import com.powerfactor.quantum.QuarryMenu;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;

/**
 * Quarry console: a live mine-shaft cross-section (strata, the cut face and the head), an LCD with
 * area/layer/throughput, and the controls. The quantum model's shaft shimmers with folded space.
 */
public class QuarryScreen extends StorageScreens.Base<QuarryMenu> {
    private static final int VIOLET = 0xFFB18CFF, AMBER = 0xFFFFB347, CYAN = 0xFF6FE3FF;

    public QuarryScreen(QuarryMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void main(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        long t = Util.getMillis();
        boolean q = menu.quantum();
        int accent = q ? VIOLET : AMBER;
        QuarryBlockEntity.Status status = menu.status();
        // ---- shaft cross-section
        int sx = x + 8, sy = y + 20, sw = 16, sh = 72;
        Skin.well(g, sx - 1, sy - 1, sw + 2, sh + 2);
        int[] strata = {0xFF5E7F3A, 0xFF6B5A44, 0xFF6E6E6E, 0xFF5D5D63, 0xFF45454E, 0xFF2E2E36};
        for (int i = 0; i < sh; i++) {
            int band = Math.min(strata.length - 1, i * strata.length / sh);
            g.fill(sx, sy + i, sx + sw, sy + i + 1, strata[band] | ((i * 7 % 5 == 0) ? 0x00101010 : 0));
        }
        float frac = Mth.clamp((float) menu.depth() / menu.totalDepth(), 0, 1);
        int cut = Math.round(sh * frac);
        g.fill(sx + 2, sy, sx + sw - 2, sy + cut, 0xFF0A0D10);
        if (q) {
            // Folded space glitters in the cut.
            for (int k = 0; k < 6; k++) {
                int py = sy + (int) ((t / 30 + k * 23) % Math.max(1, cut));
                int px = sx + 3 + (k * 5 + (int) (t / 200)) % (sw - 6);
                g.fill(px, py, px + 1, py + 1, k % 2 == 0 ? VIOLET : CYAN);
            }
        }
        if (status == QuarryBlockEntity.Status.MINING) {
            float pulse = 0.5F + 0.5F * Mth.sin(t / 90F);
            g.fill(sx + 1, sy + cut - 1, sx + sw - 1, sy + cut + 1, mixAlpha(accent, 0.6F + 0.4F * pulse));
        }
        // ---- centre column (x 30-128): two-line readout, two steppers, a row of toggles.
        int lx = x + 30, lw = 98;
        Skin.lcd(g, lx, y + 20, lw, 24);
        int statusColor = switch (status) {
            case MINING -> 0xFF8FF0B0;
            case DONE -> CYAN;
            case NO_POWER, OUTPUT_FULL, WAITING_CHUNKS -> 0xFFFFC060;
            default -> Skin.LCD_DIM;
        };
        Skin.lcdRows(g, font, lx, y + 20, lw, 24, java.util.List.of(
                new Skin.Row(Skin.statusIcon(statusColor), Component.translatable("gui.powerfactor.quarry.status." + status.name().toLowerCase(Locale.ROOT)), statusColor),
                new Skin.Row(Skin.Icon.DEPTH, Component.literal(menu.depth() + "/" + menu.totalDepth() + "   " + Skin.compact(menu.fe()) + " FE/t"), Skin.LCD_TEXT,
                        Component.translatable("gui.powerfactor.quarry.layer", menu.depth(), menu.totalDepth(), Skin.compact(menu.fe())))));
        stepper(g, 48, Skin.Icon.AREA, Component.literal(menu.size() + "×" + menu.size()), q ? VIOLET : AMBER,
                Component.translatable("gui.powerfactor.quarry.smaller"), Component.translatable("gui.powerfactor.quarry.bigger"), 0, 1);
        stepper(g, 63, Skin.Icon.FORTUNE, menu.fortune() == 0 ? Component.literal("—") : Component.literal("I".repeat(menu.fortune())),
                menu.silk() ? Skin.LCD_DIM : 0xFF7CFF8A, Component.translatable("gui.powerfactor.quarry.fortune.tip"),
                Component.translatable("gui.powerfactor.quarry.fortune.tip"), 3, 4);
        // Toggles as icons: silk touch, void stone, restart (three equal chips, 2-px gutters).
        iconButton(30, 78, 31, 14, Skin.Icon.SILK, Component.empty(), Component.translatable("gui.powerfactor.quarry.silk.tip"), CYAN, menu.silk(), true,
                () -> send(2));
        iconButton(63, 78, 32, 14, Skin.Icon.VOID, Component.empty(), Component.translatable("gui.powerfactor.quarry.void.tip"), AMBER, menu.voidStone(), true,
                () -> send(5));
        iconButton(97, 78, 31, 14, Skin.Icon.RESTART, Component.empty(), Component.translatable("gui.powerfactor.quarry.restart"), Skin.LCD_TEXT, false, false,
                () -> send(6));
        // ---- energy tube between the controls and the upgrade column
        long cap = q ? 20_000_000L : 1_000_000L;
        Skin.energyTube(g, x + 134, y + 20, 10, 72, menu.energy(), cap, status == QuarryBlockEntity.Status.MINING);
    }

    /** A full-width stepper row at y: [−] value glass [+]. */
    private void stepper(GuiGraphicsExtractor g, int y, Skin.Icon icon, Component value, int color, Component lessTip, Component moreTip, int less, int more) {
        iconButton(30, y, 14, 14, Skin.Icon.MINUS, Component.empty(), lessTip, Skin.LCD_TEXT, false, false, () -> send(less));
        Skin.lcd(g, leftPos + 46, topPos + y, 66, 14);
        int w = Math.min(48, font.width(value)), cx = leftPos + 79 - (w + 11) / 2;
        Skin.icon(g, icon, cx, topPos + y + 3);
        Skin.condensed(g, font, value, cx + 11, topPos + y + 3, 48, color);
        iconButton(114, y, 14, 14, Skin.Icon.PLUS, Component.empty(), moreTip, Skin.LCD_TEXT, false, false, () -> send(more));
    }

    private static int mixAlpha(int argb, float a) {
        return ((int) (255 * Mth.clamp(a, 0, 1)) << 24) | (argb & 0xFFFFFF);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        if (mx >= leftPos + 134 && mx < leftPos + 144 && my >= topPos + 20 && my < topPos + 92) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.powerfactor.buffer", String.format("%,d", menu.energy()),
                    String.format("%,d", menu.quantum() ? 20_000_000 : 1_000_000)), mx, my);
        } else if (mx >= leftPos + 30 && mx < leftPos + 128 && my >= topPos + 20 && my < topPos + 44) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.powerfactor.quarry.mined", Skin.compact(menu.mined()), Skin.compact(menu.fe())), mx, my);
        } else if (mx >= leftPos + 8 && mx < leftPos + 24 && my >= topPos + 20 && my < topPos + 92) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.powerfactor.quarry.depth", menu.depth(), menu.totalDepth()), mx, my);
        }
    }
}
