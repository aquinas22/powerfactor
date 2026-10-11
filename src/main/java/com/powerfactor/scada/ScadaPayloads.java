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

    /** Server to client: readings for a display wall, keyed by its master panel. */
    public record DisplaySnapshot(BlockPos master, List<ScadaReading> readings) implements CustomPacketPayload {
        public static final Type<DisplaySnapshot> TYPE = new Type<>(PowerFactor.id("display_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DisplaySnapshot> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, DisplaySnapshot::master,
                ScadaReading.STREAM_CODEC.apply(ByteBufCodecs.list(512)), DisplaySnapshot::readings,
                DisplaySnapshot::new);

        @Override
        public Type<DisplaySnapshot> type() {
            return TYPE;
        }
    }

    /** Client to server: new Arc Saber settings for the saber in a hand. */
    public record SaberConfig(boolean mainHand, com.powerfactor.item.SaberSettings settings) implements CustomPacketPayload {
        public static final Type<SaberConfig> TYPE = new Type<>(PowerFactor.id("saber_config"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SaberConfig> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, SaberConfig::mainHand,
                com.powerfactor.item.SaberSettings.STREAM_CODEC, SaberConfig::settings,
                SaberConfig::new);

        @Override
        public Type<SaberConfig> type() {
            return TYPE;
        }
    }

    /** Client-side receivers, set by the client entry point so this class stays server-safe. */
    public static java.util.function.Consumer<Snapshot> clientHandler = s -> {};
    public static java.util.function.Consumer<DisplaySnapshot> displayHandler = s -> {};

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(Snapshot.TYPE, Snapshot.STREAM_CODEC, (payload, context) -> clientHandler.accept(payload));
        registrar.playToServer(Command.TYPE, Command.STREAM_CODEC, ScadaPayloads::handleCommand);
        registrar.playToServer(SaberConfig.TYPE, SaberConfig.STREAM_CODEC, (payload, context) -> {
            var stack = context.player().getItemInHand(payload.mainHand() ? net.minecraft.world.InteractionHand.MAIN_HAND
                    : net.minecraft.world.InteractionHand.OFF_HAND);
            if (stack.getItem() instanceof com.powerfactor.item.ArcSaberItem) {
                stack.set(com.powerfactor.registry.ModComponents.SABER.get(), payload.settings());
                com.powerfactor.item.ArcSaberItem.refreshModel(stack);
            }
        });
        registrar.playToClient(DisplaySnapshot.TYPE, DisplaySnapshot.STREAM_CODEC, (payload, context) -> displayHandler.accept(payload));
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
