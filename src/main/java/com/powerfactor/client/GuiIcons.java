package com.powerfactor.client;

import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.machine.SideConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Small pixel symbols shared by face controls, legends and redstone selectors. */
public final class GuiIcons {
    public static void port(GuiGraphicsExtractor g, int x, int y, SideConfig.Mode mode, int color) {
        switch (mode) {
            case NONE -> { for (int i = 0; i < 7; i++) { g.fill(x + 2 + i, y + i, x + 3 + i, y + i + 1, color); g.fill(x + 8 - i, y + i, x + 9 - i, y + i + 1, color); } }
            case INPUT -> { g.outline(x + 7, y, 5, 9, color); arrow(g, x, x + 8, y + 4, color); }
            case OUTPUT -> { g.outline(x, y, 5, 9, color); arrow(g, x + 3, x + 11, y + 4, color); }
            case BOTH -> { arrow(g, x, x + 11, y + 2, color); arrow(g, x + 11, x, y + 7, color); }
        }
    }
    private static void arrow(GuiGraphicsExtractor g, int from, int to, int y, int color) {
        g.fill(Math.min(from, to), y, Math.max(from, to) + 1, y + 1, color);
        int direction = Integer.signum(to - from);
        for (int i = 1; i <= 2; i++) { int x = to - i * direction; g.fill(x, y - i, x + 1, y - i + 1, color); g.fill(x, y + i, x + 1, y + i + 1, color); }
    }
    public static void redstone(GuiGraphicsExtractor g, int x, int y, MachineBlockEntity.RedstoneMode mode) {
        if (mode == MachineBlockEntity.RedstoneMode.IGNORE) {
            g.text(net.minecraft.client.Minecraft.getInstance().font, "∞", x + 2, y + 1, Skin.TEXT, false); return;
        }
        g.fill(x + 5, y + 4, x + 7, y + 11, 0xFFA88958);
        g.fill(x + 3, y + 1, x + 9, y + 5, mode == MachineBlockEntity.RedstoneMode.HIGH ? 0xFFFF5145 : 0xFF805650);
        if (mode == MachineBlockEntity.RedstoneMode.HIGH) g.fill(x + 4, y, x + 8, y + 2, 0xFFFFCE9D);
        else for (int i = 0; i < 9; i++) g.fill(x + 1 + i, y + 8 - i, x + 2 + i, y + 9 - i, Skin.TEXT);
    }
    private GuiIcons() {}
}
