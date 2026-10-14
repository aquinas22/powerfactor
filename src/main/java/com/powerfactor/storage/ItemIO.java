package com.powerfactor.storage;

import com.google.common.primitives.Ints;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/** Small transactional helpers over NeoForge item handlers. Callers are never inside a transaction. */
public final class ItemIO {
    public static @Nullable ResourceHandler<ItemResource> handler(ServerLevel level, BlockPos pos, Direction side) {
        if (!level.isLoaded(pos)) return null;
        return level.getCapability(Capabilities.Item.BLOCK, pos, side);
    }

    public static long insert(ResourceHandler<ItemResource> h, ItemResource r, long amount, boolean simulate) {
        try (Transaction tx = Transaction.openRoot()) {
            int n = h.insert(r, Ints.saturatedCast(amount), tx);
            if (!simulate) tx.commit();
            return n;
        }
    }

    public static long extract(ResourceHandler<ItemResource> h, ItemResource r, long amount, boolean simulate) {
        try (Transaction tx = Transaction.openRoot()) {
            int n = h.extract(r, Ints.saturatedCast(amount), tx);
            if (!simulate) tx.commit();
            return n;
        }
    }

    /** Whether {@code stack}'s item matches the filter resource (exact components when asked). */
    public static boolean matches(ItemStack filter, ItemResource r, boolean exact) {
        if (filter.isEmpty()) return false;
        return exact ? r.matches(filter) : r.is(filter.getItem());
    }

    private ItemIO() {}
}
