package com.powerfactor.weapon;

import com.powerfactor.PowerFactor;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server to client: a short-lived world effect (beam trail, shockwave, flash). The server decides
 * where; each client draws it its own way. {@code shooter} gets recoil and screen shake.
 */
public record FxPayload(int kind, Vec3 from, Vec3 to, int color, float scale, int shooter) implements CustomPacketPayload {
    public static final int RAIL = 0, PLASMA_BURST = 1, SHOCKWAVE = 2, DASH = 3, COLLAPSE = 4, BOLT = 5;

    public static final Type<FxPayload> TYPE = new Type<>(PowerFactor.id("fx"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FxPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FxPayload::kind, Vec3.STREAM_CODEC, FxPayload::from, Vec3.STREAM_CODEC, FxPayload::to,
            ByteBufCodecs.INT, FxPayload::color, ByteBufCodecs.FLOAT, FxPayload::scale, ByteBufCodecs.VAR_INT, FxPayload::shooter, FxPayload::new);

    public static Consumer<FxPayload> handler = p -> {};

    @Override
    public Type<FxPayload> type() {
        return TYPE;
    }

    /** Send to everyone within 128 blocks of the effect's start. */
    public static void send(ServerLevel level, FxPayload payload) {
        for (var player : level.players()) {
            if (player.distanceToSqr(payload.from()) < 128 * 128 && player.connection.hasChannel(payload)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }
}
