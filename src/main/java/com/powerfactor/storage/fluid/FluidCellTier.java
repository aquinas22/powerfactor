package com.powerfactor.storage.fluid;

import net.minecraft.world.item.Rarity;

/** Fluid cell sizes: bytes as for item cells, but each byte holds eight buckets. */
public enum FluidCellTier {
    K4("fluid_cell_4k", 4_096, 15, 0xFF4FA8FF, Rarity.COMMON, 0),
    K64("fluid_cell_64k", 65_536, 15, 0xFF3FE0C8, Rarity.UNCOMMON, 1),
    QUANTUM("quantum_fluid_cell", 16_777_216, 63, 0xFF7C9CFF, Rarity.EPIC, 3);

    public static final long MB_PER_BYTE = 8_000;

    public final String id;
    public final long bytes;
    public final int types;
    public final int color;
    public final Rarity rarity;
    public final int glow;

    FluidCellTier(String id, long bytes, int types, int color, Rarity rarity, int glow) {
        this.id = id;
        this.bytes = bytes;
        this.types = types;
        this.color = color;
        this.rarity = rarity;
        this.glow = glow;
    }

    /** Total capacity in buckets for a single fluid. */
    public long buckets() {
        return (bytes - 8) * MB_PER_BYTE / 1000;
    }
}
