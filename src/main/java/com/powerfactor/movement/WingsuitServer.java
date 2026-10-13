package com.powerfactor.movement;

import com.powerfactor.PowerFactor;
import com.powerfactor.item.WingsuitItem;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Deliberate deploy/fold state, sharing Minecraft's collision-aware gliding pose and movement. */
@EventBusSubscriber(modid = PowerFactor.MODID)
public final class WingsuitServer {
    public static final Identifier GLIDE = PowerFactor.id("wingsuit_glide");
    private static final Set<UUID> ACTIVE = new HashSet<>();
    public static boolean toggle(Player player) {
        if (ACTIVE.contains(player.getUUID())) { fold(player); return true; }
        if (!canDeploy(player)) { sync(player, false); return false; }
        player.getItemBySlot(EquipmentSlot.CHEST).set(com.powerfactor.registry.ModComponents.WINGSUIT_OPEN.get(), true);
        deploy(player); sync(player, true);
        player.level().playSound(null, player.blockPosition(), com.powerfactor.registry.ModSounds.WINGSUIT_OPEN.get(), net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1);
        return true;
    }
    public static boolean canDeploy(Player player) {
        return WingsuitItem.worn(player.getItemBySlot(EquipmentSlot.CHEST)) && !player.onGround() && !player.isInWater() && !player.isInLava()
                && !player.isPassenger() && !player.getAbilities().flying && player.isAlive() && !player.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION);
    }
    private static void deploy(Player player) {
        var attribute = player.getAttribute(NeoForgeMod.GLIDING_FLIGHT); if (attribute == null) return;
        attribute.removeModifier(GLIDE);
        attribute.addTransientModifier(new AttributeModifier(GLIDE, 1, AttributeModifier.Operation.ADD_VALUE));
        ACTIVE.add(player.getUUID()); player.startFallFlying();
        if (player.getKnownMovement().horizontalDistance() < 0.2) player.setDeltaMovement(player.getDeltaMovement().add(player.getViewVector(1).scale(0.24)));
    }
    public static void fold(Player player) {
        boolean wasActive = ACTIVE.remove(player.getUUID()); var attribute = player.getAttribute(NeoForgeMod.GLIDING_FLIGHT);
        var chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!chest.isEmpty()) chest.set(com.powerfactor.registry.ModComponents.WINGSUIT_OPEN.get(), false);
        if (attribute != null) attribute.removeModifier(GLIDE);
        if (wasActive) player.stopFallFlying();
        sync(player, false);
    }
    public static void tick(Player player) {
        if (ACTIVE.contains(player.getUUID()) && (!canDeploy(player) || !player.isFallFlying())) fold(player);
    }
    private static void sync(Player player, boolean open) {
        if (player instanceof net.minecraft.server.level.ServerPlayer server && server.connection != null
                && server.connection.hasChannel(MovementPayloads.WingSync.TYPE)) server.connection.send(new MovementPayloads.WingSync(open));
    }
    @SubscribeEvent static void beforeTick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide() && player.getItemBySlot(EquipmentSlot.CHEST).getOrDefault(com.powerfactor.registry.ModComponents.WINGSUIT_OPEN.get(), false)
                && !ACTIVE.contains(player.getUUID())) {
            if (canDeploy(player)) deploy(player); else fold(player);
        }
    }
    @SubscribeEvent static void onTick(PlayerTickEvent.Post event) { if (!event.getEntity().level().isClientSide()) tick(event.getEntity()); }
    @SubscribeEvent static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) { ACTIVE.remove(event.getEntity().getUUID()); }
    private WingsuitServer() {}
}
