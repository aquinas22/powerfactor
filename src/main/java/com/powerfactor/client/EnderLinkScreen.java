package com.powerfactor.client;

import com.powerfactor.PowerFactor;
import com.powerfactor.ender.EnderLinkBlockEntity;
import com.powerfactor.ender.EnderLinkMenu;
import com.powerfactor.machine.MachineBlockEntity;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;

/** Channel picker (16 dye-colored channels), throughput, and how much interference this spot is soaking up. */
public class EnderLinkScreen extends MachineScreen<EnderLinkMenu> {
    private static final Identifier TEXTURE = PowerFactor.id("textures/gui/ender_link.png");
    private static final int SW = 12, GX = 34, GY = 22;

    public EnderLinkScreen(EnderLinkMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 166);
    }

    @Override
    protected List<Tab> tabs() {
        return List.of(Tab.ELECTRICAL, Tab.CONFIG);
    }

    @Override
    protected MachineBlockEntity.RedstoneMode redstoneMode() {
        return menu.redstoneMode();
    }

    @Override
    protected Electrical electrical() {
        double i = menu.interference();
        Component status = Component.translatable(i > 1 ? "gui.powerfactor.ender.severe" : i > 0.5 ? "gui.powerfactor.ender.noisy"
                : menu.flow() > 0 ? "gui.powerfactor.ender.linked" : "gui.powerfactor.ender.idle");
        return new Electrical(1.0, 60.0, menu.flow(), menu.ee(), EnderLinkBlockEntity.RATE * 1.4, status,
                i > 1 ? 0xFFFF4040 : i > 0.5 ? 0xFFFFB020 : 0xFF55FF55);
    }

    @Override
    protected void extractMain(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0, 0, MAIN_W, imageHeight, 256, 256);
        for (int c = 0; c < 16; c++) {
            int x = leftPos + GX + (c % 8) * (SW + 2), y = topPos + GY + (c / 8) * (SW + 2);
            int color = 0xFF000000 | DyeColor.byId(c).getTextureDiffuseColor();
            boolean selected = c == menu.channel();
            g.fill(x - 1, y - 1, x + SW + 1, y + SW + 1, selected ? 0xFFFFFFFF : 0xFF1A1A1A);
            g.fill(x, y, x + SW, y + SW, color);
        }
        GuiUtil.energyBar(g, leftPos + 156, topPos + 16, 12, 54, menu.stored(), EnderLinkBlockEntity.BUFFER);
        // Interference meter.
        int mx = leftPos + 34, my = topPos + 62, mw = 110;
        g.fill(mx, my, mx + mw, my + 5, 0xFF0B0B0B);
        int fill = (int) (mw * Math.min(1, menu.interference() / 1.5));
        g.fillGradient(mx, my, mx + fill, my + 5, 0xFF7A3CFF, 0xFFFF40C0);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
        super.extractLabels(g, xm, ym);
        g.text(font, Component.translatable(menu.transmitter() ? "gui.powerfactor.ender.sending" : "gui.powerfactor.ender.receiving",
                menu.flow()), 34, 51, 0xFFB070FF, false);
        Component channel = Component.translatable("gui.powerfactor.ender.channel", menu.channel() + 1);
        g.text(font, channel, 34 + 110 - font.width(channel), 51, 0xFF000000 | DyeColor.byId(menu.channel()).getTextureDiffuseColor(), false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (int c = 0; c < 16; c++) {
            int x = leftPos + GX + (c % 8) * (SW + 2), y = topPos + GY + (c / 8) * (SW + 2);
            if (event.x() >= x && event.x() < x + SW && event.y() >= y && event.y() < y + SW) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, EnderLinkMenu.BUTTON_CHANNEL + c);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        if (isHovering(34, 62, 110, 5, mouseX, mouseY)) {
            g.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.powerfactor.ender.interference",
                    Math.round(menu.interference() * 100)), Component.translatable("gui.powerfactor.ender.interference.desc").withColor(0xAAAAAA)),
                    mouseX, mouseY);
        }
    }
}
