package com.powerfactor.vehicle;

import com.powerfactor.item.PoweredItem;
import com.powerfactor.item.WrenchItem;
import com.powerfactor.movement.MovementPhysics;
import com.powerfactor.registry.ModComponents;
import com.powerfactor.registry.ModItems;
import com.powerfactor.registry.ModMaterials;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import org.jspecify.annotations.Nullable;

/** Single-seat, server-driven electric rover. Collision movement supplies real one-block hill climbing. */
public final class RoverEntity extends Entity {
    public static final int CAPACITY = 500_000, DRIVE_FE = 20, BOOST_FE = 50;
    public static final float MAX_HEALTH = 80;
    private static final EntityDataAccessor<Integer> CHARGE = SynchedEntityData.defineId(RoverEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> HEALTH = SynchedEntityData.defineId(RoverEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(RoverEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> STEER = SynchedEntityData.defineId(RoverEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> BRAKE = SynchedEntityData.defineId(RoverEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> MOTOR = SynchedEntityData.defineId(RoverEntity.class, EntityDataSerializers.BOOLEAN);
    private final InterpolationHandler interpolation = new InterpolationHandler(this, 2);
    private final SimpleEnergyHandler battery = new SimpleEnergyHandler(CAPACITY, 8_000, 0) {
        @Override protected void onEnergyChanged(int before) { entityData.set(CHARGE, getAmountAsInt()); }
    };
    private float throttle, steering, wheel, oldWheel, suspension, oldSuspension;
    private boolean brake, boost;
    private int inputAge;
    public RoverEntity(EntityType<? extends RoverEntity> type, Level level) { super(type, level); blocksBuilding = true; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(CHARGE, 0); builder.define(HEALTH, MAX_HEALTH); builder.define(SPEED, 0F); builder.define(STEER, 0F);
        builder.define(BRAKE, false); builder.define(MOTOR, false);
    }
    public EnergyHandler energyHandler() { return battery; }
    public int charge() { return entityData.get(CHARGE); }
    public float health() { return entityData.get(HEALTH); }
    public float speed() { return entityData.get(SPEED); }
    public float steering() { return entityData.get(STEER); }
    public boolean braking() { return entityData.get(BRAKE); }
    public boolean motorActive() { return entityData.get(MOTOR); }
    public float wheel(float partial) { return Mth.lerp(partial, oldWheel, wheel); }
    public float suspension(float partial) { return Mth.lerp(partial, oldSuspension, suspension); }
    public @Nullable Player driver() { return getFirstPassenger() instanceof Player player ? player : null; }
    /** Leaving vanilla control ownership unset prevents client vehicle-position packets from driving this entity. */
    @Override public boolean isClientAuthoritative() { return false; }
    @Override protected boolean isLocalClientAuthoritative() { return false; }
    @Override public InterpolationHandler getInterpolation() { return interpolation; }
    @Override public float maxUpStep() { return 1.25F; }
    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean isPushable() { return true; }
    @Override public boolean canBeCollidedWith(@Nullable Entity other) { return true; }
    @Override protected boolean canAddPassenger(Entity passenger) { return getPassengers().isEmpty() && passenger instanceof Player; }
    @Override protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) { return new Vec3(0, 0.8, 0.12).yRot(-getYRot() * Mth.DEG_TO_RAD); }
    public boolean input(Player player, float throttle, float steer, boolean brake, boolean boost) {
        if (driver() != player || !Float.isFinite(throttle) || !Float.isFinite(steer)) return false;
        this.throttle = Math.clamp(throttle, -1, 1); steering = Math.clamp(steer, -1, 1);
        this.brake = brake; this.boost = boost; inputAge = 0; return true;
    }
    @Override public void tick() {
        super.tick(); oldWheel = wheel; oldSuspension = suspension;
        if (level().isClientSide()) {
            Vec3 old = position(); float previousYaw = getYRot(); interpolation.interpolate();
            if (driver() != null && driver().isLocalPlayer()) {
                var rider = driver(); rider.setYRot(rider.getYRot() + Mth.wrapDegrees(getYRot() - previousYaw));
            }
            double yaw = getYRot() * Mth.DEG_TO_RAD;
            Vec3 traveled = position().subtract(old);
            wheel += (float) ((-Math.sin(yaw) * traveled.x + Math.cos(yaw) * traveled.z) / 0.34);
            suspension += ((float) Math.clamp(traveled.y * 0.6, -0.12, 0.12) - suspension) * 0.35F;
            if (Math.abs(wheel) > Math.PI * 200) { wheel -= Math.copySign((float) (Math.PI * 200), wheel); oldWheel -= Math.copySign((float) (Math.PI * 200), oldWheel); }
            return;
        }
        if (driver() == null || ++inputAge > 10) { throttle = steering = 0; brake = true; boost = false; }
        boolean submerged = isInWater() || isInLava();
        boolean powered = throttle != 0 && !brake && !submerged;
        int cost = boost && throttle > 0 ? BOOST_FE : DRIVE_FE;
        powered &= driver() != null && (driver().getAbilities().instabuild || battery.getAmountAsInt() >= cost);
        if (powered && !driver().getAbilities().instabuild) battery.set(battery.getAmountAsInt() - cost);
        entityData.set(STEER, steering); entityData.set(BRAKE, brake || submerged); entityData.set(MOTOR, powered);
        Vec3 old = position();
        var step = MovementPhysics.drive(new MovementPhysics.Vector(getDeltaMovement().x, getDeltaMovement().y, getDeltaMovement().z),
                getYRot(), powered ? throttle : 0, steering, brake || submerged, boost, onGround());
        setYRot((float) step.yaw()); setDeltaMovement(step.velocity().x(), step.velocity().y(), step.velocity().z());
        if (submerged) setDeltaMovement(getDeltaMovement().multiply(0.75, 0.8, 0.75));
        var next = position().add(getDeltaMovement());
        // An unknown chunk is a stop line, never an invitation to generate terrain while driving.
        var footprint = getBoundingBox().move(getDeltaMovement());
        boolean loaded = true;
        for (double x : new double[] {footprint.minX, footprint.maxX}) for (double z : new double[] {footprint.minZ, footprint.maxZ})
            loaded &= level().hasChunkAt(net.minecraft.core.BlockPos.containing(x, next.y, z));
        if (!loaded || !level().getWorldBorder().isWithinBounds(footprint)) {
            setDeltaMovement(0, getDeltaMovement().y, 0);
        }
        move(MoverType.SELF, getDeltaMovement());
        Vec3 moved = position().subtract(old); entityData.set(SPEED, (float) moved.horizontalDistance());
        float targetPitch = (float) Math.clamp(-Math.toDegrees(Math.atan2(moved.y, Math.max(0.15, moved.horizontalDistance()))), -22, 22);
        setXRot(getXRot() + (targetPitch - getXRot()) * 0.18F);
        if (onGround()) resetFallDistance();
        if (driver() != null) { driver().resetFallDistance(); driver().setYBodyRot(getYRot()); }
    }
    public ItemStack packed() {
        var stack = new ItemStack(ModItems.ELECTRIC_ROVER.get()); stack.set(ModComponents.ENERGY.get(), charge());
        stack.set(ModComponents.VEHICLE_HEALTH.get(), health());
        if (hasCustomName()) stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, getCustomName());
        return stack;
    }
    public void restore(ItemStack stack) {
        battery.set(Math.clamp(PoweredItem.energy(stack), 0, CAPACITY));
        entityData.set(HEALTH, Math.clamp(stack.getOrDefault(ModComponents.VEHICLE_HEALTH.get(), MAX_HEALTH), 1, MAX_HEALTH));
        setCustomName(stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME));
    }
    @Override public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        var held = player.getItemInHand(hand);
        if (held.getItem() instanceof WrenchItem && player.isShiftKeyDown()) {
            if (isVehicle() || speed() > 0.1F) return InteractionResult.FAIL;
            if (!level().isClientSide()) { var stack = packed(); if (!player.getInventory().add(stack)) player.drop(stack, false); discard(); }
            return InteractionResult.SUCCESS;
        }
        if (held.is(ModMaterials.INGOTS.get("steel").get()) && health() < MAX_HEALTH) {
            if (!level().isClientSide()) { entityData.set(HEALTH, Math.min(MAX_HEALTH, health() + 10)); held.consume(1, player); }
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive() || isVehicle()) return InteractionResult.PASS;
        if (!level().isClientSide()) {
            if (!player.startRiding(this)) return InteractionResult.FAIL;
            // Native player rotation packets keep free look; the client follows subsequent chassis turns.
        }
        return InteractionResult.SUCCESS;
    }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isRemoved() || isInvulnerableToBase(source) || !Float.isFinite(amount) || amount <= 0) return false;
        if (source.getEntity() instanceof Player player && player.getAbilities().instabuild) { discard(); return true; }
        entityData.set(HEALTH, Math.max(0, health() - amount)); markHurt();
        if (health() <= 0) {
            ejectPassengers();
            if (level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.ENTITY_DROPS)) {
                spawnAtLocation(level, new ItemStack(ModMaterials.INGOTS.get("steel").get(), 4));
                spawnAtLocation(level, new ItemStack(ModItems.RUBBER.get(), 2));
            }
            discard();
        }
        return true;
    }
    @Override protected void addAdditionalSaveData(ValueOutput output) { battery.serialize(output); output.putFloat("hull", health()); }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        battery.deserialize(input); battery.set(Math.min(CAPACITY, battery.getAmountAsInt())); entityData.set(CHARGE, battery.getAmountAsInt());
        entityData.set(HEALTH, Math.clamp(input.getFloatOr("hull", MAX_HEALTH), 1, MAX_HEALTH));
    }
}
