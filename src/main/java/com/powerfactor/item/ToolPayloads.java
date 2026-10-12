package com.powerfactor.item;

import com.powerfactor.PowerFactor;
import com.powerfactor.registry.ModComponents;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Network messages for the hand tools: clamp meter readings and jetpack control. */
public final class ToolPayloads {
    /** Client to server: what's the clamp meter pointed at? */
    public record MeterRequest(BlockPos pos) implements CustomPacketPayload {
        public static final Type<MeterRequest> TYPE = new Type<>(PowerFactor.id("meter_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MeterRequest> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, MeterRequest::pos, MeterRequest::new);

        @Override
        public Type<MeterRequest> type() {
            return TYPE;
        }
    }

    /**
     * Server to client: the reading. {@code volts} is per-unit, or negative when there's no supply.
     * {@code status}: 0 idle, 1 running, 2 warning, 3 fault.
     */
    public record MeterReading(BlockPos pos, Component title, List<Component> lines, float volts, float fe, float ee, int status)
            implements CustomPacketPayload {
        public static final Type<MeterReading> TYPE = new Type<>(PowerFactor.id("meter_reading"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MeterReading> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, MeterReading::pos,
                ComponentSerialization.STREAM_CODEC, MeterReading::title,
                ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list(8)), MeterReading::lines,
                ByteBufCodecs.FLOAT, MeterReading::volts,
                ByteBufCodecs.FLOAT, MeterReading::fe,
                ByteBufCodecs.FLOAT, MeterReading::ee,
                ByteBufCodecs.VAR_INT, MeterReading::status,
                MeterReading::new);

        @Override
        public Type<MeterReading> type() {
            return TYPE;
        }
    }

    /** Client to server: what the jetpack wearer is asking for (off, hover, thrust). */
    public record JetpackInput(int mode) implements CustomPacketPayload {
        public static final Type<JetpackInput> TYPE = new Type<>(PowerFactor.id("jetpack_input"));
        public static final StreamCodec<RegistryFriendlyByteBuf, JetpackInput> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, JetpackInput::mode, JetpackInput::new);

        @Override
        public Type<JetpackInput> type() {
            return TYPE;
        }
    }

    /** Client to server: the hover key was pressed. */
    public record JetpackToggle() implements CustomPacketPayload {
        public static final Type<JetpackToggle> TYPE = new Type<>(PowerFactor.id("jetpack_toggle"));
        public static final StreamCodec<RegistryFriendlyByteBuf, JetpackToggle> STREAM_CODEC = StreamCodec.unit(new JetpackToggle());

        @Override
        public Type<JetpackToggle> type() {
            return TYPE;
        }
    }

    /** Server to clients: a player's jetpack mode, for its fans, glow and sound. */
    public record JetpackSync(int entity, int mode) implements CustomPacketPayload {
        public static final Type<JetpackSync> TYPE = new Type<>(PowerFactor.id("jetpack_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, JetpackSync> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, JetpackSync::entity, ByteBufCodecs.VAR_INT, JetpackSync::mode, JetpackSync::new);

        @Override
        public Type<JetpackSync> type() {
            return TYPE;
        }
    }

    /** Client-side receivers, set by the client entry point so this class stays server-safe. */
    public static java.util.function.Consumer<MeterReading> meterHandler = r -> {};
    public static java.util.function.Consumer<JetpackSync> jetpackHandler = s -> {};

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(MeterRequest.TYPE, MeterRequest.STREAM_CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            var stack = player.getMainHandItem().getItem() instanceof ClampMeterItem ? player.getMainHandItem() : player.getOffhandItem();
            if (!(stack.getItem() instanceof ClampMeterItem) || payload.pos().distToCenterSqr(player.getEyePosition()) > 100
                    || !level.isLoaded(payload.pos())) {
                return;
            }
            boolean flat = !PoweredItem.drain(stack, ClampMeterItem.PER_READING, player);
            var r = MeterService.read(level, payload.pos(), flat);
            // Keyed by the block actually looked at (the top of a tall machine reads as its base).
            player.connection.send(new MeterReading(payload.pos(), r.title(), r.lines(), r.volts(), r.fe(), r.ee(), r.status()));
        });
        registrar.playToClient(MeterReading.TYPE, MeterReading.STREAM_CODEC, (payload, context) -> meterHandler.accept(payload));
        registrar.playToServer(JetpackInput.TYPE, JetpackInput.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                JetpackServer.input(player, payload.mode());
            }
        });
        registrar.playToServer(JetpackToggle.TYPE, JetpackToggle.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                JetpackServer.toggleHover(player);
            }
        });
        registrar.playToClient(JetpackSync.TYPE, JetpackSync.STREAM_CODEC, (payload, context) -> jetpackHandler.accept(payload));
        registrar.playToClient(com.powerfactor.radio.RadioPayload.TYPE, com.powerfactor.radio.RadioPayload.STREAM_CODEC,
                (payload, context) -> com.powerfactor.radio.RadioPayload.clientHandler.accept(payload));
        registrar.playToClient(com.powerfactor.logistics.PipeTraffic.TYPE, com.powerfactor.logistics.PipeTraffic.STREAM_CODEC,
                (payload, context) -> com.powerfactor.logistics.PipeTraffic.clientHandler.accept(payload));
    }

    private ToolPayloads() {}
}
