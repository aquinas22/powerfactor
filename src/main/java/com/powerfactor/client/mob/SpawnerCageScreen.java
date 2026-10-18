package com.powerfactor.client.mob;

import com.powerfactor.client.Skin;
import com.powerfactor.client.storage.StorageScreens;
import com.powerfactor.mob.SoulSnareItem;
import com.powerfactor.mob.SpawnerCageBlockEntity;
import com.powerfactor.mob.SpawnerCageMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/**
 * Spawner Cage console. The hero is the soul vessel: the snared mob, live and watching the cursor, standing
 * in soul fire that fills up from the floor as the next copy forms. Beside it, the snare slot and a
 * readout of the state, the price of a copy and how many it has made.
 */
public class SpawnerCageScreen extends StorageScreens.Base<SpawnerCageMenu> {
    private static final int SOUL = 0xFF4FE3F0;
    private @Nullable LivingEntity shown;
    private @Nullable EntityType<?> shownType;

    public SpawnerCageScreen(SpawnerCageMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void main(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        long t = Util.getMillis();
        // Soul vessel (x 8..80, y 20..92).
        int vx = x + 8, vy = y + 20, vw = 72, vh = 72;
        Skin.lcd(g, vx, vy, vw, vh);
        float p = menu.progress();
        int fill = (int) ((vh - 4) * p);
        for (int k = 0; k < fill; k++) {
            int row = vy + vh - 2 - k;
            // Flickering soul fire: brighter at the surface.
            float lift = 1 - (float) k / Math.max(1, fill);
            int a = (int) (30 + 90 * (1 - lift) + 20 * Math.sin((t / 90.0) + k * 0.7));
            g.fill(vx + 2, row - 1, vx + vw - 2, row, Math.clamp(a, 0, 255) << 24 | SOUL & 0xFFFFFF);
        }
        EntityType<?> type = SoulSnareItem.soul(menu.snare());
        if (type != shownType) {
            shownType = type;
            shown = type != null && minecraft.level != null && type.create(minecraft.level, EntitySpawnReason.LOAD) instanceof LivingEntity l ? l : null;
            if (shown != null) shown.setId(-1);
        }
        if (shown != null) {
            int size = (int) (30 / Math.max(1, Math.max(shown.getBbWidth(), shown.getBbHeight()) * 0.9F));
            InventoryScreen.extractEntityInInventoryFollowsMouse(g, vx + 4, vy + 4, vx + vw - 4, vy + vh - 4, size, 0.0625F, mx, my, shown);
        } else {
            Skin.icon(g, Skin.Icon.HOSTILE, vx + vw / 2 - 4, vy + vh / 2 - 4, Component.translatable("gui.powerfactor.cage.empty"));
        }
        // Snare slot with its label icon.
        Skin.icon(g, Skin.Icon.INPUT, x + 75, y + 28, Component.translatable("gui.powerfactor.cage.snare"));
        Skin.progress(g, x + 108, y + 29, 42, p, SOUL);
        // Readout (x 86..150, y 46..92).
        int state = menu.state();
        Skin.lcd(g, x + 86, y + 46, 64, 46);
        Skin.lcdRows(g, font, x + 86, y + 46, 64, 46, java.util.List.of(
                new Skin.Row(switch (state) {
                    case SpawnerCageBlockEntity.STATE_RUNNING -> Skin.Icon.OK;
                    case SpawnerCageBlockEntity.STATE_CROWDED -> Skin.Icon.WARNING;
                    case SpawnerCageBlockEntity.STATE_NO_POWER -> Skin.Icon.ERROR;
                    default -> Skin.Icon.WAIT;
                }, Component.translatable("gui.powerfactor.cage.state." + state), state == SpawnerCageBlockEntity.STATE_RUNNING ? 0xFF7CFF8A : Skin.LCD_DIM),
                new Skin.Row(Skin.Icon.FE, Component.literal(Skin.compact(menu.cost())), Skin.FE, Component.translatable("gui.powerfactor.cage.cost", String.format("%,d", menu.cost()))),
                new Skin.Row(Skin.Icon.HOSTILE, Component.literal(String.valueOf(menu.spawned())), Skin.LCD_TEXT,
                        Component.translatable("gui.powerfactor.cage.spawned", menu.spawned()))));
        Skin.energyTube(g, x + 156, y + 20, 12, 72, menu.energy(), SpawnerCageBlockEntity.CAPACITY, state == SpawnerCageBlockEntity.STATE_RUNNING);
    }
}
