package com.powerfactor.client.dilation;

import com.powerfactor.client.Skin;
import com.powerfactor.client.storage.StorageScreens;
import com.powerfactor.dilation.TimeFieldBlockEntity;
import com.powerfactor.dilation.TimeFieldMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;

/**
 * Time Dilation Field console. The hero is a clock face whose hand sweeps at the field's speed (a slow
 * reference hand behind it at 1x), ringed by tick marks that blur as it speeds up. Five chips pick the
 * speed; the readout shows the price, what's inside and the reach.
 */
public class TimeFieldScreen extends StorageScreens.Base<TimeFieldMenu> {
    private static final int GOLD = 0xFFFFD84A, CYAN = 0xFF6FE3FF, VIOLET = 0xFFB18CFF;

    public TimeFieldScreen(TimeFieldMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void main(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        long t = Util.getMillis();
        int n = TimeFieldBlockEntity.SPEEDS[Math.clamp(menu.speedIndex(), 0, TimeFieldBlockEntity.SPEEDS.length - 1)];
        boolean on = menu.on();
        // Hero: the dial (x 8..72, y 20..70); the range-card slot sits under it.
        int cx = x + 40, cy = y + 45, r = 22;
        Skin.lcd(g, x + 8, y + 20, 64, 50);
        for (int i = 0; i < 60; i++) {
            double a = i * Math.PI * 2 / 60;
            int len = i % 5 == 0 ? 4 : 2;
            for (int k = 0; k < len; k++) {
                int px = cx + (int) Math.round(Math.cos(a) * (r - k)), py = cy + (int) Math.round(Math.sin(a) * (r - k));
                g.fill(px, py, px + 1, py + 1, i % 5 == 0 ? 0xFFC8D0D4 : 0xFF4E6A7A);
            }
        }
        // Reference hand at 1x (dim) and the dilated hand (bright), with motion blur trailing it.
        double base = (t % 60000) / 60000.0 * Math.PI * 2 - Math.PI / 2;
        hand(g, cx, cy, base, r - 8, 0xFF4E6A7A);
        double fast = on ? base * n : base;
        int trail = on ? Math.min(12, n) : 1;
        for (int k = trail - 1; k >= 0; k--) {
            int alpha = 255 - k * (200 / Math.max(1, trail));
            hand(g, cx, cy, fast - k * 0.06, r - 4, alpha << 24 | (on ? GOLD : 0xFF8FA6B5) & 0xFFFFFF);
        }
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF);
        // Speed chips: x1, x2, x4, x8, x16 in a row (x 78..168, y 20..34).
        for (int i = 0; i < TimeFieldBlockEntity.SPEEDS.length; i++) {
            int id = i;
            button(78 + i * 18, 20, 17, 14, Component.literal("×" + TimeFieldBlockEntity.SPEEDS[i]),
                    Component.translatable("gui.powerfactor.time_field.speed", TimeFieldBlockEntity.SPEEDS[i],
                            String.format("%,d", TimeFieldBlockEntity.fieldCost(TimeFieldBlockEntity.SPEEDS[i])),
                            String.format("%,d", TimeFieldBlockEntity.targetCost(TimeFieldBlockEntity.SPEEDS[i]))),
                    i == menu.speedIndex() ? GOLD : Skin.LCD_DIM, i == menu.speedIndex(), () -> send(TimeFieldBlockEntity.BUTTON_SPEED + id));
        }
        // Readout (x 78..150, y 38..84).
        Skin.lcd(g, x + 78, y + 38, 72, 46);
        Skin.lcdRows(g, font, x + 78, y + 38, 72, 46, java.util.List.of(
                new Skin.Row(on ? Skin.Icon.OK : n == 1 ? Skin.Icon.WAIT : Skin.Icon.ERROR, Component.translatable(on ? "gui.powerfactor.time_field.on"
                        : n == 1 ? "gui.powerfactor.time_field.idle" : "gui.powerfactor.time_field.no_power"), on ? 0xFF7CFF8A : Skin.LCD_DIM),
                new Skin.Row(Skin.Icon.FE, Skin.compact(menu.cost()) + "/t", Skin.FE),
                new Skin.Row(Skin.Icon.CLOCK, Component.literal(String.valueOf(menu.targets())), Skin.LCD_TEXT,
                        Component.translatable("gui.powerfactor.time_field.targets", menu.targets())),
                new Skin.Row(Skin.Icon.RANGE, (menu.radius() * 2 + 1) + "³", Skin.LCD_TEXT)));
        Skin.icon(g, Skin.Icon.RANGE, x + 30, y + 79, Component.translatable("gui.powerfactor.time_field.cards"));
        Skin.energyTube(g, x + 156, y + 38, 12, 54, menu.energy(), TimeFieldBlockEntity.CAPACITY, on);
    }

    private static void hand(GuiGraphicsExtractor g, int cx, int cy, double a, int len, int color) {
        for (int k = 0; k < len; k++) {
            int px = cx + (int) Math.round(Math.cos(a) * k), py = cy + (int) Math.round(Math.sin(a) * k);
            g.fill(px, py, px + 1, py + 1, color);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        if (mx >= leftPos + 156 && mx < leftPos + 168 && my >= topPos + 38 && my < topPos + 92) {
            com.powerfactor.client.GuiUtil.energyTooltip(g, font, menu.energy(), TimeFieldBlockEntity.CAPACITY, mx, my);
        }
    }
}
