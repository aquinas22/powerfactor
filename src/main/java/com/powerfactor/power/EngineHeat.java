package com.powerfactor.power;

/**
 * BuildCraft engine heat. A running engine settles at a heat that depends on how backed up its
 * output is: drained promptly it idles in the blue/green, left full it climbs into the red. At
 * maximum heat it seizes and won't run again until it has cooled back to blue.
 */
public final class EngineHeat {
    public static final int MAX = 1000;
    public static final int GREEN = 250, YELLOW = 500, RED = 750;
    private static final int RISE = 2, FALL = 1, COOL = 3;

    private int heat;
    private boolean seized;

    /**
     * @param running  fuel and redstone say go
     * @param fill     how full the output buffer is, 0..1
     * @param heatCap  hottest this engine type can get
     * @return true if the engine may produce power this tick
     */
    public boolean tick(boolean running, double fill, int heatCap) {
        if (seized) {
            heat = Math.max(0, heat - COOL);
            if (heat < GREEN) {
                seized = false;
            }
            return false;
        }
        if (!running) {
            heat = Math.max(0, heat - COOL);
            return false;
        }
        int target = (int) Math.min(heatCap, 100 + (MAX + 50) * Math.clamp(fill, 0, 1));
        heat = heat < target ? Math.min(target, heat + RISE) : Math.max(target, heat - FALL);
        if (heat >= MAX) {
            seized = true;
            return false;
        }
        return true;
    }

    /** 0 blue, 1 green, 2 yellow, 3 red. */
    public int stage() {
        return heat < GREEN ? 0 : heat < YELLOW ? 1 : heat < RED ? 2 : 3;
    }

    public int heat() {
        return heat;
    }

    public boolean seized() {
        return seized;
    }

    public void load(int heat, boolean seized) {
        this.heat = Math.clamp(heat, 0, MAX);
        this.seized = seized;
    }
}
