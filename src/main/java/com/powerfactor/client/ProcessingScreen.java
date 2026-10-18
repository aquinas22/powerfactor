package com.powerfactor.client;

import com.powerfactor.machine.Drive;
import com.powerfactor.machine.MachineMenu;
import com.powerfactor.machine.ProcessingBlockEntity;
import com.powerfactor.machine.ProcessingMenu;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Compact process console: separate power, material flow, cycle progress and inventory. */
public class ProcessingScreen<M extends ProcessingMenu> extends MachineScreen<M> {
    public static final int H = 214;
    private static final int BAR_X = 156, BAR_Y = Skin.Layout.CONTENT_TOP, BAR_W = 12, BAR_H = 56;
    private static final int VIEW_X = 8, VIEW_Y = Skin.Layout.CONTENT_TOP, VIEW_W = 56;
    /** Status strip: 4 px under the view and gauge (which end at y 76), down to the content zone's bottom. */
    private static final int STRIP_Y = 80, STRIP_H = Skin.Layout.contentBottom(H) - STRIP_Y;
    private static final int TOOL_RING = 0xFFC6A1E6;
    private final int arrowX, arrowY;
    private final double ratedCe;
    private final boolean motor;
    private final net.minecraft.world.level.ItemLike icon;
    private float shownProgress;
    private long lastFrame;

    public ProcessingScreen(M menu, Inventory inventory, Component title, Identifier texture, int arrowX, int arrowY, double ratedCe,
            boolean motor, net.minecraft.world.level.ItemLike icon) {
        this(menu, inventory, title, texture, arrowX, arrowY, ratedCe, motor, icon, H);
    }

    protected ProcessingScreen(M menu, Inventory inventory, Component title, Identifier texture, int arrowX, int arrowY, double ratedCe,
            boolean motor, net.minecraft.world.level.ItemLike icon, int height) {
        super(menu, inventory, title, height);
        this.icon = icon;
        this.arrowX = arrowX;
        this.arrowY = arrowY;
        this.ratedCe = ratedCe;
        this.motor = motor;
    }

    @Override protected net.minecraft.world.level.ItemLike machineIcon() { return icon; }
    @Override protected List<Tab> tabs() { return List.of(Tab.ELECTRICAL, Tab.UPGRADES, Tab.CONFIG); }

    @Override protected void init() {
        super.init();
        shownProgress = progressValue();
        lastFrame = Util.getMillis();
    }

    /** Only ease forward to a real reading. A new recipe snaps back, and paused work holds its value. */
    private void updateProgress() {
        long now = Util.getMillis();
        float target = progressValue();
        float blend = 1 - (float) Math.exp(-Math.clamp(now - lastFrame, 0, 100) / 50.0);
        shownProgress = target < shownProgress || !working() ? target : Mth.lerp(blend, shownProgress, target);
        lastFrame = now;
    }

    private boolean working() {
        return menu.workState() == ProcessingBlockEntity.WorkState.READY && menu.driveState() == Drive.State.RUNNING && !menu.stalled();
    }

    @Override protected void extractMain(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        updateProgress();
        Skin.panel(g, x, y, MAIN_W, imageHeight);
        Skin.title(g, font, title, x, y, MAIN_W);
        int color = workColor();
        Skin.lamp(g, x + MAIN_W - 14, y + 6, color);
        Skin.scope(g, x + VIEW_X, y + VIEW_Y, VIEW_W, 24,
                (float) (Math.hypot(menu.fe(), menu.ee()) / Math.max(1, ratedCe)), menu.fe() > 0);
        Skin.lcd(g, x + VIEW_X, y + VIEW_Y + 25, VIEW_W, 31);

        boolean firstOutput = true;
        for (Slot slot : menu.slots) {
            if (slot instanceof ProcessingMenu.UpgradeSlot || slot.container instanceof Inventory) continue;
            int sx = x + slot.x - 1, sy = y + slot.y - 1;
            if (slot instanceof MachineMenu.OutputSlotMarker) {
                if (firstOutput) { Skin.bigSlot(g, sx - 4, sy - 4, Skin.OUTPUT); firstOutput = false; }
                else Skin.slot(g, sx, sy, Skin.OUTPUT);
            } else Skin.slot(g, sx, sy, isToolSlot(slot) ? TOOL_RING : Skin.INPUT);
        }
        if (arrowX > 0) Skin.chevrons(g, x + arrowX, y + arrowY, 3, shownProgress, working());
        extractExtras(g, x, y);
        Skin.energyTube(g, x + BAR_X, y + BAR_Y, BAR_W, BAR_H, menu.stored(), menu.capacity(), menu.fe() > 0);

        Skin.lcd(g, x + 8, y + STRIP_Y, 160, STRIP_H);
        g.fill(x + 9, y + STRIP_Y + 2, x + 11, y + STRIP_Y + 22, color);
        Skin.progress(g, x + 12, y + STRIP_Y + 25, 152, shownProgress, color);
        Skin.inventory(g, font, x + 8, y + ProcessingMenu.INVENTORY_V2);
    }

    protected float progressValue() { return menu.progress(); }
    protected Component cycleLabel() { return Component.translatable("gui.powerfactor.console.cycle", Math.round(menu.progress() * 100)); }

    protected void extractExtras(GuiGraphicsExtractor g, int x, int y) {}
    protected boolean isToolSlot(Slot slot) { return false; }

