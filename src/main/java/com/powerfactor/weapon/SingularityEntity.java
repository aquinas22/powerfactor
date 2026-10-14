package com.powerfactor.weapon;

import com.powerfactor.PowerFactorConfig;
import com.powerfactor.registry.ModQuantum;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A captured singularity. In flight it is a dark mote; on impact (or after two seconds) it opens into
 * a black hole that drags every creature and loose item within 14 blocks into its event horizon for
 * five seconds, then collapses in a blinding detonation. It never eats terrain unless the server
 * config allows super-weapons to break blocks.
 */
public class SingularityEntity extends Projectile {
    public static final int FLIGHT = 40, OPEN = 100, COLLAPSE = 12;
    public static final double PULL_RADIUS = 14, HORIZON = 2.2;
    private static final EntityDataAccessor<Integer> PHASE_AGE = SynchedEntityData.defineId(SingularityEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> OPENED = SynchedEntityData.defineId(SingularityEntity.class, EntityDataSerializers.BOOLEAN);
    private int age;

    public SingularityEntity(EntityType<? extends SingularityEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public SingularityEntity(Level level, LivingEntity owner, Vec3 velocity) {
        this(ModQuantum.SINGULARITY_ENTITY.get(), level);
        setOwner(owner);
        setPos(owner.getEyePosition().add(owner.getViewVector(1).scale(1.2)).add(0, -0.2, 0));
        setDeltaMovement(velocity);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(PHASE_AGE, 0);
        builder.define(OPENED, false);
    }

    public boolean opened() {
        return entityData.get(OPENED);
    }

    /** Ticks since the hole opened (synced so every client animates the same). */
    public int phaseAge() {
        return entityData.get(PHASE_AGE);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256 * 256;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            clientEffects();
            if (!opened()) setPos(position().add(getDeltaMovement()));
            return;
        }
        age++;
        ServerLevel level = (ServerLevel) level();
        if (!opened()) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS || age >= FLIGHT) {
                if (hit.getType() != HitResult.Type.MISS) setPos(hit.getLocation().subtract(getDeltaMovement().normalize().scale(0.6)));
                open(level);
            } else {
                setPos(position().add(getDeltaMovement()));
            }
            return;
        }
        int t = phaseAge() + 1;
        entityData.set(PHASE_AGE, t);
        if (t <= OPEN) pull(level, t);
        if (t == OPEN) collapse(level);
        if (t >= OPEN + COLLAPSE) discard();
    }

    private void open(ServerLevel level) {
        entityData.set(OPENED, true);
        setDeltaMovement(Vec3.ZERO);
        level.playSound(null, blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.2F, 1.6F);
        level.playSound(null, blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.5F, 0.6F);
    }

    private void pull(ServerLevel level, int t) {
        Vec3 c = position();
        float ramp = Math.min(1, t / 20F);
        List<Entity> caught = level.getEntities(this, new AABB(c, c).inflate(PULL_RADIUS),
                e -> (e instanceof LivingEntity || e instanceof ItemEntity) && e.isAlive() && !(e instanceof Player p && (p.isCreative() || p.isSpectator())));
        for (Entity e : caught) {
            Vec3 to = c.subtract(e.position().add(0, e.getBbHeight() / 2, 0));
            double d = Math.max(0.5, to.length());
            if (d > PULL_RADIUS) continue;
            double strength = ramp * 0.12 * (1.2 - d / PULL_RADIUS) + 0.035;
            e.setDeltaMovement(e.getDeltaMovement().scale(0.88).add(to.normalize().scale(strength * (e == getOwner() ? 0.35 : 1))));
            e.hurtMarked = true;
            e.resetFallDistance();
            if (d < HORIZON && e instanceof LivingEntity living && t % 8 == 0) {
                living.hurtServer(level, damageSources().indirectMagic(this, getOwner()), 7F);
            }
        }
        if (t % 20 == 0) level.playSound(null, blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.5F, 0.5F);
        if (PowerFactorConfig.superweaponsBreakBlocks() && t % 4 == 0) eatBlocks(level, c, 1 + t / 30);
    }

    /** With the server option on, the horizon swallows soft terrain around it. */
    private void eatBlocks(ServerLevel level, Vec3 c, int radius) {
        BlockPos center = BlockPos.containing(c);
        var r = level.getRandom();
        for (int i = 0; i < 6; i++) {
            BlockPos p = center.offset(r.nextInt(radius * 2 + 1) - radius, r.nextInt(radius * 2 + 1) - radius, r.nextInt(radius * 2 + 1) - radius);
            var s = level.getBlockState(p);
            if (!s.isAir() && s.getDestroySpeed(level, p) >= 0 && s.getDestroySpeed(level, p) < 10 && level.getBlockEntity(p) == null
                    && (!(getOwner() instanceof Player owner) || level.mayInteract(owner, p))) {
                level.removeBlock(p, false);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX() + .5, p.getY() + .5, p.getZ() + .5, 4, .2, .2, .2, .1);
            }
        }
    }

    private void collapse(ServerLevel level) {
        Vec3 c = position();
        boolean breaks = PowerFactorConfig.superweaponsBreakBlocks();
        level.explode(this, damageSources().indirectMagic(this, getOwner()), null, c.x, c.y, c.z, 5F, false,
                breaks ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        level.playSound(null, blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3F, 0.7F);
        FxPayload.send(level, new FxPayload(FxPayload.COLLAPSE, c, c, 0xFFB18CFF, 6F, -1));
        level.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 120, 0.2, 0.2, 0.2, 0.9);
    }

    private void clientEffects() {
        Vec3 c = position();
        var r = level().getRandom();
        if (!opened()) {
            level().addParticle(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 0, 0, 0);
            return;
        }
        int t = phaseAge();
        if (t > OPEN) return;
        // Infalling matter: streaks spiral in from all around.
        for (int i = 0; i < 6; i++) {
            double th = r.nextDouble() * Math.PI * 2, ph = (r.nextDouble() - 0.5) * 0.6, d = 4 + r.nextDouble() * 8;
            Vec3 p = c.add(Math.cos(th) * d, Math.sin(ph) * d, Math.sin(th) * d);
            Vec3 v = c.subtract(p).scale(0.09).add(new Vec3(-Math.sin(th), 0, Math.cos(th)).scale(0.25));
            level().addParticle(i % 2 == 0 ? ParticleTypes.PORTAL : ParticleTypes.END_ROD, p.x, p.y, p.z, v.x, v.y, v.z);
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("age", age);
        output.putBoolean("opened", opened());
        output.putInt("phase", phaseAge());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        age = input.getIntOr("age", 0);
        entityData.set(OPENED, input.getBooleanOr("opened", false));
        entityData.set(PHASE_AGE, input.getIntOr("phase", 0));
    }
}
