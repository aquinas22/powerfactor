package com.powerfactor.blackhole;

import com.powerfactor.PowerFactor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** The Black Hole console's live feed and its commands. */
public final class BlackHolePayloads {
    public static final int MASS = 0, HAWKING = 1, ACCRETION = 2, CONTAINMENT = 3, NET = 4, STORED = 5, FEED = 6, HOLD = 7, TARGET = 8, AUTO = 9,
            RATE = 10, STATE = 11, IGNITION = 12, FORMED = 13, COUNT = 14;

    public record Snapshot(BlockPos pos, double[] v) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(PowerFactor.id("black_hole_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> STREAM_CODEC = StreamCodec.of((buf, s) -> {
            buf.writeBlockPos(s.pos);
            for (int i = 0; i < COUNT; i++) buf.writeDouble(s.v[i]);
        }, buf -> {
            BlockPos p = buf.readBlockPos();
            double[] v = new double[COUNT];
            for (int i = 0; i < COUNT; i++) v[i] = buf.readDouble();
            return new Snapshot(p, v);
        });

        @Override
        public Type<Snapshot> type() {
            return TYPE;
        }
    }

    public static final int IGNITE = 0, SET_TARGET = 1, SET_AUTO = 2, SET_RATE = 3;

    public record Command(BlockPos pos, int op, double value) implements CustomPacketPayload {
        public static final Type<Command> TYPE = new Type<>(PowerFactor.id("black_hole_command"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Command> STREAM_CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, Command::pos,
                ByteBufCodecs.VAR_INT, Command::op, ByteBufCodecs.DOUBLE, Command::value, Command::new);

        @Override
        public Type<Command> type() {
            return TYPE;
        }
    }

    public static java.util.function.Consumer<Snapshot> clientHandler = s -> {};

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(Snapshot.TYPE, Snapshot.STREAM_CODEC, (p, ctx) -> clientHandler.accept(p));
        registrar.playToServer(Command.TYPE, Command.STREAM_CODEC, (p, ctx) -> {
            if (!(ctx.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof BlackHoleMenu menu) || !menu.pos.equals(p.pos())) return;
            var be = menu.blackHole();
            if (be == null) return;
            switch (p.op()) {
                case IGNITE -> be.ignite((ServerLevel) player.level());
                case SET_TARGET -> be.setTargetMass(p.value());
                case SET_AUTO -> be.setAutoFeed(p.value() > 0);
                case SET_RATE -> be.setFeedRate((int) p.value());
                default -> {}
            }
        });
    }

    static Snapshot snapshot(BlackHoleBlockEntity be) {
        var c = be.core();
        double[] v = new double[COUNT];
        boolean live = be.state() == BlackHoleBlockEntity.STATE_RUNNING;
        v[MASS] = live ? c.mass : 0;
        v[HAWKING] = live ? c.hawking : 0;
        v[ACCRETION] = live ? be.lastFeed() * BlackHoleCore.C2 * BlackHoleCore.ACCRETION_EFFICIENCY : 0;
        v[CONTAINMENT] = live ? c.containment : 0;
        v[NET] = live ? c.net : 0;
        v[STORED] = be.getEnergy().getAmountAsInt();
        v[FEED] = be.lastFeed() * 20;
        v[HOLD] = live ? c.holdFeed() * 20 : 0;
        v[TARGET] = be.targetMass();
        v[AUTO] = be.autoFeed() ? 1 : 0;
        v[RATE] = be.feedRate();
        v[STATE] = be.state();
        v[IGNITION] = be.ignition() / BlackHoleBlockEntity.IGNITION;
        v[FORMED] = be.formed() ? 1 : 0;
        return new Snapshot(be.getBlockPos(), v);
    }

    static void broadcast(ServerLevel level, BlackHoleBlockEntity be) {
        Snapshot s = null;
        for (ServerPlayer p : level.players()) {
            if (p.containerMenu instanceof BlackHoleMenu m && m.pos.equals(be.getBlockPos())) {
                if (s == null) s = snapshot(be);
                p.connection.send(s);
            }
        }
    }

    private BlackHolePayloads() {}
}
