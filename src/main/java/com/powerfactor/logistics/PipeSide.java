package com.powerfactor.logistics;

import net.minecraft.util.StringRepresentable;

public enum PipeSide implements StringRepresentable {
    NONE, PIPE, INSERT, EXTRACT;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public boolean connected() {
        return this != NONE;
    }

    public boolean endpoint() {
        return this == INSERT || this == EXTRACT;
    }
}
