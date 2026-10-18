package com.powerfactor.client.powernet;

import com.powerfactor.client.Skin;
import com.powerfactor.powernet.PowerNetMenu;
import com.powerfactor.powernet.PowerNetPayloads;
import com.powerfactor.powernet.PowerNetPayloads.Command;
import com.powerfactor.powernet.PowerNetPayloads.Snapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * A wireless power network device's screen: pick or create a network (name, colour, who may use it), set this
 * device up (name, priority, transfer limit, surge, chunk loading), see every device on the network, the last
 * minute of flow, and the network's people and wireless charging.
 */
public class PowerNetScreen extends AbstractContainerScreen<PowerNetMenu> {
    private static final int W = 248, H = 208, TOP = 42, BOTTOM = H - 7;
    private static final int GREEN = 0xFF56E39A, AMBER = 0xFFFFB347, RED = 0xFFFF5A4A, CYAN = 0xFF19C6D8, FE = 0xFFFF7A5A;
    private enum Tab { NETWORK, DEVICE, LINKS, STATS, PEOPLE }
    private static final Skin.Icon[] TAB_ICONS = {Skin.Icon.NETWORK, Skin.Icon.UPGRADE, Skin.Icon.LINK, Skin.Icon.FE, Skin.Icon.PLAYER};
    private static final Skin.Icon[] KIND_ICONS = {Skin.Icon.INPUT, Skin.Icon.OUTPUT, Skin.Icon.BATTERY, Skin.Icon.BATTERY, Skin.Icon.BATTERY,
            Skin.Icon.NETWORK};
    private static final Skin.Icon[] ACCESS_ICONS = {Skin.Icon.UNLOCK, Skin.Icon.LOCK, Skin.Icon.LOCK};

    private record Btn(int x, int y, int w, int h, Component label, Skin.@Nullable Icon icon, Component tip, int color, boolean on, Runnable action) {}

    private static @Nullable Snapshot latest;
    private final List<Btn> buttons = new ArrayList<>();
    private Tab tab = Tab.NETWORK;
    private EditBox netName, password, devName, limitBox, memberName;
    private int newColor = DyeColor.CYAN.getTextureDiffuseColor() & 0xFFFFFF, newAccess = 1, scroll;
    private boolean deleteArmed, formFilled;
    private int joinTarget = -1, shownNetwork = Integer.MIN_VALUE;

