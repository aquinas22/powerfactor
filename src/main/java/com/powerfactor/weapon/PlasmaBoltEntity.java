package com.powerfactor.weapon;

import com.powerfactor.registry.ModQuantum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** A ball of magnetically-confined plasma: fast, straight, and it bursts on whatever it touches. */
public class PlasmaBoltEntity extends Projectile {
    public static final float DAMAGE = 7F;
    private int life;

    public PlasmaBoltEntity(EntityType<? extends PlasmaBoltEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public PlasmaBoltEntity(Level level, LivingEntity owner, Vec3 velocity) {
        this(ModQuantum.PLASMA_BOLT.get(), level);
        setOwner(owner);
        setPos(owner.getEyePosition().add(owner.getViewVector(1).scale(0.9)).add(0, -0.2, 0));
        setDeltaMovement(velocity);
    }

    /** A turret's bolt: no owner, and it bursts without the concussive splash (turrets fire near people). */
    public PlasmaBoltEntity(Level level, Vec3 from, Vec3 velocity) {
        this(ModQuantum.PLASMA_BOLT.get(), level);
        this.precise = true;
        setPos(from);
        setDeltaMovement(velocity);
    }

    private boolean precise;

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128 * 128;
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 motion = getDeltaMovement();
        if (level().isClientSide()) {
            Vec3 p = position();
            level().addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 0, 0, 0);
            setPos(p.add(motion));
            return;
        }
        if (++life > 60 || !level().hasChunkAt(BlockPos.containing(position().add(motion)))) {
            discard();
            return;
        }
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS) {
            onHit(hit);
            return;
        }
        setPos(position().add(motion));
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        Entity owner = getOwner();
        hit.getEntity().hurtServer((ServerLevel) level(), damageSources().indirectMagic(this, owner), DAMAGE);
        hit.getEntity().igniteForSeconds(3);
        burst(hit.getLocation());
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        burst(hit.getLocation());
    }

    private void burst(Vec3 at) {
        ServerLevel level = (ServerLevel) level();
        // A small concussive pop: hurts creatures, never breaks blocks. Turret bolts skip the splash.
        if (!precise) level.explode(this, at.x, at.y, at.z, 1.2F, false, Level.ExplosionInteraction.NONE);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 14, .2, .2, .2, .4);
        level.sendParticles(ParticleTypes.GLOW, at.x, at.y, at.z, 6, .15, .15, .15, .05);
        level.playSound(null, BlockPos.containing(at), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.7F, 1.6F);
        FxPayload.send(level, new FxPayload(FxPayload.PLASMA_BURST, at, at, 0xFF7CFF9A, 1F, -1));
        discard();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("life", life);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        life = input.getIntOr("life", 0);
    }
}
