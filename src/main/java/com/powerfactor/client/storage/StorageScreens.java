package com.powerfactor.client.storage;

import com.powerfactor.client.Skin;
import com.powerfactor.storage.CellTier;
import com.powerfactor.storage.DriveBayMenu;
import com.powerfactor.storage.IoPortBlockEntity;
import com.powerfactor.storage.IoPortMenu;
import com.powerfactor.storage.StorageCellItem;
import com.powerfactor.storage.craft.PatternRackMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Screens for the drive bay, the three ports and the pattern racks, in the shared storage style. */
public final class StorageScreens {
    static final int ACCENT = 0xFF6FE3FF, QUANTUM = 0xFFB18CFF, CRAFT = 0xFFFFB347;

    /** A 176x180 storage-device screen: panel, title, slot wells, player inventory and custom buttons. */
    public abstract static class Base<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {
        public record Btn(int x, int y, int w, int h, Component label, Component tip, int color, boolean selected, Runnable action,
                Skin.@org.jspecify.annotations.Nullable Icon icon, boolean dim) {
            public Btn(int x, int y, int w, int h, Component label, Component tip, int color, boolean selected, Runnable action) {
                this(x, y, w, h, label, tip, color, selected, action, null, false);
            }
        }

        protected final List<Btn> buttons = new ArrayList<>();

        protected Base(M menu, Inventory inv, Component title) {
            super(menu, inv, title, 176, 190);
        }

        protected abstract void main(GuiGraphicsExtractor g, int x, int y, int mx, int my);

        @Override
        public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
            super.extractBackground(g, mx, my, a);
            buttons.clear();
            int x = leftPos, y = topPos;
            boolean bay = menu.slots.stream().anyMatch(sl -> sl instanceof com.powerfactor.machine.MachineMenu.BaySlot);
            if (bay) Skin.panel(g, x + imageWidth - 4, y + 8, 32, 4 * 18 + 22);   // the upgrade bay, docked behind the right edge
            Skin.panel(g, x, y, imageWidth, imageHeight);
            if (bay) Skin.icon(g, Skin.Icon.UPGRADE, x + imageWidth + 9, y + 12, Component.translatable("gui.powerfactor.upgrades.bay"));
            Skin.title(g, font, title, x, y, imageWidth);
            for (Slot s : menu.slots) {
                if (s.container instanceof Inventory || !s.isActive()) continue;
                if (s instanceof com.powerfactor.machine.MachineMenu.BaySlot) {
                    Skin.slot(g, x + s.x - 1, y + s.y - 1, 0xFF9C74F0);
                    continue;
                }
                if (s instanceof IoPortMenu.GhostSlot) ghost(g, x + s.x - 1, y + s.y - 1);
                else Skin.slot(g, x + s.x - 1, y + s.y - 1, slotRing(s));
            }
            main(g, x, y, mx, my);
            Skin.inventory(g, font, x + 8, y + imageHeight - 82);
            for (Btn b : buttons) {
                boolean hover = mx >= b.x() && mx < b.x() + b.w() && my >= b.y() && my < b.y() + b.h();
                Skin.control(g, b.x(), b.y(), b.w(), b.h(), b.selected(), hover);
                boolean hasText = !b.label().getString().isEmpty();
                int iw = b.icon() == null ? 0 : hasText ? 11 : 9;
                int lw = hasText ? Math.min(b.w() - 4 - iw, font.width(b.label())) : 0;
                int cx = b.x() + (b.w() - iw - lw) / 2;
                if (b.icon() != null) {
                    Skin.icon(g, b.icon(), cx, b.y() + (b.h() - 9) / 2, null);
                    if (b.dim()) g.fill(cx, b.y() + (b.h() - 9) / 2, cx + 9, b.y() + (b.h() - 9) / 2 + 9, 0x9A17242E);
                }
                if (hasText) Skin.condensed(g, font, b.label(), cx + iw, b.y() + (b.h() - 8) / 2, b.w() - 4 - iw, b.color());
            }
        }

