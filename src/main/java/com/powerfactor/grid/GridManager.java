package com.powerfactor.grid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Tracks the wire grids in one dimension. Grids are built lazily by flood fill the first time
 * something asks for one, and thrown away whenever a wire, a connection, or a chunk they cross
 * changes. Grids hold at most one tick of FE, so rebuilding loses almost nothing.
 */
public final class GridManager {
    private static final Map<ResourceKey<Level>, GridManager> MANAGERS = new HashMap<>();
    private static final int MAX_WIRES = 16_384;

    private final ServerLevel level;
    private final Map<BlockPos, PowerGrid> byPos = new HashMap<>();
    private final Set<PowerGrid> grids = new LinkedHashSet<>();
    private final Map<BlockPos, java.util.Optional<CableTier>> breakerTiers = new HashMap<>();

    private GridManager(ServerLevel level) {
        this.level = level;
    }

    public static GridManager get(ServerLevel level) {
        GridManager manager = MANAGERS.get(level.dimension());
        if (manager == null || manager.level != level) {
            manager = new GridManager(level);
            MANAGERS.put(level.dimension(), manager);
        }
        return manager;
    }

    static void clearAll() {
        MANAGERS.clear();
    }

    /** The grid this wire, closed breaker or pylon belongs to, building it if needed; null if none. */
    public @Nullable PowerGrid gridAt(BlockPos pos) {
        PowerGrid grid = byPos.get(pos);
        if (grid != null) {
            return grid;
        }
        if (!level.isLoaded(pos)) {
            return null;
        }
        CableTier tier = tierAt(pos);
        return tier == null ? null : build(pos.immutable(), tier);
    }

