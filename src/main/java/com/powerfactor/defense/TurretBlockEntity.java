package com.powerfactor.defense;

import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.registry.ModDefense;
import com.powerfactor.scada.ScadaReading;
import com.powerfactor.weapon.FxPayload;
import com.powerfactor.weapon.PlasmaBoltEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import org.jspecify.annotations.Nullable;

/**
 * A powered defence turret. It scans for targets it may shoot (hostile mobs by default; animals and
 * players only when enabled, never its owner, villagers, pets or armour stands), swings its head onto
 * the nearest one in sight, and fires. The plasma turret streams bolts; the railgun turret charges
 * and fires a piercing slug that strikes every valid target along the line.
 */
public class TurretBlockEntity extends MachineBlockEntity {
    public enum Tier {
        PLASMA(20, 6, 0, 1_500, 400_000, 8_000, 14F, 7F),
        RAIL(40, 50, 30, 40_000, 2_000_000, 60_000, 6F, 30F);

        public final int range, cooldown, charge, shotCost, capacity, maxInput;
        public final float turnRate, damage;

        Tier(int range, int cooldown, int charge, int shotCost, int capacity, int maxInput, float turnRate, float damage) {
            this.range = range;
            this.cooldown = cooldown;
            this.charge = charge;
            this.shotCost = shotCost;
            this.capacity = capacity;
            this.maxInput = maxInput;
            this.turnRate = turnRate;
            this.damage = damage;
        }
    }

    public static final int MODE_ANIMALS = 1, MODE_PLAYERS = 2;
    public static final int DATA_ENERGY_HI = 0, DATA_ENERGY_LO = 1, DATA_MODE = 2, DATA_TARGET = 3, DATA_KILLS = 4, DATA_CHARGE = 5, DATA_COUNT = 6;
    public static final int BUTTON_ANIMALS = 0, BUTTON_PLAYERS = 1;

    private final Tier tier;
    private final SimpleEnergyHandler energy;
    private int mode;
    private @Nullable UUID owner;
    private @Nullable LivingEntity target;
    private float yaw, pitch;
    private int cooldown, charging, kills, ticks, shots;
    // Client view: the last synced aim, and the smoothed aim the renderer draws.
    private float clientYaw, clientPitch, clientCharge, drawYaw, drawPitch;
    private int clientShots, clientTarget = -1;
    private long lastShot = Long.MIN_VALUE / 2;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case DATA_ENERGY_HI -> energy.getAmountAsInt() >>> 16;
                case DATA_ENERGY_LO -> energy.getAmountAsInt() & 0xFFFF;
                case DATA_MODE -> mode;
                case DATA_TARGET -> target == null ? 0 : 1;
                case DATA_KILLS -> kills;
                case DATA_CHARGE -> tier.charge == 0 ? 0 : Math.round(1000F * charging / tier.charge);
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public TurretBlockEntity(Tier tier, BlockPos pos, BlockState state) {
        super(tier == Tier.RAIL ? ModDefense.RAIL_TURRET_BE.get() : ModDefense.PLASMA_TURRET_BE.get(), pos, state);
        this.tier = tier;
        this.energy = new SimpleEnergyHandler(tier.capacity, tier.maxInput, 0) {
            @Override
            protected void onEnergyChanged(int previousAmount) {
                setChanged();
            }
        };
    }

    public Tier tier() {
        return tier;
    }

    public SimpleEnergyHandler getEnergy() {
        return energy;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        setChanged();
    }

    public int mode() {
        return mode;
    }

    public void toggle(int flag) {
        mode ^= flag;
        target = null;
        setChanged();
    }

    /** Muzzle height above the block's base, where the barrel's pivot sits. */
    public static final double PIVOT = 0.95;

    Vec3 pivot() {
        return Vec3.atBottomCenterOf(worldPosition).add(0, PIVOT, 0);
    }

