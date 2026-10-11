package com.powerfactor.client;

import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.machine.ProcessingMenu;
import com.powerfactor.machine.SideConfig;
import com.powerfactor.power.UpgradeEffects;
import com.powerfactor.registry.ModItems;
import com.powerfactor.registry.ModSounds;
import com.powerfactor.upgrade.UpgradeItem;
import com.powerfactor.upgrade.UpgradeType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * A 176-wide machine GUI with side tabs on its right edge. One tab can be open at a time; its panel
 * opens to the right of the tab column. The last opened tab is remembered across machines.
 */
public abstract class MachineScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {
    public enum Tab { ELECTRICAL, UPGRADES, CONFIG }

    /** What the Electrical tab shows. */
    public record Electrical(double volts, double hertz, double fe, double ee, double ratedCe, Component status, int statusColor) {}

    protected static final int MAIN_W = 176;
    private static final int TAB_X = MAIN_W - 1, TAB_W = 26, TAB_H = 24, TAB_GAP = 2;
    private static final int PANEL_X = MAIN_W + TAB_W - 1;
    private static final int BG = 0xFF0A100D, TEXT = 0xFFB8F5C0, DIM = 0xFF5F8A66;
    private static @Nullable Tab lastOpen;

    private @Nullable Tab open;
    private final ElectricalPanel panel = new ElectricalPanel();

    protected MachineScreen(M menu, Inventory inventory, Component title, int height) {
        super(menu, inventory, title, MAIN_W, height);
        inventoryLabelY = height - 94;
    }

    protected abstract List<Tab> tabs();

    protected abstract Electrical electrical();

    /** Draws the main 176-wide face. */
    protected abstract void extractMain(GuiGraphicsExtractor g, int mouseX, int mouseY);

    @Override
    protected void init() {
        super.init();
        open = lastOpen != null && tabs().contains(lastOpen) ? lastOpen : null;
        reposition();
    }

    /** Slide the whole GUI left when an open panel would run off the right edge of the screen. */
    private void reposition() {
        leftPos = (width - imageWidth) / 2;
        int right = leftPos + (open == null ? TAB_X + TAB_W : PANEL_X + panelWidth(open)) + 2;
        if (right > width) {
            leftPos = Math.max(2, leftPos - (right - width));
        }
    }

