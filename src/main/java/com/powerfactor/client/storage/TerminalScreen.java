package com.powerfactor.client.storage;

import com.powerfactor.client.Skin;
import com.powerfactor.storage.GridPayloads;
import com.powerfactor.storage.TerminalBlockEntity;
import com.powerfactor.storage.TerminalMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * The storage terminal: a searchable, sortable view of the whole network with AE2-style clicks,
 * a crafting grid or pattern encoder underneath, and overlays for ordering crafts and watching jobs.
 */
public class TerminalScreen extends AbstractContainerScreen<TerminalMenu> {
    private static final int W = TerminalMenu.WIDTH, GX = TerminalMenu.GRID_X, GY = TerminalMenu.GRID_Y, COLS = TerminalMenu.COLS;
    private static final int SCROLL_X = 175, ACCENT = 0xFF19C6D8, QUANTUM = 0xFFB18CFF, CRAFT = 0xFFFFB347, MISSING = 0xFFFF6464;

    // Saved across screens for the session, like AE2's terminal settings.
    private static ClientGrid.Sort sort = ClientGrid.Sort.COUNT;
    private static boolean descending;
    private static ClientGrid.View viewMode = ClientGrid.View.ALL;
    private static String lastSearch = "";

    private enum Modal { NONE, AMOUNT, PLAN, JOBS }

    private record Btn(int x, int y, int w, int h, Component label, int color, boolean enabled, Runnable action) {
        boolean over(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final int rows;
    private EditBox search, amountBox;
    private List<ClientGrid.Stack> view = List.of();
    private long viewRevision = -1;
    private String viewKey = "";
    private int scroll;
    private boolean draggingScroll;
    private Modal modal = Modal.NONE;
    private @Nullable ItemResource craftTarget;
    private GridPayloads.@Nullable PlanReply plan;
    private GridPayloads.@Nullable Jobs jobs;
    private int listScroll;
    private boolean waiting;
    private final List<Btn> buttons = new ArrayList<>();
    private int hoveredCell = -1;

    /** The title plate is 60 px: "Crafting Terminal" reads as "Crafting" (the full name is in its tooltip). */
    private Component plateTitle() {
        String full = title.getString();
        if (font.width(full) * 0.85F <= 60) return title;
        String shorter = full.replace(" Terminal", "");
        if (font.width(shorter) * 0.85F > 60) shorter = shorter.replace("Wireless ", "").replace("Quantum ", "");
        return Component.literal(shorter);
    }

    public TerminalScreen(TerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, TerminalMenu.height(menu.kind(), menu.rows()));
        this.rows = menu.rows();
        this.inventoryLabelY = TerminalMenu.inventoryTop(menu.kind(), menu.rows()) - 11;
        GridPayloads.planHandler = this::onPlan;
        GridPayloads.jobsHandler = j -> jobs = j;
    }

    @Override
    protected void init() {
        super.init();
        search = addRenderableWidget(new EditBox(font, leftPos + 84, topPos + 6, 86, 10, Component.translatable("gui.powerfactor.terminal.search")));
        search.setBordered(false);
        search.setMaxLength(64);
        search.setTextColor(0xFFDDF6FF);
        search.setHint(Component.translatable("gui.powerfactor.terminal.search_hint").withColor(0xFF5E7C88));
        search.setValue(lastSearch);
        search.setResponder(s -> {
            lastSearch = s;
            scroll = 0;
        });
        amountBox = addWidget(new EditBox(font, leftPos + 62, topPos + 58, 70, 12, Component.translatable("gui.powerfactor.craft.amount")));
        amountBox.setBordered(false);
        amountBox.setMaxLength(7);
        amountBox.setTextColor(0xFFFFE2B0);
        amountBox.visible = false;
    }

    // ------------------------------------------------------------------ data

