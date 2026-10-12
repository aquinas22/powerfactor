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
    private static final int BAR_X = 156, BAR_Y = 16, BAR_W = 12, BAR_H = 54;

    private final Identifier texture;
    private final int arrowX, arrowY;
    private final double ratedCe;
    private final boolean motor;
    private final net.minecraft.world.level.ItemLike icon;

    public ProcessingScreen(M menu, Inventory inventory, Component title, Identifier texture, int arrowX, int arrowY, double ratedCe, boolean motor,
            net.minecraft.world.level.ItemLike icon) {
        this(menu, inventory, title, texture, arrowX, arrowY, ratedCe, motor, icon, 166);
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

    @Override
    protected void extractMain(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.blit(RenderPipelines.GUI_TEXTURED, texture, leftPos, topPos, 0, 0, MAIN_W, imageHeight, 256, 256);
        int w = Mth.ceil(menu.progress() * 24F);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW_SPRITE, 24, 16, 0, 0, leftPos + arrowX, topPos + arrowY, w, 16);
        GuiUtil.energyBar(g, leftPos + BAR_X, topPos + BAR_Y, BAR_W, BAR_H, menu.stored(), menu.capacity());
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
        super.extractLabels(g, xm, ym);
        GuiUtil.powerLine(g, font, menu.fe(), menu.ee(), 8, 60);
        // Small status lamp in the title bar.
        int color = driveColor(menu.driveState(), menu.speed());
        g.fill(MAIN_W - 30, 6, MAIN_W - 24, 12, color);
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
        return new Electrical(menu.volts(), menu.hertz(), menu.fe(), menu.ee(), ratedCe, status, driveColor(s, menu.speed()));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        if (isHovering(BAR_X, BAR_Y, BAR_W, BAR_H, mouseX, mouseY)) {
            GuiUtil.energyTooltip(g, font, menu.stored(), menu.capacity(), mouseX, mouseY);
        }
    }
}
