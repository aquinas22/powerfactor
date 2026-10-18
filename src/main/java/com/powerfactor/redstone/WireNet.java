package com.powerfactor.redstone;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Solves a connected run of wire at once: the whole run is on if anything outside it powers any of its wire,
 * at full strength end to end. Bundled cable splits into sixteen channels, each joining the insulated wire of
 * its colour. While sampling inputs every wire of ours reads as off, so a run can't hold itself on through the
 * blocks it powers; vanilla dust powered by a run is never read back as an input for the same reason.
 */
public final class WireNet {
    private static int suppress;
    private static boolean solving;
    private static final ArrayDeque<BlockPos> PENDING = new ArrayDeque<>();

    static boolean suppressed() {
        return suppress > 0;
    }

    static boolean solving() {
        return solving;
    }

    /** A wire (channel -1) or one channel of a bundled cable. */
    private record Node(BlockPos pos, int channel) {}

    public static void update(ServerLevel level, BlockPos start) {
        if (solving) {
            PENDING.add(start.immutable());
            return;
        }
        solving = true;
        try {
            Set<Node> done = new HashSet<>();
            PENDING.add(start.immutable());
            int guard = 0;
            while (!PENDING.isEmpty() && guard++ < 4096) {
                BlockPos p = PENDING.poll();
                BlockState s = level.getBlockState(p);
                if (!(s.getBlock() instanceof WireBlock w)) continue;
                if (w.kind() == WireBlock.Kind.BUNDLED) {
                    for (int c = 0; c < 16; c++) {
                        Node n = new Node(p, c);
                        if (!done.contains(n)) solve(level, collect(level, n, done));
                    }
                } else {
                    Node n = new Node(p, -1);
                    if (!done.contains(n)) solve(level, collect(level, n, done));
                }
            }
        } finally {
            solving = false;
            PENDING.clear();
        }
    }

    private static List<Node> collect(ServerLevel level, Node start, Set<Node> done) {
        List<Node> out = new ArrayList<>();
        ArrayDeque<Node> queue = new ArrayDeque<>();
        queue.add(start);
        done.add(start);
        while (!queue.isEmpty() && out.size() < 8192) {
            Node n = queue.poll();
            out.add(n);
            BlockState s = level.getBlockState(n.pos());
            if (!(s.getBlock() instanceof WireBlock w)) continue;
            for (BlockPos l : w.links(level, n.pos(), s)) {
                if (!(level.getBlockState(l).getBlock() instanceof WireBlock o)) continue;
                Node next;
                if (n.channel() < 0) {
                    next = o.kind() == WireBlock.Kind.BUNDLED ? (w.kind() == WireBlock.Kind.INSULATED ? new Node(l, w.color()) : null) : new Node(l, -1);
                } else {
                    next = o.kind() == WireBlock.Kind.BUNDLED ? new Node(l, n.channel())
                            : o.kind() == WireBlock.Kind.INSULATED && o.color() == n.channel() ? new Node(l, -1) : null;
                }
                if (next != null && done.add(next)) queue.add(next);
            }
        }
        return out;
    }

    private static void solve(ServerLevel level, List<Node> nodes) {
        boolean on = false;
        suppress++;
        try {
            outer:
            for (Node n : nodes) {
                if (n.channel() >= 0) continue;
                for (Direction d : Direction.values()) {
                    BlockPos at = n.pos().relative(d);
                    BlockState s = level.getBlockState(at);
                    if (s.getBlock() instanceof RedStoneWireBlock) continue;
                    if (level.getSignal(at, d) > 0) {
                        on = true;
                        break outer;
                    }
                }
            }
        } finally {
            suppress--;
        }
        List<BlockPos> changed = new ArrayList<>();
        for (Node n : nodes) {
            if (n.channel() >= 0) continue;
            BlockState s = level.getBlockState(n.pos());
            if (s.hasProperty(WireBlock.POWERED) && s.getValue(WireBlock.POWERED) != on) {
                level.setBlock(n.pos(), s.setValue(WireBlock.POWERED, on), Block.UPDATE_CLIENTS);
                changed.add(n.pos());
            }
        }
        for (BlockPos p : changed) {
            Block b = level.getBlockState(p).getBlock();
            level.updateNeighborsAt(p, b);
            level.updateNeighborsAt(p.below(), b);
            for (Direction d : Direction.Plane.HORIZONTAL) level.updateNeighborsAt(p.relative(d), b);
        }
    }

    private WireNet() {}
}