    private void refreshView() {
        String key = lastSearch + "|" + sort + descending + viewMode;
        if (ClientGrid.revision() != viewRevision || !key.equals(viewKey)) {
            view = ClientGrid.view(lastSearch, sort, descending, viewMode);
            viewRevision = ClientGrid.revision();
            viewKey = key;
        }
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    private int maxScroll() {
        return Math.max(0, (view.size() + COLS - 1) / COLS - rows);
    }

    private ClientGrid.@Nullable Stack stackAt(double mx, double my) {
        int cx = (int) Math.floor((mx - leftPos - GX + 1) / 18), cy = (int) Math.floor((my - topPos - GY + 1) / 18);
        if (mx < leftPos + GX - 1 || cx < 0 || cx >= COLS || my < topPos + GY - 1 || cy < 0 || cy >= rows) return null;
        int i = (scroll + cy) * COLS + cx;
        hoveredCell = cy * COLS + cx;
        return i < view.size() ? view.get(i) : null;
    }

    private boolean overGrid(double mx, double my) {
        return mx >= leftPos + GX - 1 && mx < leftPos + GX - 1 + COLS * 18 && my >= topPos + GY - 1 && my < topPos + GY - 1 + rows * 18;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractBackground(g, mx, my, a);
        refreshView();
        int x = leftPos, y = topPos;
        long t = Util.getMillis();
        Skin.panel(g, x, y, W, imageHeight);
        // Header: engraved title plate with the network status lamp; search LCD on the right.
        g.fill(x + 6, y + 5, x + 8, y + 14, ACCENT);
        int lamp = switch (ClientGrid.status) {
            case 0 -> pulse(0xFF40FF9A, t, 900);
            case 1 -> 0xFFFF5040;
            case 2 -> (t / 300) % 2 == 0 ? 0xFFFFB020 : 0xFF6A4410;
            default -> 0xFF505A60;
        };
        Skin.lamp(g, x + 11, y + 6, lamp);
        Skin.condensed(g, font, plateTitle(), x + 19, y + 6, 60, Skin.TEXT);
        Skin.lcd(g, x + 81, y + 3, 93, 14);
        // Side tabs.
        sideTabs(g, mx, my, t);
        // Item grid.
        Skin.well(g, x + GX - 2, y + GY - 2, COLS * 18 + 2, rows * 18 + 2);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < COLS; c++) {
                int cx = x + GX - 1 + c * 18, cy = y + GY - 1 + r * 18;
                g.fillGradient(cx, cy, cx + 18, cy + 18, 0xFFF3F7FA, 0xFFE1E9EF);
                g.fill(cx, cy + 17, cx + 18, cy + 18, 0xFFC2D0DA);
                g.fill(cx + 17, cy, cx + 18, cy + 17, 0xFFC2D0DA);
            }
        }
        // A slow scan line sweeps the grid: the network is alive.
        if (ClientGrid.status == 0) {
            int span = rows * 18;
            int sy = (int) ((t / 25) % (span + 40)) - 20;
            if (sy > 0 && sy < span) {
                g.fillGradient(x + GX - 1, y + GY - 1 + Math.max(0, sy - 10), x + GX - 1 + COLS * 18, y + GY - 1 + sy, 0x0019C6D8, 0x3019C6D8);
            }
        }
        // Scrollbar.
        int trackH = rows * 18;
        Skin.well(g, x + SCROLL_X - 1, y + GY - 2, 14, trackH + 2);
        int max = maxScroll();
        int thumbH = max == 0 ? trackH - 2 : Math.max(10, (trackH - 2) * rows / (rows + max));
        int thumbY = y + GY - 1 + (max == 0 ? 0 : (trackH - 2 - thumbH) * scroll / max);
        boolean thumbHover = mx >= x + SCROLL_X && mx < x + SCROLL_X + 12 && my >= thumbY && my < thumbY + thumbH;
        g.fill(x + SCROLL_X, thumbY, x + SCROLL_X + 12, thumbY + thumbH, max == 0 ? 0xFFB4C4CF : thumbHover || draggingScroll ? ACCENT : 0xFF6F8797);
        g.fill(x + SCROLL_X, thumbY, x + SCROLL_X + 12, thumbY + 1, 0x60FFFFFF);
        for (int k = -2; k <= 2; k += 2) g.fill(x + SCROLL_X + 3, thumbY + thumbH / 2 + k, x + SCROLL_X + 9, thumbY + thumbH / 2 + k + 1, 0x60000000);
        // Capacity bar under the grid.
        int barY = y + GY + rows * 18 + 1;
        float fill = ClientGrid.totalBytes <= 0 ? 0 : (float) ClientGrid.usedBytes / ClientGrid.totalBytes;
        g.fill(x + GX - 1, barY, x + GX - 1 + COLS * 18, barY + 3, 0xFFB7C7D3);
        int fw = Math.round(COLS * 18 * Mth.clamp(fill, 0, 1));
        if (fw > 0) {
            g.fillGradient(x + GX - 1, barY, x + GX - 1 + fw, barY + 3, fill > 0.9F ? 0xFFFF6A50 : ACCENT, fill > 0.9F ? 0xFFB02818 : QUANTUM);
            int shimmer = (int) ((t / 12) % (fw + 30)) - 15;
            if (shimmer > 0 && shimmer < fw) g.fill(x + GX - 1 + shimmer, barY, x + GX + shimmer + 3, barY + 3, 0x80FFFFFF);
        }
        lowerArea(g, x, y + GY + rows * 18 + 6, mx, my, t);
        Skin.inventory(g, font, x + 9, y + TerminalMenu.inventoryTop(menu.kind(), rows));
    }

