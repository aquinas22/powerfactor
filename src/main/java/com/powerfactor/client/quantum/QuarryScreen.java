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
        // ---- LCD readout
        int lx = x + 28, ly = y + 20, lw = 104, lh = 42;
        Skin.lcd(g, lx, ly, lw, lh);
        int statusColor = switch (status) {
            case MINING -> 0xFF8FF0B0;
            case DONE -> CYAN;
            case NO_POWER, OUTPUT_FULL, WAITING_CHUNKS -> 0xFFFFC060;
            default -> Skin.DIM;
        };
        g.text(font, Skin.fit(font, Component.translatable("gui.powerfactor.quarry.status." + status.name().toLowerCase(Locale.ROOT)), lw - 8),
                lx + 4, ly + 4, statusColor, false);
        g.text(font, Skin.fit(font, Component.translatable("gui.powerfactor.quarry.area", menu.size(), menu.size(), menu.depth(), menu.totalDepth()),
                lw - 8), lx + 4, ly + 16, Skin.LCD_TEXT, false);
        g.text(font, Skin.fit(font, Component.translatable("gui.powerfactor.quarry.mined", Skin.compact(menu.mined()), Skin.compact(menu.fe())), lw - 8),
                lx + 4, ly + 28, Skin.LCD_TEXT, false);
        // ---- energy tube (between LCD and upgrades)
        long cap = q ? 20_000_000L : 1_000_000L;
        Skin.energyTube(g, x + 136, y + 20, 10, 72, menu.energy(), cap, status == QuarryBlockEntity.Status.MINING);
        // ---- controls
        button(28, 66, 14, 13, Component.literal("−"), Component.translatable("gui.powerfactor.quarry.smaller"), Skin.TEXT, false, () -> send(0));
        g.centeredText(font, Component.literal(menu.size() + "²"), x + 56, y + 69, q ? 0xFF6A48D8 : 0xFFC06A00);
        button(70, 66, 14, 13, Component.literal("+"), Component.translatable("gui.powerfactor.quarry.bigger"), Skin.TEXT, false, () -> send(1));
        button(88, 66, 44, 13, Component.translatable("gui.powerfactor.quarry.silk"), Component.translatable("gui.powerfactor.quarry.silk.tip"),
                menu.silk() ? CYAN : Skin.DIM, menu.silk(), () -> send(2));
        button(28, 82, 14, 13, Component.literal("−"), Component.translatable("gui.powerfactor.quarry.fortune.tip"), Skin.TEXT, false, () -> send(3));
        g.centeredText(font, Component.literal(menu.fortune() == 0 ? "F —" : "F " + "I".repeat(menu.fortune())), x + 56, y + 85,
                menu.silk() ? Skin.DIM : 0xFF1E9E5A);
        button(70, 82, 14, 13, Component.literal("+"), Component.translatable("gui.powerfactor.quarry.fortune.tip"), Skin.TEXT, false, () -> send(4));
        button(88, 82, 26, 13, Component.translatable("gui.powerfactor.quarry.void"), Component.translatable("gui.powerfactor.quarry.void.tip"),
                menu.voidStone() ? AMBER : Skin.DIM, menu.voidStone(), () -> send(5));
        button(116, 82, 16, 13, Component.literal("⟲"), Component.translatable("gui.powerfactor.quarry.restart"), Skin.TEXT, false, () -> send(6));
    }

    private static int mixAlpha(int argb, float a) {
        return ((int) (255 * Mth.clamp(a, 0, 1)) << 24) | (argb & 0xFFFFFF);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        if (mx >= leftPos + 136 && mx < leftPos + 146 && my >= topPos + 20 && my < topPos + 92) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.powerfactor.buffer", String.format("%,d", menu.energy()),
                    String.format("%,d", menu.quantum() ? 20_000_000 : 1_000_000)), mx, my);
        } else if (mx >= leftPos + 8 && mx < leftPos + 24 && my >= topPos + 20 && my < topPos + 92) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.powerfactor.quarry.depth", menu.depth(), menu.totalDepth()), mx, my);
        }
    }
}
