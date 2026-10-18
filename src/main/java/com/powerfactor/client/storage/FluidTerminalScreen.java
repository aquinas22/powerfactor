package com.powerfactor.client.storage;

import com.powerfactor.client.Skin;
import com.powerfactor.storage.fluid.FluidPayloads;
import com.powerfactor.storage.fluid.FluidTerminalMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * Fluid Terminal: every fluid on the network as a glass tile showing the fluid itself, with its
 * amount. Click a tile with an empty bucket or tank to fill it; click anywhere in the grid with a full
 * one to pour it in. Scroll for more.
 */
public class FluidTerminalScreen extends AbstractContainerScreen<FluidTerminalMenu> {
    private static final int GX = 9, GY = 22, CELL = 18;   // grid glass at y 20-96: inside the content zone
    private static List<FluidPayloads.Line> lines = new ArrayList<>();
    private static int status = 3;
    private int scroll;

    static {
        FluidPayloads.syncHandler = p -> {
            var mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.containerMenu.containerId == p.containerId()) {
                lines = new ArrayList<>(p.lines());
                status = p.status();
            }
        };
    }

    public FluidTerminalScreen(FluidTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 178, 200);
        lines = new ArrayList<>();
        status = 3;
    }

    private static TextureAtlasSprite sprite(FluidResource f) {
        var model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(f.getFluid().defaultFluidState());
        return model.stillMaterial().sprite();
    }

    private static int tint(FluidResource f) {
        var model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(f.getFluid().defaultFluidState());
        var t = model.fluidTintSource();
        return t == null ? -1 : 0xFF000000 | t.colorAsStack(f.toStack(1000));
    }

    static String amount(long mb) {
        if (mb >= 1_000_000_000) return String.format("%.1fM", mb / 1e9);
        if (mb >= 1_000_000) return String.format("%.1fk", mb / 1e6);
        if (mb >= 1_000) return String.format("%.0f", mb / 1e3);
        return String.format("%.1f", mb / 1e3);
    }

    private int index(double mx, double my) {
        int cx = (int) Math.floor((mx - leftPos - GX) / CELL), cy = (int) Math.floor((my - topPos - GY) / CELL);
        if (cx < 0 || cy < 0 || cx >= FluidTerminalMenu.COLS || cy >= FluidTerminalMenu.ROWS) return -2;
        int i = (scroll + cy) * FluidTerminalMenu.COLS + cx;
        return i < lines.size() ? i : -1;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractBackground(g, mx, my, a);
        int x = leftPos, y = topPos;
        Skin.panel(g, x, y, imageWidth, imageHeight);
        Skin.title(g, font, title, x, y, imageWidth);
        Skin.lcd(g, x + GX - 2, y + GY - 2, FluidTerminalMenu.COLS * CELL + 4, FluidTerminalMenu.ROWS * CELL + 4);
        int hover = index(mx, my);
        for (int r = 0; r < FluidTerminalMenu.ROWS; r++) {
            for (int c = 0; c < FluidTerminalMenu.COLS; c++) {
                int i = (scroll + r) * FluidTerminalMenu.COLS + c, px = x + GX + c * CELL, py = y + GY + r * CELL;
                g.fill(px, py, px + 16, py + 16, 0xFF0E1A22);
                if (i >= lines.size()) continue;
                FluidPayloads.Line line = lines.get(i);
                // The fluid itself, filling the tile from the bottom with a slow shimmer on the surface.
                g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(line.fluid()), px, py, 16, 16, tint(line.fluid()));
                int wave = (int) ((Util.getMillis() / 120 + i * 3) % 16);
                g.fill(px + wave, py, px + Math.min(16, wave + 3), py + 1, 0x70FFFFFF);
                String n = amount(line.amount());
                g.pose().pushMatrix();
                g.pose().translate(px + 17 - font.width(n) * 0.5F, py + 12);
                g.pose().scale(0.5F, 0.5F);
                g.text(font, n, 0, 0, 0xFFFFFFFF, true);
                g.pose().popMatrix();
                if (i == hover) g.fill(px, py, px + 16, py + 16, 0x50FFFFFF);
            }
        }
        if (status != 0) {
            Component msg = Component.translatable(switch (status) {
                case 1 -> "gui.powerfactor.storage.no_power";
                case 2 -> "gui.powerfactor.storage.conflict";
                default -> "gui.powerfactor.storage.no_network";
            });
            var wrapped = font.split(msg, FluidTerminalMenu.COLS * CELL - 16);
            int cy = y + GY + FluidTerminalMenu.ROWS * CELL / 2 - wrapped.size() * 5;
            for (int i = 0; i < wrapped.size(); i++) g.centeredText(font, wrapped.get(i), x + GX + FluidTerminalMenu.COLS * CELL / 2, cy + i * 10, 0xFFFF7060);
        } else if (lines.isEmpty()) {
            // Empty network: say so, and how to put fluid in (cells repeat the hint in their tooltips).
            int mid = x + GX + FluidTerminalMenu.COLS * CELL / 2, cy = y + GY + FluidTerminalMenu.ROWS * CELL / 2;
            g.centeredText(font, Component.translatable("gui.powerfactor.fluid_terminal.empty"), mid, cy - 9, Skin.LCD_TEXT);
            g.centeredText(font, Skin.fit(font, Component.translatable("gui.powerfactor.fluid_terminal.hint"), FluidTerminalMenu.COLS * CELL - 12), mid, cy + 2,
                    Skin.LCD_DIM);
        }
        Skin.inventory(g, font, x + 8, y + 118);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        int i = index(mx, my);
        if (i >= 0) {
            var line = lines.get(i);
            g.setComponentTooltipForNextFrame(font, List.of(line.fluid().toStack(1).getHoverName(),
                    Component.literal(String.format("%,d mB", line.amount())).withColor(Skin.DIM),
                    Component.translatable("gui.powerfactor.fluid_terminal.fill_tip").withColor(Skin.DIM)), mx, my);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int i = index(event.x(), event.y());
        if (i != -2 && !menu.getCarried().isEmpty()) {
            // On a fluid: fill the carried container with it; the server pours it in instead if it can't fill.
            Optional<FluidResource> target = i >= 0 ? Optional.of(lines.get(i).fluid()) : Optional.empty();
            ClientPacketDistributor.sendToServer(new FluidPayloads.Click(menu.containerId, target));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int rows = (lines.size() + FluidTerminalMenu.COLS - 1) / FluidTerminalMenu.COLS;
        scroll = Math.clamp(scroll - (int) Math.signum(dy), 0, Math.max(0, rows - FluidTerminalMenu.ROWS));
        return true;
    }
}
