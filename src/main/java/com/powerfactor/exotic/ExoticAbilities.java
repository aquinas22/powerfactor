package com.powerfactor.exotic;

import com.powerfactor.PowerFactor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * Exotic tool abilities. Area and vein breaking go through the player's own block-breaking so
 * protection, drops, experience and durability all behave as if each block were mined by hand.
 */
@EventBusSubscriber(modid = PowerFactor.MODID)
public final class ExoticAbilities {
    public static final int VEIN_LIMIT = 64;
    /** Set while we break the extra blocks, so they don't trigger more area breaks. */
    private static boolean breaking;

    static void onMined(ExoticTool tool, ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity owner) {
        if (breaking || !(owner instanceof ServerPlayer player) || player.isShiftKeyDown() || !(level instanceof ServerLevel server)) return;
        ExoticMaterial m = tool.material();
        List<BlockPos> extra = new ArrayList<>();
        boolean paxel = tool.kind() == ExoticTool.Kind.PAXEL;
        if (m.veinMines() && ((tool.kind() == ExoticTool.Kind.PICKAXE || paxel) && state.is(Tags.Blocks.ORES)
                || (tool.kind() == ExoticTool.Kind.AXE || paxel) && state.is(BlockTags.LOGS))) {
            extra.addAll(vein(server, pos, state));
        } else if (m.excavates() && (tool.kind() == ExoticTool.Kind.PICKAXE || tool.kind() == ExoticTool.Kind.SHOVEL || paxel)) {
            extra.addAll(plane(player, pos, m == ExoticMaterial.QUANTUM && !state.is(Tags.Blocks.ORES) ? 2 : 1));
        }
        if (extra.isEmpty()) return;
        breaking = true;
        try {
            for (BlockPos p : extra) {
                BlockState s = server.getBlockState(p);
                if (s.isAir() || s.getDestroySpeed(server, p) < 0 || !stack.isCorrectToolForDrops(s) && !s.is(state.getBlock())) continue;
                // Area breaks stay roughly as hard as the block you broke: no bedrock-adjacent shortcuts.
                if (s.getDestroySpeed(server, p) > state.getDestroySpeed(server, pos) * 3 + 1) continue;
                if (player.gameMode.destroyBlock(p) && m.tier >= 3) {
                    server.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX() + .5, p.getY() + .5, p.getZ() + .5, 4, .3, .3, .3, .02);
                }
            }
        } finally {
            breaking = false;
        }
    }

    /** The square of blocks around {@code pos} facing the player's view axis. */
    private static List<BlockPos> plane(Player player, BlockPos pos, int radius) {
        Vec3 look = player.getViewVector(1);
        Direction.Axis axis = Math.abs(look.y) > 0.75 ? Direction.Axis.Y : Math.abs(look.x) > Math.abs(look.z) ? Direction.Axis.X : Direction.Axis.Z;
        List<BlockPos> list = new ArrayList<>();
        for (int a = -radius; a <= radius; a++) {
            for (int b = -radius; b <= radius; b++) {
                if (a == 0 && b == 0) continue;
                list.add(switch (axis) {
                    case X -> pos.offset(0, a, b);
                    case Y -> pos.offset(a, 0, b);
                    case Z -> pos.offset(a, b, 0);
                });
            }
        }
        return list;
    }

    /** Connected blocks of the same kind, nearest first, up to {@link #VEIN_LIMIT}. */
    private static List<BlockPos> vein(ServerLevel level, BlockPos start, BlockState state) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty() && out.size() < VEIN_LIMIT) {
            BlockPos p = queue.poll();
            for (BlockPos n : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
                BlockPos q = n.immutable();
                if (seen.contains(q) || !level.isLoaded(q)) continue;
                seen.add(q);
                if (level.getBlockState(q).is(state.getBlock())) {
                    out.add(q);
                    queue.add(q);
                }
            }
        }
        return out;
    }

    static void onHit(ExoticTool tool, ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(target.level() instanceof ServerLevel level)) return;
        ExoticMaterial m = tool.material();
        Vec3 at = target.position().add(0, target.getBbHeight() * 0.5, 0);
        switch (m) {
            case ADAMANTIUM -> {
                // Crushing weight: heavy knockback and a staggering slow.
                Vec3 push = target.position().subtract(attacker.position()).normalize();
                target.push(push.x * 0.9, 0.35, push.z * 0.9);
                target.hurtMarked = true;
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
                level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 12, .3, .3, .3, .3);
            }
            case FARADAENIUM -> arc(level, attacker, target, 3, 5F, 0xFF3FF2E0);
            case CORTANIUM -> {
                target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1));
                level.sendParticles(new DustParticleOptions(0xE0303C, 1.2F), at.x, at.y, at.z, 18, .35, .4, .35, 0);
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y, at.z, 4, .2, .2, .2, .1);
            }
            case QUANTUM -> {
                // A rift tears outward: everything near the target takes half the blow, then the target is lifted.
                arc(level, attacker, target, 5, 6F, 0xFFB18CFF);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(3),
                        e -> e != attacker && e != target && e.isAlive() && !(e instanceof Player p && p.isCreative()))) {
                    e.hurtServer(level, attacker.damageSources().magic(), 6F);
                }
                target.push(0, 0.45, 0);
                target.hurtMarked = true;
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 40, .5, .6, .5, .15);
                level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 10, .2, .2, .2, .2);
                level.playSound(null, target.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 1.6F);
            }
        }
    }

    /** Lightning leaps from the target to up to {@code hops} more creatures. */
    static void arc(ServerLevel level, LivingEntity attacker, LivingEntity first, int hops, float damage, int color) {
        List<LivingEntity> struck = new ArrayList<>();
        struck.add(first);
        LivingEntity from = first;
        for (int i = 0; i < hops; i++) {
            LivingEntity next = null;
            double best = 36;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, from.getBoundingBox().inflate(6),
                    e -> e.isAlive() && e != attacker && !struck.contains(e) && !(e instanceof Player))) {
                double d = e.distanceToSqr(from);
                if (d < best) {
                    best = d;
                    next = e;
                }
            }
            if (next == null) break;
            bolt(level, from.position().add(0, from.getBbHeight() * .6, 0), next.position().add(0, next.getBbHeight() * .6, 0), color);
            next.hurtServer(level, attacker.damageSources().lightningBolt(), damage);
            struck.add(next);
            from = next;
            damage *= 0.8F;
        }
        if (struck.size() > 1) level.playSound(null, first.blockPosition(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.35F, 1.8F);
        Vec3 at = first.position().add(0, first.getBbHeight() * .6, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 16, .3, .4, .3, .4);
    }

    static void bolt(ServerLevel level, Vec3 a, Vec3 b, int color) {
        DustParticleOptions dust = new DustParticleOptions(color & 0xFFFFFF, 0.8F);
        Vec3 d = b.subtract(a);
        int steps = Math.max(4, (int) (d.length() * 5));
        var r = level.getRandom();
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps, j = i == 0 || i == steps ? 0 : 0.25;
            Vec3 p = a.add(d.scale(t)).add((r.nextDouble() - .5) * j, (r.nextDouble() - .5) * j, (r.nextDouble() - .5) * j);
            level.sendParticles(dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            if (r.nextFloat() < 0.3F) level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0, 0, 0, 0.05);
        }
    }

    /** Magnetic tools: drops go straight into the miner's inventory (what doesn't fit stays on the ground). */
    @SubscribeEvent
    static void onDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)) return;
        boolean magnetic = event.getTool().getItem() instanceof ExoticTool tool && tool.material().magnetic()
                || event.getTool().getItem() instanceof com.powerfactor.weapon.QuantumDisassemblerItem;
        if (!magnetic) return;
        for (ItemEntity drop : List.copyOf(event.getDrops())) {
            ItemStack s = drop.getItem();
            player.getInventory().add(s);
            if (s.isEmpty()) event.getDrops().remove(drop);
        }
        int xp = event.getDroppedExperience();
        if (xp > 0) {
            player.giveExperiencePoints(xp);
            event.setDroppedExperience(0);
        }
    }

    private ExoticAbilities() {}
}
