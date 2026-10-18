package com.powerfactor.client.mob;

import com.powerfactor.client.Skin;
import com.powerfactor.client.storage.StorageScreens;
import com.powerfactor.mob.MobGrinderBlockEntity;
import com.powerfactor.mob.MobGrinderMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;

/**
 * Mob Grinder console. The hero is a top-down plan of the kill zone: the grinder at the near edge, its
 * saw spinning, the 5x5 floor in front flashing red while it cuts. Below it, the kill tally and the
 * experience store with a button to draw it out; the spoils fill the 3x3 grid on the right.
 */
public class MobGrinderScreen extends StorageScreens.Base<MobGrinderMenu> {
    private static final int RED = 0xFFFF4A3A, XP = 0xFF8CFF3A;

    public MobGrinderScreen(MobGrinderMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void main(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        long t = Util.getMillis();
        boolean on = menu.on();
        // Plan (x 8..90, y 20..78).
        Skin.lcd(g, x + 8, y + 20, 82, 58);
        int zx = x + 29, zy = y + 23;
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                boolean flash = on && ((t / 120 + i * 3 + j * 5) % 7 == 0);
                g.fill(zx + i * 8, zy + j * 8, zx + i * 8 + 7, zy + j * 8 + 7, flash ? RED : on ? 0xFF5A2A2A : 0xFF2A3A40);
            }
        }
        // The grinder below the zone, with its saw as a spinning spoke.
        int gx = x + 45, gy = y + 66;
        g.fill(gx, gy, gx + 8, gy + 8, 0xFF8FA6B5);
        double a = (t / (on ? 25.0 : 400.0)) % (Math.PI * 2);
        for (int k = -3; k <= 3; k++) {
            int px = gx + 4 + (int) Math.round(Math.cos(a) * k), py = gy + 4 + (int) Math.round(Math.sin(a) * k);
            g.fill(px, py, px + 1, py + 1, on ? 0xFFFFFFFF : 0xFF4E6A7A);
        }
        Skin.icon(g, Skin.Icon.AREA, x + 11, y + 23, Component.translatable("gui.powerfactor.grinder.zone"));
        // Spoils.
        // Kills and experience (y 82..92).
        Skin.iconValue(g, font, Skin.Icon.KILLS, Component.literal(String.valueOf(menu.kills())), x + 8, y + 83, Skin.TEXT,
                Component.translatable("gui.powerfactor.grinder.kills", menu.kills()));
        int xp = menu.xp();
        Skin.bar(g, x + 40, y + 86, 50, Math.min(1, xp / 1395F), XP);   // a full bar is level 30
        iconButton(98, 80, 54, 13, Skin.Icon.PLAYER, Component.literal(Skin.compact(xp) + " XP"),
                Component.translatable("gui.powerfactor.grinder.draw", String.format("%,d", xp)), XP, false, false, () -> send(MobGrinderMenu.BUTTON_XP));
        Skin.energyTube(g, x + 156, y + 20, 12, 72, menu.energy(), MobGrinderBlockEntity.CAPACITY, on);
    }
}
