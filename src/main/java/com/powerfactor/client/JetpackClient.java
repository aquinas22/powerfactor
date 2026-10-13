package com.powerfactor.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.powerfactor.PowerFactor;
import com.powerfactor.item.JetpackItem;
import com.powerfactor.item.JetpackServer;
import com.powerfactor.item.PoweredItem;
import com.powerfactor.item.ToolPayloads;
import com.powerfactor.registry.ModSounds;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Client side of the jetpack: reads the wearer's keys and moves them (thrust, hover, sink), tells the
 * server what it's doing, spins every visible jetpack's fans, plays their sound, and draws the gauge.
 */
public final class JetpackClient {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(PowerFactor.id("powerfactor"));
    public static final KeyMapping HOVER_KEY = new KeyMapping("key.powerfactor.jetpack_hover", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);

    /** Mode of every player's jetpack we've been told about, by entity id. */
    private static final Map<Integer, Integer> MODES = new HashMap<>();
    /** Fan state by entity id: angle, previous angle, speed (degrees per tick). */
    private static final Map<Integer, float[]> FANS = new HashMap<>();
    private static final Map<Integer, JetpackSound> SOUNDS = new HashMap<>();
    private static int sentMode = -1;
    private static int localMode;

    public static void onSync(ToolPayloads.JetpackSync sync) {
        if (Minecraft.getInstance().player != null && sync.entity() == Minecraft.getInstance().player.getId()) {
            return; // our own mode is decided here, not echoed back
        }
        MODES.put(sync.entity(), sync.mode());
    }

    public static int mode(int entity) {
        LocalPlayer self = Minecraft.getInstance().player;
        return self != null && self.getId() == entity ? localMode : MODES.getOrDefault(entity, JetpackServer.OFF);
    }

