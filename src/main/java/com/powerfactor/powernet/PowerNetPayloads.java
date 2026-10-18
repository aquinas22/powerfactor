package com.powerfactor.powernet;

import com.powerfactor.PowerFactor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** The power network screen's feed (server → client, while open) and its commands (client → server). */
public final class PowerNetPayloads {
    // ---------------------------------------------------------------- snapshot

    public record NetSummary(int id, String name, int color, int access, int role, int devices) {}

    public record Member(String name, int role) {}

    public record Device(int kind, String name, String dim, long pos, int flow, long energy, int priority, boolean surge) {}

    public record Snapshot(BlockPos pos, int kind, String name, int priority, int limit, boolean surge, boolean unlimited, boolean chunkLoad, int flow,
                           long energy, int network, int status, boolean canEdit, List<NetSummary> nets, String netName, int netColor, int access,
                           String owner, long input, long output, long charged, long stored, long capacity, int charging, long[] history,
                           List<Member> members, List<Device> devices) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(PowerFactor.id("power_net_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> STREAM_CODEC = StreamCodec.of(Snapshot::write, Snapshot::read);

        @Override
        public Type<Snapshot> type() {
            return TYPE;
        }

        private static void write(RegistryFriendlyByteBuf b, Snapshot s) {
            b.writeBlockPos(s.pos);
            b.writeVarInt(s.kind);
            b.writeUtf(s.name);
            b.writeVarInt(s.priority);
            b.writeVarInt(s.limit);
            b.writeBoolean(s.surge);
            b.writeBoolean(s.unlimited);
            b.writeBoolean(s.chunkLoad);
            b.writeVarInt(s.flow);
            b.writeVarLong(s.energy);
            b.writeVarInt(s.network);
            b.writeVarInt(s.status);
            b.writeBoolean(s.canEdit);
            b.writeVarInt(s.nets.size());
            for (NetSummary n : s.nets) {
                b.writeVarInt(n.id);
                b.writeUtf(n.name);
                b.writeInt(n.color);
                b.writeVarInt(n.access);
                b.writeVarInt(n.role);
                b.writeVarInt(n.devices);
            }
            b.writeUtf(s.netName);
            b.writeInt(s.netColor);
            b.writeVarInt(s.access);
            b.writeUtf(s.owner);
            b.writeVarLong(s.input);
            b.writeVarLong(s.output);
            b.writeVarLong(s.charged);
            b.writeVarLong(s.stored);
            b.writeVarLong(s.capacity);
            b.writeVarInt(s.charging);
            b.writeVarInt(s.history.length);
            for (long v : s.history) b.writeVarLong(Math.max(0, v));
            b.writeVarInt(s.members.size());
            for (Member m : s.members) {
                b.writeUtf(m.name);
                b.writeVarInt(m.role);
            }
            b.writeVarInt(s.devices.size());
            for (Device d : s.devices) {
                b.writeVarInt(d.kind);
                b.writeUtf(d.name);
                b.writeUtf(d.dim);
                b.writeLong(d.pos);
                b.writeInt(d.flow);
                b.writeVarLong(Math.max(0, d.energy));
                b.writeInt(d.priority);
                b.writeBoolean(d.surge);
            }
        }

        private static Snapshot read(RegistryFriendlyByteBuf b) {
            BlockPos pos = b.readBlockPos();
            int kind = b.readVarInt();
            String name = b.readUtf();
            int priority = b.readVarInt(), limit = b.readVarInt();
            boolean surge = b.readBoolean(), unlimited = b.readBoolean(), chunk = b.readBoolean();
            int flow = b.readVarInt();
            long energy = b.readVarLong();
            int network = b.readVarInt(), status = b.readVarInt();
            boolean canEdit = b.readBoolean();
            int n = Math.min(b.readVarInt(), 256);
            List<NetSummary> nets = new ArrayList<>();
            for (int i = 0; i < n; i++) nets.add(new NetSummary(b.readVarInt(), b.readUtf(), b.readInt(), b.readVarInt(), b.readVarInt(), b.readVarInt()));
            String netName = b.readUtf();
            int netColor = b.readInt(), access = b.readVarInt();
            String owner = b.readUtf();
            long input = b.readVarLong(), output = b.readVarLong(), charged = b.readVarLong(), stored = b.readVarLong(), capacity = b.readVarLong();
            int charging = b.readVarInt();
            long[] history = new long[Math.min(b.readVarInt(), 512)];
            for (int i = 0; i < history.length; i++) history[i] = b.readVarLong();
            int m = Math.min(b.readVarInt(), 64);
            List<Member> members = new ArrayList<>();
            for (int i = 0; i < m; i++) members.add(new Member(b.readUtf(), b.readVarInt()));
            int d = Math.min(b.readVarInt(), 128);
            List<Device> devices = new ArrayList<>();
            for (int i = 0; i < d; i++) devices.add(new Device(b.readVarInt(), b.readUtf(), b.readUtf(), b.readLong(), b.readInt(), b.readVarLong(), b.readInt(), b.readBoolean()));
            return new Snapshot(pos, kind, name, priority, limit, surge, unlimited, chunk, flow, energy, network, status, canEdit, nets, netName, netColor,
                    access, owner, input, output, charged, stored, capacity, charging, history, members, devices);
        }
    }

    // ---------------------------------------------------------------- commands

    public static final int SELECT = 0, CREATE = 1, EDIT = 2, DELETE = 3, DEVICE = 4, CHARGING = 5, MEMBER = 6, LEAVE = 7;

    /** Generic command: op, two ints, a long, two strings and a flag word. */
    public record Command(BlockPos pos, int op, int a, int b, long c, String s1, String s2, int flags) implements CustomPacketPayload {
        public static final Type<Command> TYPE = new Type<>(PowerFactor.id("power_net_command"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Command> STREAM_CODEC = StreamCodec.of((buf, c) -> {
            buf.writeBlockPos(c.pos);
            buf.writeVarInt(c.op);
            buf.writeInt(c.a);
            buf.writeInt(c.b);
            buf.writeLong(c.c);
            buf.writeUtf(c.s1, 64);
            buf.writeUtf(c.s2, 64);
            buf.writeVarInt(c.flags);
        }, buf -> new Command(buf.readBlockPos(), buf.readVarInt(), buf.readInt(), buf.readInt(), buf.readLong(), buf.readUtf(64), buf.readUtf(64), buf.readVarInt()));

        @Override
        public Type<Command> type() {
            return TYPE;
        }
    }

    private static final Map<UUID, Integer> LAST = new HashMap<>();

    /** The network this player last joined a device to: new devices they place join it. */
    public static int lastNetwork(ServerPlayer p) {
        return LAST.getOrDefault(p.getUUID(), -1);
    }

    public static java.util.function.Consumer<Snapshot> clientHandler = s -> {};

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(Snapshot.TYPE, Snapshot.STREAM_CODEC, (p, ctx) -> clientHandler.accept(p));
        registrar.playToServer(Command.TYPE, Command.STREAM_CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp && sp.containerMenu instanceof PowerNetMenu m && m.pos.equals(p.pos())
                    && sp.level().getBlockEntity(p.pos()) instanceof PowerNodeBlockEntity d) {
                apply(sp, d, p);
                send(sp, d);
            }
        });
    }

