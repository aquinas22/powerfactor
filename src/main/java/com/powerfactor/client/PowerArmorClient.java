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
        HudPreferences.tick();
        Minecraft mc = Minecraft.getInstance();
        while (SETTINGS.consumeClick()) if (mc.player != null && (worn() || !com.powerfactor.movement.GrappleServer.device(mc.player).isEmpty()
                || com.powerfactor.item.WingsuitItem.worn(mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST))) && mc.gui.screen() == null) mc.gui.setScreen(new PowerArmorScreen());
    }
    public static boolean worn() {
        var player = Minecraft.getInstance().player; if (player == null) return false;
        for (var part : PowerArmorItem.Part.values()) if (!PowerArmorServer.piece(player, part).isEmpty()) return true;
        return false;
    }
    public static int hudHeight() { return HudPreferences.armor() && worn() ? 26 : 0; }
    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        var mc = Minecraft.getInstance(); if (mc.gui.hud.isHidden() || mc.player == null || hudHeight() == 0) return;
        int x=6,y=6,width=84; long stored=0,capacity=0;
        for(var part:PowerArmorItem.Part.values()) {
            var stack=PowerArmorServer.piece(mc.player,part);
            if(stack.getItem() instanceof PowerArmorItem armor){stored+=PoweredItem.energy(stack);capacity+=armor.capacity(stack);}
        }
        Skin.hud(g,x,y,width,26);
        g.text(mc.font,Component.translatable("gui.powerfactor.armor.compact"),x+5,y+4,Skin.TEAL,false);
        String pct=capacity==0?"0%":Math.round(100.0*stored/capacity)+"%";
        g.text(mc.font,pct,x+width-5-mc.font.width(pct),y+4,stored*100<capacity*15?Skin.FE:Skin.TEXT,false);
        int i=0;
        for(var part:PowerArmorItem.Part.values()) {
            var stack=PowerArmorServer.piece(mc.player,part);int sx=x+5+i++*19;
            g.text(mc.font,Component.translatable("gui.powerfactor.armor."+part.name().toLowerCase(java.util.Locale.ROOT)+".letter"),sx,y+15,Skin.DIM,false);
            float fill=stack.getItem() instanceof PowerArmorItem armor?(float)PoweredItem.energy(stack)/armor.capacity(stack):0;
            Skin.bar(g,sx+7,y+17,10,fill,fill<.15F?Skin.FE:Skin.TEAL);
        }
    }
    private PowerArmorClient() {}
}
