package com.powerfactor.storage.fluid;

import com.powerfactor.PowerFactor;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/** Fluid Terminal networking: the fluid list, and clicks on it. */
public final class FluidPayloads {
    public record Line(FluidResource fluid, long amount) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Line> STREAM_CODEC = StreamCodec.composite(
                FluidResource.STREAM_CODEC, Line::fluid, ByteBufCodecs.VAR_LONG, Line::amount, Line::new);
    }

    /** Server to client: the whole fluid view (fluid networks hold few types). status as for the item grid. */
    public record Sync(int containerId, int status, List<Line> lines) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(PowerFactor.id("fluid_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Sync::containerId, ByteBufCodecs.VAR_INT, Sync::status,
                Line.STREAM_CODEC.apply(ByteBufCodecs.list(512)), Sync::lines, Sync::new);

        @Override
        public Type<Sync> type() {
            return TYPE;
        }
    }

    /** Client to server: fill the carried container with a fluid, or (no fluid) drain it into the network. */
    public record Click(int containerId, java.util.Optional<FluidResource> fluid) implements CustomPacketPayload {
        public static final Type<Click> TYPE = new Type<>(PowerFactor.id("fluid_click"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Click> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Click::containerId, ByteBufCodecs.optional(FluidResource.STREAM_CODEC), Click::fluid, Click::new);

        @Override
        public Type<Click> type() {
            return TYPE;
        }
    }

    public static Consumer<Sync> syncHandler = s -> {};

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(Sync.TYPE, Sync.STREAM_CODEC, (p, c) -> syncHandler.accept(p));
        registrar.playToServer(Click.TYPE, Click.STREAM_CODEC, (p, c) -> {
            if (c.player() instanceof ServerPlayer sp && sp.containerMenu instanceof FluidTerminalMenu m && m.containerId == p.containerId()) {
                m.click(sp, p.fluid().orElse(FluidResource.EMPTY));
            }
        });
    }

    private FluidPayloads() {}
}
