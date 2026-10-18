package com.powerfactor.elevator;

import com.powerfactor.PowerFactor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** The client saw a jump (up) or a sneak (down) while standing on an elevator. */
public record ElevatorPayload(boolean up) implements CustomPacketPayload {
    public static final Type<ElevatorPayload> TYPE = new Type<>(PowerFactor.id("elevator"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ElevatorPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, ElevatorPayload::up,
            ElevatorPayload::new);

    @Override
    public Type<ElevatorPayload> type() {
        return TYPE;
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(TYPE, STREAM_CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) ElevatorBlock.ride(sp, p.up());
        });
    }
}
