package com.powerfactor.scada;

import com.powerfactor.PowerFactor;
import com.powerfactor.api.ScadaDevice;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ScadaPayloads {
    /** Server to client: the latest readings for the terminal the player has open. */
    public record Snapshot(BlockPos terminal, List<ScadaReading> readings) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(PowerFactor.id("scada_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Snapshot::terminal,
                ScadaReading.STREAM_CODEC.apply(ByteBufCodecs.list(512)), Snapshot::readings,
                Snapshot::new);

        @Override
        public Type<Snapshot> type() {
            return TYPE;
        }
    }

    /** Client to server: the operator switched a device on or off. */
    public record Command(BlockPos terminal, BlockPos device, boolean enabled) implements CustomPacketPayload {
        public static final Type<Command> TYPE = new Type<>(PowerFactor.id("scada_command"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Command> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Command::terminal,
                BlockPos.STREAM_CODEC, Command::device,
                ByteBufCodecs.BOOL, Command::enabled,
                Command::new);

        @Override
        public Type<Command> type() {
            return TYPE;
        }
    }

    /** Client-side receiver, set by the client entry point so this class stays server-safe. */
    public static java.util.function.Consumer<Snapshot> clientHandler = s -> {};

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(Snapshot.TYPE, Snapshot.STREAM_CODEC, (payload, context) -> clientHandler.accept(payload));
        registrar.playToServer(Command.TYPE, Command.STREAM_CODEC, ScadaPayloads::handleCommand);
    }

    private static void handleCommand(Command command, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        // Only from a terminal the player actually has open, and only to a device on its network.
        if (!(player.containerMenu instanceof ScadaTerminalMenu menu) || !menu.terminal().equals(command.terminal())
                || !menu.stillValid(player)) {
            return;
        }
        ScadaDevice device = ScadaNetwork.discover(level, command.terminal()).get(command.device());
        if (device != null && device.scadaReading().controllable()) {
            device.setRemoteEnabled(command.enabled());
        }
    }

    private ScadaPayloads() {}
}
