package com.powerfactor.item;

import com.powerfactor.registry.ModSounds;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Fells a whole tree from one chainsaw cut. Finds the logs connected to the cut (only if they carry
 * natural leaves, so log cabins are safe), then brings them down over a few ticks in a sweep that
 * rises from the cut, paying FE per log. Afterwards the tree's orphaned leaves drop quickly instead
 * of decaying over minutes.
 */
public final class TreeFeller {
    public static final int MAX_LOGS = 256, REACH = 9, LOGS_PER_TICK = 5, LEAVES_PER_TICK = 24, SETTLE_TICKS = 120;

    private record Job(ServerPlayer player, ItemStack tool, Deque<BlockPos> logs, Set<BlockPos> felled, int[] settle,
                       Deque<BlockPos> leaves) {}

    private static final Map<ServerLevel, List<Job>> JOBS = new WeakHashMap<>();

    /** The logs that make up the tree whose log at {@code start} was just cut, or an empty list if it isn't a tree. */
    public static List<BlockPos> findTree(ServerLevel level, BlockPos start) {
        List<BlockPos> logs = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        int naturalLeaves = 0;
        while (!queue.isEmpty() && logs.size() < MAX_LOGS) {
            BlockPos p = queue.poll();
            logs.add(p);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = p.offset(dx, dy, dz);
                        if (Math.abs(n.getX() - start.getX()) > REACH || Math.abs(n.getZ() - start.getZ()) > REACH || !seen.add(n)) {
                            continue;
                        }
                        BlockState s = level.getBlockState(n);
                        if (s.is(BlockTags.LOGS)) {
                            queue.add(n);
                        } else if (s.getBlock() instanceof LeavesBlock && !s.getValue(LeavesBlock.PERSISTENT)) {
                            naturalLeaves++;
                        }
                    }
                }
            }
        }
        return naturalLeaves >= 4 ? logs : List.of();
    }

    /** Start felling: {@code cut} was already broken by the player. */
    public static void fell(ServerLevel level, ServerPlayer player, ItemStack tool, BlockPos cut, List<BlockPos> logs) {
        List<BlockPos> rest = new ArrayList<>(logs);
        rest.remove(cut);
        if (rest.isEmpty()) {
            return;
        }
        rest.sort(Comparator.<BlockPos>comparingInt(p -> p.getY()).thenComparingDouble(p -> p.distSqr(cut)));
        Set<BlockPos> felled = new HashSet<>();
        felled.add(cut);
        JOBS.computeIfAbsent(level, l -> new ArrayList<>()).add(new Job(player, tool, new ArrayDeque<>(rest), felled, new int[] {0},
                new ArrayDeque<>()));
        level.playSound(null, cut, ModSounds.TREE_FALL.get(), SoundSource.BLOCKS, 1.4F, 0.95F + level.getRandom().nextFloat() * 0.1F);
    }

    public static void tick(ServerLevel level) {
        List<Job> jobs = JOBS.get(level);
        if (jobs == null || jobs.isEmpty()) {
            return;
        }
        jobs.removeIf(job -> step(level, job));
    }

    /** Returns true when the job is finished. */
    private static boolean step(ServerLevel level, Job job) {
        if (!job.logs().isEmpty()) {
            for (int i = 0; i < LOGS_PER_TICK && !job.logs().isEmpty(); i++) {
                BlockPos p = job.logs().poll();
                if (!level.getBlockState(p).is(BlockTags.LOGS)) {
                    continue;
                }
                if (!ChainsawItem.powered(job.tool()) && !job.player().getAbilities().instabuild) {
                    job.logs().clear();
                    break;
                }
                PoweredItem.drain(job.tool(), ChainsawItem.perLog(job.tool()), job.player());
                level.destroyBlock(p, true, job.player());
                job.felled().add(p);
            }
            if (job.logs().isEmpty()) {
                job.leaves().addAll(orphanedLeafCandidates(level, job.felled()));
            }
            return false;
        }
        // Leaf distances update a block per tick, so a big canopy takes a while to learn its logs are
        // gone. Keep cycling through the canopy, dropping leaves as they become orphaned, for a few seconds.
        int waited = job.settle()[0]++;
        if (waited < 6) {
            return false;
        }
        for (int i = 0; i < LEAVES_PER_TICK && !job.leaves().isEmpty(); i++) {
            BlockPos p = job.leaves().poll();
            BlockState s = level.getBlockState(p);
            if (!(s.getBlock() instanceof LeavesBlock) || s.getValue(LeavesBlock.PERSISTENT)) {
                continue;
            }
            if (s.getValue(LeavesBlock.DISTANCE) >= LeavesBlock.DECAY_DISTANCE) {
                Block.dropResources(s, level, p);
                level.removeBlock(p, false);
                if (level.getRandom().nextInt(3) == 0) {
                    level.levelEvent(2001, p, Block.getId(s));
                }
            } else if (waited < SETTLE_TICKS) {
                job.leaves().add(p);   // still held (or not updated yet): look again later
            }
        }
        if (waited >= SETTLE_TICKS) {
            return true;
        }
        return job.leaves().isEmpty();
    }

    private static List<BlockPos> orphanedLeafCandidates(ServerLevel level, Set<BlockPos> felled) {
        Set<BlockPos> out = new HashSet<>();
        for (BlockPos log : felled) {
            for (BlockPos p : BlockPos.betweenClosed(log.offset(-4, -1, -4), log.offset(4, 4, 4))) {
                BlockState s = level.getBlockState(p);
                if (s.getBlock() instanceof LeavesBlock && !s.getValue(LeavesBlock.PERSISTENT)) {
                    out.add(p.immutable());
                }
            }
        }
        List<BlockPos> sorted = new ArrayList<>(out);
        sorted.sort(Comparator.comparingInt(p -> -p.getY()));
        return sorted;
    }

    public static boolean busy(@Nullable ServerLevel level) {
        return level != null && JOBS.containsKey(level) && !JOBS.get(level).isEmpty();
    }

    private TreeFeller() {}
}