    private void sideTabs(GuiGraphicsExtractor g, int mx, int my, long t) {
        buttons.clear();
        int x = leftPos - 24, y = topPos + 6;
        ItemStack sortIcon = new ItemStack(switch (sort) {
            case NAME -> net.minecraft.world.item.Items.NAME_TAG;
            case COUNT -> net.minecraft.world.item.Items.COMPARATOR;
            case MOD -> net.minecraft.world.item.Items.BOOK;
        });
        tab(g, x, y, sortIcon, null, mx, my, () -> {
            sort = ClientGrid.Sort.values()[(sort.ordinal() + 1) % 3];
            click();
        });
        tab(g, x, y + 24, ItemStack.EMPTY, descending ? "▼" : "▲", mx, my, () -> {
            descending = !descending;
            click();
        });
        ItemStack viewIcon = switch (viewMode) {
            case ALL -> new ItemStack(net.minecraft.world.item.Items.CHEST);
            case STORED -> new ItemStack(com.powerfactor.registry.ModStorage.DRIVE_BAY.get());
            case CRAFTABLE -> new ItemStack(net.minecraft.world.item.Items.CRAFTING_TABLE);
        };
        tab(g, x, y + 48, viewIcon, null, mx, my, () -> {
            viewMode = ClientGrid.View.values()[(viewMode.ordinal() + 1) % 3];
            scroll = 0;
            click();
        });
        tab(g, x, y + 72, new ItemStack(com.powerfactor.registry.ModStorage.QUANTUM_CRAFTING_PROCESSOR.get()), null, mx, my, this::openJobs);
        if (ClientGrid.jobs > 0) {
            // Badge, with a spinner while jobs run.
            g.fill(x + 15, y + 71, x + 24, y + 80, 0xFF8C6CFF);
            g.text(font, String.valueOf(Math.min(9, ClientGrid.jobs)), x + 17, y + 72, 0xFFFFFFFF, false);
            double ang = t / 160.0;
            for (int k = 0; k < 3; k++) {
                double a = ang + k * 2.094;
                int px = x + 11 + (int) Math.round(Math.cos(a) * 9), py = y + 83 + (int) Math.round(Math.sin(a) * 9);
                g.fill(px, py, px + 2, py + 2, 0xFF8C6CFF);
            }
        }
    }

    private void tab(GuiGraphicsExtractor g, int x, int y, ItemStack icon, @Nullable String glyph, int mx, int my, Runnable action) {
        boolean hover = mx >= x && mx < x + 22 && my >= y && my < y + 22;
        Skin.control(g, x, y, 22, 22, false, hover);
        if (!icon.isEmpty()) {
            g.item(icon, x + 3, y + 3);
        } else if (glyph != null) {
            g.pose().pushMatrix();
            g.pose().translate(x + 11, y + 11);
            g.pose().scale(1.6F, 1.6F);
            g.centeredText(font, Component.literal(glyph), 0, -4, ACCENT);
            g.pose().popMatrix();
        }
        buttons.add(new Btn(x, y, 22, 22, Component.literal(glyph == null ? "" : glyph), ACCENT, true, action));
    }

