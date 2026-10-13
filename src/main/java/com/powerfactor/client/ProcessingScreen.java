package com.powerfactor.client;

import com.powerfactor.machine.Drive;
import com.powerfactor.machine.ProcessingMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/** Crusher, electric furnace and friends: background texture, progress arrow, FE gauge, tabs. */
public class ProcessingScreen<M extends ProcessingMenu> extends MachineScreen<M> {
    private static final Identifier ARROW_SPRITE = Identifier.withDefaultNamespace("container/furnace/burn_progress");
    private static final int BAR_X = 156, BAR_Y = 21, BAR_W = 12, BAR_H = 56;

    private final Identifier texture;
    private final int arrowX, arrowY;
    private final double ratedCe;
    private final boolean motor;
    private final net.minecraft.world.level.ItemLike icon;

    public ProcessingScreen(M menu, Inventory inventory, Component title, Identifier texture, int arrowX, int arrowY, double ratedCe, boolean motor,
            net.minecraft.world.level.ItemLike icon) {
        this(menu, inventory, title, texture, arrowX, arrowY, ratedCe, motor, icon, H);
    }

    protected ProcessingScreen(M menu, Inventory inventory, Component title, Identifier texture, int arrowX, int arrowY, double ratedCe, boolean motor,
            net.minecraft.world.level.ItemLike icon, int height) {
        super(menu, inventory, title, height);
        this.icon = icon;
        this.texture = texture;
        this.arrowX = arrowX;
        this.arrowY = arrowY;
        this.ratedCe = ratedCe;
        this.motor = motor;
    }

    @Override
    protected net.minecraft.world.level.ItemLike machineIcon() {
        return icon;
    }

    @Override
    protected List<Tab> tabs() {
        return List.of(Tab.ELECTRICAL, Tab.UPGRADES, Tab.CONFIG);
    }

    /** v2 layout: viewing window, status strip, energy tube. Slots come from the menu's own positions. */
    public static final int H = 198;
    private static final int VIEW_X = 8, VIEW_Y = 21, VIEW_W = 56, VIEW_H = 56, STRIP_Y = 81;
    private static final int TOOL_RING = 0xFFB070FF;

    @Override
    protected void extractMain(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        Skin.panel(g, x, y, MAIN_W, H);
        Skin.title(g, font, title, x, y, MAIN_W);
        int color = workColor();
        Skin.lamp(g, x + MAIN_W - 14, y + 6, color);

        // Power scope where the machine's waves and numbers live; the 3D model is in the Config tab.
        double ceNow = Math.hypot(menu.fe(), menu.ee());
        Skin.scope(g, x + VIEW_X, y + VIEW_Y, VIEW_W, 26, (float) (ceNow / Math.max(1, ratedCe)), menu.fe() > 0);
        Skin.lcd(g, x + VIEW_X, y + VIEW_Y + 27, VIEW_W, VIEW_H - 27);

        // Machine slots, styled by role: the first output is the main product, in a big well.
        boolean firstOutput = true;
        for (var slot : menu.slots) {
            if (slot instanceof ProcessingMenu.UpgradeSlot || slot.container instanceof net.minecraft.world.entity.player.Inventory) {
                continue;
            }
            int sx = x + slot.x - 1, sy = y + slot.y - 1;
            if (slot instanceof com.powerfactor.machine.MachineMenu.OutputSlotMarker) {
                if (firstOutput) {
                    Skin.bigSlot(g, sx - 4, sy - 4, Skin.OUTPUT);
                    firstOutput = false;
                } else {
                    Skin.slot(g, sx, sy, 0xFFFFC07A);
                }
            } else {
                Skin.slot(g, sx, sy, isToolSlot(slot) ? TOOL_RING : Skin.INPUT);
            }
        }
        if (arrowX > 0) {
            Skin.chevrons(g, x + arrowX, y + arrowY, 3, menu.progress(), menu.driveState() == Drive.State.RUNNING);
        }
        extractExtras(g, x, y);

        // Status strip: power on top, drive status and speed below.
        Skin.lcd(g, x + 8, y + STRIP_Y, 160, 23);
        Skin.bar(g, x + 120, y + STRIP_Y + 16, 44, (float) menu.speed(), color);
        Skin.energyTube(g, x + BAR_X, y + VIEW_Y, BAR_W, VIEW_H, menu.stored(), menu.capacity(), menu.fe() > 0);
        Skin.inventory(g, font, x + 8, y + ProcessingMenu.INVENTORY_V2);
    }

