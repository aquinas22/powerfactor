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
        int sx = x + 8, sy = y + 20, size = 74, cx = sx + size / 2, cy = sy + size / 2, r = size / 2 - 3;
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
        int tx = x + 94;
        Skin.lamp(g, tx, y + 22, !powered ? RED : menu.tracking() ? accent : AMBER);
        g.text(font, Skin.fit(font, status, 48), tx + 8, y + 21, Skin.TEXT, false);
        g.text(font, Skin.fit(font, Component.translatable("gui.powerfactor.turret.kills", menu.kills()), 56), tx, y + 33, Skin.DIM, false);
        g.text(font, Skin.fit(font, Component.translatable("gui.powerfactor.turret.range", tier.range), 56), tx, y + 43, Skin.DIM, false);
        if (rail) {
            g.text(font, Component.translatable("gui.powerfactor.turret.charge"), tx, y + 53, Skin.DIM, false);
            Skin.bar(g, tx, y + 63, 52, menu.charge(), CYAN);
        }
        // Target toggles (hostiles are always engaged).
        int by = y + (rail ? 69 : 56) - topPos;
        int mode = menu.mode();
        button(94, by, 52, 13, Component.translatable("gui.powerfactor.turret.animals"), Component.translatable("gui.powerfactor.turret.animals.tip"),
                (mode & TurretBlockEntity.MODE_ANIMALS) != 0 ? GREEN : Skin.LCD_DIM, (mode & TurretBlockEntity.MODE_ANIMALS) != 0,
                () -> send(TurretBlockEntity.BUTTON_ANIMALS));
        button(94, by + 15, 52, 13, Component.translatable("gui.powerfactor.turret.players"), Component.translatable("gui.powerfactor.turret.players.tip"),
                (mode & TurretBlockEntity.MODE_PLAYERS) != 0 ? RED : Skin.LCD_DIM, (mode & TurretBlockEntity.MODE_PLAYERS) != 0,
                () -> send(TurretBlockEntity.BUTTON_PLAYERS));
        Skin.energyTube(g, x + 152, y + 20, 14, 74, menu.energy(), tier.capacity, menu.tracking());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        if (mx >= leftPos + 152 && mx < leftPos + 166 && my >= topPos + 20 && my < topPos + 94) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.powerfactor.buffer", String.format("%,d", menu.energy()),
                    String.format("%,d", menu.tier().capacity)), mx, my);
        }
    }
}
