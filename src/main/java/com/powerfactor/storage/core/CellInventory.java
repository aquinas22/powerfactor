package com.powerfactor.storage.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The contents of one storage cell: a byte budget and a type limit, AE2-style. Every stored item
 * costs one bit-eighth of a byte (8 items per byte) and each distinct type reserves
 * {@link #BYTES_PER_TYPE} bytes up front, so many types fill a cell faster than one bulk type.
 * Plain Java over any key so the arithmetic is unit tested.
 */
public final class CellInventory<K> {
    public static final int ITEMS_PER_BYTE = 8;
    public static final long BYTES_PER_TYPE = 8;

    private final long bytes;
    private final int maxTypes;
    /** Units per byte: 8 items, or 8,000 mB (eight buckets) for fluid cells. */
    private final long perByte;
    private final LinkedHashMap<K, Long> counts = new LinkedHashMap<>();
    private long items;

    public CellInventory(long bytes, int maxTypes) {
        this(bytes, maxTypes, ITEMS_PER_BYTE);
    }

    public CellInventory(long bytes, int maxTypes, long perByte) {
        this.bytes = bytes;
        this.maxTypes = maxTypes;
        this.perByte = perByte;
    }

    public long totalBytes() {
        return bytes;
    }

    public int maxTypes() {
        return maxTypes;
    }

    public int types() {
        return counts.size();
    }

    public long items() {
        return items;
    }

    public long usedBytes() {
        return counts.size() * BYTES_PER_TYPE + (items + perByte - 1) / perByte;
    }

    public long count(K key) {
        return counts.getOrDefault(key, 0L);
    }

    public Map<K, Long> contents() {
        return Collections.unmodifiableMap(counts);
    }

    /** How many of {@code key} would fit right now. */
    public long room(K key) {
        boolean known = counts.containsKey(key);
        if (!known && counts.size() >= maxTypes) return 0;
        long freeBytes = bytes - usedBytes() - (known ? 0 : BYTES_PER_TYPE);
        if (freeBytes < 0) return 0;
        // Spare items in the last partially-used byte also fit.
        long slack = (perByte - items % perByte) % perByte;
        return freeBytes * perByte + slack;
    }

    /** Inserts up to {@code amount}; returns how many went in. */
    public long insert(K key, long amount, boolean simulate) {
        if (amount <= 0) return 0;
        long n = Math.min(amount, room(key));
        if (n > 0 && !simulate) {
            counts.merge(key, n, Long::sum);
            items += n;
        }
        return n;
    }

    /** Extracts up to {@code amount}; returns how many came out. */
    public long extract(K key, long amount, boolean simulate) {
        long have = count(key);
        long n = Math.min(have, Math.max(0, amount));
        if (n > 0 && !simulate) {
            if (n == have) counts.remove(key);
            else counts.put(key, have - n);
            items -= n;
        }
        return n;
    }

    /** Loads saved contents verbatim (counts are trusted; overfull saves stay readable). */
    public void load(Map<K, Long> saved) {
        counts.clear();
        items = 0;
        for (var e : saved.entrySet()) {
            if (e.getValue() > 0) {
                counts.put(e.getKey(), e.getValue());
                items += e.getValue();
            }
        }
    }
}
