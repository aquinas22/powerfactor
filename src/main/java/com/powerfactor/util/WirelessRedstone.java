package com.powerfactor.util;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;

/**
 * The shared airwaves: each powered transmitter checks in every tick on its channel; a receiver is on
 * while any transmitter on its channel has checked in within the last few ticks. Works across
 * dimensions while the transmitter is loaded.
 */
public final class WirelessRedstone {
    public static final int CHANNELS = 16, HOLD = 4;
    private static final Map<Integer, Map<GlobalPos, Long>> ON_AIR = new HashMap<>();
    private static MinecraftServer server;

    private static void bind(MinecraftServer s) {
        if (server != s) {
            ON_AIR.clear();
            server = s;
        }
    }

    public static void transmit(MinecraftServer s, int channel, GlobalPos from) {
        bind(s);
        ON_AIR.computeIfAbsent(channel, k -> new HashMap<>()).put(from, s.overworld().getGameTime());
    }

    public static void silence(MinecraftServer s, GlobalPos from) {
        bind(s);
        for (var m : ON_AIR.values()) m.remove(from);
    }

    public static boolean live(MinecraftServer s, int channel) {
        bind(s);
        var m = ON_AIR.get(channel);
        if (m == null || m.isEmpty()) return false;
        long now = s.overworld().getGameTime();
        m.values().removeIf(t -> now - t > HOLD);
        return !m.isEmpty();
    }

    private WirelessRedstone() {}
}
