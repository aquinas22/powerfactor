package com.powerfactor.movement;

import com.powerfactor.item.GrappleItem;
import com.powerfactor.item.PoweredItem;
import com.powerfactor.item.ToolModules;
import com.powerfactor.registry.ModEntities;
import com.powerfactor.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Fast projectile, persistent anchor and motorized cable. Terrain and moving entities can be anchors. */
public final class GrappleHookEntity extends Projectile {
    private static final EntityDataAccessor<Boolean> ANCHORED = SynchedEntityData.defineId(GrappleHookEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> LENGTH = SynchedEntityData.defineId(GrappleHookEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(GrappleHookEntity.class, EntityDataSerializers.INT);
    private @Nullable BlockPos blockAnchor;
    private Vec3 targetOffset = Vec3.ZERO;
    private int age;
    public GrappleHookEntity(EntityType<? extends GrappleHookEntity> type, Level level) { super(type, level); setNoGravity(true); }
    public GrappleHookEntity(Level level, Player owner) {
        this(ModEntities.GRAPPLE_HOOK.get(), level); setOwner(owner);
        setPos(owner.getEyePosition().add(owner.getViewVector(1).scale(0.35)));
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ANCHORED, false); builder.define(LENGTH, 0F); builder.define(TARGET, -1);
    }
    public boolean anchored() { return entityData.get(ANCHORED); }
    public float ropeLength() { return entityData.get(LENGTH); }
    public int targetId() { return entityData.get(TARGET); }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 128 * 128; }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        if (!(getOwner() instanceof Player owner) || !owner.isAlive() || owner.isPassenger() || owner.level() != level()
                || ++age > 1200 || distanceToSqr(owner) > Math.pow(GrappleItem.RANGE + 8, 2) || GrappleServer.device(owner).isEmpty()) {
            discard(); return;
        }
        if (!anchored()) {
            Vec3 next = position().add(getDeltaMovement());
            if (!level().hasChunkAt(BlockPos.containing(next))) { discard(); return; }
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) attach(block);
            else if (hit instanceof EntityHitResult target && target.getEntity().isAlive()) {
                entityData.set(TARGET, target.getEntity().getId()); targetOffset = target.getLocation().subtract(target.getEntity().position());
                attach(target.getLocation());
            } else setPos(next);
        } else {
            Entity target = targetId() < 0 ? null : level().getEntity(targetId());
            if (targetId() >= 0) {
                if (target == null || !target.isAlive()) { discard(); return; }
                setPos(target.position().add(targetOffset));
            } else if (blockAnchor == null || !level().hasChunkAt(blockAnchor)
                    || level().getBlockState(blockAnchor).getCollisionShape(level(), blockAnchor).isEmpty()) { discard(); return; }
            if (!clearLine(owner)) { discard(); return; }
            Vec3 tether = position().subtract(owner.position().add(0, 0.85, 0));
            boolean reel = !owner.isShiftKeyDown();
            if (reel && tether.length() < 2.0) { discard(); return; }
            var device = GrappleServer.device(owner);
            int cost = ToolModules.cost(device, GrappleItem.REEL_FE);
            if (reel && !owner.getAbilities().instabuild && PoweredItem.energy(device) < cost) { discard(); return; }
            if (reel) PoweredItem.drain(device, cost, owner);
            var velocity = owner.getKnownMovement();
            var motion = MovementPhysics.cable(vector(velocity), vector(tether), ropeLength(), reel, GrappleItem.reelMultiplier(device));
            entityData.set(LENGTH, (float) motion.length());
            owner.setDeltaMovement(vec(motion.velocity())); owner.hurtMarked = true;
            if (reel || motion.taut()) {
                owner.resetFallDistance();
                if (owner instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) serverPlayer.connection.resetFlyingTicks();
            }
        }
    }
    public void attach(BlockHitResult hit) {
        blockAnchor = hit.getBlockPos(); entityData.set(TARGET, -1);
        attach(hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getUnitVec3i()).scale(0.03)));
    }
    private void attach(Vec3 point) {
        setPos(point); setDeltaMovement(Vec3.ZERO); entityData.set(ANCHORED, true);
        if (getOwner() instanceof Player owner) entityData.set(LENGTH, (float) Math.max(2, point.distanceTo(owner.position().add(0, 0.85, 0))));
        level().playSound(null, blockPosition(), ModSounds.GRAPPLE_ATTACH.get(), SoundSource.PLAYERS, 0.65F, 1);
    }
    private boolean clearLine(Player owner) {
        Vec3 start = owner.getEyePosition(), end = position();
        for (int step = 0; step <= 8; step++) if (!level().hasChunkAt(BlockPos.containing(start.lerp(end, step / 8.0)))) return false;
        var hit = level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        return hit.getType() == HitResult.Type.MISS || blockAnchor != null && hit.getBlockPos().equals(blockAnchor);
    }
    public static MovementPhysics.Vector vector(Vec3 v) { return new MovementPhysics.Vector(v.x, v.y, v.z); }
    public static Vec3 vec(MovementPhysics.Vector v) { return new Vec3(v.x(), v.y(), v.z()); }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); output.putBoolean("anchored", anchored()); output.putFloat("rope", ropeLength()); output.putInt("age", age);
        if (blockAnchor != null) output.putLong("anchor", blockAnchor.asLong());
        // Moving anchors deliberately release when the session's entity identity disappears.
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); entityData.set(ANCHORED, input.getBooleanOr("anchored", false));
        entityData.set(LENGTH, Math.clamp(input.getFloatOr("rope", 0), 0, 80)); age = input.getIntOr("age", 0);
        blockAnchor = input.getLong("anchor").map(BlockPos::of).orElse(null);
    }
}
