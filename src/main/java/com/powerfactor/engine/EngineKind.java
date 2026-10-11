package com.powerfactor.engine;

import com.powerfactor.PowerFactorConfig;

public enum EngineKind {
    /** Wooden engine: free power from a redstone signal, but only a trickle. */
    REDSTONE(false, 600),
    /** Stone engine: burns furnace fuel. */
    STIRLING(true, EngineBlockEntity.MAX_HEAT);

    public final boolean burnsFuel;
    /** Hottest it can get; the redstone engine tops out in the yellow. */
    public final int heatCap;

    EngineKind(boolean burnsFuel, int heatCap) {
        this.burnsFuel = burnsFuel;
        this.heatCap = heatCap;
    }

    public int fePerTick() {
        if (!PowerFactorConfig.SPEC.isLoaded()) {
            return this == REDSTONE ? 4 : 30;
        }
        return (this == REDSTONE ? PowerFactorConfig.REDSTONE_ENGINE_FE : PowerFactorConfig.STIRLING_ENGINE_FE).getAsInt();
    }
}
