package com.powerfactor.client;

import com.powerfactor.laser.LaserNodeBlockEntity;
import com.powerfactor.laser.LaserNodeMenu;
import com.powerfactor.storage.IoPortMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Laser Node console: three big channel switches (Items, Fluids, Energy) that glow in their
 * channel's colour and show which way things flow, a live link map with beams pulsing to every
 * linked node, the item filter and the priority.
 */
public class LaserNodeScreen extends AbstractContainerScreen<LaserNodeMenu> {
    static final int[] COLORS = {0xFFFFC94A, 0xFF4FA8FF, 0xFFFF5A4A};
    private static final String[] KEYS = {"items", "fluids", "energy"};
    private static final Skin.Icon[] CHANNEL_ICONS = {Skin.Icon.ITEM, Skin.Icon.FLUID, Skin.Icon.FE};
    private record Btn(int x, int y, int w, int h, int id, Component tip) {}
    private final List<Btn> btns = new ArrayList<>();

    public LaserNodeScreen(LaserNodeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 232);   // inventory at 150: the priority row ends 4 px above its divider
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractBackground(g, mx, my, a);
        btns.clear();
        int x = leftPos, y = topPos;
        long ms = Util.getMillis();
        Skin.panel(g, x, y, imageWidth, imageHeight);
        Skin.title(g, font, title, x, y, imageWidth);
        // Channel switches.
        for (int c = 0; c < 3; c++) {
            int bx = x + 8, by = y + 18 + c * 21, mode = menu.mode(c), col = COLORS[c];
            boolean hover = mx >= bx && mx < bx + 84 && my >= by && my < by + 19;
            boolean flowing = (menu.activity() & (1 << c)) != 0;
            Skin.control(g, bx, by, 84, 19, mode != LaserNodeBlockEntity.OFF, hover);
            // Channel icon in a lit socket (dark when off), then the mode.
            g.fill(bx + 3, by + 3, bx + 16, by + 16, mode == LaserNodeBlockEntity.OFF ? 0xFF0A1016 : 0x60000000 | col & 0xFFFFFF);
            Skin.icon(g, CHANNEL_ICONS[c], bx + 5, by + 5, Component.translatable("gui.powerfactor.laser.channel." + KEYS[c]));
            if (mode == LaserNodeBlockEntity.OFF) g.fill(bx + 5, by + 5, bx + 14, by + 14, 0x9A0A1016);
            g.text(font, Component.translatable("gui.powerfactor.laser.mode." + mode), bx + 20, by + 6,
                    mode == LaserNodeBlockEntity.OFF ? Skin.LCD_DIM : col, false);
            // Flow chevrons: marching outward when sending, inward when receiving.
            if (mode != LaserNodeBlockEntity.OFF) {
                int phase = (int) ((ms / (flowing ? 90 : 300)) % 6);
                for (int k = 0; k < 3; k++) {
                    int cx = bx + 66 + ((mode == LaserNodeBlockEntity.SEND ? k : 2 - k) * 5 + phase) % 15;
                    int alpha = flowing ? 255 : 110;
                    for (int d = 0; d < 3; d++) {
                        int px = mode == LaserNodeBlockEntity.SEND ? cx + d : cx + 2 - d;
                        g.fill(px, by + 7 + d, px + 1, by + 8 + d, alpha << 24 | col & 0xFFFFFF);
                        g.fill(px, by + 13 - d, px + 1, by + 14 - d, alpha << 24 | col & 0xFFFFFF);
                    }
                }
            }
            btns.add(new Btn(bx, by, 84, 19, LaserNodeMenu.BUTTON_CHANNEL + c, Component.translatable("gui.powerfactor.laser.channel_tip")));
        }
        // Link map: this node at the centre, each link a beam to its true direction and distance.
        int mapX = x + 96, mapY = y + 18, mw = 72, mh = 61, cx = mapX + mw / 2, cy = mapY + mh / 2;
        Skin.lcd(g, mapX, mapY, mw, mh);
        for (int r = 10; r <= 30; r += 10) {
            for (int i = 0; i < 48; i++) {
                double an = i * Math.PI * 2 / 48;
                int px = cx + (int) Math.round(Math.cos(an) * r), py = cy + (int) Math.round(Math.sin(an) * r * 0.9);
                g.fill(px, py, px + 1, py + 1, 0x403F8FA8);
            }
        }
        var be = minecraft.level == null ? null : minecraft.level.getBlockEntity(menu.pos());
        if (be instanceof LaserNodeBlockEntity node) {
            for (BlockPos p : node.clientLinks()) {
                int dx = p.getX() - menu.pos().getX(), dz = p.getZ() - menu.pos().getZ();
                double len = Math.max(1, Math.sqrt(dx * dx + dz * dz));
                double sc = 1.25 * Math.min(len, LaserNodeBlockEntity.RANGE) / len;     // 1.25 px per block, clamped to range
                int ex = cx + (int) Math.round(dx * sc), ey = cy + (int) Math.round(dz * sc * 0.9);
                int steps = Math.max(Math.abs(ex - cx), Math.abs(ey - cy));
                int active = menu.activity();
                int beamCol = (active & 4) != 0 ? COLORS[2] : (active & 2) != 0 ? COLORS[1] : (active & 1) != 0 ? COLORS[0] : 0xFF6FE3FF;
                for (int s = 0; s <= steps; s++) {
                    int px = cx + (ex - cx) * s / Math.max(1, steps), py = cy + (ey - cy) * s / Math.max(1, steps);
                    boolean pulse = active != 0 && (s - ms / 60) % 6 == 0;
                    g.fill(px, py, px + 1, py + 1, pulse ? 0xFFFFFFFF : beamCol);
                }
                g.fill(ex - 1, ey - 1, ex + 2, ey + 2, 0xFFE6FCFF);
            }
        }
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFFFFFFFF);
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFF6FE3FF);
        Skin.iconValue(g, font, Skin.Icon.LINK, Component.literal(menu.links() + "/" + LaserNodeBlockEntity.MAX_LINKS), mapX + 4, mapY + mh - 10,
                Skin.LCD_DIM, Component.translatable("gui.powerfactor.laser.links", menu.links(), LaserNodeBlockEntity.MAX_LINKS));
        // Filter: one row of ghost slots, then allow/deny beside it.
        Skin.iconValue(g, font, Skin.Icon.FILTER, Component.translatable("gui.powerfactor.laser.filter"), x + 8, y + 84, Skin.TEXT, null);
        for (Slot s : menu.slots) {
            if (s instanceof IoPortMenu.GhostSlot) {
                Skin.well(g, x + s.x - 1, y + s.y - 1, 18, 18);
                g.fill(x + s.x, y + s.y, x + s.x + 16, y + s.y + 16, 0xFFEAF1F6);
            }
        }
        boolean deny = menu.deny();
        int dx = x + 120, dy = y + 94;
        Skin.control(g, dx, dy, 48, 18, deny, mx >= dx && mx < dx + 48 && my >= dy && my < dy + 18);
        Skin.icon(g, deny ? Skin.Icon.DENY : Skin.Icon.ALLOW, dx + 20, dy + 5, null);
        btns.add(new Btn(dx, dy, 48, 18, LaserNodeMenu.BUTTON_DENY, Component.translatable("gui.powerfactor.port.mode.tip")));
        // Priority.
        Skin.iconValue(g, font, Skin.Icon.PRIORITY, Component.literal(String.valueOf(menu.priority())), x + 8, y + 119, Skin.TEXT,
                Component.translatable("gui.powerfactor.laser.priority", menu.priority()));
        String[] labels = {"-10", "-1", "+1", "+10"};
        for (int i = 0; i < 4; i++) {
            int px = x + 84 + i * 21, py = y + 117;
            Skin.control(g, px, py, 20, 13, false, mx >= px && mx < px + 20 && my >= py && my < py + 13);
            g.centeredText(font, labels[i], px + 10, py + 3, Skin.LCD_TEXT);
            btns.add(new Btn(px, py, 20, 13, LaserNodeMenu.BUTTON_PRIORITY + i, Component.translatable("gui.powerfactor.storage.priority.tip")));
        }
        Skin.inventory(g, font, x + 8, y + LaserNodeMenu.INV_Y);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        for (Btn b : btns) {
            if (mx >= b.x() && mx < b.x() + b.w() && my >= b.y() && my < b.y() + b.h()) {
                g.setTooltipForNextFrame(font, b.tip(), mx, my);
                break;
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (Btn b : List.copyOf(btns)) {
            if (event.x() >= b.x() && event.x() < b.x() + b.w() && event.y() >= b.y() && event.y() < b.y() + b.h()) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.4F, 0.35F));
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, b.id());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
