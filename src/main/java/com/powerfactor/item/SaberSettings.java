package com.powerfactor.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** How an Arc Saber is set up: blade color (RGB), blade style, and hit effect. */
public record SaberSettings(int color, Style style, Effect effect) {
    public enum Style { SOLID, PLASMA, ARC, RAINBOW }

    public enum Effect { NONE, STUN, IGNITE, CHAIN, DRAIN }

    public static final SaberSettings DEFAULT = new SaberSettings(0x33E0FF, Style.SOLID, Effect.NONE);

    public static final Codec<SaberSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("color").forGetter(SaberSettings::color),
            Codec.STRING.xmap(s -> Style.valueOf(s), Style::name).fieldOf("style").forGetter(SaberSettings::style),
            Codec.STRING.xmap(s -> Effect.valueOf(s), Effect::name).fieldOf("effect").forGetter(SaberSettings::effect)
    ).apply(i, SaberSettings::new));

    public static final StreamCodec<ByteBuf, SaberSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, SaberSettings::color,
            ByteBufCodecs.idMapper(i -> Style.values()[i], Style::ordinal), SaberSettings::style,
            ByteBufCodecs.idMapper(i -> Effect.values()[i], Effect::ordinal), SaberSettings::effect,
            SaberSettings::new);
}