    /** Extra machine-specific drawing (tanks, previews) over the main area. */
    protected void extractExtras(GuiGraphicsExtractor g, int x, int y) {}

    /** Slots for tools the recipe wears (electrodes, photomasks): purple ring. */
    protected boolean isToolSlot(net.minecraft.world.inventory.Slot slot) {
        return false;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
        double ce = Math.hypot(menu.fe(), menu.ee());
        String pf = ce < 0.5 ? "PF ---" : String.format("PF %.2f", menu.fe() / ce);
        int lx = VIEW_X + 3, ly = VIEW_Y + 29;
        g.text(font, menu.fe() + " FE/t", lx, ly, Skin.FE, false);
        g.text(font, menu.ee() + " EE/t", lx, ly + 9, Skin.EE, false);
        g.text(font, pf, lx, ly + 18, Skin.LCD_TEXT, false);
        int ty = STRIP_Y + 3;
        g.text(font, font.substrByWidth(electrical().status(), 150).getString(), 12, ty, Skin.LCD_TEXT, false);
        g.text(font, Component.translatable("gui.powerfactor.speed"), 12, ty + 11, Skin.DIM, false);
    }

    @Override
    protected Electrical electrical() {
        Drive.State s = menu.driveState();
        Component status = switch (s) {
            case RUNNING -> menu.stalled() ? Component.translatable("gui.powerfactor.panel.no_power")
                    : Component.translatable(motor ? "gui.powerfactor.panel.motor_running" : "gui.powerfactor.panel.heater_running",
                            Math.round(menu.speed() * 100));
            case IDLE -> Component.translatable("gui.powerfactor.panel.motor_idle");
            case TRIPPED -> Component.translatable("gui.powerfactor.panel.motor_tripped");
            case OFF -> Component.translatable(motor ? "gui.powerfactor.panel.motor_off" : "gui.powerfactor.panel.heater_off");
        };
        if (s != Drive.State.TRIPPED && menu.workState() != com.powerfactor.machine.ProcessingBlockEntity.WorkState.READY)
            status = Component.translatable("gui.powerfactor.work." + menu.workState().name().toLowerCase(java.util.Locale.ROOT));
        return new Electrical(menu.volts(), menu.hertz(), menu.fe(), menu.ee(), ratedCe, status, workColor());
    }

    private int workColor() {
        if (menu.driveState() == Drive.State.TRIPPED) return 0xFFFF4040;
        return switch (menu.workState()) {
            case OUTPUT_FULL, NEEDS_FLUID, NO_ENERGY -> 0xFFFFB020;
            case DISABLED, NO_RECIPE -> Skin.DIM;
            case READY -> driveColor(menu.driveState(), menu.speed());
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        if (isHovering(BAR_X, BAR_Y, BAR_W, BAR_H, mouseX, mouseY)) {
            GuiUtil.energyTooltip(g, font, menu.stored(), menu.capacity(), mouseX, mouseY);
        }
        if (isHovering(8, STRIP_Y, 160, 23, mouseX, mouseY)) g.setTooltipForNextFrame(font,
                Component.translatable("gui.powerfactor.work." + menu.workState().name().toLowerCase(java.util.Locale.ROOT) + ".help"), mouseX, mouseY);
    }
}
