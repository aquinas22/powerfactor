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

    /** The grid this wire belongs to, building it if needed; null if there's no wire here. */
    public @Nullable PowerGrid gridAt(BlockPos pos) {
        PowerGrid grid = byPos.get(pos);
        if (grid != null) {
            return grid;
        }
        if (!level.isLoaded(pos) || !(level.getBlockState(pos).getBlock() instanceof WireBlock wire)) {
            return null;
        }
        return build(pos.immutable(), wire);
    }

    public void invalidate(BlockPos pos) {
        drop(byPos.get(pos));
        for (Direction d : Direction.values()) {
            drop(byPos.get(pos.relative(d)));
        }
    }

    void onChunkUnloaded(ChunkPos chunk) {
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

    private PowerGrid build(BlockPos start, WireBlock type) {
        Set<BlockPos> wires = new HashSet<>();
        List<PowerGrid.Endpoint> endpoints = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        wires.add(start);
        queue.add(start);
        while (!queue.isEmpty() && wires.size() < MAX_WIRES) {
            BlockPos pos = queue.poll();
            BlockState state = level.getBlockState(pos);
            for (Direction d : Direction.values()) {
                if (!state.getValue(ConnectorBlock.CONNECTIONS.get(d))) {
                    continue;
                }
                BlockPos next = pos.relative(d);
                if (!level.isLoaded(next)) {
                    continue;
                }
                if (level.getBlockState(next).getBlock() == type) {
                    if (wires.add(next)) {
                        queue.add(next);
                    }
                } else {
                    endpoints.add(new PowerGrid.Endpoint(next, d.getOpposite()));
                }
            }
        }
        PowerGrid grid = new PowerGrid(wires, endpoints, type.ratingCe());
        for (BlockPos p : wires) {
            drop(byPos.get(p));
            byPos.put(p, grid);
        }
        grids.add(grid);
        return grid;
    }
}
