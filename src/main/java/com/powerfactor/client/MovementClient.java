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
    private static float flightFov;
    public static float flightFov() { return flightFov; }
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
        if (player == null || mc.level == null) { jumpDown = false; bank = previousBank = flightFov = 0; wind = null; OPEN.clear(); return; }
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
        var velocity = player.getDeltaMovement();
        float headingError = (float) Math.toDegrees(Math.atan2(Math.sin(Math.toRadians(player.getYRot()) - Math.atan2(-velocity.x,velocity.z)),
                Math.cos(Math.toRadians(player.getYRot()) - Math.atan2(-velocity.x,velocity.z))));
        float targetBank = flying ? Math.clamp(turn * 22 + headingError * .4F,-32F,32F) : 0;
        bank += (targetBank - bank) * 0.18F;
        flightFov += ((flying ? (float) Math.clamp((velocity.length() - 1) * .035, 0, .14) : 0) - flightFov) * .12F;
        if (flying) {
            if (mc.gui.screen() == null) {
                if (turn != 0) player.setYRot(player.getYRot() + turn * 2.6F);
                float pitchInput = (player.input.keyPresses.forward() ? 1 : 0) - (player.input.keyPresses.backward() ? 1 : 0);
                if (pitchInput != 0) player.setXRot(Math.clamp(player.getXRot() + pitchInput * 2.4F, -80F, 85F));
            }
            // Aerodynamics run once inside travel, rather than again after vanilla Elytra forces.
            if (wind == null || wind.isStopped()) {
                wind = new WindSound(player); mc.getSoundManager().play(wind);
            }
        }
    }
    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        var mc = Minecraft.getInstance(); if (mc.player == null || mc.gui.hud.isHidden() || !HudPreferences.movement() || (mc.player.getVehicle() instanceof com.powerfactor.vehicle.RoverEntity || mc.player.getVehicle() instanceof com.powerfactor.vehicle.HoverBikeEntity || mc.player.getVehicle() instanceof com.powerfactor.vehicle.MechEntity)) return;
        var player = mc.player; var device = GrappleServer.device(player); var chest = player.getItemBySlot(EquipmentSlot.CHEST);
        var hook = GrappleServer.hook(player); boolean wing = WingsuitItem.worn(chest);
        if (device.isEmpty() && !wing && hook == null) return;
        int x=6,y=6+(PowerArmorClient.hudHeight()>0?PowerArmorClient.hudHeight()+4:0),width=104;
        boolean grapple=!device.isEmpty() || hook!=null;
        Skin.hud(g,x,y,width,grapple && wing?35:22);
        if(grapple) {
            Component status=Component.translatable("gui.powerfactor.grapple.compact."+(hook==null?"ready":!hook.anchored()?"firing":player.isShiftKeyDown()?"swing":"reel"));
            String readout=hook==null?GRAPPLE.getTranslatedKeyMessage().getString()+" · "+(int)GrappleItem.RANGE+"m":String.format(java.util.Locale.ROOT,"%.1fm",hook.position().distanceTo(player.position()));
            g.text(mc.font,Skin.fit(mc.font,status,width-12-mc.font.width(readout)),x+5,y+4,Skin.TEAL,false);
            g.text(mc.font,readout,x+width-5-mc.font.width(readout),y+4,Skin.TEXT,false);
            float fill=device.getItem() instanceof GrappleItem item?(float)PoweredItem.energy(device)/item.capacity(device):0;
            Skin.bar(g,x+5,y+16,width-10,fill,fill<.15F?Skin.FE:Skin.INPUT);
        }
        if(wing) {
            int rowY=y+(grapple?24:5);String speed=Math.round(player.getDeltaMovement().length()*20)+"m/s";
            Component state=Component.translatable(player.isFallFlying()?"gui.powerfactor.wingsuit.compact.open":"gui.powerfactor.wingsuit.compact.ready");
            g.text(mc.font,Skin.fit(mc.font,state,width-12-mc.font.width(speed)),x+5,rowY,Skin.TEAL,false);
            g.text(mc.font,speed,x+width-5-mc.font.width(speed),rowY,Skin.TEXT,false);
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