        /** Areas outside the main panel that JEI must keep clear: the upgrade bay. */
        public java.util.List<net.minecraft.client.renderer.Rect2i> extraAreas() {
            boolean bay = menu.slots.stream().anyMatch(sl -> sl instanceof com.powerfactor.machine.MachineMenu.BaySlot);
            return bay ? java.util.List.of(new net.minecraft.client.renderer.Rect2i(leftPos + imageWidth - 4, topPos + 8, 34, 4 * 18 + 24)) : java.util.List.of();
        }

        @Override
        protected boolean hasClickedOutside(double mx, double my, int left, int top) {
            boolean bay = menu.slots.stream().anyMatch(sl -> sl instanceof com.powerfactor.machine.MachineMenu.BaySlot);
            if (bay && mx >= left + imageWidth - 2 && mx < left + imageWidth + 28 && my >= top + 8 && my < top + 8 + 4 * 18 + 22) return false;
            return super.hasClickedOutside(mx, my, left, top);
        }

        protected int slotRing(Slot s) {
            return Skin.INPUT;
        }

        static void ghost(GuiGraphicsExtractor g, int x, int y) {
            Skin.well(g, x, y, 18, 18);
            g.fill(x + 2, y + 2, x + 16, y + 16, 0xFFEAF1F6);
            for (int i = 1; i < 17; i += 3) {
                g.fill(x + i, y + 1, x + i + 1, y + 2, 0xFF19C6D8);
                g.fill(x + i, y + 16, x + i + 1, y + 17, 0xFF19C6D8);
            }
        }

        protected void button(int x, int y, int w, int h, Component label, Component tip, int color, boolean selected, Runnable action) {
            buttons.add(new Btn(leftPos + x, topPos + y, w, h, label, tip, color, selected, action));
        }

        /** An icon chip (show, don't tell); a toggle that is off shows its icon dimmed. Label may be empty. */
        protected void iconButton(int x, int y, int w, int h, Skin.Icon icon, Component label, Component tip, int color, boolean on, boolean toggle,
                Runnable action) {
            buttons.add(new Btn(leftPos + x, topPos + y, w, h, label, tip, color, toggle && on, action, icon, toggle && !on));
        }

