package com.powerfactor.movement;

import com.powerfactor.PowerFactor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class MovementPayloads {
    public record RoverInput(float throttle, float steer, boolean brake, boolean boost) implements CustomPacketPayload {
        public static final Type<RoverInput> TYPE = new Type<>(PowerFactor.id("rover_input"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RoverInput> CODEC = StreamCodec.composite(ByteBufCodecs.FLOAT, RoverInput::throttle,
                ByteBufCodecs.FLOAT, RoverInput::steer, ByteBufCodecs.BOOL, RoverInput::brake, ByteBufCodecs.BOOL, RoverInput::boost, RoverInput::new);
        @Override public Type<RoverInput> type() { return TYPE; }
    }
    public record GrappleAction(int action) implements CustomPacketPayload {
        public static final Type<GrappleAction> TYPE = new Type<>(PowerFactor.id("grapple_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GrappleAction> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, GrappleAction::action, GrappleAction::new);
        @Override public Type<GrappleAction> type() { return TYPE; }
    }
    public record WingToggle() implements CustomPacketPayload {
        public static final Type<WingToggle> TYPE = new Type<>(PowerFactor.id("wing_toggle"));
        public static final StreamCodec<RegistryFriendlyByteBuf, WingToggle> CODEC = StreamCodec.unit(new WingToggle());
        @Override public Type<WingToggle> type() { return TYPE; }
    }
    public record WingSync(boolean open) implements CustomPacketPayload {
        public static final Type<WingSync> TYPE = new Type<>(PowerFactor.id("wing_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, WingSync> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, WingSync::open, WingSync::new);
        @Override public Type<WingSync> type() { return TYPE; }
    }
    public static java.util.function.Consumer<WingSync> wingHandler = packet -> {};
    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(RoverInput.TYPE, RoverInput.CODEC, (packet, context) -> {
            if (context.player().getVehicle() instanceof com.powerfactor.vehicle.RoverEntity rover)
                rover.input(context.player(), packet.throttle(), packet.steer(), packet.brake(), packet.boost());
        });
        registrar.playToServer(GrappleAction.TYPE, GrappleAction.CODEC, (packet, context) -> {
            if (packet.action() == 0) GrappleServer.toggle(context.player());
            else if (packet.action() == 1) GrappleServer.release(context.player());
        });
        registrar.playToServer(WingToggle.TYPE, WingToggle.CODEC, (packet, context) -> WingsuitServer.toggle(context.player()));
        registrar.playToClient(WingSync.TYPE, WingSync.CODEC, (packet, context) -> wingHandler.accept(packet));
    }
    private MovementPayloads() {}
}
