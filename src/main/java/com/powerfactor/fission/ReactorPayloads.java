package com.powerfactor.fission;

import com.powerfactor.PowerFactor;
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
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** The reactor console's live feed (server → client, 4/s while open) and its commands (client → server). */
public final class ReactorPayloads {
    /** Column data packed as floats: x, z, rod, setpoint, temp, flux for each column. */
    public record Snapshot(BlockPos pos, boolean formed, Component problem, float[] values, float[] columns, int alarms, int unacked, int fuel,
                           int assemblies, int spent) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(PowerFactor.id("reactor_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> STREAM_CODEC = StreamCodec.of((buf, s) -> {
            buf.writeBlockPos(s.pos);
            buf.writeBoolean(s.formed);
            ComponentSerialization.STREAM_CODEC.encode(buf, s.problem);
            writeFloats(buf, s.values);
            writeFloats(buf, s.columns);
            buf.writeVarInt(s.alarms);
            buf.writeVarInt(s.unacked);
            buf.writeVarInt(s.fuel);
            buf.writeVarInt(s.assemblies);
            buf.writeVarInt(s.spent);
        }, buf -> new Snapshot(buf.readBlockPos(), buf.readBoolean(), ComponentSerialization.STREAM_CODEC.decode(buf), readFloats(buf), readFloats(buf),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<Snapshot> type() {
            return TYPE;
        }

        // values[] indices
        public static final int POWER = 0, TEMP = 1, REACTIVITY = 2, PERIOD = 3, XENON = 4, DECAY = 5, DAMAGE = 6, WATER = 7, STEAM = 8,
                STEAM_RATE = 9, TARGET = 10, TRIP_TEMP = 11, TRIP_POWER = 12, SCRAMMED = 13, AUTO = 14, THERMAL = 15, CAPACITY = 16, ROD_MEAN = 17,
                K = 18, COOLANT = 19, COUNT = 20;
    }

    /** op: see the constants. */
    public record Command(BlockPos pos, int op, int column, float value) implements CustomPacketPayload {
        public static final int SCRAM = 0, RESET = 1, RODS = 2, AUTO = 3, TARGET = 4, ACK = 5, TRIP_TEMP = 6, TRIP_POWER = 7, PURGE = 8;
        public static final Type<Command> TYPE = new Type<>(PowerFactor.id("reactor_command"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Command> STREAM_CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, Command::pos,
                ByteBufCodecs.VAR_INT, Command::op, ByteBufCodecs.VAR_INT, Command::column, ByteBufCodecs.FLOAT, Command::value, Command::new);

        @Override
        public Type<Command> type() {
            return TYPE;
        }
    }

    private static void writeFloats(RegistryFriendlyByteBuf buf, float[] a) {
        buf.writeVarInt(a.length);
        for (float f : a) buf.writeFloat(f);
    }

    private static float[] readFloats(RegistryFriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 4096);
        float[] a = new float[n];
        for (int i = 0; i < n; i++) a[i] = buf.readFloat();
        return a;
    }

    public static java.util.function.Consumer<Snapshot> clientHandler = s -> {};

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(Snapshot.TYPE, Snapshot.STREAM_CODEC, (p, ctx) -> clientHandler.accept(p));
        registrar.playToServer(Command.TYPE, Command.STREAM_CODEC, (p, ctx) -> {
            if (!(ctx.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof ReactorMenu menu) || !menu.pos.equals(p.pos())) return;
            var r = menu.reactor();
            if (r == null) return;
            apply(r, (ServerLevel) player.level(), p.op(), p.column(), p.value());
        });
    }

    /** One command from the console or SCADA. */
    public static void apply(ReactorControllerBlockEntity r, ServerLevel level, int op, int column, float value) {
        switch (op) {
            case Command.SCRAM -> r.scram(level, "operator");
            case Command.RESET -> r.reset();
            case Command.RODS -> r.setRods(column, value);
            case Command.AUTO -> r.setAuto(value > 0);
            case Command.TARGET -> r.setTarget(value);
            case Command.ACK -> r.acknowledge();
            case Command.TRIP_TEMP -> r.setTrips(value, r.tripPower());
            case Command.TRIP_POWER -> r.setTrips(r.tripTemp(), value);
            case Command.PURGE -> r.purge();
            default -> {}
        }
    }

