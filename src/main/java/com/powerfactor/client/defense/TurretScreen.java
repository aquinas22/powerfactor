package com.powerfactor.client.defense;

import com.powerfactor.client.Skin;
import com.powerfactor.client.storage.StorageScreens;
import com.powerfactor.defense.TurretBlockEntity;
import com.powerfactor.defense.TurretMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Turret console: a live radar scope (sweep, range ring, every creature nearby plotted where it
 * stands, hostiles in red), status and kill count, target toggles and the energy reserve.
 */
public class TurretScreen extends StorageScreens.Base<TurretMenu> {
    private static final int RED = 0xFFFF5A4A, GREEN = 0xFF5CFF8A, AMBER = 0xFFFFB347, CYAN = 0xFF8FF4FF;

    public TurretScreen(TurretMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void main(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        TurretBlockEntity.Tier tier = menu.tier();
        boolean rail = tier == TurretBlockEntity.Tier.RAIL;
        int accent = rail ? CYAN : GREEN;
        // Radar scope.
        int sx = x + 8, sy = y + 20, size = 72, cx = sx + size / 2, cy = sy + size / 2, r = size / 2 - 3;
        Skin.lcd(g, sx, sy, size, size);
        for (int i = 0; i < 64; i++) {
            double a = i * Math.PI * 2 / 64;
            for (float f : new float[] {1F, 0.5F}) {
                int px = cx + (int) Math.round(Math.cos(a) * r * f), py = cy + (int) Math.round(Math.sin(a) * r * f);
                g.fill(px, py, px + 1, py + 1, f == 1F ? 0xFF3C6E62 : 0xFF2A4A44);
            }
        }
        g.fill(cx - r, cy, cx + r, cy + 1, 0xFF22403A);
        g.fill(cx, cy - r, cx + 1, cy + r, 0xFF22403A);
        double sweep = (Util.getMillis() % 2400) / 2400.0 * Math.PI * 2;
        for (int t = 0; t < 14; t++) {
            double a = sweep - t * 0.05;
            for (int k = 2; k < r; k += 2) {
                int px = cx + (int) Math.round(Math.cos(a) * k), py = cy + (int) Math.round(Math.sin(a) * k);
                g.fill(px, py, px + 1, py + 1, ((int) (150 * (1 - t / 14F))) << 24 | accent & 0xFFFFFF);
            }
        }
        if (minecraft.level != null) {
            var pos = menu.pos();
            double range = tier.range;
            for (LivingEntity e : minecraft.level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(range), LivingEntity::isAlive)) {
                double dx = e.getX() - pos.getX() - 0.5, dz = e.getZ() - pos.getZ() - 0.5;
                if (dx * dx + dz * dz > range * range) continue;
                int px = cx + (int) Math.round(dx / range * r), py = cy + (int) Math.round(dz / range * r);
                int col = e instanceof Enemy ? RED : e instanceof Player ? CYAN : e instanceof Animal ? AMBER : 0xFFC8D2DA;
                g.fill(px - 1, py - 1, px + 2, py + 2, col);
            }
        }
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF);
        // Status.
        boolean powered = menu.energy() >= tier.shotCost;
        Component status = Component.translatable(!powered ? "gui.powerfactor.turret.no_power"
                : menu.tracking() ? "gui.powerfactor.turret.tracking" : "gui.powerfactor.turret.scanning");
        // Readout glass over the target toggles (x 86-150), 6 px from the radar and from the FE tube.
        int tx = x + 86, tw = 64;
        Skin.lcd(g, tx, y + 20, tw, 40);
        Skin.icon(g, !powered ? Skin.Icon.ERROR : menu.tracking() ? Skin.Icon.OK : Skin.Icon.WAIT, tx + 2, y + 22);
        Skin.condensed(g, font, status, tx + 12, y + 23, tw - 15, !powered ? 0xFFFF6A5A : Skin.LCD_TEXT);
        Skin.iconValue(g, font, Skin.Icon.KILLS, Component.literal(String.valueOf(menu.kills())), tx + 4, y + 33, Skin.LCD_TEXT,
                Component.translatable("gui.powerfactor.turret.kills", menu.kills()));
        Skin.iconValue(g, font, Skin.Icon.RANGE, Component.literal(tier.range + " m"), tx + 4, y + 43, Skin.LCD_TEXT,
                Component.translatable("gui.powerfactor.turret.range", tier.range));
        if (rail) {
            Skin.icon(g, Skin.Icon.FE, tx + 36, y + 32, Component.translatable("gui.powerfactor.turret.charge"));
            Skin.bar(g, tx + 47, y + 35, tw - 51, menu.charge(), CYAN);
        }
        // Target toggles (hostiles are always engaged).
        int mode = menu.mode();
        // Who it engages, as icons: hostiles always (shown lit, not a button), animals and players toggle.
        Skin.lcd(g, leftPos + 86, topPos + 64, 20, 28);
        Skin.icon(g, Skin.Icon.HOSTILE, leftPos + 91, topPos + 73, Component.translatable("gui.powerfactor.turret.hostiles.tip"));
        boolean animals = (mode & TurretBlockEntity.MODE_ANIMALS) != 0, players = (mode & TurretBlockEntity.MODE_PLAYERS) != 0;
        iconButton(108, 64, 42, 13, Skin.Icon.ANIMAL, Component.empty(), Component.translatable("gui.powerfactor.turret.animals.tip"), GREEN, animals, true,
                () -> send(TurretBlockEntity.BUTTON_ANIMALS));
        iconButton(108, 79, 42, 13, Skin.Icon.PLAYER, Component.empty(), Component.translatable("gui.powerfactor.turret.players.tip"), RED, players, true,
                () -> send(TurretBlockEntity.BUTTON_PLAYERS));
        Skin.energyTube(g, x + 156, y + 20, 12, 72, menu.energy(), tier.capacity, menu.tracking());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        if (mx >= leftPos + 156 && mx < leftPos + 168 && my >= topPos + 20 && my < topPos + 92) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.powerfactor.buffer", String.format("%,d", menu.energy()),
                    String.format("%,d", menu.tier().capacity)), mx, my);
        }
    }
}
