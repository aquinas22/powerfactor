package com.powerfactor.client.quantum;

import com.powerfactor.client.Skin;
import com.powerfactor.client.storage.StorageScreens;
import com.powerfactor.quantum.StarforgeBlockEntity;
import com.powerfactor.quantum.StarforgeMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Starforge console: three tool slots feed energy lines into the catalyst, which feeds the paxel
 * output. Lines fill with the banked energy; empty slots show faint hints of what belongs there.
 */
public class StarforgeScreen extends StorageScreens.Base<StarforgeMenu> {
    private static final int HOT = 0xFFFF9A3C, CORE = 0xFFFFE9C8;

    public StarforgeScreen(StarforgeMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void main(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        long t = Util.getMillis();
        float p = menu.progress();
        boolean running = menu.rate() > 0;
        int[][] s = StarforgeMenu.SLOT_XY;
        // Feed lines: each tool into the catalyst, then the catalyst into the output.
        for (int i = 0; i < 3; i++) {
            int ly = y + s[i][1] + 8;
            line(g, x + s[i][0] + 18, ly, x + s[3][0] - 1, y + s[3][1] + 8, p, running, t, i);
        }
        line(g, x + s[3][0] + 18, y + s[3][1] + 8, x + s[4][0] - 5, y + s[4][1] + 8, p, running, t, 3);
        // Catalyst ring.
        int cx = x + s[3][0] + 8, cy = y + s[3][1] + 8;
        for (int k = 0; k < 16; k++) {
            double a = k * Math.PI / 8 + t / 600.0 * (running ? 4 : 1);
            int px = cx + (int) Math.round(Math.cos(a) * 15), py = cy + (int) Math.round(Math.sin(a) * 15);
            g.fill(px, py, px + 1, py + 1, k < p * 16 ? HOT : 0xFF9DB3C2);
        }
        Skin.bigSlot(g, x + s[4][0] - 5, y + s[4][1] - 5, Skin.OUTPUT);
        // Hints in empty slots.
        ItemStack[] hints = {new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.DIAMOND_AXE), new ItemStack(Items.DIAMOND_SHOVEL)};
        for (int i = 0; i < 3; i++) {
            if (menu.getSlot(i).getItem().isEmpty()) {
                g.item(hints[i], x + s[i][0], y + s[i][1]);
                g.fill(x + s[i][0], y + s[i][1], x + s[i][0] + 16, y + s[i][1] + 16, 0xB0E9F0F5);
            }
        }
        // Status.
        Component status = !menu.formed() ? Component.translatable("gui.powerfactor.starforge.unformed")
                : menu.recipe() < 0 ? Component.translatable("gui.powerfactor.starforge.load")
                : Component.translatable("gui.powerfactor.starforge.forging", Math.round(p * 100));
        // Status strip under the catalyst and output (the tool column runs the full height on the left).
        Skin.lcd(g, x + 54, y + 78, 96, 14);
        Component rate = Component.literal(Skin.compact(menu.rate()) + " FE/t");
        int rw = running ? font.width(rate) + 4 : 0;
        int sc = !menu.formed() ? 0xFFFF6A5A : running ? 0xFFFFB347 : Skin.LCD_DIM;
        Skin.icon(g, !menu.formed() ? Skin.Icon.ERROR : running ? Skin.Icon.HEAT : Skin.Icon.WAIT, x + 57, y + 80);
        Skin.condensed(g, font, status, x + 68, y + 81, 78 - rw, sc);
        if (running) g.text(font, rate, x + 146 - font.width(rate), y + 81, Skin.FE, false);
        Skin.energyTube(g, x + 156, y + 20, 12, 72, menu.energy(), StarforgeBlockEntity.CAPACITY, running);
    }

    private static void line(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, float p, boolean running, long t, int seed) {
        int mid = (x0 + x1) / 2;
        int len = Math.abs(mid - x0) + Math.abs(y1 - y0) + Math.abs(x1 - mid);
        int lit = Math.round(len * p);
        int i = 0;
        for (int x = Math.min(x0, mid); x <= Math.max(x0, mid); x++, i++) dot(g, x, y0, i, lit, running, t, seed);
        for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++, i++) dot(g, mid, y, i, lit, running, t, seed);
        for (int x = Math.min(mid, x1); x <= Math.max(mid, x1); x++, i++) dot(g, x, y1, i, lit, running, t, seed);
    }

    private static void dot(GuiGraphicsExtractor g, int x, int y, int i, int lit, boolean running, long t, int seed) {
        boolean on = i < lit;
        boolean pulse = running && (i + seed * 7 - (int) (t / 40)) % 12 == 0;
        g.fill(x, y, x + 1, y + 2, pulse ? CORE : on ? HOT : 0xFFB7C7D3);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        if (!menu.formed() && mx >= leftPos + 54 && mx < leftPos + 150 && my >= topPos + 78 && my < topPos + 92) {
            g.setComponentTooltipForNextFrame(font, java.util.List.of(Component.translatable("gui.powerfactor.starforge.unformed"),
                    Component.translatable("gui.powerfactor.multiblock.auto_hint")), mx, my);
        }
    }
}