    public static Snapshot snapshot(ReactorControllerBlockEntity r) {
        var core = r.core();
        var shape = r.shape();
        float[] v = new float[Snapshot.COUNT];
        v[Snapshot.POWER] = (float) core.n;
        v[Snapshot.TEMP] = (float) core.fuelTemp;
        v[Snapshot.REACTIVITY] = (float) core.reactivity;
        v[Snapshot.PERIOD] = (float) Math.clamp(core.period(), -9999, 9999);
        v[Snapshot.XENON] = (float) core.xenonWorth();
        v[Snapshot.DECAY] = (float) core.decayHeat;
        v[Snapshot.DAMAGE] = (float) core.damage;
        v[Snapshot.WATER] = r.tanks().amount(0);
        v[Snapshot.STEAM] = r.tanks().amount(1);
        v[Snapshot.STEAM_RATE] = (float) core.steamMade;
        v[Snapshot.TARGET] = (float) r.target();
        v[Snapshot.TRIP_TEMP] = (float) r.tripTemp();
        v[Snapshot.TRIP_POWER] = (float) r.tripPower();
        v[Snapshot.SCRAMMED] = r.scrammed() ? 1 : 0;
        v[Snapshot.AUTO] = r.auto() ? 1 : 0;
        v[Snapshot.THERMAL] = (float) core.power;
        v[Snapshot.CAPACITY] = ReactorControllerBlockEntity.TANK;
        v[Snapshot.K] = (float) core.k;
        v[Snapshot.COOLANT] = r.tanks().coolant().ordinal();
        int n = shape == null ? 0 : shape.columns().size();
        float[] cols = new float[n * 6];
        double mean = 0;
        for (int c = 0; c < n; c++) {
            var col = shape.columns().get(c);
            cols[c * 6] = col.x() - shape.min().getX() - 1;
            cols[c * 6 + 1] = col.z() - shape.min().getZ() - 1;
            cols[c * 6 + 2] = (float) r.rods()[c];
            cols[c * 6 + 3] = (float) r.setpoints()[c];
            cols[c * 6 + 4] = (float) r.columnTemp()[c];
            cols[c * 6 + 5] = (float) r.columnFlux()[c];
            mean += r.rods()[c];
        }
        v[Snapshot.ROD_MEAN] = (float) (n == 0 ? 1 : mean / n);
        int fuel = 0;
        for (int i = 0; i < r.assemblyCount(); i++) if (FuelRodItem.fresh(r.fuel().getItem(i))) fuel++;
        int spent = 0;
        for (int i = 0; i < r.spent().getContainerSize(); i++) if (!r.spent().getItem(i).isEmpty()) spent++;
        int w = shape == null ? 0 : shape.max().getX() - shape.min().getX() - 1, d = shape == null ? 0 : shape.max().getZ() - shape.min().getZ() - 1;
        // Pack the interior footprint into the alarms field's high bits is messy; send it as two extra floats instead.
        float[] withSize = java.util.Arrays.copyOf(cols, cols.length + 2);
        withSize[cols.length] = w;
        withSize[cols.length + 1] = d;
        // Formed: the problem slot carries the first-out trip cause instead.
        Component note = shape == null ? r.problem() : r.tripCause().isEmpty() ? Component.empty()
                : Component.translatable("gui.powerfactor.fission.trip." + r.tripCause());
        return new Snapshot(r.getBlockPos(), shape != null, note, v, withSize, r.alarms(), r.unacknowledged(), fuel, r.assemblyCount(), spent);
    }

    /** To every player with this reactor's console open. */
    public static void broadcast(ServerLevel level, ReactorControllerBlockEntity r) {
        Snapshot s = null;
        for (ServerPlayer p : level.players()) {
            if (p.containerMenu instanceof ReactorMenu m && m.pos.equals(r.getBlockPos())) {
                if (s == null) s = snapshot(r);
                p.connection.send(s);
            }
        }
    }

    private ReactorPayloads() {}
}
