package com.powerfactor.radio;

import com.powerfactor.PowerFactor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: radio status for the HUD. Channel 0 means the transmission ended. */
public record RadioPayload(int channel, String speaker, boolean self) implements CustomPacketPayload {
    public static final Type<RadioPayload> TYPE = new Type<>(PowerFactor.id("radio"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RadioPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RadioPayload::channel, ByteBufCodecs.STRING_UTF8, RadioPayload::speaker, ByteBufCodecs.BOOL, RadioPayload::self,
            RadioPayload::new);

    public static java.util.function.Consumer<RadioPayload> clientHandler = p -> {};

    @Override
    public Type<RadioPayload> type() {
        return TYPE;
    }
}
