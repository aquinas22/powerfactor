package com.powerfactor.client;

import com.powerfactor.PowerFactor;
import com.powerfactor.item.ArcGunItem;
import com.powerfactor.item.ArcSaberItem;
import com.powerfactor.item.ChainsawItem;
import com.powerfactor.item.DrillItem;
import com.powerfactor.item.DrillModule;
import com.powerfactor.item.JetpackItem;
import com.powerfactor.item.PoweredItem;
import com.powerfactor.item.ToolKind;
import com.powerfactor.item.ToolModules;
import com.powerfactor.registry.ModComponents;
import com.powerfactor.upgrade.UpgradeTableMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The upgrade table: a holographic bench. The tool turns slowly in the middle on a projector ring;
 * installed modules light their circuit traces and stream energy into it; the stats panel animates
 * toward the tool's new numbers and flags what an install just changed.
 */
public class UpgradeTableScreen extends AbstractContainerScreen<UpgradeTableMenu> {
    private static final Identifier TEXTURE = PowerFactor.id("textures/gui/upgrade_table.png");
    private static final Identifier HOLOGRAM = PowerFactor.id("textures/gui/upgrade_table_hologram.png");
    private static final int W = 248, H = 218;
    private static final int CX = 84, CY = 66, BAY_R = 30;
    private static final int PANEL_X = 162, PANEL_Y = 20, PANEL_W = 76;
    private static final int CYAN = 0xFF3FE0FF, CYAN_DIM = 0xFF1D5A66, TEXT = 0xFFCFF6FF, DIM = 0xFF6FA4B0, GREEN = 0xFF5CFF8A, RED = 0xFFFF4A5A;

    /** A stat row: label, the value shown, and a 0..1 bar. */
    private record Stat(String label, String value, float bar) {}

    private final float[] shown = new float[5];
    private final long[] bumped = new long[5];
    private final String[] lastValue = new String[5];
    private ItemStack lastTool = ItemStack.EMPTY;
    private int lastModules = -1;
    private long insertedAt, syncedAt;