    /** What tier of grid a node here belongs to. A breaker takes the tier of an adjacent wire. */
    private @Nullable CableTier tierAt(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof WireBlock wire) {
            return wire.tier();
        }
        if (state.getBlock() instanceof PylonBlock) {
            return CableTier.HV;
        }
        if (state.getBlock() instanceof BreakerBlock && state.getValue(BreakerBlock.CLOSED)) return breakerTier(pos);
        return null;
    }

    /** A whole closed-breaker group must have one voltage; conflicting groups join neither grid. */
    private @Nullable CableTier breakerTier(BlockPos start) {
        var known = breakerTiers.get(start);
        if (known != null) return known.orElse(null);
        Set<BlockPos> group = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        group.add(start.immutable()); queue.add(start.immutable());
        CableTier selected = null; boolean conflict = false;
        while (!queue.isEmpty() && group.size() <= MAX_WIRES) {
            BlockPos pos = queue.remove();
            for (Direction d : Direction.values()) {
                BlockPos next = pos.relative(d);
                if (!level.isLoaded(next)) continue;
                BlockState state = level.getBlockState(next);
                if (state.getBlock() instanceof BreakerBlock && state.getValue(BreakerBlock.CLOSED)) {
                    if (group.add(next)) queue.add(next);
                } else {
                    CableTier wire = state.getBlock() instanceof WireBlock w ? w.tier() : state.getBlock() instanceof PylonBlock ? CableTier.HV : null;
                    if (wire == null) continue;
                    if (selected != null && !selected.compatible(wire)) conflict = true;
                    if (selected == null || wire.ratingCe < selected.ratingCe) selected = wire;
                }
            }
        }
        if (conflict || !queue.isEmpty()) selected = null;
        var result = java.util.Optional.ofNullable(selected);
        for (BlockPos pos : group) breakerTiers.put(pos, result);
        return selected;
    }

    private boolean isNode(BlockPos pos, BlockState state, CableTier tier) {
        return state.getBlock() instanceof WireBlock w && w.tier().compatible(tier)
                || state.getBlock() instanceof PylonBlock && tier == CableTier.HV
                || state.getBlock() instanceof BreakerBlock && state.getValue(BreakerBlock.CLOSED)
                && breakerTier(pos) instanceof CableTier breaker && breaker.compatible(tier);
    }

    /** Something worth delivering to or hearing from: an FE handler or an EE device. */
    private boolean isEndpoint(BlockPos pos, Direction side) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ConnectorBlock || state.getBlock() instanceof PylonBlock || state.getBlock() instanceof BreakerBlock) {
            return false;
        }
        return level.getBlockEntity(pos) instanceof com.powerfactor.api.ReactiveLoad
                || level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK, pos, side) != null;
    }

    public void invalidate(BlockPos pos) {
        breakerTiers.clear();
        drop(byPos.get(pos));
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> breakers = new ArrayDeque<>();
        if (level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof BreakerBlock) {
            visited.add(pos.immutable()); breakers.add(pos.immutable());
        }
        for (Direction d : Direction.values()) {
            BlockPos next = pos.relative(d);
            drop(byPos.get(next));
            if (level.isLoaded(next) && level.getBlockState(next).getBlock() instanceof BreakerBlock && visited.add(next)) breakers.add(next);
        }
        // A previously incompatible breaker chain may become valid when a remote terminal changes.
        while (!breakers.isEmpty() && visited.size() <= MAX_WIRES) {
            BlockPos breaker = breakers.remove();
            drop(byPos.get(breaker));
            for (Direction d : Direction.values()) {
                BlockPos next = breaker.relative(d);
                if (!level.isLoaded(next)) continue;
                drop(byPos.get(next));
                BlockState state = level.getBlockState(next);
                if (state.getBlock() instanceof BreakerBlock && state.getValue(BreakerBlock.CLOSED) && visited.add(next)) breakers.add(next);
            }
        }
    }

    void onChunkUnloaded(ChunkPos chunk) {
        breakerTiers.clear();
        for (PowerGrid grid : List.copyOf(grids)) {
            for (BlockPos p : grid.wires()) {
                if (ChunkPos.containing(p).equals(chunk)) {
                    drop(grid);
                    break;
                }
            }
        }
    }

    void tick() {
        for (PowerGrid grid : List.copyOf(grids)) {
            if (grid.isValid()) {
                grid.tick(level);
            }
        }
    }

    private void drop(@Nullable PowerGrid grid) {
        if (grid != null && grids.remove(grid)) {
            grid.invalidate();
            for (BlockPos p : grid.wires()) {
                byPos.remove(p);
                // Anyone caching this wire's handler must re-query and get the new grid's.
                level.invalidateCapabilities(p);
            }
        }
    }

    private PowerGrid build(BlockPos start, CableTier tier) {
        CableTier limitingTier = tier;
        Set<BlockPos> wires = new HashSet<>();
        Set<PowerGrid.Endpoint> endpoints = new java.util.LinkedHashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        wires.add(start);
        queue.add(start);
        while (!queue.isEmpty() && wires.size() < MAX_WIRES) {
            BlockPos pos = queue.poll();
            BlockState state = level.getBlockState(pos);
            for (Direction d : Direction.values()) {
                // Wires only reach along their connected arms; breakers and pylons along any face.
                if (state.getBlock() instanceof WireBlock && !state.getValue(ConnectorBlock.CONNECTIONS.get(d))) {
                    continue;
                }
                BlockPos next = pos.relative(d);
                if (!level.isLoaded(next)) {
                    continue;
                }
                BlockState nextState = level.getBlockState(next);
                if (isNode(next, nextState, tier)) {
                    if (nextState.getBlock() instanceof WireBlock wire && wire.ratingCe() < limitingTier.ratingCe) limitingTier = wire.tier();
                    if (wires.add(next)) {
                        queue.add(next);
                    }
                } else if (isEndpoint(next, d.getOpposite())) {
                    endpoints.add(new PowerGrid.Endpoint(next, d.getOpposite()));
                }
            }
            // Conductors strung between pylons.
            if (state.getBlock() instanceof PylonBlock && level.getBlockEntity(pos) instanceof PylonBlockEntity pylon) {
                for (BlockPos link : pylon.links()) {
                    if (level.isLoaded(link) && level.getBlockState(link).getBlock() instanceof PylonBlock && wires.add(link)) {
                        queue.add(link);
                    }
                }
            }
        }
        PowerGrid grid = new PowerGrid(wires, new ArrayList<>(endpoints), limitingTier);
        for (BlockPos p : wires) {
            drop(byPos.get(p));
            byPos.put(p, grid);
        }
        grids.add(grid);
        return grid;
    }
}
