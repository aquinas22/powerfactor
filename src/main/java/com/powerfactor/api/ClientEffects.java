package com.powerfactor.api;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** Cosmetic updates skip clients without a negotiated channel, such as headless test players. */
public final class ClientEffects {
    public static void send(ServerLevel level, ChunkPos chunk, CustomPacketPayload payload) {
        for (var player : level.getChunkSource().chunkMap.getPlayers(chunk, false)) {
            if (player.connection.hasChannel(payload)) player.connection.send(payload);
        }
    }
    private ClientEffects() {}
}
