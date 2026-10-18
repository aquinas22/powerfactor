package com.powerfactor.client.pocketvoid;

import com.powerfactor.client.Skin;
import com.powerfactor.pocketvoid.PocketVoidItem;
import com.powerfactor.pocketvoid.PocketVoidMenu;
import com.powerfactor.pocketvoid.PocketVoidPayloads;
import com.powerfactor.pocketvoid.VoidContents;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The Pocket Void: nine deep cells across the top, each with its count and a fill bar; the selected (building)
 * cell glows violet, locked cells keep their kind. Below, the pickup mode and the fill.
 */
public class PocketVoidScreen extends AbstractContainerScreen<PocketVoidMenu> {
    private static final int W = 176, H = 166, CELL_Y = 22, CTRL_Y = 46, VIOLET = 0xFFB18CFF, LOCKED = 0xFF19C6D8;
    private static final Skin.Icon[] MODE_ICONS = {Skin.Icon.DENY, Skin.Icon.FILTER, Skin.Icon.INPUT, Skin.Icon.VOID};

    public PocketVoidScreen(PocketVoidMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H);
        inventoryLabelY = 10_000;
        titleLabelY = 10_000;
    }

    private VoidContents contents() {
        return PocketVoidItem.contents(menu.voidStack());
    }

    private int capacity() {
        return menu.voidStack().getItem() instanceof PocketVoidItem pv ? pv.capacity() : 1;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractBackground(g, mx, my, a);
        int x = leftPos, y = topPos;
        Skin.panel(g, x, y, W, H);
        Skin.title(g, font, title, x, y, W);
        VoidContents c = contents();
        int cap = capacity();
        long t = Util.getMillis();
        for (int i = 0; i < VoidContents.SLOTS; i++) {
            var s = c.slot(i);
            int cx = x + 7 + i * 18, cy = y + CELL_Y;
            boolean sel = i == c.selected();
            Skin.slot(g, cx, cy, sel ? VIOLET : 0);
            if (sel) {
                // The building cell breathes.
                int glow = (int) (60 + 50 * Math.sin(t / 300.0));
                g.fill(cx + 2, cy + 2, cx + 16, cy + 16, (glow << 24) | (VIOLET & 0xFFFFFF));
            }
            if (s.item().isPresent()) {
                g.item(s.display(), cx + 1, cy + 1);
                float f = Math.min(1F, s.count() / (float) cap);
                g.fill(cx + 2, cy + 16, cx + 16, cy + 17, 0xFF8AA0AF);
                if (f > 0) g.fill(cx + 2, cy + 16, cx + 2 + Math.max(1, Math.round(14 * f)), cy + 17, f >= 1 ? 0xFFFF5A4A : 0xFF19C6D8);
            }
            if (s.locked()) {
                g.fill(cx + 1, cy + 1, cx + 4, cy + 2, LOCKED);
                g.fill(cx + 1, cy + 1, cx + 2, cy + 4, LOCKED);
            }
        }
        // Mode chip (left) and fill readout (right), split about the centre line.
        boolean hoverMode = in(mx, my, x + 7, y + CTRL_Y, 79, 18);
        Skin.control(g, x + 7, y + CTRL_Y, 79, 18, c.mode() != VoidContents.Mode.OFF, hoverMode);
        Skin.lcd(g, x + 90, y + CTRL_Y, 79, 18);
        Skin.inventory(g, font, x + 8, y + PocketVoidMenu.INV_Y);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        VoidContents c = contents();
        for (int i = 0; i < VoidContents.SLOTS; i++) {
            var s = c.slot(i);
            if (s.count() <= 1) continue;
            String n = Skin.compact(s.count());
            int cx = 7 + i * 18, cy = CELL_Y;
            g.pose().pushMatrix();
            g.pose().translate(cx + 17, cy + 10);
            g.pose().scale(0.5F, 0.5F);
            g.text(font, Component.literal(n), -font.width(n) - 1, 0, 0xFFFFFFFF, true);
            g.pose().popMatrix();
        }
        String mode = c.mode().name().toLowerCase(java.util.Locale.ROOT);
        Skin.icon(g, MODE_ICONS[c.mode().ordinal()], 11, CTRL_Y + 5, null);
        Skin.condensed(g, font, Component.translatable("gui.powerfactor.pocket_void.mode." + mode), 23, CTRL_Y + 5, 60, 0xFFCDF6FF);
        int used = 0, kinds = 0;
        for (var s : c.slots()) {
            used += s.count();
            if (s.item().isPresent()) kinds++;
        }
        Skin.iconValue(g, font, Skin.Icon.ITEM, Component.literal(Skin.compact(used) + " / " + Skin.compact((double) capacity() * VoidContents.SLOTS)),
                94, CTRL_Y + 5, Skin.LCD_TEXT, Component.translatable("gui.powerfactor.pocket_void.fill", String.format("%,d", used), kinds));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        super.extractRenderState(g, mx, my, partial);
        int cell = cellAt(mx, my);
        if (cell >= 0 && menu.getCarried().isEmpty()) {
            VoidContents c = contents();
            var s = c.slot(cell);
            List<Component> lines = new ArrayList<>();
            if (s.item().isPresent()) {
                lines.add(s.display().getHoverName());
                lines.add(Component.translatable("gui.powerfactor.pocket_void.count", String.format("%,d", s.count()), String.format("%,d", capacity()))
                        .withColor(0xFF7FA4B4));
                if (s.locked()) lines.add(Component.translatable("gui.powerfactor.pocket_void.locked").withColor(LOCKED));
            } else {
                lines.add(Component.translatable("gui.powerfactor.pocket_void.empty"));
            }
            if (cell == c.selected()) lines.add(Component.translatable("gui.powerfactor.pocket_void.selected.tip").withColor(VIOLET));
            lines.add(Component.translatable("gui.powerfactor.pocket_void.help").withColor(0xFF5B7080));
            g.setComponentTooltipForNextFrame(font, lines, mx, my);
        } else if (in(mx, my, leftPos + 7, topPos + CTRL_Y, 79, 18)) {
            String mode = contents().mode().name().toLowerCase(java.util.Locale.ROOT);
            g.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.powerfactor.pocket_void.mode." + mode),
                    Component.translatable("gui.powerfactor.pocket_void.mode." + mode + ".tip").withColor(0xFF7FA4B4)), mx, my);
        }
    }

    private int cellAt(double mx, double my) {
        for (int i = 0; i < VoidContents.SLOTS; i++) {
            if (in(mx, my, leftPos + 7 + i * 18, topPos + CELL_Y, 18, 18)) return i;
        }
        return -1;
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int cell = cellAt(mx, my);
        int action = -1;
        if (cell >= 0) {
            if (event.button() == 2 || event.hasAltDown()) action = PocketVoidPayloads.SELECT;
            else if (event.hasControlDown()) action = PocketVoidPayloads.LOCK;
            else if (event.hasShiftDown()) action = PocketVoidPayloads.TO_INVENTORY;
            else if (event.button() == 1) action = PocketVoidPayloads.HALF_OR_ONE;
            else if (event.button() == 0) action = PocketVoidPayloads.TAKE_OR_PUT;
        } else if (in(mx, my, leftPos + 7, topPos + CTRL_Y, 79, 18) && event.button() == 0) {
            action = PocketVoidPayloads.MODE;
        }
        if (action >= 0) {
            ClientPacketDistributor.sendToServer(new PocketVoidPayloads.Click(cell, action));
            if (action == PocketVoidPayloads.MODE || action == PocketVoidPayloads.SELECT || action == PocketVoidPayloads.LOCK) {
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1F));
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
