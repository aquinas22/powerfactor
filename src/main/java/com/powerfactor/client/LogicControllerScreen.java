package com.powerfactor.client;

import com.powerfactor.client.storage.StorageScreens;
import com.powerfactor.device.LogicControllerBlockEntity;
import com.powerfactor.device.LogicControllerMenu;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Logic Controller console: a schematic of the block with live input and output lamps, a scrolling
 * logic-analyser trace of all four signals, the eight programs, and the N setting.
 */
public class LogicControllerScreen extends StorageScreens.Base<LogicControllerMenu> {
    private static final int ON = 0xFFFF4A3A, OFF = 0xFF4A2A2A, TRACE_ON = 0xFF5CFF8A, TRACE_OFF = 0xFF2A5A44;
    private final boolean[][] trace = new boolean[4][64];
    private int head;
    private long lastSample;

    public LogicControllerScreen(LogicControllerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void main(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        int in = menu.inputs();
        boolean[] now = {(in & 1) != 0, (in & 2) != 0, (in & 4) != 0, menu.output()};
        long t = net.minecraft.util.Util.getMillis() / 50;
        if (t != lastSample) {
            lastSample = t;
            for (int k = 0; k < 4; k++) trace[k][head] = now[k];
            head = (head + 1) % 64;
        }
        // Schematic: the controller seen from above with its three inputs and the output.
        Skin.lcd(g, x + 8, y + 18, 52, 46);
        int cx = x + 34, cy = y + 40;
        g.fill(cx - 10, cy - 10, cx + 10, cy + 10, 0xFF3A4652);
        g.fill(cx - 9, cy - 9, cx + 9, cy + 9, 0xFF22303A);
        String prog = menu.program().name();
        g.centeredText(font, Component.literal(prog.length() > 4 ? prog.substring(0, 4) : prog), cx, cy - 4, Skin.LCD_TEXT);
        lamp(g, cx - 19, cy - 2, now[0], "L");
        lamp(g, cx - 2, cy + 15, now[1], "B");
        lamp(g, cx + 13, cy - 2, now[2], "R");
        lamp(g, cx - 2, cy - 21, now[3], "Q");
        // Logic analyser.
        Skin.lcd(g, x + 63, y + 18, 80, 46);
        String[] names = {"L", "B", "R", "Q"};
        for (int k = 0; k < 4; k++) {
            int ty = y + 21 + k * 11;
            g.text(font, names[k], x + 66, ty, Skin.LCD_DIM, false);
            for (int i = 0; i < 64; i++) {
                boolean v = trace[k][(head + i) % 64];
                int px = x + 74 + i;
                g.fill(px, v ? ty : ty + 7, px + 1, v ? ty + 1 : ty + 8, k == 3 ? (v ? ON : 0xFF6A2A2A) : (v ? TRACE_ON : TRACE_OFF));
                boolean prev = trace[k][(head + i + 63) % 64];
                if (i > 0 && prev != v) g.fill(px, ty, px + 1, ty + 8, k == 3 ? ON : TRACE_ON);
            }
        }
        // Programs.
        var programs = LogicControllerBlockEntity.Program.values();
        for (int i = 0; i < programs.length; i++) {
            int bx = 8 + (i % 4) * 40, by = 67 + (i / 4) * 14;
            boolean sel = menu.program() == programs[i];
            int id = LogicControllerBlockEntity.BUTTON_PROGRAM + i;
            button(bx, by, 38, 13, Component.translatable("gui.powerfactor.logic.program." + programs[i].name().toLowerCase(Locale.ROOT)),
                    Component.translatable("gui.powerfactor.logic.help." + programs[i].name().toLowerCase(Locale.ROOT)),
                    sel ? Skin.TEAL : Skin.LCD_TEXT, sel, () -> send(id));
        }
        // N.
        String[] labels = {"-10", "-1", "+1", "+10"};
        for (int i = 0; i < 4; i++) {
            int id = LogicControllerBlockEntity.BUTTON_N + i;
            button(146, 18 + i * 12, 24, 11, Component.literal(labels[i]), Component.translatable("gui.powerfactor.logic.n_tip"), Skin.LCD_TEXT, false,
                    () -> send(id));
        }
        // N, small, in the schematic's corner (clear of the lamps).
        g.pose().pushMatrix();
        g.pose().translate(x + 11, y + 21);
        g.pose().scale(0.75F, 0.75F);
        g.text(font, Component.literal("N=" + menu.n()), 0, 0, Skin.LCD_TEXT, false);
        g.pose().popMatrix();
    }

    private void lamp(GuiGraphicsExtractor g, int x, int y, boolean on, String label) {
        g.fill(x - 1, y - 1, x + 6, y + 6, 0xFF101418);
        g.fill(x, y, x + 5, y + 5, on ? ON : OFF);
        if (on) g.fill(x + 1, y + 1, x + 3, y + 2, 0xFFFFC0B0);
        g.text(font, label, x + (label.equals("L") ? -6 : label.equals("R") ? 6 : 7), y - 1, Skin.LCD_DIM, false);
    }
}
