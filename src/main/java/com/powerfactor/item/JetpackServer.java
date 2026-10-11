package com.powerfactor.item;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The server side of jetpack flight. The client moves the player (that's how Minecraft movement
 * works); the server pays the FE, cancels fall damage, sends the exhaust to everyone nearby, and
 * zeroes the wearer's gravity while the fans are carrying them, which is also what keeps the
 * server's anti-fly check from kicking them.
 */
public final class JetpackServer {
    public static final int OFF = 0, HOVER = 1, THRUST = 2;
    private static final Identifier LIFT = Identifier.fromNamespaceAndPath("powerfactor", "jetpack_lift");
    private static final Map<UUID, Integer> REQUESTED = new HashMap<>();
    private static final Map<UUID, Integer> ACTIVE = new HashMap<>();

    public static void input(ServerPlayer player, int mode) {
        REQUESTED.put(player.getUUID(), Math.clamp(mode, OFF, THRUST));
    }

    public static void toggleHover(ServerPlayer player) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() instanceof JetpackItem) {
            boolean on = !JetpackItem.hover(chest);
            chest.set(com.powerfactor.registry.ModComponents.JETPACK_HOVER.get(), on);
            player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(on ? "message.powerfactor.jetpack.hover_on"
                    : "message.powerfactor.jetpack.hover_off"));
        }
    }

    public static void tick(ServerPlayer player) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        int mode = REQUESTED.getOrDefault(player.getUUID(), OFF);
        if (!(chest.getItem() instanceof JetpackItem) || !JetpackItem.powered(chest) && !player.getAbilities().instabuild
                || player.getAbilities().flying || player.isFallFlying() || player.isPassenger()) {
            mode = OFF;
        }
        if (mode != OFF) {
            PoweredItem.drain(chest, mode == THRUST ? JetpackItem.THRUST_FE : JetpackItem.HOVER_FE, player);
            player.resetFallDistance();
            exhaust((ServerLevel) player.level(), player, mode);
        }
        AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
        if (gravity != null) {
            boolean lifting = mode != OFF;
            if (lifting && !gravity.hasModifier(LIFT)) {
                gravity.addTransientModifier(new AttributeModifier(LIFT, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            } else if (!lifting && gravity.hasModifier(LIFT)) {
                gravity.removeModifier(LIFT);
            }
        }
        Integer before = ACTIVE.put(player.getUUID(), mode);
        // The server counts ticks spent floating even while gravity is off; clear it so letting go
        // of the jetpack in mid-air doesn't trip the anti-fly kick the moment gravity returns.
        if (mode != OFF || before != null && before != OFF) {
            player.connection.resetFlyingTicks();
        }
        if (before == null || before != mode) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new ToolPayloads.JetpackSync(player.getId(), mode));
        }
    }

    /** Downwash from both fans, a little behind and below the shoulders. */
    private static void exhaust(ServerLevel level, ServerPlayer player, int mode) {
        if (level.getGameTime() % (mode == THRUST ? 1 : 3) != 0) {
            return;
        }
        double yaw = Math.toRadians(player.yBodyRot);
        Vec3 back = new Vec3(Math.sin(yaw), 0, -Math.cos(yaw)).scale(0.38);
        Vec3 side = new Vec3(Math.cos(yaw), 0, Math.sin(yaw)).scale(0.27);
        for (int s = -1; s <= 1; s += 2) {
            Vec3 at = player.position().add(back).add(side.scale(s)).add(0, 0.7, 0);
            level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 0, 0, -1, 0, mode == THRUST ? 0.25 : 0.1);
            if (mode == THRUST) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y - 0.1, at.z, 1, 0.03, 0.05, 0.03, 0.05);
            }
        }
    }

    public static void forget(UUID player) {
        REQUESTED.remove(player);
        ACTIVE.remove(player);
    }

    private JetpackServer() {}
}