        protected void send(int id) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
            super.extractRenderState(g, mx, my, a);
            for (Btn b : buttons) {
                if (mx >= b.x() && mx < b.x() + b.w() && my >= b.y() && my < b.y() + b.h()) {
                    g.setTooltipForNextFrame(font, b.tip(), mx, my);
                    break;
                }
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            for (Btn b : List.copyOf(buttons)) {
                if (event.x() >= b.x() && event.x() < b.x() + b.w() && event.y() >= b.y() && event.y() < b.y() + b.h()) {
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.4F, 0.35F));
                    b.action().run();
                    return true;
                }
            }
            return super.mouseClicked(event, doubleClick);
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {}

        protected void priority(int value, int x, int y, int firstButton) {
            String[] labels = {"-10", "-1", "+1", "+10"};
            for (int i = 0; i < 4; i++) {
                int id = firstButton + i;
                int bx = x + i * 22;   // the guide's stepper: four 20-px chips, 2-px gutters (86 px)
                button(bx, y, 20, 13, Component.literal(labels[i]), Component.translatable("gui.powerfactor.storage.priority.tip"),
                        i < 2 ? 0xFFFFA090 : 0xFF8FF0B0, false, () -> send(id));
            }
        }
    }

    // ------------------------------------------------------------------ drive bay

    public static class DriveBay extends Base<DriveBayMenu> {
        public DriveBay(DriveBayMenu menu, Inventory inv, Component title) {
            super(menu, inv, title);
        }

        @Override
        protected void main(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
            long t = Util.getMillis();
            // Rack rails left and right of the bays, with a status LED per cell under each slot.
            // Rack rails either side of the bays.
            g.fill(x + DriveBayMenu.CELL_X - 5, y + DriveBayMenu.CELL_Y - 3, x + DriveBayMenu.CELL_X - 3, y + DriveBayMenu.CELL_Y + 37, 0xFF8FA6B5);
            g.fill(x + DriveBayMenu.CELL_X + 91, y + DriveBayMenu.CELL_Y - 3, x + DriveBayMenu.CELL_X + 93, y + DriveBayMenu.CELL_Y + 37, 0xFF8FA6B5);
            for (int i = 0; i < 10; i++) {
                ItemStack s = menu.getSlot(i).getItem();
                int lx = x + DriveBayMenu.CELL_X + (i % 5) * 18, ly = y + DriveBayMenu.CELL_Y + (i / 5) * 18 + 17;
                int color = 0xFF263038;
                if (s.getItem() instanceof StorageCellItem cell) {
                    float f = cell.fill(s);
                    color = f > 0.95F ? ((t / 250) % 2 == 0 ? 0xFFFF4040 : 0xFF601010) : f > 0.75F ? 0xFFFFC040 : cell.tier().color;
                }
                g.fill(lx + 4, ly - 1, lx + 12, ly, color);
            }
            Skin.iconValue(g, font, Skin.Icon.PRIORITY, Component.literal(String.valueOf(menu.priority())), x + 8, y + 70, Skin.TEXT,
                    Component.translatable("gui.powerfactor.storage.priority", menu.priority()));
            priority(menu.priority(), 83, 68, 0);   // right-aligned to the content edge (x 169)
            // Total capacity across cells.
            long used = 0, total = 0;
            for (int i = 0; i < 10; i++) {
                ItemStack s = menu.getSlot(i).getItem();
                if (s.getItem() instanceof StorageCellItem cell) {
                    total += cell.tier().bytes;
                    used += Math.round(cell.fill(s) * cell.tier().bytes);
                }
            }
            Skin.bar(g, x + 8, y + 20, 160, total == 0 ? 0 : (float) used / total, ACCENT);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
            super.extractRenderState(g, mx, my, a);
            if (mx >= leftPos + 8 && mx < leftPos + 168 && my >= topPos + 19 && my < topPos + 24) {
                long used = 0, total = 0;
                for (int i = 0; i < 10; i++) {
                    ItemStack s = menu.getSlot(i).getItem();
                    if (s.getItem() instanceof StorageCellItem cell) {
                        total += cell.tier().bytes;
                        used += Math.round(cell.fill(s) * cell.tier().bytes);
                    }
                }
                g.setTooltipForNextFrame(font, Component.translatable("gui.powerfactor.drive.capacity", String.format("%,d", used), String.format("%,d", total)), mx, my);
            }
        }
    }

    // ------------------------------------------------------------------ ports

    public static class IoPort extends Base<IoPortMenu> {
        public IoPort(IoPortMenu menu, Inventory inv, Component title) {
            super(menu, inv, title);
        }

        @Override
        protected void main(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
            IoPortBlockEntity.Kind kind = menu.kind();
            Skin.icon(g, Skin.Icon.FILTER, x + 8, y + 22, Component.translatable(menu.deny() ? "gui.powerfactor.port.deny" : "gui.powerfactor.port.allow"));
            g.text(font, Component.translatable(menu.deny() ? "gui.powerfactor.port.deny" : "gui.powerfactor.port.allow"), x + 19, y + 23,
                    menu.deny() ? 0xFFC8402A : 0xFF1E9E5A, false);
            iconButton(8, 56, 22, 14, menu.deny() ? Skin.Icon.DENY : Skin.Icon.ALLOW, Component.empty(), Component.translatable("gui.powerfactor.port.mode.tip"),
                    0, false, false, () -> send(IoPortMenu.BUTTON_DENY));
            iconButton(32, 56, 22, 14, Skin.Icon.EXACT, Component.empty(), Component.translatable(menu.exact() ? "gui.powerfactor.port.exact.tip"
                    : "gui.powerfactor.port.fuzzy"), ACCENT, menu.exact(), true, () -> send(IoPortMenu.BUTTON_EXACT));
            if (kind == IoPortBlockEntity.Kind.STORAGE_BUS) {
                Skin.iconValue(g, font, Skin.Icon.PRIORITY, Component.literal(String.valueOf(menu.priority())), x + 8, y + 81, Skin.TEXT,
                        Component.translatable("gui.powerfactor.storage.priority", menu.priority()));
                priority(menu.priority(), 83, 78, IoPortMenu.BUTTON_PRIORITY);
            } else {
                // Arrows show which way items flow.
                long t = Util.getMillis();
                boolean in = kind == IoPortBlockEntity.Kind.IMPORTER;
                // Bottom-left (beside the upgrade row): which way items flow, with marching arrows.
                Skin.icon(g, in ? Skin.Icon.INPUT : Skin.Icon.OUTPUT, x + 8, y + 75);
                Skin.condensed(g, font, Component.translatable(in ? "gui.powerfactor.port.importing" : "gui.powerfactor.port.exporting"), x + 19, y + 76, 71,
                        in ? 0xFF0E8FA6 : 0xFFC06A00);
                for (int k = 0; k < 6; k++) {
                    int px = (int) ((t / 40 + k * 14) % 82);
                    int ax = x + 8 + (in ? 82 - px : px);
                    g.fill(ax, y + 88, ax + 3, y + 90, in ? 0xFF19C6D8 : 0xFFE08A1A);
                }
                Skin.icon(g, Skin.Icon.SPEED, x + 160, y + 61, Component.translatable("gui.powerfactor.port.speed_upgrades"));
            }
        }
    }

    // ------------------------------------------------------------------ pattern racks

    public static class PatternRack extends Base<PatternRackMenu> {
        public PatternRack(PatternRackMenu menu, Inventory inv, Component title) {
            super(menu, inv, title);
        }

        @Override
        protected void main(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
            long t = Util.getMillis();
            // A softly glowing circuit trace around the pattern matrix.
            int bx = x + PatternRackMenu.PATTERN_X - 4, by = y + PatternRackMenu.PATTERN_Y - 4, size = 3 * 18 + 6;
            int glow = menu.assembler() ? QUANTUM : CRAFT;
            g.fill(bx, by, bx + size, by + 1, glow & 0x80FFFFFF);
            g.fill(bx, by + size - 1, bx + size, by + size, glow & 0x80FFFFFF);
            g.fill(bx, by, bx + 1, by + size, glow & 0x80FFFFFF);
            g.fill(bx + size - 1, by, bx + size, by + size, glow & 0x80FFFFFF);
            int perimeter = size * 4, p = (int) ((t / 15) % perimeter);
            int px = p < size ? bx + p : p < size * 2 ? bx + size - 1 : p < size * 3 ? bx + size * 3 - p : bx;
            int py = p < size ? by : p < size * 2 ? by + p - size : p < size * 3 ? by + size - 1 : by + size * 4 - p;
            g.fill(px - 1, py - 1, px + 2, py + 2, 0xFFFFFFFF);
            g.text(font, Component.translatable(menu.assembler() ? "gui.powerfactor.assembler.hint" : "gui.powerfactor.provider.hint"), x + 8, y + 82,
                    Skin.DIM, false);
            if (!menu.assembler()) {
                button(8, 22, 46, 14, Component.translatable(menu.blocking() ? "gui.powerfactor.provider.blocking" : "gui.powerfactor.provider.free"),
                        Component.translatable("gui.powerfactor.provider.blocking.tip"), CRAFT, menu.blocking(), () -> send(PatternRackMenu.BUTTON_BLOCKING));
            }
        }
    }

    private StorageScreens() {}
}