    public UpgradeTableScreen(UpgradeTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H);
    }

    private static float time() {
        return (Util.getMillis() % 1_000_000L) / 1000F;
    }

    // ------------------------------------------------------------------ background and bay

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0, 0, W, H, 256, 256);
        track();
        float t = time();
        int x0 = leftPos, y0 = topPos;

        // A slow scan line sweeping the main area.
        int sweep = (int) ((t * 40) % 140);
        g.fill(x0 + 6, y0 + 16 + sweep, x0 + 158, y0 + 17 + sweep, 0x2A3FE0FF);
        g.fill(x0 + 6, y0 + 15 + sweep, x0 + 158, y0 + 16 + sweep, 0x123FE0FF);

        ItemStack tool = menu.toolStack();
        boolean hasTool = menu.kind() != null;

        // Lit traces and energy pulses from every occupied socket into the bay.
        for (int i = 0; i < UpgradeTableMenu.SOCKETS.length; i++) {
            int[] s = UpgradeTableMenu.SOCKETS[i];
            int sx = x0 + s[0] + 8, sy = y0 + s[1] + 8;
            boolean filled = hasTool && !menu.modules().getItem(i).isEmpty();
            float dx = (x0 + CX) - sx, dy = (y0 + CY) - sy, len = Mth.sqrt(dx * dx + dy * dy);
            float ux = dx / len, uy = dy / len;
            float from = 11, to = len - BAY_R + 2;
            if (filled) {
                dotted(g, sx + ux * from, sy + uy * from, sx + ux * to, sy + uy * to, 0xFF2A8FA3);
                for (int k = 0; k < 3; k++) {
                    float p = ((t * 0.9F + k / 3F + i * 0.13F) % 1F);
                    float px = sx + ux * Mth.lerp(p, from, to), py = sy + uy * Mth.lerp(p, from, to);
                    g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, Math.round(px) - 3, Math.round(py) - 3, 40, 224, 7, 7, 256, 256);
                }
            }
        }

        // Projector ring, turning; brighter with every module fitted.
        int modules = installedCount();
        int ringAlpha = hasTool ? 0x90 + Math.min(0x6F, modules * 0x12) : 0x40;
        g.pose().pushMatrix();
        g.pose().translate(x0 + CX + 0.5F, y0 + CY + 0.5F);
        g.pose().rotate(t * (hasTool ? 0.6F : 0.15F));
        g.blit(RenderPipelines.GUI_TEXTURED, HOLOGRAM, -32, -32, 0, 0, 64, 64, 64, 64, ringAlpha << 24 | 0xFFFFFF);
        g.pose().popMatrix();

        // Sockets: glowing where the carried module would fit, crossed out where it wouldn't.
        ItemStack carried = menu.getCarried();
        for (int i = 0; i < UpgradeTableMenu.SOCKETS.length; i++) {
            int[] s = UpgradeTableMenu.SOCKETS[i];
            int sx = x0 + s[0] - 2, sy = y0 + s[1] - 2;
            if (!hasTool) {
                g.fill(sx + 2, sy + 2, sx + 18, sy + 18, 0x60000000);
                continue;
            }
            if (carried.getItem() instanceof DrillModule) {
                if (menu.accepts(carried, i)) {
                    int pulse = (int) (0x80 + 0x7F * Math.sin(t * 6 + i));
                    g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, sx, sy, 0, 224, 20, 20, 256, 256, pulse << 24 | 0xFFFFFF);
                } else {
                    g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, sx, sy, 20, 224, 20, 20, 256, 256);
                }
            } else if (!menu.modules().getItem(i).isEmpty()) {
                g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, sx, sy, 0, 224, 20, 20, 256, 256, 0x70FFFFFF);
            }
        }

        // The tool: a turning, bobbing hologram, revealed by a scan when it's first put in.
        if (hasTool) {
            float reveal = Mth.clamp((Util.getMillis() - insertedAt) / 600F, 0, 1);
            float spin = Mth.cos(t * 1.3F);
            float sx = Math.max(0.12F, Math.abs(spin)) * Math.signum(spin == 0 ? 1 : spin);
            float bob = Mth.sin(t * 2.1F) * 1.5F;
            int top = y0 + CY - 26, bottom = y0 + CY + 26;
            int cut = (int) Mth.lerp(reveal, top, bottom);
            g.enableScissor(x0 + CX - 30, top, x0 + CX + 30, cut);
            g.pose().pushMatrix();
            g.pose().translate(x0 + CX, y0 + CY + bob);
            g.pose().scale(2.8F * sx, 2.8F);
            g.pose().translate(-8, -8);
            g.item(tool, 0, 0);
            g.pose().popMatrix();
            g.disableScissor();
            if (reveal < 1) {
                g.fill(x0 + CX - 28, cut - 1, x0 + CX + 28, cut + 1, 0xE0BFF6FF);
                g.fill(x0 + CX - 28, cut + 1, x0 + CX + 28, cut + 4, 0x403FE0FF);
            }
        } else {
            boolean on = (Util.getMillis() / 500) % 2 == 0;
            int c = on ? CYAN : CYAN_DIM;
            centered(g, Component.translatable("gui.powerfactor.upgrade.insert"), x0 + CX, y0 + CY - 9, c);
            centered(g, Component.translatable("gui.powerfactor.upgrade.device"), x0 + CX, y0 + CY + 1, c);
        }

        // Install flash: a ring bursting out from the bay.
        float burst = (Util.getMillis() - syncedAt) / 500F;
        if (burst < 1) {
            ring(g, x0 + CX, y0 + CY, BAY_R + burst * 26, (int) (0xD0 * (1 - burst)) << 24 | 0x7FF0FF);
        }

        stats(g, x0 + PANEL_X, y0 + PANEL_Y, tool);
    }

    /** Notice a new tool or a changed module count, to start the animations. */
    private void track() {
        ItemStack tool = menu.toolStack();
        if (!ItemStack.isSameItem(tool, lastTool)) {
            insertedAt = Util.getMillis();
            lastModules = -1;
            java.util.Arrays.fill(shown, 0F);
            java.util.Arrays.fill(lastValue, null);
        }
        lastTool = tool;
        int n = installedCount();
        if (lastModules >= 0 && n != lastModules) {
            syncedAt = Util.getMillis();
        }
        lastModules = n;
    }

    private int installedCount() {
        if (menu.kind() == null) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < ToolModules.SLOTS; i++) {
            n += menu.modules().getItem(i).isEmpty() ? 0 : 1;
        }
        return n;
    }

    // ------------------------------------------------------------------ stats panel

    private void stats(GuiGraphicsExtractor g, int x, int y, ItemStack tool) {
        ToolKind kind = menu.kind();
        if (kind == null) {
            textFit(g, Component.translatable("gui.powerfactor.upgrade.no_device"), x, y, PANEL_W, DIM);
            List<Component> help = List.of(Component.translatable("gui.powerfactor.upgrade.help1"),
                    Component.translatable("gui.powerfactor.upgrade.help2"));
            int ly = y + 16;
            for (Component c : help) {
                for (var line : font.split(c, PANEL_W)) {
                    g.text(font, line, x, ly, DIM, false);
                    ly += 10;
                }
                ly += 4;
            }
            return;
        }
        textFit(g, Component.translatable("tool_kind.powerfactor." + kind.name().toLowerCase(Locale.ROOT) + ".short"), x, y, PANEL_W, CYAN);
        List<Stat> rows = rows(kind, tool);
        long now = Util.getMillis();
        for (int i = 0; i < rows.size(); i++) {
            Stat st = rows.get(i);
            int ry = y + 13 + i * 18;
            if (lastValue[i] != null && !lastValue[i].equals(st.value()) && now - insertedAt > 700) {
                bumped[i] = now;
            }
            lastValue[i] = st.value();
            shown[i] += (st.bar() - shown[i]) * 0.12F;
            boolean hot = now - bumped[i] < 1200;
            g.text(font, st.label(), x, ry, hot ? GREEN : DIM, false);
            g.text(font, st.value(), x + PANEL_W - font.width(st.value()), ry, hot ? GREEN : TEXT, false);
            int by = ry + 10;
            g.fill(x, by, x + PANEL_W, by + 3, 0xFF0A1A20);
            int fill = Math.round(PANEL_W * Mth.clamp(shown[i], 0, 1));
            g.fillGradient(x, by, x + fill, by + 3, hot ? GREEN : CYAN, hot ? 0xFF2A9A50 : 0xFF1E8AA0);
            // Bright leading edge.
            if (fill > 1) {
                g.fill(x + fill - 1, by - 1, x + fill, by + 4, 0xFFE8FDFF);
            }
        }
        // Footer: module count and the tool's charge.
        int fy = y + 13 + 4 * 18 + 2;
        String mods = installedCount() + "/" + ToolModules.SLOTS;
        g.text(font, Component.translatable("gui.powerfactor.upgrade.modules"), x, fy, DIM, false);
        g.text(font, mods, x + PANEL_W - font.width(mods), fy, TEXT, false);
        int cap = capacity(kind, tool), energy = tool.getOrDefault(ModComponents.ENERGY.get(), 0);
        float charge = cap <= 0 ? 0 : Mth.clamp((float) energy / cap, 0, 1);
        g.fill(x, fy + 11, x + PANEL_W, fy + 15, 0xFF2A0F0F);
        g.fill(x, fy + 11, x + Math.round(PANEL_W * charge), fy + 15, 0xFFFF5A4A);
    }

    private static int capacity(ToolKind kind, ItemStack tool) {
        return switch (kind) {
            case DRILL -> DrillItem.capacity(tool);
            case SABER -> ArcSaberItem.capacity(tool);
            case ARC_GUN -> ArcGunItem.capacity(tool);
            default -> tool.getItem() instanceof PoweredItem p ? p.capacity(tool) : 1;
        };
    }

    private List<Stat> rows(ToolKind kind, ItemStack tool) {
        List<Stat> out = new ArrayList<>();
        int cap = capacity(kind, tool);
        out.add(new Stat("CAP", compact(cap) + " FE", Math.min(1, cap / 480_000F)));
        switch (kind) {
            case DRILL -> {
                int speed = ToolModules.count(tool, DrillModule.Type.SPEED);
                out.add(new Stat("COST", DrillItem.costPerBlock(tool) + " FE", 1 - DrillItem.costPerBlock(tool) / 120F));
                out.add(new Stat("SPEED", "x" + fmt(1 + 0.5 * speed), (1 + 0.5F * speed) / 2.5F));
                boolean silk = ToolModules.count(tool, DrillModule.Type.SILK) > 0;
                int fortune = ToolModules.count(tool, DrillModule.Type.FORTUNE);
                String mode = (ToolModules.count(tool, DrillModule.Type.AREA) > 0 ? "3x3 " : "") + (silk ? "SILK" : fortune > 0 ? "FORT " + fortune : "");
                out.add(new Stat("MODE", mode.isBlank() ? "STD" : mode.trim(), (silk || fortune > 0 ? 0.5F : 0F)
                        + (ToolModules.count(tool, DrillModule.Type.AREA) > 0 ? 0.5F : 0)));
            }
            case CHAINSAW -> {
                int speed = ToolModules.count(tool, DrillModule.Type.SPEED);
                out.add(new Stat("COST", ChainsawItem.perLog(tool) + " FE", 1 - ChainsawItem.perLog(tool) / 70F));
                out.add(new Stat("SPEED", "x" + fmt(1 + 0.5 * speed), (1 + 0.5F * speed) / 2.5F));
                float dmg = 8 + ToolModules.overcharge(tool, 3F);
                out.add(new Stat("DMG", fmt(dmg), dmg / 17F));
            }
            case JETPACK -> {
                float thrust = JetpackItem.thrust(tool);
                int plates = ToolModules.count(tool, DrillModule.Type.PLATING);
                out.add(new Stat("THRUST", Math.round(thrust * 100) + "%", thrust / 2.05F));
                out.add(new Stat("ARMOR", String.valueOf(3 + 2 * plates), (3 + 2 * plates) / 9F));
                int cost = com.powerfactor.item.ToolModules.cost(tool, JetpackItem.THRUST_FE);
                out.add(new Stat("COST", cost + "/t", 1 - cost / 60F));
            }
            case SABER -> {
                float dmg = ArcSaberItem.LIT_DAMAGE + ToolModules.overcharge(tool, 3F);
                int chain = ToolModules.count(tool, DrillModule.Type.CHAIN);
                out.add(new Stat("DMG", fmt(dmg), dmg / 19F));
                out.add(new Stat("CHAIN", chain == 0 ? "-" : "+" + chain, chain / 2F));
                int cost = ToolModules.cost(tool, ArcSaberItem.PER_HIT);
                out.add(new Stat("COST", cost + " FE", 1 - cost / 180F));
            }
            case ARC_GUN -> {
                float dmg = 10 + ToolModules.overcharge(tool, 4F);
                int hops = 4 + 2 * ToolModules.count(tool, DrillModule.Type.CHAIN);
                out.add(new Stat("DMG", fmt(dmg), dmg / 22F));
                out.add(new Stat("ARCS", String.valueOf(hops + 1), (hops + 1) / 9F));
                out.add(new Stat("COST", ArcGunItem.cost(tool) + " FE", 1 - ArcGunItem.cost(tool) / 700F));
            }
        }
        return out;
    }

    private static String compact(int n) {
        return n >= 1_000_000 ? fmt(n / 1_000_000.0) + "M" : n >= 1000 ? Math.round(n / 1000.0) + "k" : String.valueOf(n);
    }

    private static String fmt(double v) {
        return Math.abs(v - Math.round(v)) < 0.05 ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
    }

    // ------------------------------------------------------------------ labels, slots, helpers

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
        boolean cursor = (Util.getMillis() / 400) % 2 == 0;
        Component title = Component.translatable("gui.powerfactor.upgrade.title");
        g.text(font, title, 8, 4, CYAN, false);
        if (cursor) {
            g.fill(10 + font.width(title), 4, 15 + font.width(title), 12, CYAN);
        }
        Component status = menu.kind() == null ? Component.translatable("gui.powerfactor.upgrade.standby")
                : Component.translatable("gui.powerfactor.upgrade.linked");
        g.text(font, status, W - 8 - font.width(status), 4, menu.kind() == null ? DIM : GREEN, false);
        g.text(font, Component.translatable("gui.powerfactor.upgrade.inventory"), UpgradeTableMenu.INV_X, 126, DIM, false);
    }

    /** The tool socket shows the hologram instead of a little item icon. */
    @Override
    protected void extractSlot(GuiGraphicsExtractor g, Slot slot, int mouseX, int mouseY) {
        if (slot.index == UpgradeTableMenu.TOOL_SLOT) {
            return;
        }
        super.extractSlot(g, slot, mouseX, mouseY);
    }

    private void textFit(GuiGraphicsExtractor g, Component c, int x, int y, int w, int color) {
        g.text(font, font.substrByWidth(c, w).getString(), x, y, color, false);
    }

    private void centered(GuiGraphicsExtractor g, Component c, int x, int y, int color) {
        g.text(font, c, x - font.width(c) / 2, y, color, false);
    }

    private static void dotted(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int color) {
        int steps = (int) Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i++) {
            int x = Math.round(Mth.lerp((float) i / Math.max(1, steps), x0, x1));
            int y = Math.round(Mth.lerp((float) i / Math.max(1, steps), y0, y1));
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static void ring(GuiGraphicsExtractor g, int cx, int cy, float r, int color) {
        int n = (int) (r * 6);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }
}
