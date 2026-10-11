package com.powerfactor.item;

import com.powerfactor.registry.ModComponents;
import com.powerfactor.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Tesla arc gun. Dumps its capacitor into the first creature in the line of fire, then the arc leaps
 * to the next nearest creature up to four times, weakening each hop. Runs on FE.
 */
public class ArcGunItem extends Item {
    public static final int CAPACITY = 60_000, COST = 600, COOLDOWN = 12;
    private static final double RANGE = 24, HOP_RANGE = 7;
    private static final int HOPS = 4;
    private static final float DAMAGE = 10F, FALLOFF = 0.75F;

    public ArcGunItem(Properties properties) {
        super(properties);
    }

    public static int capacity(ItemStack stack) {
        return ToolModules.capacity(stack, CAPACITY);
    }

    public static int cost(ItemStack stack) {
        return ToolModules.cost(stack, COST);
    }

    public static int energy(ItemStack stack) {
        return stack.getOrDefault(ModComponents.ENERGY.get(), 0);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean creative = player.getAbilities().instabuild;
        if (!creative && energy(stack) < cost(stack)) {
            level.playSound(player, player.blockPosition(), net.minecraft.sounds.SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6F, 1.6F);
            return InteractionResult.FAIL;
        }
        player.getCooldowns().addCooldown(stack, COOLDOWN);
        if (level instanceof ServerLevel server) {
            if (!creative) {
                stack.set(ModComponents.ENERGY.get(), energy(stack) - cost(stack));
            }
            fire(server, player, stack);
        }
        return InteractionResult.SUCCESS;
    }

    private static void fire(ServerLevel level, Player player, ItemStack stack) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1F);
        Vec3 end = eye.add(look.scale(RANGE));
        HitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 reach = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, reach, new AABB(eye, reach).inflate(1.5),
                e -> e instanceof LivingEntity && e.isAlive() && e != player, eye.distanceToSqr(reach));

        Vec3 muzzle = eye.add(look.scale(0.6)).add(0, -0.15, 0);
        level.playSound(null, player.blockPosition(), ModSounds.ARC_GUN_FIRE.get(), SoundSource.PLAYERS, 1.2F, 0.9F + level.getRandom().nextFloat() * 0.2F);
        if (hit == null) {
            bolt(level, muzzle, reach);
            return;
        }
        List<Entity> struck = new ArrayList<>();
        LivingEntity target = (LivingEntity) hit.getEntity();
        Vec3 from = muzzle;
        float damage = DAMAGE + ToolModules.overcharge(stack, 4F);
        int hops = HOPS + 2 * ToolModules.count(stack, DrillModule.Type.CHAIN);
        for (int hop = 0; hop <= hops && target != null; hop++) {
            Vec3 to = target.position().add(0, target.getBbHeight() * 0.6, 0);
            bolt(level, from, to);
            target.hurtServer(level, player.damageSources().playerAttack(player), damage);
            target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 30, 2));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 20, 0.3, 0.4, 0.3, 0.4);
            struck.add(target);
            from = to;
            damage *= FALLOFF;
            target = nearest(level, to, struck, player);
        }
    }

    private static LivingEntity nearest(ServerLevel level, Vec3 at, List<Entity> exclude, Player player) {
        LivingEntity best = null;
        double bestDist = HOP_RANGE * HOP_RANGE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(HOP_RANGE),
                e -> e.isAlive() && e != player && !exclude.contains(e))) {
            double d = e.position().distanceToSqr(at);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    /** A jagged bolt of sparks and blue-white dust between two points. */
    private static void bolt(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        int steps = Math.max(4, (int) (delta.length() * 4));
        Vec3 prev = from;
        var rand = level.getRandom();
        DustParticleOptions core = new DustParticleOptions(0xA8D8FF, 0.9F);
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            double jitter = i == steps ? 0 : 0.35 * Mth.sin((float) (t * Math.PI));
            Vec3 p = from.add(delta.scale(t)).add((rand.nextDouble() - 0.5) * jitter, (rand.nextDouble() - 0.5) * jitter,
                    (rand.nextDouble() - 0.5) * jitter);
            for (int k = 0; k < 3; k++) {
                Vec3 q = prev.lerp(p, k / 3.0);
                level.sendParticles(core, q.x, q.y, q.z, 1, 0, 0, 0, 0);
            }
            if (rand.nextFloat() < 0.4F) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.05);
            }
            prev = p;
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13F * Mth.clamp((float) energy(stack) / capacity(stack), 0, 1));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x6FC8FF;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.powerfactor.energy", String.format("%,d", energy(stack)), String.format("%,d", capacity(stack)))
                .withColor(0xFF5A4A));
        builder.accept(Component.translatable("tooltip.powerfactor.arc_gun", COST, HOPS).withStyle(ChatFormatting.GRAY));
    }
}
