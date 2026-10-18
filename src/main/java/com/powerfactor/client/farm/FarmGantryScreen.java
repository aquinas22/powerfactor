package com.powerfactor.client.farm;

import com.powerfactor.client.GuiUtil;
import com.powerfactor.client.Skin;
import com.powerfactor.client.storage.StorageScreens;
import com.powerfactor.farm.FarmGantryBlockEntity;
import com.powerfactor.farm.FarmGantryMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;

/**
 * Farm Gantry console: seeds on the left, harvest on the right, and between them a map of the field with
 * the snake path, the squares already worked this pass, and the head (a pulsing cross) where it is now.
 */
public class FarmGantryScreen extends StorageScreens.Base<FarmGantryMenu> {
    public FarmGantryScreen(FarmGantryMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void main(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        long t = Util.getMillis();
        Skin.icon(g, Skin.Icon.INPUT, x + 31, y + 22, Component.translatable("gui.powerfactor.farm.seeds"));
        Skin.icon(g, Skin.Icon.OUTPUT, x + 139, y + 22, Component.translatable("gui.powerfactor.farm.harvest"));
        // Field map (x 64..112, y 20..68): 9x9 squares of 5 px, worked squares lit.
        int mxp = x + 64, myp = y + 20, n = FarmGantryBlockEntity.SIZE;
        Skin.lcd(g, mxp, myp, 48, 48);
        int done = menu.cell();
        for (int i = 0; i < n * n; i++) {
            int[] uv = FarmGantryBlockEntity.cellUV(i);
            int cx = mxp + 2 + (uv[0] + FarmGantryBlockEntity.HALF) * 5, cy = myp + 46 - uv[1] * 5;
            int col = i < done ? 0xFF3E7A3A : 0xFF2A3A30;
            g.fill(cx, cy, cx + 4, cy + 4, col);
            if (i == done) {
                int pulse = (int) (t / 150 % 2);
                g.fill(cx - 1, cy + 1, cx + 5, cy + 3, pulse == 0 ? 0xFFFFD84A : 0xFFFFFFFF);
                g.fill(cx + 1, cy - 1, cx + 3, cy + 5, pulse == 0 ? 0xFFFFD84A : 0xFFFFFFFF);
            }
        }
        g.fill(mxp + 22, myp + 47, mxp + 26, myp + 48, 0xFFFFD84A);   // the gantry itself, at the near edge
        // Under the map: water and power, and how much it has harvested.
        Skin.icon(g, Skin.Icon.FLUID, x + 64, y + 72, Component.translatable("gui.powerfactor.fluid_amount", Component.translatable("block.minecraft.water"),
                String.format("%,d", menu.water()), String.format("%,d", FarmGantryBlockEntity.TANK)));
        Skin.bar(g, x + 75, y + 75, 37, menu.water() / (float) FarmGantryBlockEntity.TANK, 0xFF3F76E4);
        Skin.icon(g, Skin.Icon.FE, x + 64, y + 82, Component.translatable("gui.powerfactor.buffer", String.format("%,d", menu.energy()),
                String.format("%,d", FarmGantryBlockEntity.CAPACITY)));
        Skin.bar(g, x + 75, y + 85, 37, menu.energy() / (float) FarmGantryBlockEntity.CAPACITY, Skin.FE);
        // Header corners, mirrored: status lamp at the far left, harvest tally at the far right.
        Skin.icon(g, menu.on() ? Skin.Icon.OK : Skin.Icon.ERROR, x + 8, y + 22, Component.translatable(menu.on() ? "gui.powerfactor.farm.on" : "gui.powerfactor.farm.off"));
        Component count = Component.literal(String.valueOf(menu.harvested()));
        Skin.iconValue(g, font, Skin.Icon.MINED, count, x + 170 - 11 - font.width(count), y + 23, Skin.TEXT,
                Component.translatable("gui.powerfactor.farm.harvested", menu.harvested()));
    }
}
