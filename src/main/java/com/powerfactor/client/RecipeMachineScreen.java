package com.powerfactor.client;

import com.powerfactor.machine.RecipeMachine;
import com.powerfactor.machine.RecipeMachineBlockEntity;
import com.powerfactor.machine.RecipeMachineMenu;
import com.powerfactor.registry.ModBlocks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Recipe machines; the ore washer also shows its water tank. */
public class RecipeMachineScreen extends ProcessingScreen<RecipeMachineMenu> {
    private static final int TANK_X = 94, TANK_Y = 52, TANK_W = 16, TANK_H = 24;

    public RecipeMachineScreen(RecipeMachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu.machine().guiTexture(), menu.machine().arrowX, menu.machine().arrowY, menu.machine().ratedCe(),
                !menu.machine().heater, ModBlocks.RECIPE_MACHINES.get(menu.machine()).get());
    }

    @Override
    protected boolean isToolSlot(net.minecraft.world.inventory.Slot slot) {
        return menu.machine().electrode && slot.getContainerSlot() == menu.machine().electrodeSlot()
                && !(slot instanceof com.powerfactor.machine.MachineMenu.OutputSlotMarker) && slot.index < menu.machine().slotCount();
    }

    @Override
    protected void extractExtras(GuiGraphicsExtractor g, int x, int y) {
        if (menu.machine() == RecipeMachine.ORE_WASHER) {
            GuiUtil.fluidGauge(g, x + TANK_X, y + TANK_Y, TANK_W, TANK_H, menu.tank(), RecipeMachineBlockEntity.TANK_CAPACITY, 0xFF3F76E4);
        }
        if (menu.machine() == RecipeMachine.LASER_ENGRAVER) {
            Skin.well(g, x + WAFER_X - 1, y + WAFER_Y - 1, WAFER + 2, WAFER + 2);
            wafer(g, x + WAFER_X, y + WAFER_Y);
        }
    }

    private static final int WAFER_X = 90, WAFER_Y = 24, WAFER = 26;

    /**
     * The engraver's live view: a silicon wafer whose dies are etched line by line as the job runs,
     * in the mask's pattern, under a scanning laser spot.
     */
    private void wafer(GuiGraphicsExtractor g, int x, int y) {
        float r = WAFER / 2F, cx = x + r, cy = y + r;
        g.fill(x - 1, y - 1, x + WAFER + 1, y + WAFER + 1, 0xFF0D1014);
        String pattern = menu.slots.get(menu.machine().inputs + menu.machine().outputs).getItem().getItem() instanceof com.powerfactor.item.PhotomaskItem m
                ? m.pattern() : "";
        float progress = menu.progress();
        int scan = (int) (progress * WAFER);
        long t = net.minecraft.util.Util.getMillis();
        for (int py = 0; py < WAFER; py++) {
            for (int px = 0; px < WAFER; px++) {
                float dx = px + 0.5F - r, dy = py + 0.5F - r;
                if (dx * dx + dy * dy > r * r) {
                    continue;
                }
                int color = 0xFF3A4652; // bare silicon
                if ((px + py) % 7 == 0) {
                    color = 0xFF46535F;
                }
                if (py < scan) {
                    color = etched(pattern, px, py, color);
                }
                g.fill(x + px, y + py, x + px + 1, y + py + 1, color);
            }
        }
        if (progress > 0 && progress < 1) {
            // The beam: a red scan line with a white-hot spot racing along it.
            int sy = y + Math.min(WAFER - 1, scan);
            g.fill(x, sy, x + WAFER, sy + 1, 0x90FF3020);
            int sx = x + (int) ((t / 12) % WAFER);
            g.fill(sx - 1, sy - 1, sx + 2, sy + 2, 0xFFFF5A3A);
            g.fill(sx, sy, sx + 1, sy + 1, 0xFFFFFFFF);
        }
    }

    /** Die artwork per mask; dies are 6x6 with a 1px street. */
    private static int etched(String pattern, int px, int py, int base) {
        int ux = px % 6, uy = py % 6;
        if (ux == 0 || uy == 0) {
            return 0xFF22282E; // saw street
        }
        return switch (pattern) {
            case "logic" -> (ux == 1 || ux == 5 || uy == 1 || uy == 5) ? 0xFFD9B44A : (ux + uy) % 2 == 0 ? 0xFF4FC27A : 0xFF2E7A50;
            case "memory" -> uy % 2 == 1 ? 0xFF6FA8E8 : 0xFF2C4A78;
            case "power" -> (ux >= 2 && ux <= 4 && uy >= 2 && uy <= 4) ? 0xFFE0703A : 0xFF8A3A22;
            case "sensor" -> {
                int d = Math.max(Math.abs(ux - 3), Math.abs(uy - 3));
                yield d == 1 ? 0xFFB070FF : d == 0 ? 0xFFFFFFFF : 0xFF4A2A70;
            }
            default -> base;
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        if (menu.machine() == RecipeMachine.ORE_WASHER && isHovering(TANK_X, TANK_Y, TANK_W, TANK_H, mouseX, mouseY)) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.powerfactor.water", String.format("%,d", menu.tank()),
                    String.format("%,d", RecipeMachineBlockEntity.TANK_CAPACITY)), mouseX, mouseY);
        }
    }
}
