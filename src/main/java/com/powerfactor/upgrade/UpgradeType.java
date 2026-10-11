package com.powerfactor.upgrade;

import net.minecraft.util.StringRepresentable;

public enum UpgradeType implements StringRepresentable {
    SPEED(4, 0xFF5A4A),
    EFFICIENCY(4, 0x55FF55),
    CAPACITOR(4, 0x33E0B0),
    VFD(1, 0x4AA8FF),
    SURGE(1, 0xFFD24A),
    CAPACITY(4, 0xE070FF);

    public final int maxCount;
    public final int color;

    UpgradeType(int maxCount, int color) {
        this.maxCount = maxCount;
        this.color = color;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
