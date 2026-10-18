package com.powerfactor.fission;

import com.powerfactor.PowerFactor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Once a second to a player holding a Geiger counter or dosimeter, or standing in a strong field: the dose rate where they stand and their lifetime dose. */
public record RadiationPayload(float rate, float total, boolean shielded) implements CustomPacketPayload {
    public static final Type<RadiationPayload> TYPE = new Type<>(PowerFactor.id("radiation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RadiationPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.FLOAT,
            RadiationPayload::rate, ByteBufCodecs.FLOAT, RadiationPayload::total, ByteBufCodecs.BOOL, RadiationPayload::shielded, RadiationPayload::new);

    @Override
    public Type<RadiationPayload> type() {
        return TYPE;
    }

    public static java.util.function.Consumer<RadiationPayload> clientHandler = p -> {};

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(TYPE, STREAM_CODEC, (p, ctx) -> clientHandler.accept(p));
    }
}
