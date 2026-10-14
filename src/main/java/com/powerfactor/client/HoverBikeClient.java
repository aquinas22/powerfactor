package com.powerfactor.client;

import com.powerfactor.movement.MovementPayloads;
import com.powerfactor.vehicle.HoverBikeEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Hover-bike controls (shared input packet with the rover) and a speed/charge dashboard. */
public final class HoverBikeClient {
    private static int seat = -1;

    public static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) {
            seat = -1;
            return;
        }
        if (mc.player.getVehicle() instanceof HoverBikeEntity bike && bike.driver() == mc.player && !AgentWorkbenchClient.scriptedDrive()) {
            if (seat != bike.getId()) {
                seat = bike.getId();
                mc.player.setYRot(bike.getYRot());
                mc.player.setXRot(10);
            }
            var keys = mc.player.input.keyPresses;
            boolean free = mc.gui.screen() == null;
            float throttle = free ? (keys.forward() ? 1 : 0) - (keys.backward() ? 1 : 0) : 0;
            float steering = free ? (keys.right() ? 1 : 0) - (keys.left() ? 1 : 0) : 0;
            ClientPacketDistributor.sendToServer(new MovementPayloads.RoverInput(throttle, steering, free && keys.jump(), free && keys.sprint()));
        } else if (mc.player.getVehicle() instanceof com.powerfactor.vehicle.MechEntity mech && mech.driver() == mc.player
                && !AgentWorkbenchClient.scriptedDrive()) {
            if (seat != mech.getId()) {
                seat = mech.getId();
                mc.player.setYRot(mech.getYRot());
            }
            var keys = mc.player.input.keyPresses;
            boolean free = mc.gui.screen() == null;
            float throttle = free ? (keys.forward() ? 1 : 0) - (keys.backward() ? 1 : 0) : 0;
            float steering = free ? (keys.right() ? 1 : 0) - (keys.left() ? 1 : 0) : 0;
            ClientPacketDistributor.sendToServer(new MovementPayloads.RoverInput(throttle, steering, free && keys.jump(), free && keys.sprint()));
        } else {
            seat = -1;
        }
    }

    public static void renderMech(GuiGraphicsExtractor g, DeltaTracker delta) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden() || !(mc.player.getVehicle() instanceof com.powerfactor.vehicle.MechEntity mech)) return;
        int w = 144, x = g.guiWidth() - w - 6, y = g.guiWidth() < 360 ? 6 : g.guiHeight() - 103;
        Skin.panel(g, x, y, w, 54);
        String speed = Math.round(mech.speed() * 72) + " km/h";
        g.text(mc.font, Component.translatable("gui.powerfactor.mech.title"), x + 6, y + 5, Skin.TEAL, false);
        g.text(mc.font, speed, x + w - 6 - mc.font.width(speed), y + 5, Skin.TEXT, false);
        Skin.bar(g, x + 6, y + 18, 100, (float) mech.charge() / com.powerfactor.vehicle.MechEntity.CAPACITY, mech.charge() < 40_000 ? Skin.FE : Skin.INPUT);
        String pct = Math.round(100F * mech.charge() / com.powerfactor.vehicle.MechEntity.CAPACITY) + "%";
        g.text(mc.font, pct, x + w - 6 - mc.font.width(pct), y + 16, Skin.TEXT, false);
        g.text(mc.font, Skin.fit(mc.font, Component.translatable(mech.jets() ? "gui.powerfactor.mech.jets" : "gui.powerfactor.mech.help"), w - 12),
                x + 6, y + 29, mech.jets() ? 0xFFD0503A : Skin.DIM, false);
        g.text(mc.font, Component.translatable("gui.powerfactor.rover.hull", Math.round(mech.health() * 100 / com.powerfactor.vehicle.MechEntity.MAX_HEALTH)),
                x + 6, y + 41, Skin.DIM, false);
    }

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden() || !(mc.player.getVehicle() instanceof HoverBikeEntity bike)) return;
        int w = 144, x = g.guiWidth() - w - 6, y = g.guiWidth() < 360 ? 6 : g.guiHeight() - 103;
        Skin.panel(g, x, y, w, 54);
        String speed = Math.round(bike.speed() * 72) + " km/h";
        g.text(mc.font, Component.translatable("gui.powerfactor.hover_bike.title"), x + 6, y + 5, Skin.TEAL, false);
        g.text(mc.font, speed, x + w - 6 - mc.font.width(speed), y + 5, bike.boosting() ? 0xFFD0503A : Skin.TEXT, false);
        Skin.bar(g, x + 6, y + 18, 100, (float) bike.charge() / HoverBikeEntity.CAPACITY,
                bike.charge() < 20_000 ? Skin.FE : Skin.INPUT);
        String pct = Math.round(100F * bike.charge() / HoverBikeEntity.CAPACITY) + "%";
        g.text(mc.font, pct, x + w - 6 - mc.font.width(pct), y + 16, Skin.TEXT, false);
        Component mode = Component.translatable(!bike.field() ? "gui.powerfactor.hover_bike.no_field"
                : bike.boosting() ? "gui.powerfactor.hover_bike.boost" : "gui.powerfactor.hover_bike.help");
        g.text(mc.font, Skin.fit(mc.font, mode, w - 12), x + 6, y + 29, Skin.DIM, false);
        g.text(mc.font, Component.translatable("gui.powerfactor.rover.hull", Math.round(bike.health() * 100 / HoverBikeEntity.MAX_HEALTH)), x + 6, y + 41,
                Skin.DIM, false);
    }

    private HoverBikeClient() {}
}
