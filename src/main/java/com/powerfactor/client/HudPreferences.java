package com.powerfactor.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Local overlay choices. They do not alter equipment or send control packets to the server. */
public final class HudPreferences {
    public static final KeyMapping TOGGLE = new KeyMapping("key.powerfactor.toggle_hud",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_F8,JetpackClient.CATEGORY);
    private static boolean loaded, armor = true, movement = true, hidden;
    private static Path file() { return Minecraft.getInstance().gameDirectory.toPath().resolve("config/powerfactor-hud.json"); }
    private static void load() {
        if (loaded) return; loaded = true;
        try {
            if (!Files.isRegularFile(file())) return;
            var data = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
            armor = !data.has("armor") || data.get("armor").getAsBoolean();
            movement = !data.has("movement") || data.get("movement").getAsBoolean();
            hidden = data.has("hidden") && data.get("hidden").getAsBoolean();
        } catch (Exception ignored) { armor = movement = true; hidden = false; }
    }
    private static void save() {
        try {
            var data = new JsonObject(); data.addProperty("armor",armor);data.addProperty("movement",movement);data.addProperty("hidden",hidden);
            Files.createDirectories(file().getParent());Path temp=file().resolveSibling("powerfactor-hud.json.tmp");Files.writeString(temp,data+"\n");
            Files.move(temp,file(),StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception failure) { com.powerfactor.PowerFactor.LOGGER.warn("Could not save PowerFactor overlay settings: {}",failure.getMessage()); }
    }
    public static boolean armor() { load(); return armor && !hidden; }
    public static boolean movement() { load(); return movement && !hidden; }
    public static void toggleArmor() { load(); if(hidden){hidden=false;armor=true;}else armor=!armor;save(); }
    public static void toggleMovement() { load(); if(hidden){hidden=false;movement=true;}else movement=!movement;save(); }
    public static void set(boolean suit,boolean move) { load();armor=suit;movement=move;hidden=false;save(); }
    public static void tick() { while(TOGGLE.consumeClick()){load();hidden=!hidden;save();} }
    private HudPreferences() {}
}
