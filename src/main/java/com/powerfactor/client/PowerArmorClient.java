package com.powerfactor.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.powerfactor.item.PowerArmorItem;
import com.powerfactor.item.PowerArmorServer;
import com.powerfactor.item.PoweredItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Compact four-battery HUD and the suit's settings key. */
public final class PowerArmorClient {
    public static final KeyMapping SETTINGS = new KeyMapping("key.powerfactor.armor_settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, JetpackClient.CATEGORY);
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        while (SETTINGS.consumeClick()) if (mc.player != null && worn() && mc.gui.screen() == null) mc.gui.setScreen(new PowerArmorScreen());
    }
    public static boolean worn() {
        var player = Minecraft.getInstance().player; if (player == null) return false;
        for (var part : PowerArmorItem.Part.values()) if (!PowerArmorServer.piece(player, part).isEmpty()) return true;
        return false;
    }
    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        var mc = Minecraft.getInstance(); if (mc.gui.hud.isHidden() || mc.player == null || !worn()) return;
        int x = 6, y = 6, width = 88;
        Skin.panel(g, x, y, width, 13 + 4 * 14);
        g.text(mc.font, Component.translatable("gui.powerfactor.armor.hud"), x + 5, y + 4, Skin.TEAL, false);
        int row = 0;
        for (var part : PowerArmorItem.Part.values()) {
            var stack = PowerArmorServer.piece(mc.player, part);
            int ry = y + 15 + row++ * 14;
            g.text(mc.font, Component.translatable("gui.powerfactor.armor." + part.name().toLowerCase(java.util.Locale.ROOT) + ".short"), x + 5, ry, Skin.DIM, false);
            if (stack.getItem() instanceof PowerArmorItem armor) {
                float fill = (float) PoweredItem.energy(stack) / armor.capacity(stack);
                Skin.bar(g, x + 30, ry + 2, 25, fill, fill < 0.15 ? Skin.FE : Skin.TEAL);
                String pct = Math.round(fill * 100) + "%";
                g.text(mc.font, pct, x + width - 4 - mc.font.width(pct), ry, fill < 0.15 ? Skin.FE : Skin.TEXT, false);
            } else g.text(mc.font, "—", x + 61, ry, Skin.DIM, false);
        }
    }
    private PowerArmorClient() {}
}
