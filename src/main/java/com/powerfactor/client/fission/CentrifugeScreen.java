package com.powerfactor.client.fission;

import com.powerfactor.client.Skin;
import com.powerfactor.client.storage.StorageScreens;
import com.powerfactor.fission.IsotopeCentrifugeBlockEntity;
import com.powerfactor.fission.IsotopeCentrifugeMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;

/** Isotope Centrifuge console: input, a spinning rotor cross-section as the hero, and the separated outputs. */
public class CentrifugeScreen extends StorageScreens.Base<IsotopeCentrifugeMenu> {
    public CentrifugeScreen(IsotopeCentrifugeMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void main(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        long t = Util.getMillis();
        boolean on = menu.state() == IsotopeCentrifugeBlockEntity.STATE_RUNNING;
        Skin.icon(g, Skin.Icon.INPUT, x + 30, y + 36, Component.translatable("gui.powerfactor.centrifuge.input"));
        // Rotor cross-section (x 52..108, y 22..78): spokes spinning, isotopes banding by weight.
        int cx = x + 80, cy = y + 50, r = 24;
        Skin.lcd(g, x + 52, y + 22, 56, 56);
        double a = on ? (t / 20.0) % (Math.PI * 2) : 0;
        for (int ring = r; ring > 0; ring -= 1) {
            float f = ring / (float) r;
            int col = on ? com.powerfactor.client.fx.Fx.mix(0xFFB8FFC8, 0xFF2E7A3A, f) : 0xFF2A3A40;
            if (ring % 3 == 0) {
                for (int k = 0; k < 48; k++) {
                    double ang = k * Math.PI * 2 / 48;
                    int px = cx + (int) Math.round(Math.cos(ang) * ring), py = cy + (int) Math.round(Math.sin(ang) * ring);
                    g.fill(px, py, px + 1, py + 1, col);
                }
            }
        }
        for (int k = 0; k < 4; k++) {
            double ang = a + k * Math.PI / 2;
            for (int d = 0; d < r; d++) {
                int px = cx + (int) Math.round(Math.cos(ang) * d), py = cy + (int) Math.round(Math.sin(ang) * d);
                g.fill(px, py, px + 1, py + 1, 0xFFE8F0F4);
            }
        }
        Skin.progress(g, x + 52, y + 82, 56, menu.progress(), 0xFF7CFF8A);
        Skin.icon(g, Skin.Icon.OUTPUT, x + 129, y + 27, Component.translatable("gui.powerfactor.centrifuge.output"));
        Skin.energyTube(g, x + 156, y + 20, 12, 72, menu.energy(), IsotopeCentrifugeBlockEntity.CAPACITY, on);
    }
}
