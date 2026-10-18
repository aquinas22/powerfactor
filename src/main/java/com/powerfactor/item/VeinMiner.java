package com.powerfactor.item;

import com.powerfactor.PowerFactor;
import com.powerfactor.registry.ModComponents;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Vein mining. A tool fitted with a Vein Mining Matrix, swung while the vein key is held, takes the whole
 * connected vein: every touching block (diagonals too) of the same kind, or of the same ore under a
 * different stone, up to 64 within 16 blocks. The vein comes away as a wave rippling out from the first
 * block, a few blocks a tick, each one spending the tool's durability (or charge) as if mined by hand,
 * plus hunger. The drops and experience are gathered back to where the vein was struck.
 */
public final class VeinMiner {
    public static final int MAX_BLOCKS = 64, REACH = 16, PER_TICK = 4;
    public static final float HUNGER = 0.1F;

    /** Players holding the vein key (told by their clients). */
    private static final Set<UUID> HOLDING = ConcurrentHashMap.newKeySet();
    /** Players whose vein is being taken, so the blocks it breaks don't start veins of their own. */
    private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();

    private record Job(ServerPlayer player, ItemStack tool, Block kind, BlockPos origin, Deque<BlockPos> blocks, List<BlockPos> done) {}

    private static final Map<ServerLevel, List<Job>> JOBS = new WeakHashMap<>();

    public static boolean fitted(ItemStack tool) {
        return tool.has(ModComponents.VEIN_MINER.get());
    }

    /** Any tool that digs can take the matrix. */
    public static boolean fits(ItemStack tool) {
        return !tool.isEmpty() && tool.has(net.minecraft.core.component.DataComponents.TOOL) && !fitted(tool);
    }

    public static void setHolding(UUID player, boolean holding) {
        if (holding) HOLDING.add(player);
        else HOLDING.remove(player);
    }

    public static void forget(UUID player) {
        HOLDING.remove(player);
        ACTIVE.remove(player);
    }

    /** Whether {@code other} belongs to the same vein as {@code origin}: the same block, or the same ore (c:ores/x) in other stone. */
    public static boolean sameVein(BlockState origin, BlockState other) {
        if (other.isAir()) return false;
        if (other.is(origin.getBlock())) return true;
        return origin.getBlock().builtInRegistryHolder().tags().anyMatch(t -> isOreTag(t) && other.is(t));
    }

    private static boolean isOreTag(TagKey<Block> t) {
        return t.location().getNamespace().equals("c") && t.location().getPath().startsWith("ores/");
    }

    /** The connected vein from {@code start}, nearest first, not counting {@code start} itself. */
    public static List<BlockPos> find(BlockGetter level, BlockPos start, BlockState origin) {
        List<BlockPos> vein = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty() && vein.size() < MAX_BLOCKS) {
            BlockPos p = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = p.offset(dx, dy, dz);
                        if (n.distManhattan(start) > REACH * 2 || Math.abs(n.getX() - start.getX()) > REACH || Math.abs(n.getY() - start.getY()) > REACH
                                || Math.abs(n.getZ() - start.getZ()) > REACH || !seen.add(n)) continue;
                        if (!sameVein(origin, level.getBlockState(n))) continue;
                        if (vein.size() >= MAX_BLOCKS) break;
                        vein.add(n.immutable());
                        queue.add(n.immutable());
                    }
                }
            }
        }
        return vein;
    }

    /** Called as a player breaks a block by hand: starts the vein if they're holding the key with a fitted tool. */
    public static void onBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        if (!HOLDING.contains(player.getUUID()) || ACTIVE.contains(player.getUUID())) return;
        ItemStack tool = player.getMainHandItem();
        if (!fitted(tool) || state.getDestroySpeed(level, pos) < 0) return;
        List<BlockPos> vein = find(level, pos, state);
        if (vein.isEmpty()) return;
        JOBS.computeIfAbsent(level, l -> new ArrayList<>()).add(new Job(player, tool, state.getBlock(), pos.immutable(), new ArrayDeque<>(vein), new ArrayList<>()));
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5F, 1.8F);
    }

    public static void tick(ServerLevel level) {
        List<Job> jobs = JOBS.get(level);
        if (jobs == null || jobs.isEmpty()) return;
        for (var it = jobs.iterator(); it.hasNext(); ) {
            Job job = it.next();
            if (step(level, job)) {
                gather(level, job);
                it.remove();
            }
        }
    }

    /** Takes the next few blocks of the vein; true once it's finished (or has to stop). */
    private static boolean step(ServerLevel level, Job job) {
        ServerPlayer player = job.player();
        if (player.isRemoved() || player.level() != level || player.getMainHandItem() != job.tool() || !fitted(job.tool())) return true;
        ACTIVE.add(player.getUUID());
        try {
            for (int n = 0; n < PER_TICK && !job.blocks().isEmpty(); n++) {
                ItemStack tool = job.tool();
                // Stop a block short of breaking the tool, and when the miner is starving.
                if (tool.isDamageableItem() && tool.getDamageValue() >= tool.getMaxDamage() - 1) return true;
                if (!player.getAbilities().instabuild && player.getFoodData().getFoodLevel() <= 0) return true;
                BlockPos p = job.blocks().poll();
                if (!sameVein(job.kind().defaultBlockState(), level.getBlockState(p)) || p.distSqr(player.blockPosition()) > 32 * 32) continue;
                if (!player.gameMode.destroyBlock(p)) continue;
                job.done().add(p);
                player.causeFoodExhaustion(HUNGER);
                int k = job.done().size();
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.08);
                level.sendParticles(ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 2, 0.2, 0.2, 0.2, 0.02);
                if (k % 2 == 0) level.playSound(null, p, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 0.8F + Math.min(1.2F, k * 0.03F));
            }
        } finally {
            ACTIVE.remove(player.getUUID());
        }
        return job.blocks().isEmpty();
    }

    /** Brings the vein's drops and experience back to where it was struck. */
    private static void gather(ServerLevel level, Job job) {
        if (job.done().isEmpty()) return;
        AABB box = new AABB(job.origin());
        for (BlockPos p : job.done()) box = box.minmax(new AABB(p));
        box = box.inflate(1.5);
        double x = job.origin().getX() + 0.5, y = job.origin().getY() + 0.3, z = job.origin().getZ() + 0.5;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && e.getAge() < 200)) {
            item.setPos(x, y, z);
            item.setDeltaMovement(0, 0.1, 0);
        }
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, box, ExperienceOrb::isAlive)) orb.setPos(x, y, z);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + 0.3, z, 30, 0.2, 0.2, 0.2, 0.08);
        level.playSound(null, job.origin(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.8F, 1.4F);
    }

    /** Client to server: the vein key went down or up. */
    public record KeyState(boolean held) implements CustomPacketPayload {
        public static final Type<KeyState> TYPE = new Type<>(PowerFactor.id("vein_key"));
        public static final StreamCodec<RegistryFriendlyByteBuf, KeyState> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, KeyState::held, KeyState::new);

        @Override
        public Type<KeyState> type() {
            return TYPE;
        }
    }

    private VeinMiner() {}
}
