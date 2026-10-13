package com.powerfactor.client;

import com.powerfactor.movement.MovementPayloads;
import com.powerfactor.vehicle.RoverEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Seat-validated controls and a dashboard; all position changes are simulated by the server. */
public final class RoverClient {
    private static final java.util.Map<Integer, MotorSound> SOUNDS = new java.util.HashMap<>();
    private static int seat = -1;
    public static void tick() {
        var mc = Minecraft.getInstance(); if (mc.player == null || mc.level == null) { SOUNDS.clear(); seat = -1; return; }
        if (mc.player.getVehicle() instanceof RoverEntity rover && rover.driver() == mc.player) {
            if (seat != rover.getId()) { seat = rover.getId(); mc.player.setYRot(rover.getYRot()); mc.player.setXRot(8); }
            var keys = mc.player.input.keyPresses;
            float throttle = mc.gui.screen() == null ? (keys.forward() ? 1 : 0) - (keys.backward() ? 1 : 0) : 0;
            float steering = mc.gui.screen() == null ? (keys.right() ? 1 : 0) - (keys.left() ? 1 : 0) : 0;
            ClientPacketDistributor.sendToServer(new MovementPayloads.RoverInput(throttle, steering, keys.jump() || mc.gui.screen() != null, keys.sprint()));
        } else seat = -1;
        SOUNDS.entrySet().removeIf(entry -> entry.getValue().isStopped());
        for (var entity : mc.level.entitiesForRendering()) if (entity instanceof RoverEntity rover && rover.motorActive() && !SOUNDS.containsKey(rover.getId())) {
            var sound = new MotorSound(rover); SOUNDS.put(rover.getId(), sound); mc.getSoundManager().play(sound);
        }
    }
    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        var mc = Minecraft.getInstance(); if (mc.player == null || mc.gui.hud.isHidden() || !(mc.player.getVehicle() instanceof RoverEntity rover)) return;
        int w = 144, x = g.guiWidth() - w - 6, y = g.guiWidth() < 360 ? 6 : g.guiHeight() - 103;
        Skin.panel(g, x, y, w, 54);
        String speed = Math.round(rover.speed() * 72) + " km/h";
        g.text(mc.font, Component.translatable("gui.powerfactor.rover.title"), x + 6, y + 5, Skin.TEAL, false);
        g.text(mc.font, speed, x + w - 6 - mc.font.width(speed), y + 5, Skin.TEXT, false);
        Skin.bar(g, x + 6, y + 18, 100, (float) rover.charge() / RoverEntity.CAPACITY, rover.charge() < 10_000 ? Skin.FE : Skin.INPUT);
        String pct = Math.round(100F * rover.charge() / RoverEntity.CAPACITY) + "%";
        g.text(mc.font, pct, x + w - 6 - mc.font.width(pct), y + 16, Skin.TEXT, false);
        Component mode = Component.translatable(rover.braking() ? "gui.powerfactor.rover.braking" : rover.charge() < RoverEntity.DRIVE_FE ? "gui.powerfactor.rover.empty" : "gui.powerfactor.rover.help");
        g.text(mc.font, Skin.fit(mc.font, mode, w - 12), x + 6, y + 29, Skin.DIM, false);
        g.text(mc.font, Component.translatable("gui.powerfactor.rover.hull", Math.round(rover.health() * 100 / RoverEntity.MAX_HEALTH)), x + 6, y + 41, Skin.DIM, false);
    }
    private static final class MotorSound extends net.minecraft.client.resources.sounds.AbstractTickableSoundInstance {
        private final RoverEntity rover;
        MotorSound(RoverEntity rover) {
            super(com.powerfactor.registry.ModSounds.ROVER_MOTOR.get(), net.minecraft.sounds.SoundSource.NEUTRAL, net.minecraft.util.RandomSource.create());
            this.rover = rover; looping = true; delay = 0;
        }
        @Override public void tick() {
            if (rover.isRemoved() || !rover.motorActive()) { stop(); return; }
            x = rover.getX(); y = rover.getY() + 0.5; z = rover.getZ();
            float speed = Math.min(1, rover.speed() / 2.2F); volume = 0.10F + speed * 0.4F; pitch = 0.7F + speed * 0.9F;
        }
    }
    private RoverClient() {}
}
