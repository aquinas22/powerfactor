package com.powerfactor.client;

import com.powerfactor.radio.RadioPayload;
import com.powerfactor.radio.WalkieTalkieItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/** A little radio display above the hotbar: your channel, TX while you talk, RX and who's talking. */
public final class RadioHud {
    private static String speaker = "";
    private static int rxChannel, txChannel;

    public static void receive(RadioPayload p) {
        if (p.self()) {
            txChannel = p.channel();
        } else {
            rxChannel = p.channel();
            speaker = p.speaker();
        }
    }

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden()) {
            return;
        }
        ItemStack held = mc.player.getMainHandItem().getItem() instanceof WalkieTalkieItem ? mc.player.getMainHandItem()
                : mc.player.getOffhandItem().getItem() instanceof WalkieTalkieItem ? mc.player.getOffhandItem() : ItemStack.EMPTY;
        if (held.isEmpty() && rxChannel == 0) {
            return;
        }
        var font = mc.font;
        String text;
        int color;
        if (txChannel > 0) {
            text = "TX  CH " + txChannel;
            color = 0xFFFF5A4A;
        } else if (rxChannel > 0) {
            text = "RX  CH " + rxChannel + "  " + speaker;
            color = 0xFF7CFF8A;
        } else if (!WalkieTalkieItem.on(held)) {
            text = "RADIO OFF";
            color = 0xFF8A8A8A;
        } else {
            text = "CH " + WalkieTalkieItem.channel(held);
            color = 0xFFB8F5FF;
        }
        // Right of the hotbar (the jetpack gauge takes the left), clear of the action-bar text above it.
        int width = g.guiWidth(), x = width / 2 + 97, y = g.guiHeight() - 17;
        int available = width - x - 5;
        if (available < 90) { available = Math.min(148, width / 2 - 8); x = width - available - 5; y = g.guiHeight() - 64; }
        text = Skin.fit(font, Component.literal(text), available - 16).getString();
        int w = font.width(text) + 16;
        g.fill(x, y, x + w, y + 13, 0xC0101418);
        g.fill(x, y + 12, x + w, y + 13, color);
        // A blinking dot while live.
        boolean live = txChannel > 0 || rxChannel > 0;
        if (!live || (Util.getMillis() / 300) % 2 == 0) {
            g.fill(x + 4, y + 4, x + 8, y + 8, live ? color : 0xFF3A4A50);
        }
        g.text(font, Component.literal(text), x + 11, y + 2, color, false);
    }

    private RadioHud() {}
}