    static void apply(ServerPlayer player, PowerNodeBlockEntity d, Command c) {
        var server = player.level().getServer();
        PowerNets data = PowerNets.get(server);
        switch (c.op()) {
            case SELECT -> {
                PowerNet n = data.net(c.a());
                if (n == null) return;
                boolean ok = n.canUse(player) || n.access == PowerNet.Access.PASSWORD && !n.password.isEmpty() && n.password.equals(c.s1());
                if (!ok) return;
                if (n.access == PowerNet.Access.PASSWORD && n.role(player) == null) {
                    n.members.put(player.getUUID(), PowerNet.Role.USER);
                    data.changed();
                }
                d.setNetwork(n.id);
                LAST.put(player.getUUID(), n.id);
            }
            case LEAVE -> d.setNetwork(-1);
            case CREATE -> {
                String name = c.s1().isBlank() ? player.getGameProfile().name() + "'s Network" : c.s1().strip();
                PowerNet n = data.create(name, c.a() & 0xFFFFFF, player.getUUID());
                n.access = PowerNet.Access.values()[Math.clamp(c.b(), 0, 2)];
                n.password = c.s2();
                data.changed();
                d.setNetwork(n.id);
                LAST.put(player.getUUID(), n.id);
            }
            case EDIT -> {
                PowerNet n = data.net(d.network());
                if (n == null || !n.canEdit(player)) return;
                if (!c.s1().isBlank()) n.name = c.s1().strip();
                n.color = c.a() & 0xFFFFFF;
                n.access = PowerNet.Access.values()[Math.clamp(c.b(), 0, 2)];
                if (n.access == PowerNet.Access.PASSWORD && !c.s2().isEmpty()) n.password = c.s2();
                data.changed();
                for (PowerNodeBlockEntity x : PowerNets.devices(server, n.id)) x.syncClient();
            }
            case DELETE -> {
                PowerNet n = data.net(d.network());
                if (n != null && n.role(player) == PowerNet.Role.OWNER) data.delete(server, n.id);
            }
            case DEVICE -> d.applySettings(c.s1(), c.a(), c.b(), (c.flags() & 1) != 0, (c.flags() & 2) != 0, (c.flags() & 4) != 0);
            case CHARGING -> {
                PowerNet n = data.net(d.network());
                if (n != null && n.canEdit(player)) {
                    n.charging = c.flags() & 15;
                    data.changed();
                }
            }
            case MEMBER -> {
                // a: role (0 user, 1 admin, -1 remove); s1: player name (online).
                PowerNet n = data.net(d.network());
                if (n == null || !n.canEdit(player)) return;
                ServerPlayer who = server.getPlayerList().getPlayerByName(c.s1());
                if (who == null || who.getUUID().equals(n.owner)) return;
                if (c.a() < 0) n.members.remove(who.getUUID());
                else n.members.put(who.getUUID(), c.a() == 1 ? PowerNet.Role.ADMIN : PowerNet.Role.USER);
                data.changed();
            }
            default -> {}
        }
    }

