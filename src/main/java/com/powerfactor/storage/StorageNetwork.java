package com.powerfactor.storage;

import com.powerfactor.storage.craft.CraftingJob;
import com.powerfactor.storage.craft.PatternData;
import com.powerfactor.storage.craft.PatternHolder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * A storage network: everything reachable from one controller through data cables. Holds the
 * aggregated item view terminals display, routes inserts by storage priority, and diverts items that
 * running crafting jobs are waiting for.
 */
public final class StorageNetwork {
    public static final int MAX_MEMBERS = 8192;
    public static final int BASE_DRAW = 4;

    private final ServerLevel level;
    private final StorageControllerBlockEntity controller;
    private final Set<BlockPos> members = new HashSet<>();
    private final List<NodeBlockEntity> nodes = new ArrayList<>();
    private List<NetworkStorage> storages = List.of();
    private List<com.powerfactor.storage.fluid.NetworkFluidStorage> fluidStorages = List.of();
    private final Map<net.neoforged.neoforge.transfer.fluid.FluidResource, Long> fluidTotals = new HashMap<>();
    private final Map<ItemResource, Long> totals = new HashMap<>();
    private final Map<PatternData, List<PatternHolder>> patterns = new LinkedHashMap<>();
    private boolean valid = true, online, conflict, storagesDirty = true, patternsDirty = true, hasExternal;
    /** Bumped on every change to the item view; terminals diff when it moves. */
    private long version;
    private int draw = BASE_DRAW;
    private long usedBytes, totalBytes;

    StorageNetwork(ServerLevel level, StorageControllerBlockEntity controller) {
        this.level = level;
        this.controller = controller;
    }

    // ------------------------------------------------------------------ topology

