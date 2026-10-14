package com.powerfactor.vehicle;

import com.powerfactor.item.PoweredItem;
import com.powerfactor.item.WrenchItem;
import com.powerfactor.registry.ModComponents;
import com.powerfactor.registry.ModItems;
import com.powerfactor.registry.ModMaterials;
import com.powerfactor.weapon.FxPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

/**
 * Mech Suit: a three-block walker. W/S walks, A/D turns, Sprint runs, Jump fires the jump-jets; a
 * hard landing slams a shockwave into nearby creatures. Server-driven like the rover and hover-bike.
 */
public final class MechEntity extends Entity {
    public static final int CAPACITY = 2_000_000, WALK_FE = 30, RUN_FE = 80, JET_FE = 400;
    public static final float MAX_HEALTH = 200;
    private static final EntityDataAccessor<Integer> CHARGE = SynchedEntityData.defineId(MechEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> HEALTH = SynchedEntityData.defineId(MechEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(MechEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> JETS = SynchedEntityData.defineId(MechEntity.class, EntityDataSerializers.BOOLEAN);
    private final InterpolationHandler interpolation = new InterpolationHandler(this, 2);
    private final SimpleEnergyHandler battery = new SimpleEnergyHandler(CAPACITY, 20_000, 0) {
        @Override
        protected void onEnergyChanged(int before) {
            entityData.set(CHARGE, getAmountAsInt());
        }
    };
    private float throttle, steering, stride, oldStride;
    private boolean run, jet;
    private int inputAge;
    private double fallStart = Double.NaN;

    public MechEntity(EntityType<? extends MechEntity> type, Level level) {
        super(type, level);
        blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(CHARGE, 0);
        builder.define(HEALTH, MAX_HEALTH);
        builder.define(SPEED, 0F);
        builder.define(JETS, false);
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

    public boolean jets() {
        return entityData.get(JETS);
    }

    /** Walk-cycle phase in radians, advanced by distance walked (client side). */
    public float stride(float partial) {
        return Mth.lerp(partial, oldStride, stride);
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
    public float maxUpStep() {
        return 1.5F;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
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
        return new Vec3(0, 1.75, 0.05).yRot(-getYRot() * Mth.DEG_TO_RAD);
    }

    public boolean input(Player player, float throttle, float steer, boolean run, boolean jet) {
        if (driver() != player || !Float.isFinite(throttle) || !Float.isFinite(steer)) return false;
        this.throttle = Math.clamp(throttle, -1, 1);
        steering = Math.clamp(steer, -1, 1);
        this.run = run;
        this.jet = jet;
        inputAge = 0;
        return true;
    }

    @Override
    public void tick() {
        Vec3 before = position();
        super.tick();
        oldStride = stride;
        if (level().isClientSide()) {
            float previousYaw = getYRot();
            interpolation.interpolate();
            if (driver() != null && driver().isLocalPlayer()) driver().setYRot(driver().getYRot() + Mth.wrapDegrees(getYRot() - previousYaw));
            Vec3 moved = position().subtract(before);
            double yaw = getYRot() * Mth.DEG_TO_RAD;
            double along = -Math.sin(yaw) * moved.x + Math.cos(yaw) * moved.z;
            if (onGround() || moved.y > -0.05) stride += (float) (along / 0.55);
            if (jets() && tickCount % 2 == 0) {
                for (int side = -1; side <= 1; side += 2) {
                    Vec3 nozzle = position().add(new Vec3(side * 0.45, 1.2, -0.45).yRot(-getYRot() * Mth.DEG_TO_RAD));
                    level().addParticle(ParticleTypes.FLAME, nozzle.x, nozzle.y, nozzle.z, 0, -0.25, 0);
                    level().addParticle(ParticleTypes.LARGE_SMOKE, nozzle.x, nozzle.y - 0.3, nozzle.z, 0, -0.05, 0);
                }
            }
            return;
        }
        if (driver() == null || ++inputAge > 10) {
            throttle = steering = 0;
            run = jet = false;
        }
        boolean creative = driver() != null && driver().getAbilities().instabuild;
        int cost = (throttle != 0 ? (run ? RUN_FE : WALK_FE) : 0) + (jet ? JET_FE : 0);
        boolean powered = creative || battery.getAmountAsInt() >= cost;
        if (powered && !creative) battery.set(battery.getAmountAsInt() - cost);
        boolean jetting = powered && jet;
        entityData.set(JETS, jetting);
        if (powered) setYRot(getYRot() + steering * (run ? 3.5F : 5F));
        double yaw = getYRot() * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        double target = powered ? throttle * (run ? 0.36 : 0.2) : 0;
        Vec3 v = getDeltaMovement();
        double along = v.x * fwd.x + v.z * fwd.z;
        along += (target - along) * (onGround() ? 0.25 : 0.06);
        double vy = v.y - 0.08;
        if (jetting) vy = Math.min(0.45, vy + 0.16);
        vy *= 0.98;
        setDeltaMovement(fwd.x * along, vy, fwd.z * along);
        var footprint = getBoundingBox().move(getDeltaMovement());
        if (!level().hasChunkAt(BlockPos.containing(footprint.getCenter())) || !level().getWorldBorder().isWithinBounds(footprint)) {
            setDeltaMovement(0, getDeltaMovement().y, 0);
        }
        boolean wasGround = onGround();
        if (!wasGround && Double.isNaN(fallStart)) fallStart = getY();
        if (!wasGround && getY() > fallStart) fallStart = getY();
        move(MoverType.SELF, getDeltaMovement());
        entityData.set(SPEED, (float) position().subtract(before).horizontalDistance());
        if (onGround() && !Double.isNaN(fallStart)) {
            double drop = fallStart - getY();
            fallStart = Double.NaN;
            if (drop > 3) stomp((ServerLevel) level(), Math.min(1, (float) (drop / 10)));
        }
        if (onGround() && speed() > 0.05 && tickCount % (run ? 6 : 10) == 0) {
            level().playSound(null, blockPosition(), SoundEvents.IRON_GOLEM_STEP, SoundSource.NEUTRAL, 0.8F, 0.6F);
        }
        resetFallDistance();
        if (driver() != null) {
            driver().resetFallDistance();
            driver().setYBodyRot(getYRot());
        }
    }

    /** Landing shockwave: knocks back and hurts creatures within a few blocks (never the pilot). */
    private void stomp(ServerLevel level, float power) {
        double r = 3 + 3 * power;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r, 1, r),
                e -> e.isAlive() && e != driver() && e.getVehicle() != this)) {
            Vec3 away = e.position().subtract(position()).multiply(1, 0, 1);
            double d = Math.max(0.5, away.length());
            e.hurtServer(level, (driver() != null ? damageSources().playerAttack(driver()) : damageSources().generic()), (float) (4 + 10 * power * (1 - d / (r + 1))));
            e.push(away.x / d * (0.6 + power), 0.4 + 0.3 * power, away.z / d * (0.6 + power));
            e.hurtMarked = true;
        }
        level.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 1F, 0.7F);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 0.2, getZ(), 30, r / 2, 0.1, r / 2, 0.02);
        FxPayload.send(level, new FxPayload(FxPayload.SHOCKWAVE, position().add(0, 0.2, 0), position(), 0xFFFFB347, (float) r * 0.6F, -1));
    }

    public ItemStack packed() {
        var stack = new ItemStack(ModItems.MECH.get());
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
            if (isVehicle() || speed() > 0.05F) return InteractionResult.FAIL;
            if (!level().isClientSide()) {
                var stack = packed();
                if (!player.getInventory().add(stack)) player.drop(stack, false);
                discard();
            }
            return InteractionResult.SUCCESS;
        }
        if (held.is(ModMaterials.INGOTS.get("steel").get()) && health() < MAX_HEALTH) {
            if (!level().isClientSide()) {
                entityData.set(HEALTH, Math.min(MAX_HEALTH, health() + 15));
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
        if (source.getEntity() == driver()) return false;
        if (source.getEntity() instanceof Player player && player.getAbilities().instabuild) {
            discard();
            return true;
        }
        entityData.set(HEALTH, Math.max(0, health() - amount));
        markHurt();
        if (health() <= 0) {
            ejectPassengers();
            if (level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.ENTITY_DROPS)) {
                spawnAtLocation(level, new ItemStack(ModMaterials.INGOTS.get("steel").get(), 8));
            }
            level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 1.5, getZ(), 4, 0.5, 0.8, 0.5, 0);
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