    public static void send(ServerPlayer player, PowerNodeBlockEntity d) {
        var server = player.level().getServer();
        PowerNets data = PowerNets.get(server);
        PowerNet net = data.net(d.network());
        List<NetSummary> nets = new ArrayList<>();
        for (PowerNet n : data.all()) {
            boolean visible = n.canUse(player) || n.access == PowerNet.Access.PASSWORD;
            if (!visible) continue;
            var role = n.role(player);
            nets.add(new NetSummary(n.id, n.name, n.color, n.access.ordinal(), role == null ? -1 : role.ordinal(), PowerNets.devices(server, n.id).size()));
        }
        List<Member> members = new ArrayList<>();
        List<Device> devices = new ArrayList<>();
        long[] history = new long[180];
        String owner = "";
        if (net != null) {
            var cache = server.services().nameToIdCache();
            owner = cache.get(net.owner).map(NameAndId::name).orElse("?");
            for (var e : net.members.entrySet()) {
                members.add(new Member(cache.get(e.getKey()).map(NameAndId::name).orElse("?"), e.getValue().ordinal()));
            }
            for (PowerNodeBlockEntity x : PowerNets.devices(server, net.id)) {
                if (devices.size() >= 128) break;
                devices.add(new Device(x.kind().ordinal(), x.displayName().getString(), x.getLevel().dimension().identifier().getPath(),
                        x.getBlockPos().asLong(), x.flow(), x.energy(), x.priority(), x.surge()));
            }
            for (int k = 0; k < 3; k++) {
                for (int i = 0; i < 60; i++) history[k * 60 + i] = net.history[k][(net.historyHead + 1 + i) % 60];
            }
        }
        player.connection.send(new Snapshot(d.getBlockPos(), d.kind().ordinal(), d.customName(), d.priority(), d.limit(), d.surge(), d.unlimited(),
                d.chunkLoad(), d.flow(), d.energy(), d.network(), PowerNets.status(server, d.network()).ordinal(), net != null && net.canEdit(player), nets,
                net == null ? "" : net.name, net == null ? 0 : net.color, net == null ? 0 : net.access.ordinal(), owner,
                net == null ? 0 : net.input, net == null ? 0 : net.output, net == null ? 0 : net.charged, net == null ? 0 : net.stored,
                net == null ? 0 : net.capacity, net == null ? 0 : net.charging, history, members, devices));
    }

    /** Server tick: feed every open screen four times a second. */
    public static void tick(net.minecraft.server.MinecraftServer server) {
        if (server.getTickCount() % 5 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.containerMenu instanceof PowerNetMenu m && p.level().getBlockEntity(m.pos) instanceof PowerNodeBlockEntity d) send(p, d);
        }
    }

    private PowerNetPayloads() {}
}