    /** Walk cables and nodes from the controller. Never loads chunks. */
    void scan() {
        Set<BlockPos> seen = new HashSet<>();
        List<NodeBlockEntity> found = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockPos start = controller.getBlockPos();
        queue.add(start);
        seen.add(start);
        boolean otherController = false;
        while (!queue.isEmpty() && seen.size() < MAX_MEMBERS) {
            BlockPos p = queue.poll();
            BlockEntity be = level.getBlockEntity(p);
            if (be instanceof NodeBlockEntity node) {
                if (node instanceof StorageControllerBlockEntity && node != controller) otherController = true;
                found.add(node);
            }
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (seen.contains(n) || !level.isLoaded(n)) continue;
                if (StorageNetworks.isMember(level, n)) {
                    seen.add(n);
                    queue.add(n);
                }
            }
        }
        for (NodeBlockEntity old : nodes) {
            if (!found.contains(old)) {
                old.attach(null);
                old.setActive(false);
            }
        }
        members.clear();
        members.addAll(seen);
        nodes.clear();
        nodes.addAll(found);
        conflict = otherController;
        for (NodeBlockEntity n : nodes) n.attach(this);
        storagesDirty = patternsDirty = true;
        int d = BASE_DRAW;
        for (NodeBlockEntity n : nodes) if (n != controller) d += n.idleDraw();
        draw = d;
    }

    boolean touches(BlockPos pos) {
        if (members.contains(pos)) return true;
        for (Direction d : Direction.values()) if (members.contains(pos.relative(d))) return true;
        return false;
    }

    void dissolve() {
        valid = false;
        for (NodeBlockEntity n : nodes) {
            if (n != controller) {
                n.attach(null);
                if (!n.isRemoved()) n.setActive(false);
            }
        }
        nodes.clear();
    }

    public boolean valid() {
        return valid;
    }

    public boolean online() {
        return valid && online && !conflict;
    }

    public boolean conflict() {
        return conflict;
    }

    public int draw() {
        return draw;
    }

    public ServerLevel level() {
        return level;
    }

    public StorageControllerBlockEntity controller() {
        return controller;
    }

    public List<NodeBlockEntity> nodes() {
        return nodes;
    }

    // ------------------------------------------------------------------ ticking

    void tick(boolean powered) {
        boolean was = online();
        online = powered;
        if (online() != was) {
            for (NodeBlockEntity n : nodes) n.setActive(online());
            version++;
        }
        if (!online()) return;
        if (storagesDirty) rebuildStorages();
        if (hasExternal && level.getGameTime() % 20 == 0) refreshTotals();
        for (NodeBlockEntity n : List.copyOf(nodes)) {
            if (!n.isRemoved()) n.networkTick(level, this);
        }
    }

    public void markStoragesDirty() {
        storagesDirty = true;
    }

    public void markPatternsDirty() {
        patternsDirty = true;
        version++;
    }

    private void rebuildStorages() {
        storagesDirty = false;
        List<NetworkStorage> list = new ArrayList<>();
        for (NodeBlockEntity n : nodes) n.addStorages(list);
        list.sort(Comparator.comparingInt(NetworkStorage::priority).reversed());
        storages = list;
        hasExternal = list.stream().anyMatch(NetworkStorage::external);
        List<com.powerfactor.storage.fluid.NetworkFluidStorage> fluids = new ArrayList<>();
        for (NodeBlockEntity n : nodes) n.addFluidStorages(fluids);
        fluids.sort(Comparator.comparingInt(com.powerfactor.storage.fluid.NetworkFluidStorage::priority).reversed());
        fluidStorages = fluids;
        fluidTotals.clear();
        for (var f : fluids) f.collect(fluidTotals);
        refreshTotals();
    }

    private void refreshTotals() {
        Map<ItemResource, Long> fresh = new HashMap<>();
        long used = 0, total = 0;
        for (NetworkStorage s : storages) {
            s.collect(fresh);
            used += s.usedBytes();
            total += s.totalBytes();
        }
        usedBytes = used;
        totalBytes = total;
        if (!fresh.equals(totals)) {
            totals.clear();
            totals.putAll(fresh);
            version++;
        }
    }

    // ------------------------------------------------------------------ items

    public long version() {
        return version;
    }

    public Map<ItemResource, Long> totals() {
        return totals;
    }

    public long count(ItemResource r) {
        return totals.getOrDefault(r, 0L);
    }

    public long usedBytes() {
        return usedBytes;
    }

    public long totalBytes() {
        return totalBytes;
    }

    public int storageCount() {
        return storages.size();
    }

    /** Insert into the network; returns how many were accepted. Jobs waiting on the item take it first. */
    public long insert(ItemResource r, long amount, boolean simulate) {
        if (!online() || r.isEmpty() || amount <= 0) return 0;
        if (storagesDirty) rebuildStorages();
        long left = amount;
        if (!simulate) {
            for (CraftingJob job : jobs()) {
                left -= job.offer(r, left);
                if (left <= 0) return amount;
            }
        }
        long stored = 0;
        // Storages that already hold the item first, so stacks stay together; then any with room.
        for (int pass = 0; pass < 2 && left > 0; pass++) {
            for (NetworkStorage s : storages) {
                if (pass == 0 && s.count(r) <= 0) continue;
                long n = s.insert(r, left, simulate);
                left -= n;
                stored += n;
                if (left <= 0) break;
            }
        }
        if (!simulate && stored > 0) {
            totals.merge(r, stored, Long::sum);
            version++;
        }
        return amount - left;
    }

    /** Extract from the network; lowest-priority storages are drained first. */
    public long extract(ItemResource r, long amount, boolean simulate) {
        if (!online() || r.isEmpty() || amount <= 0) return 0;
        if (storagesDirty) rebuildStorages();
        long left = amount;
        for (int i = storages.size() - 1; i >= 0 && left > 0; i--) {
            left -= storages.get(i).extract(r, left, simulate);
        }
        long taken = amount - left;
        if (!simulate && taken > 0) {
            long now = totals.getOrDefault(r, 0L) - taken;
            if (now > 0) totals.put(r, now);
            else totals.remove(r);
            version++;
        }
        return taken;
    }

    // ------------------------------------------------------------------ fluids (amounts in mB)

    public Map<net.neoforged.neoforge.transfer.fluid.FluidResource, Long> fluidTotals() {
        if (storagesDirty) rebuildStorages();
        return fluidTotals;
    }

    public long fluidCount(net.neoforged.neoforge.transfer.fluid.FluidResource f) {
        return fluidTotals().getOrDefault(f, 0L);
    }

    public int fluidStorageCount() {
        if (storagesDirty) rebuildStorages();
        return fluidStorages.size();
    }

    /** Insert fluid; cells already holding it first, then any with room. Returns mB accepted. */
    public long insertFluid(net.neoforged.neoforge.transfer.fluid.FluidResource f, long amount, boolean simulate) {
        if (!online() || f.isEmpty() || amount <= 0) return 0;
        if (storagesDirty) rebuildStorages();
        long left = amount;
        for (int pass = 0; pass < 2 && left > 0; pass++) {
            for (var s : fluidStorages) {
                if (pass == 0 && s.count(f) <= 0) continue;
                left -= s.insert(f, left, simulate);
                if (left <= 0) break;
            }
        }
        long stored = amount - left;
        if (!simulate && stored > 0) {
            fluidTotals.merge(f, stored, Long::sum);
            version++;
        }
        return stored;
    }

    /**
     * Undo a journalled fluid change even if the network has just gone offline, so an aborted
     * transaction never loses or duplicates fluid. Positive delta re-inserts, negative removes.
     */
    public void revertFluid(net.neoforged.neoforge.transfer.fluid.FluidResource f, long delta) {
        boolean was = online;
        online = true;
        try {
            if (delta > 0) insertFluid(f, delta, false);
            else extractFluid(f, -delta, false);
        } finally {
            online = was;
        }
    }

    /** Extract fluid, lowest-priority cells first. Returns mB taken. */
    public long extractFluid(net.neoforged.neoforge.transfer.fluid.FluidResource f, long amount, boolean simulate) {
        if (!online() || f.isEmpty() || amount <= 0) return 0;
        if (storagesDirty) rebuildStorages();
        long left = amount;
        for (int i = fluidStorages.size() - 1; i >= 0 && left > 0; i--) left -= fluidStorages.get(i).extract(f, left, simulate);
        long taken = amount - left;
        if (!simulate && taken > 0) {
            long now = fluidTotals.getOrDefault(f, 0L) - taken;
            if (now > 0) fluidTotals.put(f, now);
            else fluidTotals.remove(f);
            version++;
        }
        return taken;
    }

    // ------------------------------------------------------------------ autocrafting

    public Map<PatternData, List<PatternHolder>> patterns() {
        if (patternsDirty) {
            patternsDirty = false;
            patterns.clear();
            for (NodeBlockEntity n : nodes) {
                if (n instanceof PatternHolder h) {
                    for (PatternData p : h.patterns()) patterns.computeIfAbsent(p, k -> new ArrayList<>()).add(h);
                }
            }
        }
        return patterns;
    }

    public boolean craftable(ItemResource r) {
        for (PatternData p : patterns().keySet()) {
            if (p.produces(r) > 0) return true;
        }
        return false;
    }

    public Set<ItemResource> craftables() {
        Set<ItemResource> set = new HashSet<>();
        for (PatternData p : patterns().keySet()) set.addAll(p.outputMap().keySet());
        return set;
    }

    public List<CraftingJob> jobs() {
        List<CraftingJob> list = new ArrayList<>();
        for (NodeBlockEntity n : nodes) {
            if (n instanceof com.powerfactor.storage.craft.CraftingProcessorBlockEntity cpu) list.addAll(cpu.jobs());
        }
        return list;
    }

    public List<com.powerfactor.storage.craft.CraftingProcessorBlockEntity> processors() {
        List<com.powerfactor.storage.craft.CraftingProcessorBlockEntity> list = new ArrayList<>();
        for (NodeBlockEntity n : nodes) {
            if (n instanceof com.powerfactor.storage.craft.CraftingProcessorBlockEntity cpu) list.add(cpu);
        }
        return list;
    }
}
