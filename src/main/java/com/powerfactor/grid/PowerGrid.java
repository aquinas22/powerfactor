package com.powerfactor.grid;

import com.powerfactor.api.Bus;
import com.powerfactor.api.GridGenerator;
import com.powerfactor.api.ReactiveLoad;
import com.powerfactor.power.BusSolver;
import com.powerfactor.power.PowerTriangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * One connected run of same-rated wire, solved as a single AC bus each tick (see {@link BusSolver}).
 *
 * <p>Generators push FE in through any wire; the grid loses some as line heat, then splits the
 * rest evenly between everything connected that will take it. Motor EE less capacitor EE is the
 * net reactive flow; turbines supply (or absorb) it in proportion to their spare capacity, and
 * whatever they can't cover collapses the voltage. Frequency is set by the turbines; a grid fed
 * only by engines and other mods' generators runs at a nominal 60 Hz.
 */
public final class PowerGrid implements Bus {
    record Endpoint(BlockPos pos, Direction side) {}

    /** Ticks without any FE arriving before the grid counts as de-energized. */
    private static final int DEAD_AFTER = 20;

    private final Set<BlockPos> wires;
    private final List<Endpoint> endpoints;
    private final int ratingCe;
    private final CableTier tier;
    private final List<BlockCapabilityCache<EnergyHandler, @Nullable Direction>> caches = new ArrayList<>();
    private final Buffer buffer;
    private boolean valid = true;
    private int rotation;

    private BusSolver.Result state = BusSolver.Result.NOMINAL;
    private double hertz = 60.0;
    private double generatorEeCapacity;
    /** A rebuilt grid assumes it's live until proven otherwise, so machines don't see a 1-tick blackout. */
    private int ticksSinceSupply = 0;
    private int lastDelivered;
    private double interference;

    PowerGrid(Set<BlockPos> wires, List<Endpoint> endpoints, CableTier tier) {
        this.wires = wires;
        this.endpoints = endpoints;
        this.tier = tier;
        this.ratingCe = tier.ratingCe;
        this.buffer = new Buffer(ratingCe);
    }

    public CableTier tier() {
        return tier;
    }

    Set<BlockPos> wires() {
        return wires;
    }

    void invalidate() {
        valid = false;
    }

    public boolean isValid() {
        return valid;
    }

    /** What wires expose to the world: insert-only, up to this tick's FE headroom. */
    public EnergyHandler handler() {
        return buffer;
    }

    public int ratingCe() {
        return ratingCe;
    }

    public boolean energized() {
        return ticksSinceSupply < DEAD_AFTER;
    }

    @Override
    public BusSolver.Result state() {
        return energized() ? state : DEAD;
    }

    @Override
    public double hertz() {
        return energized() ? hertz : 0;
    }

    public double interference() {
        return interference;
    }

    public PowerTriangle lastFlow() {
        return new PowerTriangle(state.fe(), state.netEe());
    }

    /** The EE this generator should supply (negative: absorb), by its share of spare capacity. */
    public double eeShareFor(GridGenerator generator) {
        return generatorEeCapacity <= 0 ? 0 : state.generatorEe() * generator.eeCapacity() / generatorEeCapacity;
    }

    private static final BusSolver.Result DEAD = new BusSolver.Result(0, 0, 0, 0, 0, 0);