    public PowerNetScreen(PowerNetMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H);
        PowerNetPayloads.clientHandler = s -> latest = s;
        latest = null;
    }

    private @Nullable Snapshot s() {
        return latest != null && latest.pos().equals(menu.pos) ? latest : null;
    }

    private void send(int op, int a, int b, long c, String s1, String s2, int flags) {
        ClientPacketDistributor.sendToServer(new Command(menu.pos, op, a, b, c, s1, s2, flags));
    }

    private EditBox box(int x, int y, int w, int max, String hint) {
        EditBox e = addRenderableWidget(new EditBox(font, leftPos + x + 3, topPos + y + 2, w - 6, 10, Component.translatable(hint)));
        e.setBordered(false);
        e.setMaxLength(max);
        e.setTextColor(0xFFDDF6FF);
        e.setHint(Component.translatable(hint).withColor(0xFF5E7C88));
        return e;
    }

    @Override
    protected void init() {
        super.init();
        netName = box(131, 44, 110, 32, "gui.powerfactor.power_net.name_hint");
        password = box(131, 108, 110, 32, "gui.powerfactor.power_net.password_hint");
        devName = box(127, 44, 114, 32, "gui.powerfactor.power_net.device_hint");
        limitBox = box(127, 98, 70, 10, "gui.powerfactor.power_net.limit_hint");
        memberName = box(131, 44, 110, 16, "gui.powerfactor.power_net.member_hint");
        showFields();
    }

    private void showFields() {
        netName.visible = tab == Tab.NETWORK;
        password.visible = tab == Tab.NETWORK && (newAccess == 2 || joinTarget >= 0);
        devName.visible = tab == Tab.DEVICE;
        limitBox.visible = tab == Tab.DEVICE;
        memberName.visible = tab == Tab.PEOPLE;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractBackground(g, mx, my, a);
        buttons.clear();
        Snapshot s = s();
        if (s != null && s.network() != shownNetwork) {
            shownNetwork = s.network();
            formFilled = false;
        }
        if (s != null && !formFilled) {
            // First snapshot: fill the fields with what's there.
            devName.setValue(s.name());
            limitBox.setValue(String.valueOf(s.limit()));
            if (s.network() >= 0) {
                netName.setValue(s.netName());
                newColor = s.netColor();
                newAccess = s.access();
            }
            formFilled = true;
        }
        int x = leftPos, y = topPos;
        Skin.panel(g, x, y, W, H);
        Skin.title(g, font, s != null && !s.name().isEmpty() ? Component.literal(s.name()) : title, x, y, W - 90);
        int status = s == null ? 3 : s.status();
        int lamp = switch (status) {
            case 0 -> GREEN;
            case 1 -> AMBER;
            case 2 -> RED;
            default -> 0xFF607080;
        };
        Skin.lamp(g, x + W - 14, y + 6, lamp);
        if (s != null && s.network() >= 0) {
            int tw = Math.min(70, font.width(s.netName()));
            g.fill(x + W - 30 - tw, y + 6, x + W - 22 - tw, y + 14, 0xFF000000 | s.netColor());
            Skin.condensed(g, font, Component.literal(s.netName()), x + W - 19 - tw, y + 6, 70, Skin.TEXT);
        }
        for (Tab t : Tab.values()) {
            int tx = x + 7 + t.ordinal() * 47;
            boolean hover = in(mx, my, tx, y + 20, 45, 18);
            Skin.control(g, tx, y + 20, 45, 18, tab == t, hover);
        }
        switch (tab) {
            case NETWORK -> networkTab(g, s, x, y, mx, my);
            case DEVICE -> deviceTab(g, s, x, y);
            case LINKS -> linksTab(g, s, x, y);
            case STATS -> statsTab(g, s, x, y);
            case PEOPLE -> peopleTab(g, s, x, y);
        }
        for (Btn b : buttons) {
            boolean hover = in(mx, my, b.x, b.y, b.w, b.h);
            Skin.control(g, b.x, b.y, b.w, b.h, b.on, hover);
            int tx = b.x + 4;
            if (b.icon != null) {
                Skin.icon(g, b.icon, b.label.getString().isEmpty() ? b.x + (b.w - 9) / 2 : tx, b.y + (b.h - 9) / 2, null);
                tx += 12;
            }
            if (!b.label.getString().isEmpty()) {
                int lw = Math.min(b.w - (tx - b.x) - 3, font.width(b.label));
                int lx = b.icon == null ? b.x + (b.w - lw) / 2 : tx;
                Skin.condensed(g, font, b.label, lx, b.y + (b.h - 8) / 2, b.w - (lx - b.x) - 3, b.color);
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        for (Tab t : Tab.values()) {
            int tx = 7 + t.ordinal() * 47;
            Skin.icon(g, TAB_ICONS[t.ordinal()], tx + 4, 24, null);
            Skin.condensed(g, font, Component.translatable("gui.powerfactor.power_net.tab." + t.name().toLowerCase(java.util.Locale.ROOT)), tx + 15, 25, 28,
                    tab == t ? Skin.LCD_TEXT : 0xFFB8C8D2);
        }
    }

    private void field(GuiGraphicsExtractor g, int x, int y, int w) {
        Skin.lcd(g, x, y, w, 14);
    }

    private void networkTab(GuiGraphicsExtractor g, @Nullable Snapshot s, int x, int y, int mx, int my) {
        // The list of networks this player can see.
        Skin.lcd(g, x + 7, y + TOP, 120, BOTTOM - TOP);
        if (s != null) {
            int row = 0;
            for (var n : s.nets()) {
                int ry = y + TOP + 3 + (row - scroll) * 14;
                row++;
                if (ry < y + TOP + 2 || ry > y + BOTTOM - 14) continue;
                boolean current = n.id() == s.network(), hover = in(mx, my, x + 9, ry, 116, 13);
                if (current || hover) g.fill(x + 9, ry, x + 125, ry + 13, current ? 0x5019C6D8 : 0x28FFFFFF);
                g.fill(x + 12, ry + 3, x + 19, ry + 10, 0xFF000000 | n.color());
                Skin.condensed(g, font, Component.literal(n.name()), x + 23, ry + 3, 72, current ? Skin.LCD_TEXT : 0xFFB8D8E4);
                Skin.icon(g, ACCESS_ICONS[Math.clamp(n.access(), 0, 2)], x + 100, ry + 2, null);
                g.text(font, Component.literal(String.valueOf(n.devices())), x + 112, ry + 3, Skin.LCD_DIM, false);
            }
            if (s.nets().isEmpty()) {
                g.textWithWordWrap(font, Component.translatable("gui.powerfactor.power_net.no_networks.hint"), x + 12, y + TOP + 6, 110, Skin.LCD_DIM, false);
            }
        }
        // The form: name, colour, access, password; create, or save and delete the current one.
        field(g, x + 131, y + 42, 110);
        int[] palette = new int[16];
        for (int i = 0; i < 16; i++) palette[i] = DyeColor.byId(i).getTextureDiffuseColor() & 0xFFFFFF;
        for (int i = 0; i < 16; i++) {
            int px = x + 131 + (i % 8) * 14, py = y + 60 + (i / 8) * 14;
            boolean sel = (palette[i] & 0xFFFFFF) == (newColor & 0xFFFFFF);
            g.fill(px, py, px + 12, py + 12, sel ? 0xFFFFFFFF : 0xFF4B6271);
            g.fill(px + 1, py + 1, px + 11, py + 11, 0xFF000000 | palette[i]);
        }
        String[] access = {"public", "private", "password"};
        Skin.Icon[] accessIcons = {Skin.Icon.UNLOCK, Skin.Icon.LOCK, null};
        for (int i = 0; i < 3; i++) {
            int idx = i;
            buttons.add(new Btn(x + 131 + i * 37, y + 90, 36, 14, i == 2 ? Component.literal("•••") : Component.empty(), accessIcons[i],
                    Component.translatable("gui.powerfactor.power_net.access." + access[i]).append(": ")
                            .append(Component.translatable("gui.powerfactor.power_net.access." + access[i] + ".tip")), newAccess == i ? Skin.LCD_TEXT : 0xFF8FA6B5,
                    newAccess == i, () -> {
                        newAccess = idx;
                        joinTarget = -1;
                        showFields();
                    }));
        }
        if (password.visible) field(g, x + 131, y + 106, 110);
        if (joinTarget >= 0) {
            buttons.add(new Btn(x + 131, y + 126, 110, 18, Component.translatable("gui.powerfactor.power_net.join"), Skin.Icon.LINK,
                    Component.translatable("gui.powerfactor.power_net.join.tip"), GREEN, false, () -> {
                        send(PowerNetPayloads.SELECT, joinTarget, 0, 0, password.getValue(), "", 0);
                        joinTarget = -1;
                        showFields();
                    }));
        } else {
            buttons.add(new Btn(x + 131, y + 126, 110, 18, Component.translatable("gui.powerfactor.power_net.create"), Skin.Icon.PLUS,
                    Component.translatable("gui.powerfactor.power_net.create.tip"), CYAN, false,
                    () -> send(PowerNetPayloads.CREATE, newColor, newAccess, 0, netName.getValue(), password.getValue(), 0)));
        }
        if (s != null && s.network() >= 0 && s.canEdit()) {
            buttons.add(new Btn(x + 131, y + 148, 54, 16, Component.translatable("gui.powerfactor.power_net.save"), Skin.Icon.OK,
                    Component.translatable("gui.powerfactor.power_net.save.tip"), GREEN, false,
                    () -> send(PowerNetPayloads.EDIT, newColor, newAccess, 0, netName.getValue(), password.getValue(), 0)));
            buttons.add(new Btn(x + 187, y + 148, 54, 16, Component.translatable(deleteArmed ? "gui.powerfactor.power_net.delete_now"
                    : "gui.powerfactor.power_net.delete"), Skin.Icon.ERROR, Component.translatable("gui.powerfactor.power_net.delete.tip"),
                    deleteArmed ? RED : 0xFFFF9A8A, deleteArmed, () -> {
                        if (deleteArmed) send(PowerNetPayloads.DELETE, 0, 0, 0, "", "", 0);
                        deleteArmed = !deleteArmed;
                    }));
        }
        if (s != null && s.network() >= 0) {
            buttons.add(new Btn(x + 131, y + 168, 110, 14, Component.translatable("gui.powerfactor.power_net.leave"), Skin.Icon.DENY,
                    Component.translatable("gui.powerfactor.power_net.leave.tip"), 0xFFB8C8D2, false, () -> send(PowerNetPayloads.LEAVE, 0, 0, 0, "", "", 0)));
            Skin.lcd(g, x + 131, y + 184, 110, 17);
            Component who = Component.translatable("gui.powerfactor.power_net.owner", s.owner());
            Skin.condensed(g, font, who, x + 135, y + 189, 102, Skin.LCD_DIM);
        }
    }

    private void deviceTab(GuiGraphicsExtractor g, @Nullable Snapshot s, int x, int y) {
        // What this device is doing, on the left.
        Skin.lcd(g, x + 7, y + TOP, 114, BOTTOM - TOP);
        if (s != null) {
            int kind = Math.clamp(s.kind(), 0, KIND_ICONS.length - 1);
            g.pose().pushMatrix();
            g.pose().translate(x + 50, y + TOP + 10);
            g.pose().scale(3F, 3F);
            Skin.icon(g, KIND_ICONS[kind], 0, 0, null);
            g.pose().popMatrix();
            List<Skin.Row> rows = new ArrayList<>();
            String[] statusKeys = {"running", "no_controller", "two_controllers", "none"};
            int sc = switch (s.status()) {
                case 0 -> GREEN;
                case 1 -> AMBER;
                case 2 -> RED;
                default -> Skin.LCD_DIM;
            };
            rows.add(new Skin.Row(Skin.statusIcon(sc), Component.translatable("gui.powerfactor.power_net.status." + statusKeys[Math.clamp(s.status(), 0, 3)]), sc));
            rows.add(new Skin.Row(Skin.Icon.FE, Component.literal(Skin.compact(Math.abs(s.flow())) + " FE/t"), FE));
            if (kind >= 2 && kind <= 4) {
                long cap = new long[] {4_000_000L, 64_000_000L, 1_000_000_000L}[kind - 2];
                rows.add(new Skin.Row(Skin.Icon.BATTERY, Component.literal(Skin.compact(s.energy()) + " / " + Skin.compact(cap)), CYAN));
                Skin.lcdRows(g, font, x + 7, y + TOP + 42, 114, 50, rows);
                Skin.progress(g, x + 13, y + TOP + 96, 102, (float) s.energy() / cap, CYAN);
            } else {
                Skin.lcdRows(g, font, x + 7, y + TOP + 42, 114, 50, rows);
            }
        }
        if (s == null) return;
        boolean editable = s.kind() != 5;
        // Settings, on the right.
        field(g, x + 127, y + 42, 114);
        int prio = s.priority();
        Skin.icon(g, Skin.Icon.PRIORITY, x + 127, y + 64, null);
        g.text(font, Component.literal(String.valueOf(prio)), x + 140, y + 65, Skin.TEXT, false);
        int[] steps = {-10, -1, 1, 10};
        for (int i = 0; i < 4; i++) {
            int d = steps[i];
            buttons.add(new Btn(x + 161 + i * 20, y + 61, 18, 14, Component.literal((d > 0 ? "+" : "") + d), null,
                    Component.translatable("gui.powerfactor.power_net.priority.tip"), CYAN, false, () -> apply(prio + d, null, null, null, null)));
        }
        field(g, x + 127, y + 96, 70);
        Skin.icon(g, Skin.Icon.SPEED, x + 127, y + 84, null);
        g.text(font, Component.translatable("gui.powerfactor.power_net.limit"), x + 140, y + 85, Skin.DIM, false);
        buttons.add(new Btn(x + 199, y + 96, 42, 14, Component.translatable("gui.powerfactor.power_net.unlimited"), null,
                Component.translatable("gui.powerfactor.power_net.unlimited.tip"), s.unlimited() ? GREEN : 0xFF8FA6B5, s.unlimited(),
                () -> apply(null, null, null, !s().unlimited(), null)));
        buttons.add(new Btn(x + 127, y + 116, 114, 18, Component.translatable("gui.powerfactor.power_net.surge"), Skin.Icon.POWER,
                Component.translatable("gui.powerfactor.power_net.surge.tip"), s.surge() ? AMBER : 0xFF8FA6B5, s.surge(),
                () -> apply(null, null, !s().surge(), null, null)));
        buttons.add(new Btn(x + 127, y + 136, 114, 18, Component.translatable("gui.powerfactor.power_net.chunk"), Skin.Icon.AREA,
                Component.translatable("gui.powerfactor.power_net.chunk.tip"), s.chunkLoad() ? GREEN : 0xFF8FA6B5, s.chunkLoad(),
                () -> apply(null, null, null, null, !s().chunkLoad())));
        buttons.add(new Btn(x + 127, y + 183, 114, 18, Component.translatable("gui.powerfactor.power_net.apply"), Skin.Icon.OK,
                Component.translatable("gui.powerfactor.power_net.apply.tip"), GREEN, false, () -> apply(null, null, null, null, null)));
        if (!editable) {
            g.textWithWordWrap(font, Component.translatable("gui.powerfactor.power_net.controller_note"), x + 128, y + 158, 112, Skin.DIM, false);
        }
    }

    /** Sends the device's settings, with any of them changed. */
    private void apply(@Nullable Integer priority, @Nullable Integer limit, @Nullable Boolean surge, @Nullable Boolean unlimited, @Nullable Boolean chunk) {
        Snapshot s = s();
        if (s == null) return;
        int lim = limit != null ? limit : parse(limitBox.getValue(), s.limit());
        int flags = ((surge != null ? surge : s.surge()) ? 1 : 0) | ((unlimited != null ? unlimited : s.unlimited()) ? 2 : 0)
                | ((chunk != null ? chunk : s.chunkLoad()) ? 4 : 0);
        send(PowerNetPayloads.DEVICE, priority != null ? priority : s.priority(), lim, 0, devName.getValue(), "", flags);
    }

    private static int parse(String t, int fallback) {
        try {
            return (int) Math.min(Integer.MAX_VALUE, Long.parseLong(t.isEmpty() ? "0" : t));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void linksTab(GuiGraphicsExtractor g, @Nullable Snapshot s, int x, int y) {
        Skin.lcd(g, x + 7, y + TOP, W - 14, BOTTOM - TOP);
        if (s == null || s.network() < 0) {
            g.text(font, Component.translatable("gui.powerfactor.power_net.no_network"), x + 12, y + TOP + 6, Skin.LCD_DIM, false);
            return;
        }
        int rows = (BOTTOM - TOP - 6) / 12;
        scroll = Math.clamp(scroll, 0, Math.max(0, s.devices().size() - rows));
        for (int i = 0; i < rows && i + scroll < s.devices().size(); i++) {
            var d = s.devices().get(i + scroll);
            int ry = y + TOP + 4 + i * 12;
            Skin.icon(g, KIND_ICONS[Math.clamp(d.kind(), 0, KIND_ICONS.length - 1)], x + 11, ry, null);
            Skin.condensed(g, font, Component.literal(d.name()), x + 23, ry + 1, 78, Skin.LCD_TEXT);
            var p = net.minecraft.core.BlockPos.of(d.pos());
            String where = p.getX() + " " + p.getY() + " " + p.getZ() + (d.dim().equals("overworld") ? "" : " " + d.dim());
            Skin.condensed(g, font, Component.literal(where), x + 104, ry + 1, 72, Skin.LCD_DIM);
            String flow = (d.flow() < 0 ? "−" : "") + Skin.compact(Math.abs(d.flow()));
            boolean cell = d.kind() >= 2 && d.kind() <= 4;
            g.text(font, Component.literal(flow), x + 207 - font.width(flow), ry + 1,
                    d.flow() == 0 ? Skin.LCD_DIM : d.flow() < 0 ? AMBER : cell ? CYAN : d.kind() == 0 ? GREEN : FE, false);
            String pr = d.surge() ? "S" : String.valueOf(d.priority());
            g.text(font, Component.literal(pr), x + 236 - font.width(pr), ry + 1, d.surge() ? AMBER : Skin.LCD_DIM, false);
        }
    }

    private void statsTab(GuiGraphicsExtractor g, @Nullable Snapshot s, int x, int y) {
        int gx = x + 7, gy = y + TOP, gw = W - 14, gh = 106;
        Skin.lcd(g, gx, gy, gw, gh);
        for (int k = 1; k < 4; k++) g.fill(gx + 2, gy + k * gh / 4, gx + gw - 2, gy + k * gh / 4 + 1, 0x30FFFFFF);
        if (s != null && s.history().length >= 180) {
            long peak = 1;
            for (int i = 0; i < 120; i++) peak = Math.max(peak, s.history()[i]);
            series(g, s.history(), 0, peak, gx, gy, gw, gh, GREEN);
            series(g, s.history(), 60, peak, gx, gy, gw, gh, FE);
            series(g, s.history(), 120, Math.max(1, s.capacity()), gx, gy, gw, gh, CYAN);
            g.text(font, Component.literal(Skin.compact(peak) + " FE/t"), gx + gw - 4 - font.width(Skin.compact(peak) + " FE/t"), gy + 4, Skin.LCD_DIM, false);
        }
        Skin.icon(g, Skin.Icon.INPUT, gx + 4, gy + 4, null);
        Skin.icon(g, Skin.Icon.OUTPUT, gx + 15, gy + 4, null);
        Skin.icon(g, Skin.Icon.BATTERY, gx + 26, gy + 4, null);
        // Four tiles: in, out, wireless charging, stored.
        int ty = gy + gh + 4, tw = (gw - 6) / 4;
        long[] vals = s == null ? new long[4] : new long[] {s.input(), s.output(), s.charged(), s.stored()};
        Skin.Icon[] icons = {Skin.Icon.INPUT, Skin.Icon.OUTPUT, Skin.Icon.PLAYER, Skin.Icon.BATTERY};
        int[] cols = {GREEN, FE, AMBER, CYAN};
        for (int i = 0; i < 4; i++) {
            int tx = gx + i * (tw + 2);
            Skin.lcd(g, tx, ty, tw, BOTTOM - ty);
            Skin.icon(g, icons[i], tx + 4, ty + 5, null);
            String v = Skin.compact(vals[i]) + (i < 3 ? "/t" : "");
            Skin.condensed(g, font, Component.literal(v), tx + 15, ty + 6, tw - 18, cols[i]);
            if (i == 3 && s != null && s.capacity() > 0) Skin.progress(g, tx + 4, ty + 22, tw - 8, (float) s.stored() / s.capacity(), CYAN);
        }
    }

    private static void series(GuiGraphicsExtractor g, long[] h, int off, long top, int x, int y, int w, int hh, int color) {
        int px = -1, py = -1;
        for (int i = 0; i < 60; i++) {
            int cx = x + 3 + (int) ((w - 6) * i / 59F);
            int cy = y + hh - 4 - (int) ((hh - 8) * Math.min(1F, (float) h[off + i] / top));
            if (px >= 0) g.fill(px, Math.min(py, cy), cx + 1, Math.max(py, cy) + 1, color);
            px = cx;
            py = cy;
        }
    }

    private void peopleTab(GuiGraphicsExtractor g, @Nullable Snapshot s, int x, int y) {
        Skin.lcd(g, x + 7, y + TOP, 120, BOTTOM - TOP);
        if (s == null || s.network() < 0) {
            g.text(font, Component.translatable("gui.powerfactor.power_net.no_network"), x + 12, y + TOP + 6, Skin.LCD_DIM, false);
            return;
        }
        String[] roles = {"user", "admin", "owner"};
        int ry = y + TOP + 4;
        Skin.icon(g, Skin.Icon.PLAYER, x + 11, ry, null);
        Skin.condensed(g, font, Component.literal(s.owner()), x + 23, ry + 1, 60, AMBER);
        g.text(font, Component.translatable("gui.powerfactor.power_net.role.owner"), x + 86, ry + 1, Skin.LCD_DIM, false);
        for (var m : s.members()) {
            ry += 12;
            if (ry > y + BOTTOM - 12) break;
            Skin.icon(g, Skin.Icon.PLAYER, x + 11, ry, null);
            Skin.condensed(g, font, Component.literal(m.name()), x + 23, ry + 1, 60, Skin.LCD_TEXT);
            g.text(font, Component.translatable("gui.powerfactor.power_net.role." + roles[Math.clamp(m.role(), 0, 2)]), x + 86, ry + 1, Skin.LCD_DIM, false);
            if (s.canEdit()) {
                String name = m.name();
                buttons.add(new Btn(x + 113, ry - 1, 11, 11, Component.literal("×"), null, Component.translatable("gui.powerfactor.power_net.remove"), RED,
                        false, () -> send(PowerNetPayloads.MEMBER, -1, 0, 0, name, "", 0)));
            }
        }
        if (s.canEdit()) {
            field(g, x + 131, y + 42, 110);
            buttons.add(new Btn(x + 131, y + 60, 54, 14, Component.translatable("gui.powerfactor.power_net.add_user"), Skin.Icon.PLUS,
                    Component.translatable("gui.powerfactor.power_net.add_user.tip"), CYAN, false,
                    () -> send(PowerNetPayloads.MEMBER, 0, 0, 0, memberName.getValue(), "", 0)));
            buttons.add(new Btn(x + 187, y + 60, 54, 14, Component.translatable("gui.powerfactor.power_net.add_admin"), Skin.Icon.PLUS,
                    Component.translatable("gui.powerfactor.power_net.add_admin.tip"), AMBER, false,
                    () -> send(PowerNetPayloads.MEMBER, 1, 0, 0, memberName.getValue(), "", 0)));
        }
        // Wireless charging: which parts of members' inventories the controller fills.
        g.text(font, Component.translatable("gui.powerfactor.power_net.charging"), x + 131, y + 84, Skin.DIM, false);
        String[] parts = {"hotbar", "armor", "offhand", "inventory"};
        Skin.Icon[] icons = {Skin.Icon.ITEM, Skin.Icon.SHIELD, Skin.Icon.ITEM, Skin.Icon.MATTER};
        for (int i = 0; i < 4; i++) {
            int bit = 1 << i;
            boolean on = (s.charging() & bit) != 0;
            buttons.add(new Btn(x + 131 + (i % 2) * 56, y + 96 + (i / 2) * 20, 54, 18, Component.translatable("gui.powerfactor.power_net.charge." + parts[i]),
                    icons[i], Component.translatable("gui.powerfactor.power_net.charge.tip"), on ? GREEN : 0xFF8FA6B5, on,
                    () -> send(PowerNetPayloads.CHARGING, 0, 0, 0, "", "", s().charging() ^ bit)));
        }
        Skin.lcd(g, x + 131, y + 140, 110, 18);
        Skin.iconValue(g, font, Skin.Icon.PLAYER, Component.literal(Skin.compact(s.charged()) + " FE/t"), x + 135, y + 145, AMBER,
                Component.translatable("gui.powerfactor.power_net.charged"));
    }

    // ---------------------------------------------------------------- input

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        super.extractRenderState(g, mx, my, partial);
        for (Btn b : buttons) {
            if (in(mx, my, b.x, b.y, b.w, b.h) && !b.tip.getString().isEmpty()) g.setTooltipForNextFrame(font, b.tip, mx, my);
        }
        for (Tab t : Tab.values()) {
            if (in(mx, my, leftPos + 7 + t.ordinal() * 47, topPos + 20, 45, 18)) {
                g.setTooltipForNextFrame(font, Component.translatable("gui.powerfactor.power_net.tab." + t.name().toLowerCase(java.util.Locale.ROOT) + ".tip"), mx, my);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        for (Tab t : Tab.values()) {
            if (in(mx, my, leftPos + 7 + t.ordinal() * 47, topPos + 20, 45, 18)) {
                tab = t;
                scroll = 0;
                deleteArmed = false;
                showFields();
                click();
                return true;
            }
        }
        for (Btn b : List.copyOf(buttons)) {
            if (in(mx, my, b.x, b.y, b.w, b.h)) {
                b.action.run();
                click();
                return true;
            }
        }
        Snapshot s = s();
        if (tab == Tab.NETWORK) {
            // Palette.
            for (int i = 0; i < 16; i++) {
                int px = leftPos + 131 + (i % 8) * 14, py = topPos + 60 + (i / 8) * 14;
                if (in(mx, my, px, py, 12, 12)) {
                    newColor = DyeColor.byId(i).getTextureDiffuseColor() & 0xFFFFFF;
                    click();
                    return true;
                }
            }
            // Network rows: join (asking for the password where one is needed).
            if (s != null && in(mx, my, leftPos + 9, topPos + TOP + 3, 116, BOTTOM - TOP - 6)) {
                int row = (int) ((my - topPos - TOP - 3) / 14) + scroll;
                if (row >= 0 && row < s.nets().size()) {
                    var n = s.nets().get(row);
                    if (n.access() == 2 && n.role() < 0) {
                        joinTarget = n.id();
                        password.setValue("");
                        showFields();
                        setFocused(password);
                    } else {
                        send(PowerNetPayloads.SELECT, n.id(), 0, 0, "", "", 0);
                        netName.setValue(n.name());
                        newColor = n.color();
                        newAccess = n.access();
                    }
                    click();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Math.max(0, scroll - (int) Math.signum(sy));
        return true;
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1F));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        for (EditBox e : new EditBox[] {netName, password, devName, limitBox, memberName}) {
            if (e.visible && e.isFocused()) {
                if (event.key() == 256) {
                    e.setFocused(false);
                    setFocused(null);
                    return true;
                }
                if ((event.key() == 257 || event.key() == 335) && (e == devName || e == limitBox)) {
                    apply(null, null, null, null, null);
                    e.setFocused(false);
                    return true;
                }
                e.keyPressed(event);
                return true;
            }
        }
        return super.keyPressed(event);
    }
}
