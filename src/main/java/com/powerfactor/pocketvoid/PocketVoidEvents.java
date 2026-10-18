package com.powerfactor.pocketvoid;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

/** A Pocket Void anywhere in the inventory pulls in what its mode allows before the item reaches the inventory. */
public final class PocketVoidEvents {
    public static void onPickup(ItemEntityPickupEvent.Pre event) {
        Player player = event.getPlayer();
        var entity = event.getItemEntity();
        if (player.level().isClientSide() || entity.hasPickUpDelay() || event.canPickup() == TriState.FALSE) return;
        ItemStack stack = entity.getItem();
        if (stack.isEmpty() || stack.getItem() instanceof PocketVoidItem) return;
        int before = stack.getCount();
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack held = inv.getItem(i);
            if (!(held.getItem() instanceof PocketVoidItem pv)) continue;
            VoidContents c = PocketVoidItem.contents(held);
            if (c.mode() == VoidContents.Mode.OFF) continue;
            var r = c.insert(stack, pv.capacity(), c.mode() == VoidContents.Mode.ALL, c.mode() == VoidContents.Mode.VOID);
            if (r.taken() > 0) {
                PocketVoidItem.set(held, r.contents());
                stack.shrink(r.taken());
            }
        }
        int taken = before - stack.getCount();
        if (taken > 0) {
            player.take(entity, taken);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F,
                    1.4F + player.getRandom().nextFloat() * 0.4F);
            if (stack.isEmpty()) {
                entity.discard();
                event.setCanPickup(TriState.FALSE);
            } else {
                entity.setItem(stack);
            }
        }
    }

    private PocketVoidEvents() {}
}