    private void lowerArea(GuiGraphicsExtractor g, int x, int y, int mx, int my, long t) {
        switch (menu.kind()) {
            case CRAFTING -> {
                // Clear, grid, chevrons and result as one group centred on the item grid above (x 90).
                for (int i = 0; i < 9; i++) Skin.slot(g, x + 41 + (i % 3) * 18, y + (i / 3) * 18, Skin.INPUT);
                Skin.chevrons(g, x + 102, y + 23, 3, 1, !menu.getSlot(0).getItem().isEmpty());
                Skin.bigSlot(g, x + 131, y + 14, Skin.OUTPUT);
                miniButton(g, x + 22, y + 18, Component.literal("✕"), Skin.DIM, mx, my, Component.translatable("gui.powerfactor.terminal.clear_grid"),
                        () -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, TerminalMenu.BUTTON_CLEAR_GRID));
            }
            case PATTERN -> {
                boolean proc = menu.processingMode();
                int ring = proc ? CRAFT : ACCENT;
                for (int i = 0; i < 9; i++) ghostWell(g, x + 8 + (i % 3) * 18, y + (i / 3) * 18, ring);
                for (int i = 0; i < 3; i++) ghostWell(g, x + 98, y + i * 18, Skin.OUTPUT);
                if (!proc) {
                    // Crafting patterns compute their own output; show where it would go as a dim hint.
                    g.fill(x + 99, y + 19, x + 115, y + 35, 0x30FFFFFF);
                }
                Skin.chevrons(g, x + 73, y + 23, 3, 1, false);
                Skin.slot(g, x + 146, y, Skin.INPUT);
                Skin.slot(g, x + 146, y + 36, Skin.OUTPUT);
                miniButton(g, x + 122, y + 1, Component.literal(proc ? "P" : "C"), proc ? CRAFT : ACCENT, mx, my,
                        Component.translatable(proc ? "gui.powerfactor.pattern.mode.processing" : "gui.powerfactor.pattern.mode.crafting"),
                        () -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, TerminalMenu.BUTTON_MODE));
                miniButton(g, x + 122, y + 39, Component.literal("✕"), Skin.DIM, mx, my, Component.translatable("gui.powerfactor.pattern.clear"),
                        () -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, TerminalMenu.BUTTON_CLEAR_PATTERN));
                // Encode: a glowing arrow from blank to encoded.
                int ex = x + 146, ey = y + 20;
                boolean hover = mx >= ex && mx < ex + 18 && my >= ey && my < ey + 14;
                Skin.control(g, ex, ey, 18, 14, false, hover);
                int glow = pulse(0xFF7FF0FF, t, 700);
                g.fill(ex + 8, ey + 3, ex + 10, ey + 8, glow);
                for (int k = 0; k < 3; k++) g.fill(ex + 6 + k, ey + 8 + k, ex + 12 - k, ey + 9 + k, glow);
                buttons.add(new Btn(ex, ey, 18, 14, Component.translatable("gui.powerfactor.pattern.encode"), glow, true,
                        () -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, TerminalMenu.BUTTON_ENCODE)));
            }
            default -> {}
        }
    }

    private void ghostWell(GuiGraphicsExtractor g, int x, int y, int ring) {
        Skin.well(g, x, y, 18, 18);
        g.fill(x + 2, y + 2, x + 16, y + 16, 0xFFEAF1F6);
        // Dashed ring: these are references, not storage.
        for (int i = 1; i < 17; i += 3) {
            g.fill(x + i, y + 1, x + i + 1, y + 2, ring | 0xFF000000);
            g.fill(x + i, y + 16, x + i + 1, y + 17, ring | 0xFF000000);
            g.fill(x + 1, y + i, x + 2, y + i + 1, ring | 0xFF000000);
            g.fill(x + 16, y + i, x + 17, y + i + 1, ring | 0xFF000000);
        }
    }

    private void miniButton(GuiGraphicsExtractor g, int x, int y, Component glyph, int color, int mx, int my, Component tip, Runnable action) {
        boolean hover = mx >= x && mx < x + 14 && my >= y && my < y + 14;
        Skin.control(g, x, y, 14, 14, false, hover);
        g.centeredText(font, glyph, x + 7, y + 3, color);
        buttons.add(new Btn(x, y, 14, 14, tip, color, true, action));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        if (modal == Modal.NONE && !plateTitle().equals(title) && mx >= leftPos + 6 && mx < leftPos + 80 && my >= topPos + 3 && my < topPos + 16) {
            g.setTooltipForNextFrame(font, title, mx, my);
        }
        hoveredCell = -1;
        ClientGrid.Stack hovered = modal == Modal.NONE ? stackAt(mx, my) : null;
        drawGridItems(g, mx, my);
        if (modal != Modal.NONE) {
            g.nextStratum();
            drawModal(g, mx, my);
        } else if (hovered != null) {
            List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(minecraft, hovered.display()));
            lines.add(Component.translatable("gui.powerfactor.terminal.stored", String.format(Locale.ROOT, "%,d", hovered.count())).withColor(ACCENT));
            if (hovered.craftable()) lines.add(Component.translatable("gui.powerfactor.terminal.craftable_hint").withColor(CRAFT));
            g.setTooltipForNextFrame(font, lines, hovered.display().getTooltipImage(), mx, my);
        } else if (mx >= leftPos + GX - 1 && mx < leftPos + GX - 1 + COLS * 18 && my >= topPos + GY + rows * 18 && my < topPos + GY + rows * 18 + 5) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.powerfactor.terminal.bytes", String.format(Locale.ROOT, "%,d", ClientGrid.usedBytes),
                    String.format(Locale.ROOT, "%,d", ClientGrid.totalBytes), ClientGrid.types()), mx, my);
        } else {
            for (Btn b : buttons) {
                if (b.over(mx, my)) {
                    g.setTooltipForNextFrame(font, tabTip(b), mx, my);
                    break;
                }
            }
        }
        if (ClientGrid.status != 0 && modal == Modal.NONE) {
            Component msg = Component.translatable(switch (ClientGrid.status) {
                case 1 -> "gui.powerfactor.storage.no_power";
                case 2 -> "gui.powerfactor.storage.conflict";
                default -> "gui.powerfactor.storage.no_network";
            });
            // Wrapped to the banner's width so long messages never spill out of the grid.
            var lines = font.split(msg, COLS * 18 - 24);
            int cx = leftPos + GX - 1 + COLS * 9, cy = topPos + GY + rows * 9 - 4 - lines.size() * 5;
            g.fill(leftPos + GX + 6, cy - 4, leftPos + GX - 8 + COLS * 18, cy + lines.size() * 10 + 3, 0xE0101820);
            for (int i = 0; i < lines.size(); i++) g.centeredText(font, lines.get(i), cx, cy + i * 10, 0xFFFF7060);
        }
    }

    private Component tabTip(Btn b) {
        int i = buttons.indexOf(b);
        return switch (i) {
            case 0 -> Component.translatable("gui.powerfactor.terminal.sort." + sort.name().toLowerCase(Locale.ROOT));
            case 1 -> Component.translatable(descending ? "gui.powerfactor.terminal.descending" : "gui.powerfactor.terminal.ascending");
            case 2 -> Component.translatable("gui.powerfactor.terminal.view." + viewMode.name().toLowerCase(Locale.ROOT));
            case 3 -> Component.translatable("gui.powerfactor.terminal.jobs", ClientGrid.jobs);
            default -> b.label();
        };
    }

    private void drawGridItems(GuiGraphicsExtractor g, int mx, int my) {
        long t = Util.getMillis();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < COLS; c++) {
                int i = (scroll + r) * COLS + c;
                if (i >= view.size()) return;
                ClientGrid.Stack s = view.get(i);
                int x = leftPos + GX + c * 18, y = topPos + GY + r * 18;
                if (s.count() <= 0) {
                    // Craft-only entry: amber pulse behind a ghosted icon.
                    g.fill(x - 1, y - 1, x + 17, y + 17, (pulse(0x60FFA020, t + i * 90, 1200) & 0x00FFFFFF) | 0x38000000);
                }
                g.item(s.display(), x, y);
                if (hoveredCell == r * COLS + c && modal == Modal.NONE) {
                    g.fill(x, y, x + 16, y + 16, 0x50A0F0FF);
                }
                String label = s.count() <= 0 ? "+" : ClientGrid.compact(s.count());
                int color = s.count() <= 0 ? 0xFFE07800 : 0xFFFFFFFF;
                g.pose().pushMatrix();
                g.pose().translate(x + 17, y + 12);
                g.pose().scale(0.5F, 0.5F);
                g.text(font, label, -font.width(label) - 1, 0, color, true);
                g.pose().popMatrix();
                if (s.craftable() && s.count() > 0) g.fill(x + 15, y, x + 17, y + 2, CRAFT);
            }
        }
    }

    // ------------------------------------------------------------------ modals

    private void openCraft(ItemResource r) {
        craftTarget = r;
        modal = Modal.AMOUNT;
        amountBox.setValue("1");
        amountBox.visible = true;
        amountBox.setFocused(true);
        setFocused(amountBox);
        search.setFocused(false);
        plan = null;
        click();
    }

    private void openJobs() {
        modal = Modal.JOBS;
        listScroll = 0;
        jobs = null;
        ClientPacketDistributor.sendToServer(new GridPayloads.JobControl(menu.containerId, 0, 0, 0));
        click();
    }

    private void closeModal() {
        if (modal == Modal.JOBS) ClientPacketDistributor.sendToServer(new GridPayloads.JobControl(menu.containerId, 1, 0, 0));
        modal = Modal.NONE;
        amountBox.visible = false;
        amountBox.setFocused(false);
        waiting = false;
    }

    private long amount() {
        try {
            return Math.max(1, Long.parseLong(amountBox.getValue()));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private void onPlan(GridPayloads.PlanReply reply) {
        waiting = false;
        plan = reply;
        if (reply.result() == 1) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_ACTIVATE, 1.6F, 0.4F));
            closeModal();
            return;
        }
        modal = Modal.PLAN;
        amountBox.visible = false;
        listScroll = 0;
    }

    private void drawModal(GuiGraphicsExtractor g, int mx, int my) {
        buttons.clear();
        int x = leftPos + 4, y = topPos + 18, w = W - 8, h = TerminalMenu.inventoryTop(menu.kind(), rows) - 32;
        g.fill(leftPos, topPos, leftPos + W, topPos + imageHeight, 0x90060A0E);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, QUANTUM);
        g.fillGradient(x, y, x + w, y + h, 0xF0141F2A, 0xF00C141B);
        g.fill(x, y, x + w, y + 1, 0x80FFFFFF);
        switch (modal) {
            case AMOUNT -> drawAmount(g, x, y, w, h, mx, my);
            case PLAN -> drawPlan(g, x, y, w, h, mx, my);
            case JOBS -> drawJobs(g, x, y, w, h, mx, my);
            default -> {}
        }
        if (modal == Modal.AMOUNT) amountBox.extractRenderState(g, mx, my, 0);
        for (Btn b : buttons) {
            boolean hover = b.enabled() && b.over(mx, my);
            Skin.control(g, b.x(), b.y(), b.w(), b.h(), false, hover);
            g.centeredText(font, Skin.fit(font, b.label(), b.w() - 4), b.x() + b.w() / 2, b.y() + (b.h() - 8) / 2,
                    b.enabled() ? b.color() : 0xFF59666C);
        }
    }

    private void header(GuiGraphicsExtractor g, int x, int y, int w, Component text, @Nullable ItemStack icon) {
        if (icon != null) g.item(icon, x + 6, y + 5);
        g.text(font, Skin.fit(font, text, w - 34), x + 26, y + 9, Skin.TEXT, false);
        g.fill(x + 4, y + 24, x + w - 4, y + 25, 0x60B18CFF);
    }

    private void drawAmount(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
        if (craftTarget == null) return;
        header(g, x, y, w, Component.translatable("gui.powerfactor.craft.title", craftTarget.getHoverName()), craftTarget.toStack(1));
        g.text(font, Component.translatable("gui.powerfactor.craft.amount"), x + 8, y + 32, Skin.DIM, false);
        Skin.lcd(g, leftPos + 58, topPos + 54, 78, 18);
        amountBox.setX(leftPos + 62);
        amountBox.setY(topPos + 59);
        int[] steps = {-64, -10, -1, 1, 10, 64};
        for (int i = 0; i < steps.length; i++) {
            int d = steps[i];
            int bx = x + 8 + i * 30, by = y + 60;
            buttons.add(new Btn(bx, by, 28, 14, Component.literal((d > 0 ? "+" : "") + d), d > 0 ? 0xFF8FF0B0 : 0xFFFFA090, true,
                    () -> amountBox.setValue(String.valueOf(Math.max(1, Math.min(9_999_999, amount() + d))))));
        }
        long have = ClientGrid.count(craftTarget);
        g.text(font, Component.translatable("gui.powerfactor.terminal.stored", String.format(Locale.ROOT, "%,d", have)), x + 8, y + 82, ACCENT, false);
        buttons.add(new Btn(x + w - 98, y + h - 20, 44, 16, Component.translatable("gui.cancel"), Skin.DIM, true, this::closeModal));
        buttons.add(new Btn(x + w - 50, y + h - 20, 44, 16, Component.translatable(waiting ? "gui.powerfactor.craft.planning" : "gui.powerfactor.craft.next"),
                ACCENT, !waiting, this::requestPlan));
    }

    private void requestPlan() {
        if (craftTarget == null) return;
        waiting = true;
        ClientPacketDistributor.sendToServer(new GridPayloads.CraftRequest(menu.containerId, craftTarget, amount(), false));
    }

    private void drawPlan(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
        if (plan == null) return;
        boolean missing = plan.lines().stream().anyMatch(l -> l.missing() > 0);
        header(g, x, y, w, Component.translatable("gui.powerfactor.craft.plan", String.format(Locale.ROOT, "%,d", plan.amount()),
                plan.resource().getHoverName(), String.format(Locale.ROOT, "%,d", plan.crafts())), plan.resource().toStack(1));
        int listY = y + 28, listH = h - 54, rowsFit = listH / 20;
        int maxScroll = Math.max(0, plan.lines().size() - rowsFit);
        listScroll = Mth.clamp(listScroll, 0, maxScroll);
        for (int i = 0; i < rowsFit && i + listScroll < plan.lines().size(); i++) {
            GridPayloads.PlanLine l = plan.lines().get(i + listScroll);
            int ry = listY + i * 20;
            g.fill(x + 4, ry, x + w - 4, ry + 19, l.missing() > 0 ? 0x40FF3030 : (i & 1) == 0 ? 0x18FFFFFF : 0x0CFFFFFF);
            g.item(l.resource().toStack(1), x + 6, ry + 1);
            List<Component> parts = new ArrayList<>();
            List<Integer> colors = new ArrayList<>();
            if (l.used() > 0) { parts.add(Component.translatable("gui.powerfactor.craft.use", ClientGrid.compact(l.used()))); colors.add(ACCENT); }
            if (l.crafted() > 0) { parts.add(Component.translatable("gui.powerfactor.craft.make", ClientGrid.compact(l.crafted()))); colors.add(CRAFT); }
            if (l.missing() > 0) { parts.add(Component.translatable("gui.powerfactor.craft.missing", ClientGrid.compact(l.missing()))); colors.add(MISSING); }
            int tx = x + w - 6;
            for (int p = parts.size() - 1; p >= 0; p--) {
                tx -= font.width(parts.get(p));
                g.text(font, parts.get(p), tx, ry + 6, colors.get(p), false);
                tx -= 6;
            }
            g.text(font, Skin.fit(font, l.resource().getHoverName(), Math.max(10, tx - x - 28)), x + 26, ry + 6, Skin.TEXT, false);
        }
        if (maxScroll > 0) {
            int th = Math.max(8, listH * rowsFit / plan.lines().size());
            int ty = listY + (listH - th) * listScroll / maxScroll;
            g.fill(x + w - 3, ty, x + w - 1, ty + th, 0xA0B18CFF);
        }
        Component status = switch (plan.result()) {
            case 2 -> Component.translatable("gui.powerfactor.craft.no_cpu").withColor(MISSING);
            case 3 -> Component.translatable("gui.powerfactor.craft.changed").withColor(MISSING);
            case 4 -> Component.translatable("gui.powerfactor.storage.no_network").withColor(MISSING);
            default -> missing ? Component.translatable("gui.powerfactor.craft.cannot").withColor(MISSING)
                    : Component.translatable("gui.powerfactor.craft.ready").withColor(0xFF8FF0B0);
        };
        g.text(font, Skin.fit(font, status, w - 110), x + 6, y + h - 16, -1, false);
        buttons.add(new Btn(x + w - 98, y + h - 20, 44, 16, Component.translatable("gui.back"), Skin.DIM, true, () -> {
            modal = Modal.AMOUNT;
            amountBox.visible = true;
        }));
        buttons.add(new Btn(x + w - 50, y + h - 20, 44, 16, Component.translatable("gui.powerfactor.craft.start"), 0xFF8FF0B0, !missing && !waiting, () -> {
            waiting = true;
            ClientPacketDistributor.sendToServer(new GridPayloads.CraftRequest(menu.containerId, plan.resource(), plan.amount(), true));
        }));
    }

    private void drawJobs(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
        header(g, x, y, w, Component.translatable("gui.powerfactor.jobs.title"), null);
        long t = Util.getMillis();
        // A small processor glyph in the header, with rotating qubits.
        for (int k = 0; k < 4; k++) {
            double a = t / 300.0 + k * Math.PI / 2;
            int px = x + 13 + (int) Math.round(Math.cos(a) * 5), py = y + 12 + (int) Math.round(Math.sin(a) * 5);
            g.fill(px - 1, py - 1, px + 1, py + 1, k % 2 == 0 ? QUANTUM : ACCENT);
        }
        if (jobs == null) {
            g.centeredText(font, Component.translatable("gui.powerfactor.craft.planning"), x + w / 2, y + 50, Skin.DIM);
        } else if (jobs.jobs().isEmpty()) {
            g.centeredText(font, Component.translatable("gui.powerfactor.jobs.none"), x + w / 2, y + 44, Skin.DIM);
            g.centeredText(font, Component.translatable("gui.powerfactor.jobs.cpus", jobs.processors(), jobs.freeProcessors()), x + w / 2, y + 58, ACCENT);
        } else {
            int listY = y + 28, rowH = 26, fit = (h - 54) / rowH;
            int maxScroll = Math.max(0, jobs.jobs().size() - fit);
            listScroll = Mth.clamp(listScroll, 0, maxScroll);
            for (int i = 0; i < fit && i + listScroll < jobs.jobs().size(); i++) {
                GridPayloads.JobLine j = jobs.jobs().get(i + listScroll);
                int ry = listY + i * rowH;
                g.fill(x + 4, ry, x + w - 4, ry + rowH - 2, j.quantum() ? 0x22B18CFF : 0x186FE3FF);
                g.item(j.target().toStack(1), x + 6, ry + 4);
                g.text(font, Skin.fit(font, Component.literal(ClientGrid.compact(j.amount()) + "× ").append(j.target().getHoverName()), w - 70), x + 26, ry + 3,
                        Skin.TEXT, false);
                int bx = x + 26, bw = w - 70, by = ry + 14;
                g.fill(bx, by, bx + bw, by + 5, 0xFF0A1218);
                int fw = Math.round(bw * j.progress());
                g.fillGradient(bx, by, bx + fw, by + 5, j.quantum() ? QUANTUM : ACCENT, j.quantum() ? 0xFF6A3FD0 : 0xFF2C88B0);
                int sweep = (int) ((t / 8 + i * 40) % (bw + 20)) - 10;
                if (j.state() == 0 && sweep > 0 && sweep < fw) g.fill(bx + sweep, by, bx + sweep + 4, by + 5, 0x90FFFFFF);
                Component st = Component.translatable(switch (j.state()) {
                    case 1 -> "gui.powerfactor.jobs.finishing";
                    case 3 -> "gui.powerfactor.jobs.stalled";
                    default -> "gui.powerfactor.jobs.running";
                });
                g.text(font, Component.literal(Math.round(j.progress() * 100) + "%"), x + w - 42, ry + 3, j.state() == 3 ? MISSING : ACCENT, false);
                buttons.add(new Btn(x + w - 20, ry + 4, 14, 14, Component.literal("✕"), MISSING, true,
                        () -> ClientPacketDistributor.sendToServer(new GridPayloads.JobControl(menu.containerId, 2, j.cpu(), j.index()))));
                if (mx >= x + 4 && mx < x + w - 22 && my >= ry && my < ry + rowH - 2) {
                    List<Component> tip = new ArrayList<>();
                    tip.add(st);
                    for (GridPayloads.PlanLine p : j.pending()) {
                        tip.add(Component.literal(p.crafted() > 0 ? "⚒ " + ClientGrid.compact(p.crafted()) + "× " : "⌛ " + ClientGrid.compact(p.used()) + "× ")
                                .append(p.resource().getHoverName()).withColor(p.crafted() > 0 ? CRAFT : ACCENT));
                    }
                    g.setComponentTooltipForNextFrame(font, tip, mx, my);
                }
            }
            g.text(font, Component.translatable("gui.powerfactor.jobs.cpus", jobs.processors(), jobs.freeProcessors()), x + 6, y + h - 16, ACCENT, false);
        }
        buttons.add(new Btn(x + w - 50, y + h - 20, 44, 16, Component.translatable("gui.done"), ACCENT, true, this::closeModal));
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        if (modal != Modal.NONE) {
            for (Btn b : List.copyOf(buttons)) {
                if (b.enabled() && b.over(mx, my)) {
                    click();
                    b.action().run();
                    return true;
                }
            }
            if (modal == Modal.AMOUNT && amountBox.isMouseOver(mx, my)) return amountBox.mouseClicked(event, doubleClick);
            return true;
        }
        for (Btn b : List.copyOf(buttons)) {
            if (b.over(mx, my)) {
                click();
                b.action().run();
                return true;
            }
        }
        if (search.isMouseOver(mx, my) || mx >= leftPos + 81 && mx < leftPos + 174 && my >= topPos + 3 && my < topPos + 17) {
            if (event.button() == 1) search.setValue("");
            search.setFocused(true);
            setFocused(search);
            return true;
        }
        search.setFocused(false);
        if (mx >= leftPos + SCROLL_X && mx < leftPos + SCROLL_X + 12 && my >= topPos + GY - 1 && my < topPos + GY - 1 + rows * 18) {
            draggingScroll = true;
            dragScroll(my);
            return true;
        }
        if (overGrid(mx, my)) {
            ClientGrid.Stack s = stackAt(mx, my);
            boolean carrying = !menu.getCarried().isEmpty();
            int action;
            if (event.button() == 2 || s != null && s.craftable() && !carrying && (s.count() <= 0 || event.hasControlDown())) {
                if (s != null && s.craftable()) openCraft(s.resource());
                return true;
            } else if (carrying) {
                action = event.button() == 1 ? GridPayloads.Action.INSERT_ONE : GridPayloads.Action.INSERT_ALL;
            } else if (s == null) {
                return true;
            } else if (event.hasShiftDown()) {
                action = GridPayloads.Action.SHIFT;
            } else {
                action = event.button() == 1 ? GridPayloads.Action.TAKE_HALF : GridPayloads.Action.TAKE_STACK;
            }
            ClientPacketDistributor.sendToServer(new GridPayloads.Action(menu.containerId, s == null ? ItemResource.EMPTY : s.resource(), action));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingScroll) {
            dragScroll(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingScroll = false;
        return super.mouseReleased(event);
    }

    private void dragScroll(double my) {
        int max = maxScroll();
        float f = (float) ((my - topPos - GY) / (rows * 18 - 8));
        scroll = Mth.clamp(Math.round(f * max), 0, max);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (modal == Modal.PLAN || modal == Modal.JOBS) {
            listScroll -= (int) Math.signum(scrollY);
            return true;
        }
        if (modal == Modal.AMOUNT) {
            amountBox.setValue(String.valueOf(Math.max(1, amount() + (int) Math.signum(scrollY) * (minecraft.hasShiftDown() ? 64 : 1))));
            return true;
        }
        if (overGrid(x, y) || x >= leftPos + SCROLL_X && x < leftPos + SCROLL_X + 12) {
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256 && modal != Modal.NONE) {
            closeModal();
            return true;
        }
        if (modal == Modal.AMOUNT) {
            if (event.key() == 257 || event.key() == 335) {
                requestPlan();
                return true;
            }
            amountBox.keyPressed(event);
            return true;
        }
        if (search.isFocused()) {
            if (event.key() == 256) {
                search.setFocused(false);
                return true;
            }
            search.keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (modal == Modal.AMOUNT) return amountBox.charTyped(event);
        if (search.isFocused()) return search.charTyped(event);
        return super.charTyped(event);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {}

    @Override
    public void removed() {
        if (modal == Modal.JOBS) ClientPacketDistributor.sendToServer(new GridPayloads.JobControl(menu.containerId, 1, 0, 0));
        super.removed();
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.4F, 0.35F));
    }

    private static int pulse(int argb, long t, int period) {
        float k = (float) (0.65 + 0.35 * Math.sin(t * Math.PI * 2 / period));
        int r = (int) (((argb >> 16) & 0xFF) * k), gg = (int) (((argb >> 8) & 0xFF) * k), b = (int) ((argb & 0xFF) * k);
        return (argb & 0xFF000000) | r << 16 | gg << 8 | b;
    }
}
