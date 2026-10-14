package com.powerfactor.client.defense;

import com.powerfactor.client.Skin;
import com.powerfactor.client.storage.StorageScreens;
import com.powerfactor.defense.ShieldGeneratorBlockEntity;
import com.powerfactor.defense.ShieldGeneratorMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;

/** Shield console: a side view of the dome drawn to scale over the ground, radius control, upkeep and repulse count. */
public class ShieldScreen extends StorageScreens.Base<ShieldGeneratorMenu> {
    private static final int CYAN = 0xFF6FE3FF;

    public ShieldScreen(ShieldGeneratorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void main(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        long ms = Util.getMillis();
        int sx = x + 8, sy = y + 18, w = 92, h = 64;
        Skin.lcd(g, sx, sy, w, h);
        int cx = sx + w / 2, ground = sy + h - 8;
        g.fill(sx + 1, ground, sx + w - 1, sy + h - 1, 0xFF1E4A2C);
        g.fill(cx - 2, ground - 3, cx + 3, ground, 0xFFC8D2DA);
        // Dome to scale: 16 blocks fills the window.
        int r = Math.round(menu.radius() * 40F / ShieldGeneratorBlockEntity.MAX_R);
        boolean on = menu.on();
        for (int i = 0; i <= 90; i++) {
            double a = Math.PI * i / 90;
            int px = cx + (int) Math.round(Math.cos(a) * r), py = ground - (int) Math.round(Math.sin(a) * r);
            float shimmer = (float) (0.5 + 0.5 * Math.sin(ms / 200.0 + i * 0.4));
            int alpha = on ? (int) (120 + 135 * shimmer) : 60;
            g.fill(px, py, px + 1, py + 1, alpha << 24 | (on ? CYAN : 0x6F8797) & 0xFFFFFF);
        }
        if (on) {
            // Hex shimmer inside the dome.
            for (int k = 0; k < 14; k++) {
                double a = Math.PI * ((k * 37 + ms / 60) % 180) / 180;
                double rr = r * (0.35 + 0.55 * ((k * 13) % 10) / 10.0);
                int px = cx + (int) Math.round(Math.cos(a) * rr), py = ground - (int) Math.round(Math.sin(a) * rr);
                g.fill(px, py, px + 1, py + 1, 0x506FE3FF);
            }
        }
        int tx = x + 104;
        Skin.lamp(g, tx, y + 22, on ? 0xFF5CFF8A : 0xFFC8402A);
        g.text(font, Skin.fit(font, Component.translatable(on ? "gui.powerfactor.shield.up" : "gui.powerfactor.shield.down"), 42), tx + 8, y + 21,
                Skin.TEXT, false);
        g.text(font, Skin.fit(font, Component.translatable("gui.powerfactor.shield.radius", menu.radius()), 46), tx, y + 33, Skin.DIM, false);
        g.text(font, Skin.fit(font, Component.literal("-" + Skin.compact(menu.upkeep()) + "/t"), 46), tx, y + 44, 0xFFD0503A, false);
        g.text(font, Skin.fit(font, Component.translatable("gui.powerfactor.shield.hits", menu.hits()), 46), tx, y + 55, Skin.DIM, false);
        button(104, 68, 21, 13, Component.literal("−"), Component.translatable("gui.powerfactor.shield.radius_tip"), Skin.LCD_TEXT, false,
                () -> send(ShieldGeneratorBlockEntity.BUTTON_SMALLER));
        button(127, 68, 21, 13, Component.literal("+"), Component.translatable("gui.powerfactor.shield.radius_tip"), Skin.LCD_TEXT, false,
                () -> send(ShieldGeneratorBlockEntity.BUTTON_BIGGER));
        Skin.energyTube(g, x + 156, y + 18, 12, 76, menu.energy(), ShieldGeneratorBlockEntity.CAPACITY, on);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        if (mx >= leftPos + 156 && mx < leftPos + 168 && my >= topPos + 18 && my < topPos + 94) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.powerfactor.buffer", String.format("%,d", menu.energy()),
                    String.format("%,d", ShieldGeneratorBlockEntity.CAPACITY)), mx, my);
        }
    }
}
