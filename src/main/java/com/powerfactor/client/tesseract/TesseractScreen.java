package com.powerfactor.client.tesseract;

import com.powerfactor.client.Skin;
import com.powerfactor.tesseract.TesseractBlockEntity;
import com.powerfactor.tesseract.TesseractMenu;
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

/**
 * Tesseract console: a wireframe hypercube turning on the left; the frequency as a big four-digit readout
 * with steppers under it; three channel switches (item, fluid, energy: off / send / receive); and the
 * lock that makes the frequency private to you or shared with everyone.
 */
public class TesseractScreen extends AbstractContainerScreen<TesseractMenu> {
    static final int[] COLORS = {0xFFFFC94A, 0xFF4FA8FF, 0xFFFF5A4A};
    private static final Skin.Icon[] ICONS = {Skin.Icon.ITEM, Skin.Icon.FLUID, Skin.Icon.FE};
    private static final String[] KEYS = {"items", "fluids", "energy"};
    private record Btn(int x, int y, int w, int h, int id, Component tip) {}
    private final List<Btn> btns = new ArrayList<>();

    public TesseractScreen(TesseractMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 122);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractBackground(g, mx, my, a);
        btns.clear();
        int x = leftPos, y = topPos;
        long t = Util.getMillis();
        Skin.panel(g, x, y, imageWidth, imageHeight);
        Skin.title(g, font, title, x, y, imageWidth);
        // Hero: the hypercube (x 8..72, y 20..84).
        Skin.lcd(g, x + 8, y + 20, 64, 64);
        hypercube(g, x + 40, y + 52, 15, t / 1000.0, menu.linked() > 0);
        Skin.iconValue(g, font, Skin.Icon.LINK, Component.literal(String.valueOf(menu.linked())), x + 12, y + 74, Skin.LCD_DIM,
                Component.translatable("gui.powerfactor.tesseract.linked", menu.linked()));
        // Frequency (x 78..168): four big digits, steppers beneath.
        Skin.lcd(g, x + 78, y + 20, 90, 20);
        String f = String.format("%04d", menu.frequency());
        g.pose().pushMatrix();
        g.pose().translate(x + 123 - font.width(f), y + 23);
        g.pose().scale(2F, 2F);
        g.text(font, f, 0, 0, 0xFFB18CFF, false);
        g.pose().popMatrix();
        String[] labels = {"−100", "−10", "−1", "+1", "+10", "+100"};
        // Steppers: down on the left half, up on the right, three each (two rows of three 29-px chips).
        for (int i = 0; i < 6; i++) {
            int bx = x + 78 + (i % 3) * 31, by = y + 42 + (i / 3) * 12;
            int label = i < 3 ? i : 3 + (i - 3);
            Skin.control(g, bx, by, 28, 11, false, mx >= bx && mx < bx + 28 && my >= by && my < by + 11);
            g.centeredText(font, labels[label], bx + 14, by + 2, i < 3 ? 0xFFFFA090 : 0xFF8FF0B0);
            btns.add(new Btn(bx, by, 28, 11, TesseractMenu.BUTTON_FREQ + label, Component.translatable("gui.powerfactor.tesseract.frequency")));
        }
        // Channels (x 78..168, y 62..): icon + mode each, cycling on click.
        for (int c = 0; c < 3; c++) {
            int bx = x + 78 + c * 31, by = y + 67, mode = menu.mode(c);
            Skin.control(g, bx, by, 28, 22, mode != TesseractBlockEntity.OFF, mx >= bx && mx < bx + 28 && my >= by && my < by + 22);
            Skin.icon(g, ICONS[c], bx + 3, by + 3, null);
            if (mode == TesseractBlockEntity.OFF) g.fill(bx + 3, by + 3, bx + 12, by + 12, 0x9A17242E);
            Skin.icon(g, mode == TesseractBlockEntity.SEND ? Skin.Icon.OUTPUT : mode == TesseractBlockEntity.RECEIVE ? Skin.Icon.INPUT : Skin.Icon.MINUS,
                    bx + 16, by + 3, null);
            g.fill(bx + 3, by + 15, bx + 25, by + 17, mode == TesseractBlockEntity.OFF ? 0xFF3A4652 : COLORS[c]);
            btns.add(new Btn(bx, by, 28, 22, TesseractMenu.BUTTON_CHANNEL + c, Component.translatable("gui.powerfactor.tesseract.channel",
                    Component.translatable("gui.powerfactor.laser.channel." + KEYS[c]), Component.translatable("gui.powerfactor.laser.mode." + mode))));
        }
        // Lock: private (yours) or shared.
        int lx = x + 8, ly = y + 91;
        Skin.control(g, lx, ly, 64, 18, false, mx >= lx && mx < lx + 64 && my >= ly && my < ly + 18);
        Skin.icon(g, menu.shared() ? Skin.Icon.UNLOCK : Skin.Icon.LOCK, lx + 4, ly + 4, null);
        g.text(font, Component.translatable(menu.shared() ? "gui.powerfactor.tesseract.shared" : "gui.powerfactor.tesseract.private"), lx + 16, ly + 5,
                Skin.LCD_TEXT, false);
        btns.add(new Btn(lx, ly, 64, 18, TesseractMenu.BUTTON_SHARED, Component.translatable("gui.powerfactor.tesseract.lock_tip")));
        Skin.lcd(g, x + 78, y + 92, 90, 16);
        Skin.condensed(g, font, Component.translatable("gui.powerfactor.tesseract.hint"), x + 82, y + 96, 82, Skin.LCD_DIM);
    }

    /** A 4D hypercube rotating in the xw and yz planes, perspective-projected to 2D. */
    static void hypercube(GuiGraphicsExtractor g, int cx, int cy, float size, double t, boolean linked) {
        double[][] v = new double[16][];
        for (int i = 0; i < 16; i++) {
            double x = (i & 1) == 0 ? -1 : 1, y = (i & 2) == 0 ? -1 : 1, z = (i & 4) == 0 ? -1 : 1, w = (i & 8) == 0 ? -1 : 1;
            double a = t * 0.7, b = t * 0.45;
            double x2 = x * Math.cos(a) - w * Math.sin(a), w2 = x * Math.sin(a) + w * Math.cos(a);
            double y2 = y * Math.cos(b) - z * Math.sin(b), z2 = y * Math.sin(b) + z * Math.cos(b);
            double k = 2.4 / (3 - w2);
            double px = x2 * k, py = y2 * k, pz = z2 * k;
            double r = t * 0.3;
            double px2 = px * Math.cos(r) - pz * Math.sin(r);
            v[i] = new double[] {cx + px2 * size, cy + py * size, w2};
        }
        for (int i = 0; i < 16; i++) {
            for (int bit = 0; bit < 4; bit++) {
                int j = i ^ (1 << bit);
                if (j < i) continue;
                int col = bit == 3 ? (linked ? 0xFFB18CFF : 0xFF5A4A80) : (linked ? 0xFF6FE3FF : 0xFF3F6A78);
                line(g, v[i][0], v[i][1], v[j][0], v[j][1], col);
            }
        }
    }

    private static void line(GuiGraphicsExtractor g, double x0, double y0, double x1, double y1, int col) {
        int steps = (int) Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) + 1;
        for (int s = 0; s <= steps; s++) {
            int px = (int) Math.round(x0 + (x1 - x0) * s / steps), py = (int) Math.round(y0 + (y1 - y0) * s / steps);
            g.fill(px, py, px + 1, py + 1, col);
        }
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