    @Override protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
        int lx = VIEW_X + 3, ly = VIEW_Y + 28;
        double ce = Math.hypot(menu.fe(), menu.ee());
        String[] values = {Integer.toString(menu.fe()), Integer.toString(menu.ee()), ce < 0.5 ? "—" : String.format(Locale.ROOT, "%.2f", menu.fe() / ce)};
        Skin.Icon[] icons = {Skin.Icon.FE, Skin.Icon.EE, Skin.Icon.PF};
        int[] colors = {Skin.FE, Skin.EE, Skin.LCD_TEXT};
        for (int i = 0; i < 3; i++) {
            Skin.icon(g, icons[i], lx, ly + i * 9 - 1);
            g.text(font, values[i], lx + 50 - font.width(values[i]), ly + i * 9, colors[i], false);
        }
        Skin.icon(g, Skin.statusIcon(workColor()), 15, STRIP_Y + 2);
        Skin.condensed(g, font, electrical().status(), 26, STRIP_Y + 3, 138, workColor());
        Skin.icon(g, Skin.Icon.CLOCK, 15, STRIP_Y + 12, cycleLabel());
        Skin.condensed(g, font, Component.literal(Math.round(progressValue() * 100) + "%"), 26, STRIP_Y + 13, 60, Skin.LCD_DIM);
        String speed = String.format(Locale.ROOT, "×%.2f", menu.speed());
        g.text(font, speed, 164 - font.width(speed), STRIP_Y + 13, working() ? Skin.LCD_TEXT : Skin.LCD_DIM, false);
        Skin.icon(g, Skin.Icon.SPEED, 164 - font.width(speed) - 11, STRIP_Y + 12);
    }

    @Override protected Electrical electrical() {
        Drive.State state = menu.driveState();
        Component status = switch (state) {
            case RUNNING -> menu.stalled() ? Component.translatable("gui.powerfactor.panel.no_power")
                    : Component.translatable(motor ? "gui.powerfactor.panel.motor_running" : "gui.powerfactor.panel.heater_running",
                            Math.round(menu.speed() * 100));
            case IDLE -> Component.translatable("gui.powerfactor.panel.motor_idle");
            case TRIPPED -> Component.translatable("gui.powerfactor.panel.motor_tripped");
            case OFF -> Component.translatable(motor ? "gui.powerfactor.panel.motor_off" : "gui.powerfactor.panel.heater_off");
        };
        if (state != Drive.State.TRIPPED && menu.workState() != ProcessingBlockEntity.WorkState.READY)
            status = Component.translatable(workKey());
        return new Electrical(menu.volts(), menu.hertz(), menu.fe(), menu.ee(), ratedCe, status, workColor());
    }

    private String workKey() { return "gui.powerfactor.work." + menu.workState().name().toLowerCase(Locale.ROOT); }
    private int workColor() {
        if (menu.driveState() == Drive.State.TRIPPED) return Skin.FE;
        return switch (menu.workState()) {
            case OUTPUT_FULL, OUTPUT_UNAVAILABLE, NEEDS_FLUID, NO_ENERGY -> Skin.OUTPUT;
            case DISABLED, NO_RECIPE -> Skin.DIM;
            case READY -> driveColor(menu.driveState(), menu.speed());
        };
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        if (isHovering(BAR_X, BAR_Y, BAR_W, BAR_H, mouseX, mouseY)) {
            GuiUtil.energyTooltip(g, font, menu.stored(), menu.capacity(), mouseX, mouseY);
        } else if (isHovering(8, STRIP_Y, 160, STRIP_H, mouseX, mouseY) || isHovering(160, 3, 12, 12, mouseX, mouseY)) {
            var lines = new java.util.ArrayList<Component>();
            lines.add(electrical().status().copy().withColor(workColor() & 0xFFFFFF));
            lines.add(Component.translatable(menu.driveState() == Drive.State.TRIPPED ? "gui.powerfactor.console.trip_help" : workKey() + ".help")
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.powerfactor.console.progress", Math.round(menu.progress() * 100)).withStyle(ChatFormatting.GRAY));
            if (working() && menu.speed() > 0 && menu.durationTicks() > 0) {
                double seconds = Math.max(0, menu.durationTicks() - menu.progressTicks()) / (20 * menu.speed());
                lines.add(Component.translatable("gui.powerfactor.console.remaining", String.format(Locale.ROOT, "%.1f", seconds)));
            }
            g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        } else if (isHovering(VIEW_X, VIEW_Y, VIEW_W, 56, mouseX, mouseY)) {
            g.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable("gui.powerfactor.console.power", menu.fe(), menu.ee()),
                    Component.translatable("gui.powerfactor.console.supply", Math.round(menu.volts() * 480),
                            String.format(Locale.ROOT, "%.2f", menu.hertz()))), mouseX, mouseY);
        } else if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.container != null
                && !(hoveredSlot.container instanceof Inventory) && !(hoveredSlot instanceof ProcessingMenu.UpgradeSlot)) {
            String role = hoveredSlot instanceof MachineMenu.OutputSlotMarker ? "output" : isToolSlot(hoveredSlot) ? "tool" : "input";
            g.setTooltipForNextFrame(font, Component.translatable("gui.powerfactor.console.slot." + role), mouseX, mouseY);
        }
    }
}
