package com.powerfactor.storage;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Live controllers per level, so a cable or node change can tell the networks it touches to rescan. */
public final class StorageNetworks {
    private static final WeakHashMap<ServerLevel, Set<StorageControllerBlockEntity>> CONTROLLERS = new WeakHashMap<>();

    static void add(ServerLevel level, StorageControllerBlockEntity controller) {
        CONTROLLERS.computeIfAbsent(level, l -> Collections.newSetFromMap(new WeakHashMap<>())).add(controller);
    }

    static void remove(ServerLevel level, StorageControllerBlockEntity controller) {
        Set<StorageControllerBlockEntity> set = CONTROLLERS.get(level);
        if (set != null) set.remove(controller);
    }

    /** Something at {@code pos} appeared, vanished or changed connectivity. */
    public static void invalidate(ServerLevel level, BlockPos pos) {
        Set<StorageControllerBlockEntity> set = CONTROLLERS.get(level);
        if (set == null) return;
        for (StorageControllerBlockEntity c : Set.copyOf(set)) {
            if (!c.isRemoved()) c.invalidateIfTouching(pos);
        }
    }

    public static boolean isMember(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof DataCableBlock || level.getBlockEntity(pos) instanceof NodeBlockEntity;
    }

    private StorageNetworks() {}
}
