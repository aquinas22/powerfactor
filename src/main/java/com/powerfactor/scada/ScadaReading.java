package com.powerfactor.scada;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * One row on a SCADA terminal. FE and EE are per tick. For loads, positive EE is drawn and negative is
 * supplied (capacitors); for generators both are output.
 */
public record ScadaReading(BlockPos pos, Component name, Status status, int fe, int ee, Component detail, boolean controllable, boolean enabled,
        boolean generator, float volts, float hertz) {
    public enum Status {
        OFF, RUNNING, WARNING, ALARM;

        public static final StreamCodec<ByteBuf, Status> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Enum::ordinal);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ScadaReading> STREAM_CODEC = StreamCodec.of(
            (buf, r) -> {
                BlockPos.STREAM_CODEC.encode(buf, r.pos);
                ComponentSerialization.STREAM_CODEC.encode(buf, r.name);
                Status.STREAM_CODEC.encode(buf, r.status);
                buf.writeVarInt(r.fe);
                buf.writeVarInt(r.ee);
                ComponentSerialization.STREAM_CODEC.encode(buf, r.detail);
                buf.writeBoolean(r.controllable);
                buf.writeBoolean(r.enabled);
                buf.writeBoolean(r.generator);
                buf.writeFloat(r.volts);
                buf.writeFloat(r.hertz);
            },
            buf -> new ScadaReading(BlockPos.STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf),
                    Status.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(), ComponentSerialization.STREAM_CODEC.decode(buf),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readFloat(), buf.readFloat()));
}
