package com.powerfactor.client;

import com.powerfactor.registry.ModBlocks;
import com.powerfactor.registry.ModItems;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/** The in-game manual: chapters down the left, the chapter's text (scrollable) on the right. */
public class HandbookScreen extends Screen {
    private record Chapter(String id, ItemLike icon) {}

    private static final List<Chapter> CHAPTERS = List.of(
            new Chapter("start", ModBlocks.COAL_TURBINE.get()),
            new Chapter("units", ModItems.CAPACITOR.get()),
            new Chapter("grid", ModBlocks.COPPER_WIRE.get()),
            new Chapter("power_factor", ModBlocks.AUTO_CAPACITOR_BANK.get()),
            new Chapter("machines", ModBlocks.CRUSHER.get()),
            new Chapter("upgrades", ModItems.SPEED_UPGRADE.get()),
            new Chapter("ores", ModItems.ZINC_DUST.get()),
            new Chapter("logistics", ModBlocks.ITEM_PIPE.get()),
            new Chapter("scada", ModBlocks.SCADA_TERMINAL.get()),
            new Chapter("transmission", ModBlocks.HV_PYLON.get()),
            new Chapter("generators", ModBlocks.WIND_TURBINE.get()),
            new Chapter("ender", ModBlocks.ENDER_TRANSMITTER.get()),
            new Chapter("tools", ModItems.ARC_SABER.get()));

    private static int selected;
    private static final int W = 340, H = 200, LIST_W = 124;
    private int left, top, scroll;
    private List<FormattedCharSequence> lines = List.of();

    public HandbookScreen() {
        super(Component.translatable("item.powerfactor.powerfactor_handbook"));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        layout();
    }

    private void layout() {
        String id = CHAPTERS.get(selected).id();
        lines = font.split(Component.translatable("handbook.powerfactor." + id + ".text"), W - LIST_W - 22);
        scroll = 0;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fill(left - 4, top - 4, left + W + 4, top + H + 4, 0xFF1B3A28);
        g.fill(left - 3, top - 3, left + W + 3, top + H + 3, 0xFFB4684D);
        g.fill(left - 2, top - 2, left + W + 2, top + H + 2, 0xFF1B3A28);
        g.fill(left, top, left + LIST_W, top + H, 0xFF0F2016);
        g.fill(left + LIST_W, top, left + W, top + H, 0xFFF2E8D4);
        g.text(font, Component.translatable("handbook.powerfactor.title"), left + 6, top + 6, 0xFFE0A050, false);
        for (int i = 0; i < CHAPTERS.size(); i++) {
            Chapter c = CHAPTERS.get(i);
            int y = top + 20 + i * 13;
            boolean hover = mouseX >= left && mouseX < left + LIST_W && mouseY >= y - 1 && mouseY < y + 12;
            if (i == selected) {
                g.fill(left + 1, y - 1, left + LIST_W - 1, y + 12, 0xFF2F6B44);
            } else if (hover) {
                g.fill(left + 1, y - 1, left + LIST_W - 1, y + 12, 0xFF1F4A30);
            }
            g.text(font, font.substrByWidth(Component.translatable("handbook.powerfactor." + c.id() + ".title"), LIST_W - 8).getString(),
                    left + 5, y + 2, i == selected ? 0xFFFFFFFF : 0xFFB8F5C0, false);
        }
        Chapter c = CHAPTERS.get(selected);
        int x = left + LIST_W + 10;
        g.item(new ItemStack(c.icon()), x, top + 6);
        g.text(font, Component.translatable("handbook.powerfactor." + c.id() + ".title"), x + 20, top + 10, 0xFF2B2B2B, false);
        int visible = (H - 34) / 10;
        for (int i = 0; i < visible && scroll + i < lines.size(); i++) {
            g.text(font, lines.get(scroll + i), x, top + 28 + i * 10, 0xFF333333, false);
        }
        if (lines.size() > visible) {
            int trackH = H - 34, thumbH = Math.max(10, trackH * visible / lines.size());
            int thumbY = top + 28 + (trackH - thumbH) * scroll / (lines.size() - visible);
            g.fill(left + W - 4, thumbY, left + W - 2, thumbY + thumbH, 0xFF8A7A60);
        }
        super.extractRenderState(g, mouseX, mouseY, a);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (int i = 0; i < CHAPTERS.size(); i++) {
            int y = top + 20 + i * 13;
            if (event.x() >= left && event.x() < left + LIST_W && event.y() >= y - 1 && event.y() < y + 12) {
                selected = i;
                layout();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        int visible = (H - 34) / 10;
        scroll = Mth.clamp(scroll - (int) Math.signum(scrollY) * 2, 0, Math.max(0, lines.size() - visible));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