    @Override
    public final void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        if (menu instanceof ProcessingMenu pm) {
            pm.setUpgradeSlotsActive(open == Tab.UPGRADES);
        }
        extractMain(g, mouseX, mouseY);
        List<Tab> tabs = tabs();
        for (int i = 0; i < tabs.size(); i++) {
            tabButton(g, tabs.get(i), leftPos + TAB_X, topPos + 4 + i * (TAB_H + TAB_GAP), tabs.get(i) == open, mouseX, mouseY);
        }
        if (open != null) {
            int x = leftPos + PANEL_X, y = topPos;
            switch (open) {
                case ELECTRICAL -> {
                    Electrical e = electrical();
                    panel.draw(g, font, x, y, e.volts(), e.hertz(), e.fe(), e.ee(), e.ratedCe(), e.status(), e.statusColor());
                }
                case UPGRADES -> upgradesPanel(g, x, y);
                case CONFIG -> configPanel(g, x, y, mouseX, mouseY);
            }
        }
    }

    private void tabButton(GuiGraphicsExtractor g, Tab tab, int x, int y, boolean selected, int mx, int my) {
        boolean hover = mx >= x && mx < x + TAB_W && my >= y && my < y + TAB_H;
        g.fill(x, y, x + TAB_W, y + TAB_H, 0xFF2B3036);
        g.fill(x, y + 1, x + TAB_W - 1, y + TAB_H - 1, selected ? 0xFF16362A : hover ? 0xFF8C949B : 0xFF6F767D);
        g.fill(x + 1, y + 2, x + TAB_W - 2, y + TAB_H - 2, selected ? 0xFF0D1710 : 0xFFC6C6C6);
        ItemStack icon = switch (tab) {
            case ELECTRICAL -> new ItemStack(ModItems.COPPER_COIL.get());
            case UPGRADES -> new ItemStack(ModItems.SPEED_UPGRADE.get());
            case CONFIG -> new ItemStack(Items.REDSTONE_TORCH);
        };
        g.item(icon, x + 4, y + 4);
    }

    private int panelWidth(Tab tab) {
        return switch (tab) {
            case ELECTRICAL -> ElectricalPanel.WIDTH;
            case UPGRADES -> 116;
            case CONFIG -> 124;
        };
    }

    private int panelHeight(Tab tab) {
        return switch (tab) {
            case ELECTRICAL -> ElectricalPanel.HEIGHT;
            case UPGRADES -> 140;
            case CONFIG -> menu instanceof ProcessingMenu ? 162 : 48;
        };
    }

    private void frame(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF2B3036);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF6F767D);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, BG);
    }

    private void upgradesPanel(GuiGraphicsExtractor g, int x, int y) {
        int w = panelWidth(Tab.UPGRADES);
        frame(g, x, y, w, panelHeight(Tab.UPGRADES));
        g.text(font, Component.translatable("gui.powerfactor.tab.upgrades"), x + 6, y + 6, TEXT, false);
        for (Slot slot : menu.slots) {
            if (slot instanceof ProcessingMenu.UpgradeSlot) {
                int sx = leftPos + slot.x - 1, sy = topPos + slot.y - 1;
                g.fill(sx, sy, sx + 18, sy + 18, 0xFF373737);
                g.fill(sx + 1, sy + 1, sx + 18, sy + 18, 0xFFFFFFFF);
                g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF1B2B23);
            }
        }
        // Live summary of what the installed cards do.
        UpgradeEffects up = clientEffects();
        List<Component> lines = new ArrayList<>();
        if (up.speed() > 0) lines.add(line(UpgradeType.SPEED, String.format("x%.1f", up.speedMultiplier())));
        if (up.speed() > 0 || up.efficiency() > 0) lines.add(line(UpgradeType.EFFICIENCY, String.format("x%.2f", up.energyPerOperation())));
        if (up.capacitor() > 0) lines.add(line(UpgradeType.CAPACITOR, "-" + up.capacitor() * UpgradeEffects.CAPACITOR_EE_PER_CARD + " EE"));
        if (up.vfd()) lines.add(line(UpgradeType.VFD, ""));
        if (up.surge()) lines.add(line(UpgradeType.SURGE, String.format("%.2f pu", up.tripVolts())));
        if (up.capacity() > 0) lines.add(line(UpgradeType.CAPACITY, String.format("x%.0f", up.bufferMultiplier())));
        if (lines.isEmpty()) {
            g.textWithWordWrap(font, Component.translatable("gui.powerfactor.tab.upgrades.empty"), x + 6, y + 70, w - 12, DIM, false);
        }
        for (int i = 0; i < lines.size(); i++) {
            g.text(font, lines.get(i), x + 6, y + 70 + i * 11, TEXT, false);
        }
    }

    private Component line(UpgradeType type, String value) {
        return Component.translatable("gui.powerfactor.upgrade_summary." + type.getSerializedName(), value).withColor(type.color);
    }

    private UpgradeEffects clientEffects() {
        Map<UpgradeType, Integer> counts = new EnumMap<>(UpgradeType.class);
        for (Slot slot : menu.slots) {
            if (slot instanceof ProcessingMenu.UpgradeSlot && slot.getItem().getItem() instanceof UpgradeItem u) {
                counts.merge(u.type(), slot.getItem().getCount(), Integer::sum);
            }
        }
        return new UpgradeEffects(counts.getOrDefault(UpgradeType.SPEED, 0), counts.getOrDefault(UpgradeType.EFFICIENCY, 0),
                counts.getOrDefault(UpgradeType.CAPACITOR, 0), counts.containsKey(UpgradeType.VFD), counts.containsKey(UpgradeType.SURGE),
                counts.getOrDefault(UpgradeType.CAPACITY, 0));
    }

    // ------------------------------------------------------------------ config tab

    protected MachineBlockEntity.@Nullable RedstoneMode redstoneMode() {
        return menu instanceof ProcessingMenu pm ? pm.redstoneMode() : null;
    }

    private record Button(int id, int x, int y, int w, int h, Component label) {}

    /** Thermal-style face diagram: top above, left/front/right across, bottom below with back beside it. */
    private static final int[][] FACE_GRID = {
            {1, 1}, // FRONT
            {2, 2}, // BACK
            {0, 1}, // LEFT
            {2, 1}, // RIGHT
            {1, 0}, // TOP
            {1, 2}, // BOTTOM
    };
    private static final int TILE = 18, TILE_GAP = 2, DIAGRAM_Y = 52;

    private List<Button> configButtons(int x, int y) {
        List<Button> buttons = new ArrayList<>();
        int w = panelWidth(Tab.CONFIG) - 12;
        MachineBlockEntity.@Nullable RedstoneMode mode = redstoneMode();
        if (mode != null) {
            buttons.add(new Button(ProcessingMenu.BUTTON_REDSTONE, x + 6, y + 28, w, 12,
                    Component.translatable("gui.powerfactor.config.redstone." + mode.name().toLowerCase(java.util.Locale.ROOT))));
        }
        if (menu instanceof ProcessingMenu pm) {
            for (SideConfig.Face face : SideConfig.Face.values()) {
                int[] cell = FACE_GRID[face.ordinal()];
                buttons.add(new Button(ProcessingMenu.BUTTON_SIDE_NEXT + face.ordinal(), x + 8 + cell[0] * (TILE + TILE_GAP),
                        y + DIAGRAM_Y + cell[1] * (TILE + TILE_GAP), TILE, TILE, Component.empty()));
            }
            buttons.add(new Button(ProcessingMenu.BUTTON_AUTO_INPUT, x + 6, y + 128, w, 12,
                    Component.translatable(pm.autoInput() ? "gui.powerfactor.config.input.on" : "gui.powerfactor.config.input.off")));
            buttons.add(new Button(ProcessingMenu.BUTTON_AUTO_EJECT, x + 6, y + 143, w, 12,
                    Component.translatable(pm.autoEject() ? "gui.powerfactor.config.eject.on" : "gui.powerfactor.config.eject.off")));
        }
        return buttons;
    }

    private void configPanel(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        frame(g, x, y, panelWidth(Tab.CONFIG), panelHeight(Tab.CONFIG));
        g.text(font, Component.translatable("gui.powerfactor.tab.config"), x + 6, y + 6, TEXT, false);
        g.text(font, Component.translatable("gui.powerfactor.config.redstone"), x + 6, y + 18, DIM, false);
        if (menu instanceof ProcessingMenu) {
            g.text(font, Component.translatable("gui.powerfactor.config.sides"), x + 6, y + 43, DIM, false);
        }
        for (Button b : configButtons(x, y)) {
            boolean hover = mx >= b.x() && mx < b.x() + b.w() && my >= b.y() && my < b.y() + b.h();
            int id = b.id() - ProcessingMenu.BUTTON_SIDE_NEXT;
            if (menu instanceof ProcessingMenu pm && id >= 0 && id < SideConfig.Face.values().length) {
                faceTile(g, pm, SideConfig.Face.values()[id], b, hover);
                continue;
            }
            g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), hover ? 0xFF2A5A3A : 0xFF16362A);
            g.outline(b.x(), b.y(), b.w(), b.h(), 0xFF5F8A66);
            g.centeredText(font, b.label(), b.x() + b.w() / 2, b.y() + 2, TEXT);
        }
        if (menu instanceof ProcessingMenu) {
            // Legend beside the diagram.
            int lx = x + 72, ly = y + DIAGRAM_Y + 2;
            for (SideConfig.Mode m : SideConfig.Mode.values()) {
                g.fill(lx, ly + 1, lx + 6, ly + 7, m.color);
                g.text(font, Component.translatable("gui.powerfactor.config.mode." + m.name().toLowerCase(java.util.Locale.ROOT)),
                        lx + 9, ly, TEXT, false);
                ly += 12;
            }
        }
    }

    private void faceTile(GuiGraphicsExtractor g, ProcessingMenu pm, SideConfig.Face face, Button b, boolean hover) {
        SideConfig.Mode mode = pm.side(face);
        g.fill(b.x(), b.y(), b.x() + TILE, b.y() + TILE, hover ? 0xFFFFFFFF : 0xFF2B3036);
        g.fill(b.x() + 1, b.y() + 1, b.x() + TILE - 1, b.y() + TILE - 1, mode.color);
        g.fill(b.x() + 3, b.y() + 3, b.x() + TILE - 3, b.y() + TILE - 3, darken(mode.color));
        if (face == SideConfig.Face.FRONT && minecraft.level != null) {
            g.item(new ItemStack(machineIcon()), b.x() + 1, b.y() + 1);
        } else {
            String letter = Component.translatable("gui.powerfactor.config.face." + face.name().toLowerCase(java.util.Locale.ROOT)).getString();
            g.centeredText(font, letter, b.x() + TILE / 2, b.y() + 5, 0xFFFFFFFF);
        }
    }

    /** The block this screen belongs to, for the diagram's front tile. */
    protected net.minecraft.world.level.ItemLike machineIcon() {
        return Items.FURNACE;
    }

    private static int darken(int argb) {
        return 0xFF000000 | ((argb >> 17) & 0x7F) << 16 | ((argb >> 9) & 0x7F) << 8 | ((argb >> 1) & 0x7F);
    }

    /** Screen areas outside the main panel that we draw on: the tab column and any open panel. For JEI. */
    public List<net.minecraft.client.renderer.Rect2i> extraAreas() {
        List<net.minecraft.client.renderer.Rect2i> areas = new ArrayList<>();
        areas.add(new net.minecraft.client.renderer.Rect2i(leftPos + TAB_X, topPos, TAB_W, 4 + tabs().size() * (TAB_H + TAB_GAP)));
        if (open != null) {
            areas.add(new net.minecraft.client.renderer.Rect2i(leftPos + PANEL_X, topPos, panelWidth(open), panelHeight(open)));
        }
        return areas;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        List<Tab> tabs = tabs();
        for (int i = 0; i < tabs.size(); i++) {
            int tx = leftPos + TAB_X, ty = topPos + 4 + i * (TAB_H + TAB_GAP);
            if (mx >= tx && mx < tx + TAB_W && my >= ty && my < ty + TAB_H) {
                open = open == tabs.get(i) ? null : tabs.get(i);
                lastOpen = open;
                reposition();
                click();
                return true;
            }
        }
        if (open == Tab.CONFIG) {
            for (Button b : configButtons(leftPos + PANEL_X, topPos)) {
                if (mx >= b.x() && mx < b.x() + b.w() && my >= b.y() && my < b.y() + b.h()) {
                    int id = b.id();
                    // Right-click cycles a face backwards.
                    if (event.button() == 1 && id >= ProcessingMenu.BUTTON_SIDE_NEXT && id < ProcessingMenu.BUTTON_SIDE_PREV) {
                        id += ProcessingMenu.BUTTON_SIDE_PREV - ProcessingMenu.BUTTON_SIDE_NEXT;
                    }
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
                    click();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.SCADA_CLICK.get(), 1.0F));
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int xo, int yo) {
        if (mx >= xo + TAB_X && mx < xo + TAB_X + TAB_W && my >= yo && my < yo + 4 + tabs().size() * (TAB_H + TAB_GAP)) {
            return false;
        }
        if (open != null && mx >= xo + PANEL_X && mx < xo + PANEL_X + panelWidth(open) && my >= yo && my < yo + panelHeight(open)) {
            return false;
        }
        return super.hasClickedOutside(mx, my, xo, yo);
    }

    // ------------------------------------------------------------------ shared drawing

    protected static int driveColor(com.powerfactor.machine.Drive.State state, double speed) {
        return switch (state) {
            case RUNNING -> speed >= 0.9 ? 0xFF55FF55 : 0xFFFFB020;
            case IDLE -> 0xFFFFB020;
            case TRIPPED -> 0xFFFF4040;
            case OFF -> DIM;
        };
    }
}
