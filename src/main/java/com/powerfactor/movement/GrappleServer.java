package com.powerfactor.movement;

import com.powerfactor.item.GrappleItem;
import com.powerfactor.item.PoweredItem;
import com.powerfactor.item.ToolModules;
import com.powerfactor.registry.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Validates inventory and FE before launching; release never rewrites the player's velocity. */
public final class GrappleServer {
    public static ItemStack device(Player player) {
        if (player.getMainHandItem().getItem() instanceof GrappleItem) return player.getMainHandItem();
        if (player.getOffhandItem().getItem() instanceof GrappleItem) return player.getOffhandItem();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot); if (stack.getItem() instanceof GrappleItem) return stack;
        }
        return ItemStack.EMPTY;
    }
    public static @Nullable GrappleHookEntity hook(Player player) {
        return player.level().getEntitiesOfClass(GrappleHookEntity.class, player.getBoundingBox().inflate(GrappleItem.RANGE + 12),
                h -> h.getOwner() == player && !h.isRemoved()).stream().findFirst().orElse(null);
    }
    public static boolean toggle(Player player) {
        var existing = hook(player);
        if (existing != null) { existing.discard(); return true; }
        ItemStack stack = device(player);
        if (stack.isEmpty() || !(player.level() instanceof ServerLevel level) || !player.isAlive() || player.isPassenger()) return false;
        if (player.getCooldowns().isOnCooldown(stack)) return false;
        int cost = ToolModules.cost(stack, GrappleItem.FIRE_FE);
        if (!player.getAbilities().instabuild && PoweredItem.energy(stack) < cost) {
            level.playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.5F, 1.4F);
            return false;
        }
        PoweredItem.drain(stack, cost, player);
        var hook = new GrappleHookEntity(level, player);
        hook.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, 3.0F, 0);
        level.addFreshEntity(hook);
        player.getCooldowns().addCooldown(stack, 6);
        level.playSound(null, player.blockPosition(), ModSounds.GRAPPLE_FIRE.get(), SoundSource.PLAYERS, 0.65F, 1);
        return true;
    }
    public static void release(Player player) { var hook = hook(player); if (hook != null) hook.discard(); }
    private GrappleServer() {}
}