    static Vec3 aim(float yaw, float pitch) {
        float y = yaw * Mth.DEG_TO_RAD, p = pitch * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(y) * Mth.cos(p), -Mth.sin(p), Mth.cos(y) * Mth.cos(p));
    }

    /** Whether this turret may shoot an entity at all (ignoring range and sight). */
    public boolean mayTarget(Entity e) {
        if (!(e instanceof LivingEntity l) || !l.isAlive() || l.isInvulnerable() || l instanceof ArmorStand || l.isSpectator()) return false;
        if (owner != null && owner.equals(l.getUUID())) return false;
        if (l instanceof Player p) return (mode & MODE_PLAYERS) != 0 && !p.isCreative();
        if (l instanceof Npc) return false;
        if (l instanceof TamableAnimal t && t.isTame()) return false;
        if (l instanceof Enemy) return true;
        return l instanceof Animal && (mode & MODE_ANIMALS) != 0 && !l.hasCustomName();
    }

    private boolean inSight(ServerLevel level, LivingEntity e) {
        Vec3 from = pivot(), to = e.getBoundingBox().getCenter();
        if (from.distanceToSqr(to) > (double) tier.range * tier.range) return false;
        // Start the ray just outside our own block so the turret's body doesn't block it.
        Vec3 start = from.add(to.subtract(from).normalize().scale(0.6));
        return level.clip(new ClipContext(start, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty())).getType()
                == HitResult.Type.MISS;
    }

    private @Nullable LivingEntity acquire(ServerLevel level) {
        AABB box = new AABB(worldPosition).inflate(tier.range);
        Vec3 from = pivot();
        return level.getEntitiesOfClass(LivingEntity.class, box, this::mayTarget).stream()
                .filter(e -> inSight(level, e))
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(from))).orElse(null);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        ticks++;
        if (cooldown > 0) cooldown--;
        boolean on = enabled(level, pos);
        if (!on) {
            target = null;
            charging = 0;
        } else if (target == null || !mayTarget(target) || (ticks % 5 == 0 && !inSight(level, target))) {
            target = ticks % 5 == 0 || target != null ? acquire(level) : null;
            charging = 0;
        }
        if (target != null) {
            Vec3 d = target.getBoundingBox().getCenter().subtract(pivot());
            float wantYaw = (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
            float wantPitch = (float) (-Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
            yaw += Mth.clamp(Mth.wrapDegrees(wantYaw - yaw), -tier.turnRate, tier.turnRate);
            pitch += Mth.clamp(wantPitch - pitch, -tier.turnRate, tier.turnRate);
            boolean aligned = Math.abs(Mth.wrapDegrees(wantYaw - yaw)) < 3 && Math.abs(wantPitch - pitch) < 3;
            boolean powered = energy.getAmountAsInt() >= tier.shotCost;
            if (aligned && cooldown == 0 && powered) {
                if (charging < tier.charge) {
                    if (charging == 0) level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.6F, 1.8F);
                    charging++;
                } else {
                    fire(level, d.normalize());
                    charging = 0;
                    cooldown = tier.cooldown;
                }
            }
        } else {
            // Idle sweep: a slow scan so a powered turret looks alive.
            if (on && energy.getAmountAsInt() > 0) yaw += 1.2F;
            pitch += Mth.clamp(-pitch, -2, 2);
        }
        yaw = Mth.wrapDegrees(yaw);
        setRunning(level, pos, state, target != null);
        if (target != null || ticks % 4 == 0) level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }

    private void fire(ServerLevel level, Vec3 dir) {
        energy.set(energy.getAmountAsInt() - tier.shotCost);
        shots++;
        Vec3 muzzle = pivot().add(dir.scale(0.75));
        if (tier == Tier.PLASMA) {
            level.addFreshEntity(new PlasmaBoltEntity(level, muzzle, dir.scale(2.4)));
            level.playSound(null, worldPosition, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 0.5F, 1.7F);
            level.sendParticles(ParticleTypes.GLOW, muzzle.x, muzzle.y, muzzle.z, 3, 0.05, 0.05, 0.05, 0.02);
            return;
        }
        // Railgun slug: every valid target on the line takes the hit, nearest first. Blocks stop it.
        Vec3 end = muzzle.add(dir.scale(tier.range));
        HitResult wall = level.clip(new ClipContext(muzzle, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (wall.getType() != HitResult.Type.MISS) end = wall.getLocation();
        List<LivingEntity> hits = new ArrayList<>();
        Vec3 a = muzzle, b = end;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(1), this::mayTarget)) {
            if (e.getBoundingBox().inflate(0.3).clip(a, b).isPresent()) hits.add(e);
        }
        hits.sort(Comparator.comparingDouble(e -> e.distanceToSqr(a)));
        float damage = tier.damage;
        for (LivingEntity e : hits) {
            e.hurtServer(level, level.damageSources().magic(), damage);
            e.push(dir.x * 0.8, 0.2, dir.z * 0.8);
            e.hurtMarked = true;
            if (!e.isAlive()) kills++;
            damage *= 0.85F;
        }
        level.playSound(null, worldPosition, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.BLOCKS, 0.9F, 1.7F);
        level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.8F, 2.0F);
        FxPayload.send(level, new FxPayload(FxPayload.RAIL, muzzle, end, 0xFF8FF4FF, 0.9F, -1));
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 30, .3, .3, .3, .5);
    }

    // ------------------------------------------------------------------ client view

    /** Eases the drawn head toward the last synced aim; called once per rendered frame. */
    public void smooth(float factor) {
        drawYaw += Mth.wrapDegrees(clientYaw - drawYaw) * factor;
        drawPitch += (clientPitch - drawPitch) * factor;
    }

    public float drawYaw() {
        return drawYaw;
    }

    public float drawPitch() {
        return drawPitch;
    }

    public float charge() {
        return clientCharge;
    }

    /** Game time of the last shot the client saw (muzzle flash and recoil). */
    public long lastShot() {
        return lastShot;
    }

    public boolean tracking() {
        return clientTarget >= 0;
    }

    @Override
    public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), target != null ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF, 0, 0,
                Component.translatable("scada.powerfactor.turret", kills), true, remoteEnabled(), true, 1F, 60F);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new TurretMenu(containerId, inventory, this, data);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        mode = input.getIntOr("mode", 0);
        kills = input.getIntOr("kills", 0);
        yaw = input.getFloatOr("yaw", 0);
        owner = input.getString("owner").map(s -> {
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        output.putInt("mode", mode);
        output.putInt("kills", kills);
        output.putFloat("yaw", yaw);
        if (owner != null) output.putString("owner", owner.toString());
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putFloat("yaw", yaw);
        tag.putFloat("pitch", pitch);
        tag.putFloat("charge", tier.charge == 0 ? 0 : (float) charging / tier.charge);
        tag.putInt("shots", shots);
        tag.putInt("target", target == null ? -1 : target.getId());
        return tag;
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        clientYaw = input.getFloatOr("yaw", 0);
        clientPitch = input.getFloatOr("pitch", 0);
        clientCharge = input.getFloatOr("charge", 0);
        int shotsNow = input.getIntOr("shots", 0);
        if (shotsNow != clientShots && level != null) lastShot = level.getGameTime();
        clientShots = shotsNow;
        clientTarget = input.getIntOr("target", -1);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ValueInput input) {
        handleUpdateTag(input);
    }
}