    void tick(ServerLevel level) {
        if (caches.isEmpty() && !endpoints.isEmpty()) {
            for (Endpoint e : endpoints) {
                caches.add(BlockCapabilityCache.create(Capabilities.Energy.BLOCK, level, e.pos(), e.side()));
            }
        }

        // Survey what's attached. One block entity can touch the grid on several sides; count it once
        // per role. Sided devices (transformers) say which role each face plays.
        Map<BlockEntity, Boolean> seenLoad = new IdentityHashMap<>(), seenSource = new IdentityHashMap<>();
        double loadEe = 0, capacitorEe = 0, genCe = 0, genHz = 0, genEeCap = 0;
        for (Endpoint e : endpoints) {
            if (!level.isLoaded(e.pos())) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(e.pos());
            if (be == null) {
                continue;
            }
            var role = be instanceof com.powerfactor.api.SidedGridDevice sided ? sided.role(e.side()) : com.powerfactor.api.SidedGridDevice.Role.BOTH;
            boolean asLoad = (role == com.powerfactor.api.SidedGridDevice.Role.LOAD || role == com.powerfactor.api.SidedGridDevice.Role.BOTH)
                    && seenLoad.put(be, Boolean.TRUE) == null;
            boolean asSource = (role == com.powerfactor.api.SidedGridDevice.Role.SOURCE || role == com.powerfactor.api.SidedGridDevice.Role.BOTH)
                    && seenSource.put(be, Boolean.TRUE) == null;
            if (asLoad && be instanceof ReactiveLoad load) {
                double ee = load.eeDemand();
                if (ee >= 0) {
                    loadEe += ee;
                } else {
                    capacitorEe -= ee;
                }
            }
            if (asSource && be instanceof GridGenerator gen && gen.hertz() > 0) {
                genCe += gen.ratedCe();
                genHz += gen.hertz() * gen.ratedCe();
                genEeCap += gen.eeCapacity();
            }
        }
        if (buffer.amount() > 0) {
            ticksSinceSupply = 0;
        } else if (ticksSinceSupply < DEAD_AFTER) {
            ticksSinceSupply++;
        }
        hertz = genCe > 0 ? genHz / genCe : 60.0;
        generatorEeCapacity = genEeCap;
        state = BusSolver.solve(ratingCe, tier.resistance, lastDelivered, loadEe, capacitorEe, genEeCap, state.volts() > 0 ? state.volts() : 1.0);

        // Ender interference: busy wireless links nearby make voltage flicker and frequency jitter.
        interference = com.powerfactor.ender.EnderNetwork.interference(level, wires.iterator().next());
        if (interference > 0.01) {
            var rand = level.getRandom();
            double dip = interference * 0.14 * rand.nextDouble();
            state = new BusSolver.Result(Math.max(BusSolver.MIN_VOLTS, state.volts() * (1 - dip)), state.current(), state.fe(), state.netEe(),
                    state.generatorEe(), state.lossFe());
            hertz += (rand.nextDouble() - 0.5) * 0.8 * interference;
        }

        // Line heat comes off the top.
        buffer.take(Math.min(buffer.amount(), (int) Math.ceil(state.lossFe())));

        // Split the rest evenly; whatever a full consumer can't take goes round again.
        List<EnergyHandler> targets = new ArrayList<>();
        Map<EnergyHandler, Boolean> unique = new IdentityHashMap<>();
        for (var cache : caches) {
            EnergyHandler h = cache.getCapability();
            if (h != null && h != buffer && unique.put(h, Boolean.TRUE) == null) {
                targets.add(h);
            }
        }
        int delivered = 0;
        if (!targets.isEmpty()) {
            Collections.rotate(targets, rotation++ % targets.size());
            for (int pass = 0; pass < 2 && buffer.amount() > 0; pass++) {
                for (int i = 0; i < targets.size() && buffer.amount() > 0; i++) {
                    int share = Math.max(1, buffer.amount() / (targets.size() - i));
                    try (Transaction tx = Transaction.openRoot()) {
                        int moved = targets.get(i).insert(Math.min(share, buffer.amount()), tx);
                        tx.commit();
                        buffer.take(moved);
                        delivered += moved;
                    }
                }
            }
        }
        lastDelivered = delivered;

        // Next tick's FE headroom: the current rating at this voltage, less what EE uses.
        buffer.setLimit((int) PowerTriangle.maxFeFor(ratingCe * Math.min(1, state.volts()), state.netEe()));
    }

    private final class Buffer extends SimpleEnergyHandler {
        Buffer(int capacity) {
            super(capacity, Integer.MAX_VALUE, 0);
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            return valid ? super.insert(amount, transaction) : 0;
        }

        int amount() {
            return energy;
        }

        void take(int amount) {
            energy -= amount;
        }

        void setLimit(int limit) {
            capacity = Math.max(limit, energy);
        }
    }
}
