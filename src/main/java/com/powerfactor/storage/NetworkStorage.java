package com.powerfactor.storage;

import java.util.Map;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** One place the network can keep items: a cell in a drive, or an inventory behind a storage bus. */
public interface NetworkStorage {
    /** Higher priorities are filled first and emptied last. */
    int priority();

    long insert(ItemResource resource, long amount, boolean simulate);

    long extract(ItemResource resource, long amount, boolean simulate);

    long count(ItemResource resource);

    /** Adds this storage's contents into {@code totals}. */
    void collect(Map<ItemResource, Long> totals);

    /** Whether the contents can change without the network noticing (an external chest). */
    default boolean external() {
        return false;
    }

    /** Byte usage for the terminal's capacity readout; external storage reports 0/0. */
    default long usedBytes() {
        return 0;
    }

    default long totalBytes() {
        return 0;
    }
}
