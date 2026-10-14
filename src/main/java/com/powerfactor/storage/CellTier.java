package com.powerfactor.storage;

import net.minecraft.world.item.Rarity;

/**
 * Storage cell sizes. Bytes follow AE2's familiar 1k-64k ladder, then PowerFactor's two late tiers;
 * the colour tints the cell's LED strip, its drive-bay lamp and the terminal capacity bar.
 */
public enum CellTier {
    K1("storage_cell_1k", 1_024, 63, 0xFF8FD3FF, Rarity.COMMON, 0),
    K4("storage_cell_4k", 4_096, 63, 0xFF7BE3A3, Rarity.COMMON, 0),
    K16("storage_cell_16k", 16_384, 63, 0xFFF0D264, Rarity.UNCOMMON, 1),
    K64("storage_cell_64k", 65_536, 63, 0xFFF0985A, Rarity.UNCOMMON, 1),
    K256("storage_cell_256k", 262_144, 127, 0xFFE76AD7, Rarity.RARE, 2),
    QUANTUM("quantum_storage_cell", 16_777_216, 255, 0xFF9C7CFF, Rarity.EPIC, 3);

    public final String id;
    public final long bytes;
    public final int types;
    public final int color;
    public final Rarity rarity;
    /** Visual tier, 0..3: higher tiers get more pronounced lights and effects. */
    public final int glow;

    CellTier(String id, long bytes, int types, int color, Rarity rarity, int glow) {
        this.id = id;
        this.bytes = bytes;
        this.types = types;
        this.color = color;
        this.rarity = rarity;
        this.glow = glow;
    }

    public String label() {
        return this == QUANTUM ? "16M" : (bytes / 1024) + "k";
    }
}
