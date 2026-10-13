package com.powerfactor.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.powerfactor.item.GrappleItem;
import com.powerfactor.item.JetpackItem;
import com.powerfactor.item.PoweredItem;
import com.powerfactor.item.WingsuitItem;
import com.powerfactor.movement.GrappleHookEntity;
import com.powerfactor.movement.GrappleServer;
import com.powerfactor.movement.MovementPayloads;
import com.powerfactor.movement.MovementPhysics;
import com.powerfactor.movement.WingsuitServer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForgeMod;
import org.lwjgl.glfw.GLFW;

/** Predictable local movement, explicit deploy controls and compact grapple/glide instrumentation. */
public final class MovementClient {
    public static final KeyMapping GRAPPLE = new KeyMapping("key.powerfactor.grapple", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, JetpackClient.CATEGORY);
    public static final KeyMapping GLIDE = new KeyMapping("key.powerfactor.wingsuit", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, JetpackClient.CATEGORY);
    private static boolean jumpDown;
    private static float bank, previousBank;
    private static net.minecraft.client.resources.sounds.AbstractTickableSoundInstance wind;
    private static final java.util.Map<Integer, float[]> OPEN = new java.util.HashMap<>();
    public static float bank(float partial) { return net.minecraft.util.Mth.lerp(partial, previousBank, bank); }
    public static float wingOpen(int entity, float partial) {
        float[] value = OPEN.get(entity); return value == null ? 0 : net.minecraft.util.Mth.lerp(partial, value[0], value[1]);
    }
    public static void wingSync(MovementPayloads.WingSync packet) {
        var player = Minecraft.getInstance().player; if (player == null) return;
        var attribute = player.getAttribute(NeoForgeMod.GLIDING_FLIGHT);
        if (attribute != null) {
            attribute.removeModifier(WingsuitServer.GLIDE);
            if (packet.open()) attribute.addTransientModifier(new AttributeModifier(WingsuitServer.GLIDE, 1, AttributeModifier.Operation.ADD_VALUE));
        }
        var chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!chest.isEmpty()) chest.set(com.powerfactor.registry.ModComponents.WINGSUIT_OPEN.get(), packet.open());
        if (packet.open()) player.startFallFlying(); else player.stopFallFlying();
    }
    public static void toggleWing() { ClientPacketDistributor.sendToServer(new MovementPayloads.WingToggle()); }
    public static void tick() {
        var mc = Minecraft.getInstance(); var player = mc.player;
        if (player == null || mc.level == null) { jumpDown = false; bank = previousBank = 0; wind = null; OPEN.clear(); return; }
        var seen = new java.util.HashSet<Integer>();
        for (var peer : mc.level.players()) {
            if (!WingsuitItem.worn(peer.getItemBySlot(EquipmentSlot.CHEST))) continue;
            seen.add(peer.getId()); float[] value = OPEN.computeIfAbsent(peer.getId(), id -> new float[2]);
            value[0] = value[1]; value[1] += ((peer.isFallFlying() ? 1 : 0) - value[1]) * 0.18F;
        }
        OPEN.keySet().retainAll(seen);
        boolean g = GRAPPLE.consumeClick(), u = GLIDE.consumeClick(), jump = player.input.keyPresses.jump();
        var hook = GrappleServer.hook(player);
        if (mc.gui.screen() == null) {
            if (g && (!GrappleServer.device(player).isEmpty() || hook != null)) ClientPacketDistributor.sendToServer(new MovementPayloads.GrappleAction(0));
            if (u && WingsuitItem.worn(player.getItemBySlot(EquipmentSlot.CHEST))) toggleWing();
            if (jump && !jumpDown && hook != null && hook.anchored()) ClientPacketDistributor.sendToServer(new MovementPayloads.GrappleAction(1));
            if (jump && !jumpDown && player.isFallFlying() && WingsuitItem.worn(player.getItemBySlot(EquipmentSlot.CHEST))
                    && JetpackItem.worn(player.getItemBySlot(EquipmentSlot.CHEST))) toggleWing();
        }
        jumpDown = jump;
        if (hook != null && hook.anchored() && !player.isPassenger() && !player.getAbilities().flying) {
            var device = GrappleServer.device(player); boolean reel = !player.isShiftKeyDown();
            if (!reel || player.getAbilities().instabuild || PoweredItem.energy(device) >= com.powerfactor.item.ToolModules.cost(device, GrappleItem.REEL_FE)) {
                var delta = hook.position().subtract(player.position().add(0, 0.85, 0));
                var step = MovementPhysics.cable(GrappleHookEntity.vector(player.getDeltaMovement()), GrappleHookEntity.vector(delta),
                        hook.ropeLength(), reel, GrappleItem.reelMultiplier(device));
                player.setDeltaMovement(GrappleHookEntity.vec(step.velocity()));
                if (step.taut() || reel) player.resetFallDistance();
            }
        }
        boolean flying = player.isFallFlying() && WingsuitItem.worn(player.getItemBySlot(EquipmentSlot.CHEST));
        previousBank = bank;
        float turn = flying ? (player.input.keyPresses.left() ? -1 : 0) + (player.input.keyPresses.right() ? 1 : 0) : 0;
        bank += ((flying ? turn * 28 : 0) - bank) * 0.15F;
        if (flying) {
            if (turn != 0) player.setYRot(player.getYRot() + turn * 2.2F);
            var motion = MovementPhysics.glide(GrappleHookEntity.vector(player.getDeltaMovement()), player.getYRot(), player.getXRot(), player.isShiftKeyDown());
            player.setDeltaMovement(GrappleHookEntity.vec(motion));
            if (wind == null || wind.isStopped()) {
                wind = new WindSound(player); mc.getSoundManager().play(wind);
            }
        }
    }
    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        var mc = Minecraft.getInstance(); if (mc.player == null || mc.gui.hud.isHidden() || mc.player.getVehicle() instanceof com.powerfactor.vehicle.RoverEntity) return;
        var player = mc.player; var device = GrappleServer.device(player); var chest = player.getItemBySlot(EquipmentSlot.CHEST);
        var hook = GrappleServer.hook(player); boolean wing = WingsuitItem.worn(chest);
        if (device.isEmpty() && !wing && hook == null) return;
        int x = 6, y = PowerArmorClient.worn() ? 80 : 6, width = 132;
        Skin.panel(g, x, y, width, wing && !device.isEmpty() ? 51 : 37);
        if (!device.isEmpty()) {
            Component status = Component.translatable(hook == null ? "gui.powerfactor.grapple.ready" : !hook.anchored() ? "gui.powerfactor.grapple.firing"
                    : player.isShiftKeyDown() ? "gui.powerfactor.grapple.swing" : "gui.powerfactor.grapple.reel");
            g.text(mc.font, status, x + 5, y + 4, Skin.TEAL, false);
            String readout = hook == null ? GRAPPLE.getTranslatedKeyMessage().getString() + " · " + (int) GrappleItem.RANGE + "m"
                    : String.format(java.util.Locale.ROOT, "%.1fm", hook.position().distanceTo(player.position()));
            g.text(mc.font, readout, x + width - 5 - mc.font.width(readout), y + 4, Skin.TEXT, false);
            var item = (GrappleItem) device.getItem();
            Skin.bar(g, x + 5, y + 17, width - 10, (float) PoweredItem.energy(device) / item.capacity(device), Skin.INPUT);
            g.text(mc.font, Skin.fit(mc.font, Component.translatable("gui.powerfactor.grapple.hud_help"), width - 10), x + 5, y + 23, Skin.DIM, false);
        }
        if (wing) {
            int rowY = y + (device.isEmpty() ? 5 : 38);
            String speed = Math.round(player.getDeltaMovement().length() * 20) + "m/s";
            g.text(mc.font, Component.translatable(player.isFallFlying() ? "gui.powerfactor.wingsuit.open" : "gui.powerfactor.wingsuit.ready"), x + 5, rowY, Skin.TEAL, false);
            g.text(mc.font, speed, x + width - 5 - mc.font.width(speed), rowY, Skin.TEXT, false);
            if (device.isEmpty()) g.text(mc.font, Component.translatable("gui.powerfactor.wingsuit.hud_help", GLIDE.getTranslatedKeyMessage()), x + 5, rowY + 14, Skin.DIM, false);
        }
    }
    private static final class WindSound extends net.minecraft.client.resources.sounds.AbstractTickableSoundInstance {
        private final net.minecraft.client.player.LocalPlayer player;
        WindSound(net.minecraft.client.player.LocalPlayer player) {
            super(com.powerfactor.registry.ModSounds.WINGSUIT_WIND.get(), net.minecraft.sounds.SoundSource.PLAYERS, net.minecraft.util.RandomSource.create());
            this.player = player; looping = true; delay = 0;
        }
        @Override public void tick() {
            if (player.isRemoved() || !player.isFallFlying() || !WingsuitItem.worn(player.getItemBySlot(EquipmentSlot.CHEST))) { stop(); return; }
            x = player.getX(); y = player.getY(); z = player.getZ();
            float speed = (float) Math.min(1, player.getDeltaMovement().length() / 2.4);
            volume = 0.05F + speed * 0.35F; pitch = 0.75F + speed * 0.45F;
        }
    }
    private MovementClient() {}
}
