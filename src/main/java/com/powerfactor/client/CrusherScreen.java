package com.powerfactor.client;

import com.powerfactor.machine.CrusherMenu;
import com.powerfactor.machine.Drive;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;

/**
 * Crusher, in the v2 look: a live 3D view of the machine (jaws working when it runs) in a viewing
 * window, the input and products in ringed wells, chevron progress, an LCD power readout and a
 * glass energy tube.
 */
public class CrusherScreen extends ProcessingScreen<CrusherMenu> {
    static final int H = 190;
    private static final int VIEW_X = 8, VIEW_Y = 21, VIEW_W = 56, VIEW_H = 56;
    private static final int TUBE_X = 156, TUBE_Y = 21, TUBE_W = 12, TUBE_H = 56;

    public CrusherScreen(CrusherMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, null, 0, 0, 25, true, com.powerfactor.registry.ModBlocks.CRUSHER.get(), H);
    }

    @Override
    protected void extractMain(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        Skin.panel(g, x, y, MAIN_W, H);
        Skin.title(g, font, title, x, y, MAIN_W);
        int color = driveColor(menu.driveState(), menu.speed());
        Skin.lamp(g, x + MAIN_W - 14, y + 6, color);

        // Viewing window with the live machine.
        Skin.well(g, x + VIEW_X, y + VIEW_Y, VIEW_W, VIEW_H);
        g.fillGradient(x + VIEW_X + 1, y + VIEW_Y + 1, x + VIEW_X + VIEW_W - 1, y + VIEW_Y + VIEW_H - 1, 0xFF1C2A33, 0xFF0B1116);
        if (menu.pos != null && minecraft.level != null && minecraft.level.getBlockEntity(menu.pos) != null) {
            float yaw = 200 + (Util.getMillis() % 24000L) / 24000F * 360F;
            MachineView.draw(g, minecraft.level.getBlockEntity(menu.pos), x + VIEW_X + 1, y + VIEW_Y + 1, x + VIEW_X + VIEW_W - 1,
                    y + VIEW_Y + VIEW_H - 1, yaw, minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        }
        // Glass reflection over the window.
        g.fill(x + VIEW_X + 2, y + VIEW_Y + 2, x + VIEW_X + 4, y + VIEW_Y + 20, 0x18FFFFFF);

        // Slots: input (blue ring), main product (orange, big well), byproduct (orange).
        Skin.slot(g, x + 70, y + 26, Skin.INPUT);
        Skin.bigSlot(g, x + 117, y + 22, Skin.OUTPUT);
        Skin.slot(g, x + 123, y + 51, 0xFFFFC07A);
        Skin.chevrons(g, x + 93, y + 31, 3, menu.progress(), menu.driveState() == Drive.State.RUNNING);

        // LCD power readout under the input.
        Skin.lcd(g, x + 68, y + 50, 50, 27);
        Skin.energyTube(g, x + TUBE_X, y + TUBE_Y, TUBE_W, TUBE_H, menu.stored(), menu.capacity(), menu.fe() > 0);
        // Status strip: drive state and motor speed, full width under the machine.
        Skin.lcd(g, x + 8, y + 81, 160, 13);
        Skin.bar(g, x + 120, y + 86, 44, (float) menu.speed(), color);
        Skin.inventory(g, font, x + 8, y + 108);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
        // Title and inventory label are drawn by the skin; only the LCD text here.
        double ce = Math.hypot(menu.fe(), menu.ee());
        g.text(font, menu.fe() + " FE/t", 71, 53, Skin.FE, false);
        g.text(font, menu.ee() + " EE/t", 71, 61, Skin.EE, false);
        String pf = ce < 0.5 ? "PF ---" : String.format("PF %.2f", menu.fe() / ce);
        g.text(font, pf, 71, 69, Skin.LCD_TEXT, false);
        Component status = electrical().status();
        g.text(font, font.substrByWidth(status, 104).getString(), 12, 84, Skin.LCD_TEXT, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        if (isHovering(TUBE_X, TUBE_Y, TUBE_W, TUBE_H, mouseX, mouseY)) {
            GuiUtil.energyTooltip(g, font, menu.stored(), menu.capacity(), mouseX, mouseY);
        }
    }
}