    public static float fanAngle(int entity, float partial) {
        float[] f = FANS.get(entity);
        return f == null ? 0 : Mth.lerp(partial, f[1], f[0]);
    }

    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            MODES.clear();
            FANS.clear();
            SOUNDS.clear();
            sentMode = -1;
            return;
        }
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        boolean worn = JetpackItem.worn(chest);
        while (HOVER_KEY.consumeClick()) {
            if (worn) {
                ClientPacketDistributor.sendToServer(new ToolPayloads.JetpackToggle());
            }
        }
        localMode = worn ? flight(player, chest) : JetpackServer.OFF;
        if (localMode != sentMode) {
            sentMode = localMode;
            ClientPacketDistributor.sendToServer(new ToolPayloads.JetpackInput(localMode));
        }

        for (Player p : mc.level.players()) {
            boolean wearing = JetpackItem.worn(p.getItemBySlot(EquipmentSlot.CHEST));
            if (!wearing) {
                FANS.remove(p.getId());
                continue;
            }
            int mode = mode(p.getId());
            float[] f = FANS.computeIfAbsent(p.getId(), k -> new float[3]);
            float target = mode == JetpackServer.THRUST ? 62F : mode == JetpackServer.HOVER ? 38F : 0F;
            f[2] += (target - f[2]) * (target > f[2] ? 0.25F : 0.05F);
            f[1] = f[0];
            f[0] += f[2];
            if (f[0] > 360F) {
                f[0] -= 360F;
                f[1] -= 360F;
            }
            JetpackSound sound = SOUNDS.get(p.getId());
            if (f[2] > 2F && (sound == null || sound.isStopped())) {
                sound = new JetpackSound(p);
                SOUNDS.put(p.getId(), sound);
                mc.getSoundManager().play(sound);
            }
        }
    }

    /** Applies this tick's lift to the local player and returns the mode it amounts to. */
    private static int flight(LocalPlayer player, ItemStack chest) {
        if (player.getAbilities().flying || player.isFallFlying() || player.isPassenger()
                || PoweredItem.energy(chest) < JetpackItem.flightCost(chest, player.input.keyPresses.jump() ? JetpackItem.THRUST_FE : JetpackItem.HOVER_FE)
                        && !player.getAbilities().instabuild) {
            return JetpackServer.OFF;
        }
        var keys = player.input.keyPresses;
        Vec3 v = player.getDeltaMovement();
        int mode;
        double vy = v.y;
        float thrust = JetpackItem.thrust(chest);
        if (keys.jump()) {
            mode = JetpackServer.THRUST;
            vy = Math.min(vy + 0.12 * thrust, 0.55 * thrust);
        } else if (JetpackItem.hover(chest) && !player.onGround()) {
            mode = JetpackServer.HOVER;
            double target = keys.shift() ? -0.35 : -0.03;
            vy += (target - vy) * 0.25;
        } else {
            return JetpackServer.OFF;
        }
        // Air control: the fans can be tilted to push you along.
        double vx = v.x, vz = v.z;
        if (keys.forward() && !player.onGround()) {
            float yaw = player.getYRot() * Mth.DEG_TO_RAD;
            vx += -Mth.sin(yaw) * 0.035 * thrust;
            vz += Mth.cos(yaw) * 0.035 * thrust;
            double h = Math.sqrt(vx * vx + vz * vz), cap = 0.75 * thrust;
            if (h > cap) {
                vx *= cap / h;
                vz *= cap / h;
            }
        }
        player.setDeltaMovement(vx, vy, vz);
        player.resetFallDistance();
        return mode;
    }

    /** The gauge beside the hotbar while a jetpack is worn. */
    public static void renderHud(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || mc.player == null || mc.player.getVehicle() instanceof com.powerfactor.vehicle.RoverEntity) {
            return;
        }
        ItemStack chest = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        if (!JetpackItem.worn(chest) || !(chest.getItem() instanceof PoweredItem jetpack)) {
            return;
        }
        int w = g.guiWidth(), h = g.guiHeight();
        String label = localMode == JetpackServer.THRUST ? "THRUST" : JetpackItem.hover(chest) ? "HOVER" : "JETPACK";
        String pct = Math.round(Mth.clamp((float) PoweredItem.energy(chest) / jetpack.capacity(chest), 0, 1) * 100) + "%";
        // Wide enough for the longest label and "100%" side by side, with a gap.
        int width = mc.font.width("JETPACK") + mc.font.width("100%") + 10;
        int x = Math.max(5, w / 2 - 91 - width - 6), y = h - (w < 420 ? 48 : 21);
        float frac = Mth.clamp((float) PoweredItem.energy(chest) / jetpack.capacity(chest), 0, 1);
        g.fill(x - 1, y - 1, x + width + 1, y + 19, 0x90000000);
        g.fill(x + 1, y + 11, x + width - 1, y + 16, 0xFF1A1A1A);
        int color = frac < 0.15F ? 0xFFFF4A3A : frac < 0.4F ? 0xFFFFB020 : 0xFF4FD8FF;
        g.fill(x + 1, y + 11, x + 1 + Math.round((width - 2) * frac), y + 16, color);
        int labelColor = localMode == JetpackServer.THRUST ? 0xFF7FE8FF : JetpackItem.hover(chest) ? 0xFFB8F5C0 : 0xFFB0B0B0;
        g.text(mc.font, Component.literal(label), x + 1, y + 1, labelColor, true);
        g.text(mc.font, Component.literal(pct), x + width - 1 - mc.font.width(pct), y + 1, 0xFFFFFFFF, true);
    }

    /** Ducted-fan roar following a jetpack wearer, swelling with thrust. */
    private static final class JetpackSound extends AbstractTickableSoundInstance {
        private final Player player;

        JetpackSound(Player player) {
            super(ModSounds.JETPACK_THRUST.get(), SoundSource.PLAYERS, RandomSource.create());
            this.player = player;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
        }

        @Override
        public void tick() {
            float[] f = FANS.get(player.getId());
            if (player.isRemoved() || f == null || f[2] < 1F) {
                stop();
                return;
            }
            x = player.getX();
            y = player.getY() + 1.0;
            z = player.getZ();
            float load = f[2] / 62F;
            volume = 0.15F + 0.75F * load;
            pitch = 0.7F + 0.45F * load;
        }
    }

    private JetpackClient() {}
}
