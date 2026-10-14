package com.powerfactor.weapon;

import com.powerfactor.item.PoweredItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * Electromagnetic railgun. Hold to charge the rails (zooming in as the capacitors fill), release to
 * fire a hypersonic slug: hitscan to 160 blocks, through every creature in its path and through
 * glass, leaves and ice, leaving an ionised trail. Damage and energy scale with charge.
 */
public class RailgunItem extends PoweredItem {
    public static final int CHARGE_TICKS = 32, MAX_FE = 12_000;
    public static final double RANGE = 160;

    public RailgunItem(Properties properties) {
        super(properties);
    }

    @Override
    public int capacity() {
        return 400_000;
    }

    public static float charge(int usedTicks) {
        return Mth.clamp(usedTicks / (float) CHARGE_TICKS, 0, 1);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.getAbilities().instabuild && energy(stack) < MAX_FE / 4) {
            level.playSound(player, player.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.7F, 1.4F);
            return InteractionResult.FAIL;
        }
        player.startUsingItem(hand);
        level.playSound(player, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F, 1.7F);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72_000;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.SPYGLASS;
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
        if (!(user instanceof Player player) || !(level instanceof ServerLevel server)) return true;
        float charge = charge(getUseDuration(stack, user) - remaining);
        if (charge < 0.2F) return false;
        int cost = Math.round(MAX_FE * charge);
        if (!drain(stack, cost, player)) {
            server.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.7F, 1.2F);
            return false;
        }
        fire(server, player, charge);
        player.getCooldowns().addCooldown(stack, 20);
        return true;
    }

    static void fire(ServerLevel level, Player player, float charge) {
        fire(level, player, player.getEyePosition(), player.getViewVector(1), charge);
    }

    /** Fire from {@code eye} along {@code dir}; separated from aim so tests and turrets can call it. */
    public static void fire(ServerLevel level, Player player, Vec3 eye, Vec3 dir, float charge) {
        dir = dir.normalize();
        Vec3 right = dir.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-6 ? Vec3.ZERO : right.normalize();
        // The barrel sits low and to the right of the eye, where the held model's muzzle is.
        Vec3 muzzle = eye.add(dir.scale(0.8)).add(right.scale(0.3)).add(0, -0.22, 0);
        Vec3 end = eye.add(dir.scale(RANGE));
        // Punch through soft blocks: glass, panes, leaves, ice. Hard blocks stop the slug.
        int punched = 0;
        Vec3 from = eye;
        HitResult hit;
        while (true) {
            hit = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK) break;
            BlockPos pos = ((net.minecraft.world.phys.BlockHitResult) hit).getBlockPos();
            BlockState s = level.getBlockState(pos);
            if (punched < 12 && charge > 0.6F && soft(s) && level.mayInteract(player, pos)) {
                level.destroyBlock(pos, false, player);
                punched++;
                from = hit.getLocation().add(dir.scale(0.05));
                continue;
            }
            end = hit.getLocation();
            break;
        }
        // Every creature along the line takes the hit, nearest first.
        float damage = 14 + 34 * charge;
        AABB sweep = new AABB(eye, end).inflate(1.0);
        List<Entity> hits = new ArrayList<>();
        for (Entity e : level.getEntities(player, sweep, e -> e instanceof LivingEntity && e.isAlive() && e.isPickable())) {
            if (e.getBoundingBox().inflate(0.3).clip(eye, end).isPresent()) hits.add(e);
        }
        hits.sort(Comparator.comparingDouble(e -> e.distanceToSqr(eye)));
        for (Entity e : hits) {
            e.hurtServer(level, player.damageSources().playerAttack(player), damage);
            e.push(dir.x * charge * 1.4, 0.2 * charge, dir.z * charge * 1.4);
            e.hurtMarked = true;
            damage *= 0.85F;
        }
        level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.5F + charge, 1.9F - charge * 0.5F);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 2.0F);
        level.playSound(null, BlockPos.containing(end), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.4F * charge, 1.8F);
        FxPayload.send(level, new FxPayload(FxPayload.RAIL, muzzle, end, 0xFF8FF4FF, 0.5F + charge, player.getId()));
        if (charge > 0.5F) FxPayload.send(level, new FxPayload(FxPayload.SHOCKWAVE, end, end, 0xFF8FF4FF, charge * 2.5F, -1));
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, (int) (20 + 40 * charge), .3, .3, .3, .6);
    }

    private static boolean soft(BlockState s) {
        return s.is(Tags.Blocks.GLASS_BLOCKS) || s.is(Tags.Blocks.GLASS_PANES) || s.is(BlockTags.LEAVES) || s.is(BlockTags.ICE)
                || s.is(Blocks.COBWEB);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        builder.accept(Component.translatable("tooltip.powerfactor.railgun").withStyle(ChatFormatting.AQUA));
        builder.accept(Component.translatable("tooltip.powerfactor.railgun.cost", String.format("%,d", MAX_FE)).withStyle(ChatFormatting.DARK_GRAY));
    }
}
