package com.powerfactor.grid;

/**
 * Conductor classes. Higher voltage carries more power through the same conductor with less loss:
 * line loss is I^2 R and current falls as voltage rises, which is why grids step up for distance.
 */
public enum CableTier {
    COPPER(512, 0.04, 480),
    INSULATED(1_024, 0.03, 480),
    ALUMINUM(4_096, 0.02, 13_800),
    HV(16_384, 0.008, 138_000);

    public final int ratingCe;
    /** Per-unit resistance at full load; reactance is three times this. */
    public final double resistance;
    public final int volts;

    CableTier(int ratingCe, double resistance, int volts) {
        this.ratingCe = ratingCe;
        this.resistance = resistance;
        this.volts = volts;
    }
}
