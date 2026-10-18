package com.powerfactor.powernet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.powerfactor.PowerFactor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.PlayerInventoryWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Every wireless power network in the world (saved), which devices are on each (runtime), and the tick that moves
 * the power: Plugs gather it, Points and the controller's wireless charging spend it by priority, Cells soak up the
 * surplus and cover the shortfall. Range is unlimited and crosses dimensions; only loaded devices take part.
 */
public final class PowerNets extends SavedData {
    private int next = 1;
    private final Map<Integer, PowerNet> nets = new LinkedHashMap<>();

    public static final Codec<PowerNets> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("next").forGetter(d -> d.next),
            PowerNet.CODEC.listOf().fieldOf("networks").forGetter(d -> new ArrayList<>(d.nets.values()))
    ).apply(i, (next, list) -> {
        PowerNets d = new PowerNets();
        d.next = next;
        for (PowerNet n : list) d.nets.put(n.id, n);
        return d;
    }));
    public static final SavedDataType<PowerNets> TYPE = new SavedDataType<>(PowerFactor.id("power_networks"), PowerNets::new, CODEC);

    /** Devices that ask to keep their chunk loaded. */
    public static final TicketController TICKETS = new TicketController(PowerFactor.id("power_net"), (level, helper) -> {
        for (var owner : List.copyOf(helper.getBlockTickets().keySet())) {
            if (!(level.getBlockEntity(owner) instanceof PowerNodeBlockEntity n) || !n.chunkLoad) helper.removeAllTickets(owner);
        }
    });

    public static PowerNets get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public @Nullable PowerNet net(int id) {
        return nets.get(id);
    }

    public List<PowerNet> all() {
        return List.copyOf(nets.values());
    }

    public PowerNet create(String name, int color, UUID owner) {
        PowerNet n = new PowerNet(next++, name, color, owner);
        nets.put(n.id, n);
        setDirty();
        return n;
    }

    public void delete(MinecraftServer server, int id) {
        nets.remove(id);
        setDirty();
        for (PowerNodeBlockEntity d : devices(server, id)) d.setNetwork(-1);
        MEMBERS.remove(id);
    }

    public void changed() {
        setDirty();
    }

    // ---------------------------------------------------------------- devices (runtime)

    private static final Map<Integer, Set<GlobalPos>> MEMBERS = new HashMap<>();
    private static @Nullable MinecraftServer bound;

    private static void bind(MinecraftServer s) {
        if (bound != s) {
            MEMBERS.clear();
            bound = s;
        }
    }

    static void join(MinecraftServer s, int net, GlobalPos pos) {
        bind(s);
        if (net >= 0) MEMBERS.computeIfAbsent(net, k -> new LinkedHashSet<>()).add(pos);
    }

    static void leave(MinecraftServer s, int net, GlobalPos pos) {
        bind(s);
        Set<GlobalPos> set = MEMBERS.get(net);
        if (set != null) set.remove(pos);
    }

    /** Loaded devices on a network. */
    public static List<PowerNodeBlockEntity> devices(MinecraftServer s, int net) {
        bind(s);
        List<PowerNodeBlockEntity> out = new ArrayList<>();
        Set<GlobalPos> set = MEMBERS.get(net);
        if (set == null) return out;
        for (GlobalPos g : List.copyOf(set)) {
            ServerLevel level = s.getLevel(g.dimension());
            if (level != null && level.isLoaded(g.pos()) && level.getBlockEntity(g.pos()) instanceof PowerNodeBlockEntity d && !d.isRemoved()
                    && d.network == net) {
                out.add(d);
            } else if (level == null || level.isLoaded(g.pos())) {
                set.remove(g);
            }
        }
        return out;
    }

    /** Runs when exactly one controller is loaded on it. */
    public static boolean running(MinecraftServer s, int net) {
        return net >= 0 && status(s, net) == Status.RUNNING;
    }

    public enum Status { RUNNING, NO_CONTROLLER, TWO_CONTROLLERS, NONE }

    public static Status status(MinecraftServer s, int net) {
        if (net < 0 || get(s).net(net) == null) return Status.NONE;
        int controllers = 0;
        for (PowerNodeBlockEntity d : devices(s, net)) if (d.kind() == PowerNodeBlockEntity.Kind.CONTROLLER) controllers++;
        return controllers == 0 ? Status.NO_CONTROLLER : controllers > 1 ? Status.TWO_CONTROLLERS : Status.RUNNING;
    }

    static void updateChunk(ServerLevel level, PowerNodeBlockEntity d) {
        boolean want = d.chunkLoad && !d.isRemoved();
        if (want == d.forced) return;
        var pos = d.getBlockPos();
        TICKETS.forceChunk(level, pos, pos.getX() >> 4, pos.getZ() >> 4, want, true);
        d.forced = want;
    }

    // ---------------------------------------------------------------- the tick

    private record Demand(PowerNodeBlockEntity point, List<EnergyHandler> targets, int want) {}

    public static void tick(MinecraftServer server) {
        PowerNets data = get(server);
        bind(server);
        for (PowerNet net : data.nets.values()) {
            List<PowerNodeBlockEntity> all = devices(server, net.id);
            List<PowerNodeBlockEntity> plugs = new ArrayList<>(), points = new ArrayList<>(), cells = new ArrayList<>();
            int controllers = 0;
            for (PowerNodeBlockEntity d : all) {
                d.flow = 0;
                switch (d.kind()) {
                    case PLUG -> plugs.add(d);
                    case POINT -> points.add(d);
                    case CONTROLLER -> controllers++;
                    default -> cells.add(d);
                }
            }
            long stored = 0, capacity = 0;
            for (PowerNodeBlockEntity c : cells) {
                stored += c.energy;
                capacity += c.kind().capacity;
            }
            net.stored = stored;
            net.capacity = capacity;
            if (controllers != 1) {
                net.input = net.output = net.charged = 0;
                for (PowerNodeBlockEntity p : plugs) p.buffered = 0;
                tickStats(net, server);
                continue;
            }
            // Plugs also draw from anything beside them that will give power up.
            for (PowerNodeBlockEntity p : plugs) {
                for (EnergyHandler h : neighbours(p)) {
                    int room = p.effectiveLimit() - p.buffered;
                    if (room <= 0) break;
                    try (Transaction tx = Transaction.openRoot()) {
                        int got = h.extract(room, tx);
                        if (got > 0) {
                            tx.commit();
                            p.buffered += got;
                        }
                    }
                }
            }
            long supply = 0;
            for (PowerNodeBlockEntity p : plugs) supply += p.buffered;
            // Cells, lowest priority first to give up their charge, highest first to take it.
            cells.sort(Comparator.comparingInt((PowerNodeBlockEntity c) -> c.priority));
            long cellOut = 0;
            for (PowerNodeBlockEntity c : cells) cellOut += Math.min(c.energy, c.effectiveLimit());
            long available = supply + cellOut;
            // Demand: what each point's neighbours would take, up to its limit; served by priority, surge first.
            List<Demand> demands = new ArrayList<>();
            for (PowerNodeBlockEntity p : points) {
                List<EnergyHandler> targets = neighbours(p);
                long want = 0;
                for (EnergyHandler h : targets) {
                    try (Transaction tx = Transaction.openRoot()) {
                        want += h.insert(p.effectiveLimit(), tx);
                    }
                }
                if (want > 0) demands.add(new Demand(p, targets, (int) Math.min(want, p.effectiveLimit())));
            }
            demands.sort(Comparator.comparingInt((Demand d) -> d.point().surge ? Integer.MAX_VALUE : d.point().priority).reversed());
            long delivered = 0;
            int i = 0;
            while (i < demands.size() && available - delivered > 0) {
                // One priority group at a time, shared in proportion to what each wants.
                int j = i;
                long groupWant = 0;
                int key = key(demands.get(i));
                while (j < demands.size() && key(demands.get(j)) == key) groupWant += demands.get(j++).want();
                double share = Math.min(1.0, (double) (available - delivered) / Math.max(1, groupWant));
                for (int k = i; k < j; k++) {
                    Demand d = demands.get(k);
                    int give = (int) Math.floor(d.want() * share);
                    int sent = 0;
                    for (EnergyHandler h : d.targets()) {
                        if (give - sent <= 0) break;
                        try (Transaction tx = Transaction.openRoot()) {
                            int got = h.insert(give - sent, tx);
                            if (got > 0) {
                                tx.commit();
                                sent += got;
                            }
                        }
                    }
                    d.point().flow = sent;
                    delivered += sent;
                }
                i = j;
            }
            // Wireless charging for the network's people, after the points.
            long charged = net.charging == 0 ? 0 : charge(server, net, available - delivered);
            long used = delivered + charged;
            // Take it from the plugs first, then the cells.
            long fromPlugs = Math.min(used, supply);
            long fromCells = used - fromPlugs;
            // Cells give up charge lowest priority first, and take it highest first; equal priorities share in proportion.
            for (List<PowerNodeBlockEntity> group : byPriority(cells, false)) {
                if (fromCells <= 0) break;
                long can = 0;
                for (PowerNodeBlockEntity c : group) can += Math.min(c.energy, c.effectiveLimit());
                double share = can == 0 ? 0 : Math.min(1.0, (double) fromCells / can);
                for (PowerNodeBlockEntity c : group) {
                    long take = Math.min(fromCells, (long) Math.ceil(Math.min(c.energy, c.effectiveLimit()) * share));
                    c.energy -= take;
                    c.flow -= (int) take;
                    fromCells -= take;
                    c.setChanged();
                }
            }
            long leftover = supply - fromPlugs;
            long intoCells = 0;
            for (List<PowerNodeBlockEntity> group : byPriority(cells, true)) {
                if (leftover - intoCells <= 0) break;
                long room = 0;
                for (PowerNodeBlockEntity c : group) room += Math.min(c.kind().capacity - c.energy, c.effectiveLimit());
                double share = room == 0 ? 0 : Math.min(1.0, (double) (leftover - intoCells) / room);
                for (PowerNodeBlockEntity c : group) {
                    long put = Math.min(leftover - intoCells, (long) Math.floor(Math.min(c.kind().capacity - c.energy, c.effectiveLimit()) * share));
                    if (put > 0) {
                        c.energy += put;
                        c.flow += (int) put;
                        intoCells += put;
                        c.setChanged();
                    }
                }
            }
            // Drain the plugs by what was taken, in proportion; what's left stays buffered and backs up the producers.
            long taken = fromPlugs + intoCells;
            for (PowerNodeBlockEntity p : plugs) {
                long t = supply == 0 ? 0 : Math.min(p.buffered, Math.round((double) p.buffered * taken / supply));
                p.flow = (int) t;
                p.buffered -= (int) t;
            }
            net.input = taken;
            net.output = used;
            net.charged = charged;
            stored = 0;
            for (PowerNodeBlockEntity c : cells) stored += c.energy;
            net.stored = stored;
            tickStats(net, server);
        }
        // Keep renderers in step about once a second.
        if (server.getTickCount() % 20 == 0) {
            for (var e : MEMBERS.entrySet()) for (PowerNodeBlockEntity d : devices(server, e.getKey())) d.syncClient();
        }
    }

    /** Cells grouped by priority: highest first if {@code descending}. */
    private static List<List<PowerNodeBlockEntity>> byPriority(List<PowerNodeBlockEntity> cells, boolean descending) {
        java.util.TreeMap<Integer, List<PowerNodeBlockEntity>> groups = new java.util.TreeMap<>();
        for (PowerNodeBlockEntity c : cells) groups.computeIfAbsent(c.priority, k -> new ArrayList<>()).add(c);
        return new ArrayList<>(descending ? groups.descendingMap().values() : groups.values());
    }

    private static int key(Demand d) {
        return d.point().surge ? Integer.MAX_VALUE : d.point().priority;
    }

    private static void tickStats(PowerNet net, MinecraftServer server) {
        net.accIn += net.input;
        net.accOut += net.output;
        net.accTicks++;
        if (server.getTickCount() % 20 == 0) net.sample();
    }

    /** Energy handlers touching a device, other network devices excluded. */
    private static List<EnergyHandler> neighbours(PowerNodeBlockEntity d) {
        List<EnergyHandler> out = new ArrayList<>();
        var level = d.getLevel();
        for (Direction dir : Direction.values()) {
            var at = d.getBlockPos().relative(dir);
            if (level.getBlockEntity(at) instanceof PowerNodeBlockEntity) continue;
            EnergyHandler h = level.getCapability(Capabilities.Energy.BLOCK, at, dir.getOpposite());
            if (h != null) out.add(h);
        }
        return out;
    }

    /** Charges the FE items of the network's owner and members wherever they are; returns FE spent. */
    private static long charge(MinecraftServer server, PowerNet net, long budget) {
        long spent = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (budget - spent <= 0) break;
            if (p.isSpectator() || net.role(p) == null) continue;
            var inv = PlayerInventoryWrapper.of(p);
            for (int slot = 0; slot < inv.size() && budget - spent > 0; slot++) {
                int bit = slot < 9 ? PowerNet.CHARGE_HOTBAR : slot < 36 ? PowerNet.CHARGE_INVENTORY : slot < 40 ? PowerNet.CHARGE_ARMOR
                        : PowerNet.CHARGE_OFFHAND;
                if ((net.charging & bit) == 0) continue;
                EnergyHandler h = ItemAccess.forHandlerIndex(inv, slot).getCapability(Capabilities.Energy.ITEM);
                if (h == null || h.getAmountAsLong() >= h.getCapacityAsLong()) continue;
                try (Transaction tx = Transaction.openRoot()) {
                    int got = h.insert((int) Math.min(Integer.MAX_VALUE, budget - spent), tx);
                    if (got > 0) {
                        tx.commit();
                        spent += got;
                    }
                }
            }
        }
        return spent;
    }
}
