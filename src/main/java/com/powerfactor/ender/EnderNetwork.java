package com.powerfactor.ender;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Server-wide Ender Energy state: one EE pool per channel (shared across dimensions, it's the End
 * after all), and the interference every busy link radiates into nearby grids.
 */
public final class EnderNetwork {
    /** EE waiting in each channel's pool. */
    private static final double[] POOL = new double[16];
    private static final double POOL_CAP = 4_000;

    private record Source(ResourceKey<Level> dimension, BlockPos pos, double strength, long tick) {}

    private static final Map<Long, Source> SOURCES = new HashMap<>();
    /** Interference reaches this far, falling off linearly. */
    public static final double RANGE = 48;

    public static double room(int channel) {
        return POOL_CAP - POOL[channel];
    }

    public static void deposit(int channel, double ee) {
        POOL[channel] = Math.min(POOL_CAP, POOL[channel] + ee);
    }

    public static double withdraw(int channel, double max) {
        double got = Math.min(max, POOL[channel]);
        POOL[channel] -= got;
        return got;
    }

    public static double pooled(int channel) {
        return POOL[channel];
    }

    /** A link reports how hard it's working (0..1+) each tick. */
    public static void radiate(Level level, BlockPos pos, double strength) {
        SOURCES.put(pos.asLong() ^ level.dimension().identifier().hashCode(),
                new Source(level.dimension(), pos.immutable(), strength, level.getGameTime()));
    }

    /** Total interference at a position: 0 is clean, 1 is one link flat out next door. */
    public static double interference(Level level, BlockPos at) {
        double total = 0;
        long now = level.getGameTime();
        for (Iterator<Source> it = SOURCES.values().iterator(); it.hasNext(); ) {
            Source s = it.next();
            if (now - s.tick() > 40 || now < s.tick()) {
                it.remove();
                continue;
            }
            if (s.dimension() != level.dimension()) {
                continue;
            }
            double d = Math.sqrt(s.pos().distSqr(at));
            if (d < RANGE) {
                total += s.strength() * (1 - d / RANGE);
            }
        }
        return total;
    }

    public static void clear() {
        java.util.Arrays.fill(POOL, 0);
        SOURCES.clear();
    }

    private EnderNetwork() {}
}
