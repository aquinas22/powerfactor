package com.powerfactor.vehicle;

import com.powerfactor.item.PoweredItem;
import com.powerfactor.item.WrenchItem;
import com.powerfactor.registry.ModComponents;
import com.powerfactor.registry.ModItems;
import com.powerfactor.registry.ModMaterials;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import org.jspecify.annotations.Nullable;

/**
 * Hover-bike: a single-seat repulsor bike that rides a cushion of field about a block above whatever
 * is beneath it, land or water, so it skims rivers and shrugs off bumps. Server-driven like the
 * rover: forward/back, steer, Sprint to boost, Jump to lift over obstacles. Without charge the field
 * collapses and it settles to the ground.
 */
public final class HoverBikeEntity extends Entity {
    public static final int CAPACITY = 1_000_000, DRIVE_FE = 40, BOOST_FE = 120, HOVER_FE = 4;
    public static final float MAX_HEALTH = 60;
    /** Cushion height above the surface, and the extra lift Jump gives. */
    public static final double HOVER = 1.1, LIFT = 2.2;
    private static final EntityDataAccessor<Integer> CHARGE = SynchedEntityData.defineId(HoverBikeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> HEALTH = SynchedEntityData.defineId(HoverBikeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(HoverBikeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> STEER = SynchedEntityData.defineId(HoverBikeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> BOOST = SynchedEntityData.defineId(HoverBikeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FIELD = SynchedEntityData.defineId(HoverBikeEntity.class, EntityDataSerializers.BOOLEAN);
    private final InterpolationHandler interpolation = new InterpolationHandler(this, 2);
    private final SimpleEnergyHandler battery = new SimpleEnergyHandler(CAPACITY, 10_000, 0) {
        @Override
        protected void onEnergyChanged(int before) {
            entityData.set(CHARGE, getAmountAsInt());
        }
    };
    private float throttle, steering, bank, oldBank;
    private boolean brake, boost, lift;
    private int inputAge;

    public HoverBikeEntity(EntityType<? extends HoverBikeEntity> type, Level level) {
        super(type, level);
        blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(CHARGE, 0);
        builder.define(HEALTH, MAX_HEALTH);
        builder.define(SPEED, 0F);
        builder.define(STEER, 0F);
        builder.define(BOOST, false);
        builder.define(FIELD, false);
    }

    public EnergyHandler energyHandler() {
        return battery;
    }

    public int charge() {
        return entityData.get(CHARGE);
    }

    public float health() {
        return entityData.get(HEALTH);
    }

    public float speed() {
        return entityData.get(SPEED);
    }

    public boolean boosting() {
        return entityData.get(BOOST);
    }

    /** Whether the repulsor field is up (powered). */
    public boolean field() {
        return entityData.get(FIELD);
    }

    /** Smoothed lean into turns, degrees. */
    public float bank(float partial) {
        return Mth.lerp(partial, oldBank, bank);
    }

    public @Nullable Player driver() {
        return getFirstPassenger() instanceof Player player ? player : null;
    }

    @Override
    public boolean isClientAuthoritative() {
        return false;
    }

    @Override
    protected boolean isLocalClientAuthoritative() {
        return false;
    }

    @Override
    public InterpolationHandler getInterpolation() {
        return interpolation;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return true;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty() && passenger instanceof Player;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return new Vec3(0, 0.55, -0.1).yRot(-getYRot() * Mth.DEG_TO_RAD);
    }

    public boolean input(Player player, float throttle, float steer, boolean brake, boolean boost, boolean lift) {
        if (driver() != player || !Float.isFinite(throttle) || !Float.isFinite(steer)) return false;
        this.throttle = Math.clamp(throttle, -1, 1);
        steering = Math.clamp(steer, -1, 1);
        this.brake = brake;
        this.boost = boost;
        this.lift = lift;
        inputAge = 0;
        return true;
    }

    /** Distance from the bike's base down to the first solid block or fluid surface (capped). */
    public double clearance() {
        Vec3 from = position().add(0, 0.2, 0), to = from.add(0, -(HOVER + LIFT + 3), 0);
        HitResult hit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        return hit.getType() == HitResult.Type.MISS ? HOVER + LIFT + 3 : from.y - hit.getLocation().y - 0.2;
    }

    @Override
    public void tick() {
        super.tick();
        oldBank = bank;
        bank += (-entityData.get(STEER) * Math.min(1, speed() * 1.5F) * 25 - bank) * 0.2F;
        if (level().isClientSide()) {
            float previousYaw = getYRot();
            interpolation.interpolate();
            if (driver() != null && driver().isLocalPlayer()) driver().setYRot(driver().getYRot() + Mth.wrapDegrees(getYRot() - previousYaw));
            if (field() && tickCount % 2 == 0) {
                // Repulsor wash under the hull; faster bikes kick up more.
                double s = Math.min(1, speed());
                level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX() + (random.nextDouble() - .5) * .8, getY() - 0.1, getZ() + (random.nextDouble() - .5) * .8,
                        0, -0.05, 0);
                if (s > 0.3 && clearance() < HOVER + 0.6) {
                    level().addParticle(ParticleTypes.CLOUD, getX(), getY() - HOVER + 0.1, getZ(), (random.nextDouble() - .5) * .2 * s, 0.02, (random.nextDouble() - .5) * .2 * s);
                }
            }
            return;
        }
        if (driver() == null || ++inputAge > 10) {
            throttle = steering = 0;
            brake = true;
            boost = lift = false;
        }
        boolean creative = driver() != null && driver().getAbilities().instabuild;
        boolean field = creative || battery.getAmountAsInt() >= HOVER_FE;
        int cost = HOVER_FE + (throttle != 0 ? (boost && throttle > 0 ? BOOST_FE : DRIVE_FE) : 0);
        if (field && !creative) battery.set(Math.max(0, battery.getAmountAsInt() - Math.min(cost, battery.getAmountAsInt())));
        boolean driving = field && throttle != 0 && !brake;
        boolean boosting = driving && boost && throttle > 0;
        entityData.set(STEER, steering);
        entityData.set(BOOST, boosting);
        entityData.set(FIELD, field);

        Vec3 v = getDeltaMovement();
        // Steering: a bike turns faster at moderate speed, slower at a standstill or flat out.
        double hs = v.horizontalDistance();
        float turn = (float) (steering * (2.5 + 4 * Math.min(1, hs * 2)) * (hs > 1.2 ? 0.7 : 1));
        if (field) setYRot(getYRot() + turn);
        double yaw = getYRot() * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        double max = boosting ? 1.35 : 0.85, accel = boosting ? 0.09 : 0.05;
        Vec3 horiz = new Vec3(v.x, 0, v.z);
        if (driving) horiz = horiz.add(fwd.scale(throttle * accel));
        // Grip: bleed sideways drift so the bike carves rather than slides.
        double along = horiz.dot(fwd);
        Vec3 side = horiz.subtract(fwd.scale(along));
        horiz = fwd.scale(along).add(side.scale(0.75));
        horiz = horiz.scale(brake ? 0.82 : field ? 0.97 : 0.9);
        if (horiz.length() > max) horiz = horiz.normalize().scale(max);
        double vy = v.y;
        if (field) {
            // Spring-damper toward the cushion height; Jump raises it.
            double target = HOVER + (lift ? LIFT : 0), err = target - clearance();
            vy = vy * 0.75 + Math.clamp(err * 0.25, -0.35, 0.45);
        } else {
            vy = Math.max(-1.5, vy - 0.08);
        }
        setDeltaMovement(horiz.x, vy, horiz.z);
        // Stop at unloaded chunks and the world border, never generating terrain on the move.
        var footprint = getBoundingBox().move(getDeltaMovement());
        boolean loaded = level().hasChunkAt(BlockPos.containing(footprint.getCenter()));
        if (!loaded || !level().getWorldBorder().isWithinBounds(footprint)) setDeltaMovement(0, getDeltaMovement().y, 0);
        Vec3 old = position();
        move(MoverType.SELF, getDeltaMovement());
        Vec3 moved = position().subtract(old);
        entityData.set(SPEED, (float) moved.horizontalDistance());
        // A crash into a wall at speed scrapes the hull.
        if (horizontalCollision && hs > 0.7) hurtServer((ServerLevel) level(), damageSources().flyIntoWall(), (float) (hs * 6));
        float targetPitch = (float) Math.clamp(-Math.toDegrees(Math.atan2(moved.y, Math.max(0.2, moved.horizontalDistance()))), -20, 20);
        setXRot(getXRot() + (targetPitch - getXRot()) * 0.2F);
        resetFallDistance();
        if (driver() != null) {
            driver().resetFallDistance();
            driver().setYBodyRot(getYRot());
        }
    }

    public ItemStack packed() {
        var stack = new ItemStack(ModItems.HOVER_BIKE.get());
        stack.set(ModComponents.ENERGY.get(), charge());
        stack.set(ModComponents.VEHICLE_HEALTH.get(), health());
        if (hasCustomName()) stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, getCustomName());
        return stack;
    }

    public void restore(ItemStack stack) {
        battery.set(Math.clamp(PoweredItem.energy(stack), 0, CAPACITY));
        entityData.set(HEALTH, Math.clamp(stack.getOrDefault(ModComponents.VEHICLE_HEALTH.get(), MAX_HEALTH), 1, MAX_HEALTH));
        setCustomName(stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME));
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        var held = player.getItemInHand(hand);
        if (held.getItem() instanceof WrenchItem && player.isShiftKeyDown()) {
            if (isVehicle() || speed() > 0.1F) return InteractionResult.FAIL;
            if (!level().isClientSide()) {
                var stack = packed();
                if (!player.getInventory().add(stack)) player.drop(stack, false);
                discard();
            }
            return InteractionResult.SUCCESS;
        }
        if (held.is(ModMaterials.INGOTS.get("steel").get()) && health() < MAX_HEALTH) {
            if (!level().isClientSide()) {
                entityData.set(HEALTH, Math.min(MAX_HEALTH, health() + 10));
                held.consume(1, player);
            }
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive() || isVehicle()) return InteractionResult.PASS;
        if (!level().isClientSide() && !player.startRiding(this)) return InteractionResult.FAIL;
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isRemoved() || isInvulnerableToBase(source) || !Float.isFinite(amount) || amount <= 0) return false;
        if (source.getEntity() instanceof Player player && player.getAbilities().instabuild) {
            discard();
            return true;
        }
        entityData.set(HEALTH, Math.max(0, health() - amount));
        markHurt();
        if (health() <= 0) {
            ejectPassengers();
            if (level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.ENTITY_DROPS)) {
                spawnAtLocation(level, new ItemStack(ModMaterials.INGOTS.get("steel").get(), 3));
                spawnAtLocation(level, new ItemStack(com.powerfactor.registry.ModQuantum.FARADAENIUM_INGOT.get()));
            }
            level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 2, 0.3, 0.2, 0.3, 0);
            discard();
        }
        return true;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        battery.serialize(output);
        output.putFloat("hull", health());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        battery.deserialize(input);
        battery.set(Math.min(CAPACITY, battery.getAmountAsInt()));
        entityData.set(CHARGE, battery.getAmountAsInt());
        entityData.set(HEALTH, Math.clamp(input.getFloatOr("hull", MAX_HEALTH), 1, MAX_HEALTH));
    }
}
