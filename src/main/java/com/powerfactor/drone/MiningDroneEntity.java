package com.powerfactor.drone;

import com.powerfactor.registry.ModDrone;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A Mining Drone: a quadcopter that flies out from its hub to the vein the hub's AI assigned, phasing
 * through stone with a bore field where it must, cuts each ore block with its laser, and flies the haul
 * home. It runs on a battery charged at the hub.
 */
public class MiningDroneEntity extends Entity {
    public static final int STATE_OUTBOUND = 0, STATE_MINING = 1, STATE_RETURNING = 2;
    public static final int BATTERY = 30_000, FE_PER_BLOCK = 2_000, CARGO = 9;
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(MiningDroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockPos> TARGET = SynchedEntityData.defineId(MiningDroneEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> CUT = SynchedEntityData.defineId(MiningDroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> INDEX = SynchedEntityData.defineId(MiningDroneEntity.class, EntityDataSerializers.INT);

    private BlockPos hub = BlockPos.ZERO;
    private final Deque<BlockPos> vein = new ArrayDeque<>();
    private final SimpleContainer cargo = new SimpleContainer(CARGO);
    private int battery = BATTERY, veinId = -1, mined;
    private double speed = 0.3;

    public MiningDroneEntity(EntityType<? extends MiningDroneEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public static MiningDroneEntity launch(ServerLevel level, BlockPos hub, int index, List<BlockPos> blocks, int veinId, double speed) {
        MiningDroneEntity d = new MiningDroneEntity(ModDrone.DRONE.get(), level);
        d.hub = hub.immutable();
        d.vein.addAll(blocks);
        d.veinId = veinId;
        d.speed = speed;
        d.setPos(hub.getX() + 0.5, hub.getY() + 1.3, hub.getZ() + 0.5);
        d.entityData.set(INDEX, index);
        d.entityData.set(TARGET, blocks.isEmpty() ? hub : blocks.getFirst());
        level.addFreshEntity(d);
        level.playSound(null, hub, SoundEvents.BEEHIVE_EXIT, SoundSource.BLOCKS, 1F, 1.6F);
        return d;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(STATE, STATE_OUTBOUND);
        b.define(TARGET, BlockPos.ZERO);
        b.define(CUT, 0);
        b.define(INDEX, 0);
    }

    public int state() {
        return entityData.get(STATE);
    }

    public BlockPos target() {
        return entityData.get(TARGET);
    }

    /** Laser progress on the current block, 0..100. */
    public int cut() {
        return entityData.get(CUT);
    }

    public int index() {
        return entityData.get(INDEX);
    }

    public int battery() {
        return battery;
    }

    public int veinId() {
        return veinId;
    }

    public int mined() {
        return mined;
    }

    public SimpleContainer cargo() {
        return cargo;
    }

    public BlockPos hub() {
        return hub;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            // Client: let the synced position glide.
            move(MoverType.SELF, getDeltaMovement());
            return;
        }
        if (!(level.getBlockEntity(hub) instanceof DroneHubBlockEntity home)) {
            // Orphaned: drop what it carries and power down.
            for (int i = 0; i < CARGO; i++) spawnAtLocation(level, cargo.getItem(i));
            discard();
            return;
        }
        switch (state()) {
            case STATE_OUTBOUND -> {
                BlockPos t = nextTarget(level);
                if (t == null || cargoFull() || battery < FE_PER_BLOCK) {
                    goHome(home);
                    return;
                }
                entityData.set(TARGET, t);
                if (flyTo(Vec3.atCenterOf(t), 1.1)) {
                    entityData.set(STATE, STATE_MINING);
                    entityData.set(CUT, 0);
                }
            }
            case STATE_MINING -> {
                BlockPos t = target();
                BlockState s = level.getBlockState(t);
                if (s.isAir() || !DroneHubBlockEntity.isOre(s)) {
                    vein.poll();
                    entityData.set(STATE, STATE_OUTBOUND);
                    return;
                }
                hover(Vec3.atCenterOf(t), 1.1);
                float hardness = Math.max(0.5F, s.getDestroySpeed(level, t));
                int step = Math.max(1, (int) (100 / (15 + hardness * 6)));
                int cut = cut() + step;
                if (tickCount % 4 == 0) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), t.getX() + 0.5, t.getY() + 0.5, t.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.05);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, t.getX() + 0.5, t.getY() + 0.5, t.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.05);
                }
                if (cut >= 100) {
                    List<ItemStack> drops = Block.getDrops(s, level, t, level.getBlockEntity(t), this, new ItemStack(Items.DIAMOND_PICKAXE));
                    level.levelEvent(2001, t, Block.getId(s));
                    level.setBlock(t, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    for (ItemStack d : drops) {
                        ItemStack rest = cargo.addItem(d);
                        if (!rest.isEmpty()) spawnAtLocation(level, rest);
                    }
                    battery -= FE_PER_BLOCK;
                    mined++;
                    home.droneMined(this, s);
                    vein.poll();
                    cut = 0;
                    entityData.set(STATE, STATE_OUTBOUND);
                }
                entityData.set(CUT, cut);
            }
            case STATE_RETURNING -> {
                Vec3 dock = Vec3.atCenterOf(hub).add(0, 0.9, 0);
                if (flyTo(dock, 0.4)) {
                    home.dock(this);
                    discard();
                }
            }
            default -> {}
        }
        // Rotor wash and the bore field when it's inside rock.
        if (tickCount % 3 == 0 && !level.getBlockState(blockPosition()).isAir()) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY(), getZ(), 2, 0.2, 0.2, 0.2, 0.01);
        }
    }

    private boolean cargoFull() {
        for (int i = 0; i < CARGO; i++) if (cargo.getItem(i).isEmpty()) return false;
        return true;
    }

    private BlockPos nextTarget(ServerLevel level) {
        while (!vein.isEmpty()) {
            BlockPos p = vein.peek();
            if (DroneHubBlockEntity.isOre(level.getBlockState(p))) return p;
            vein.poll();
        }
        return null;
    }

    private void goHome(DroneHubBlockEntity home) {
        entityData.set(STATE, STATE_RETURNING);
        entityData.set(TARGET, hub);
        home.droneReturning(this);
    }

    /** Eases toward a point; true once within {@code reach}. Costs battery while flying. */
    private boolean flyTo(Vec3 goal, double reach) {
        Vec3 d = goal.subtract(position());
        double dist = d.length();
        if (dist <= reach) {
            setDeltaMovement(Vec3.ZERO);
            return true;
        }
        double v = Math.min(speed, 0.05 + dist * 0.15);
        Vec3 vel = getDeltaMovement().scale(0.6).add(d.normalize().scale(v * 0.4));
        setDeltaMovement(vel);
        move(MoverType.SELF, vel);
        setYRot((float) Math.toDegrees(Math.atan2(-vel.x, vel.z)));
        if (tickCount % 10 == 0) battery = Math.max(0, battery - 10);
        return false;
    }

    private void hover(Vec3 at, double stand) {
        Vec3 d = at.subtract(position());
        if (d.length() > stand + 0.3) flyTo(at, stand);
        else setDeltaMovement(new Vec3(0, Math.sin(tickCount * 0.2) * 0.01, 0));
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        hub = BlockPos.of(input.getLongOr("hub", 0));
        battery = input.getIntOr("battery", BATTERY);
        veinId = input.getIntOr("vein", -1);
        mined = input.getIntOr("mined", 0);
        speed = input.getDoubleOr("speed", 0.3);
        entityData.set(STATE, input.getIntOr("state", STATE_RETURNING));
        entityData.set(INDEX, input.getIntOr("index", 0));
        vein.clear();
        int[] v = input.getIntArray("vein").orElse(new int[0]);
        for (int i = 0; i + 2 < v.length; i += 3) vein.add(new BlockPos(v[i], v[i + 1], v[i + 2]));
        cargo.clearContent();
        net.minecraft.world.ContainerHelper.loadAllItems(input, cargo.getItems());
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putLong("hub", hub.asLong());
        output.putInt("battery", battery);
        output.putInt("vein", veinId);
        output.putInt("mined", mined);
        output.putDouble("speed", speed);
        output.putInt("state", state());
        output.putInt("index", index());
        int[] v = new int[vein.size() * 3];
        int i = 0;
        for (BlockPos p : vein) {
            v[i++] = p.getX();
            v[i++] = p.getY();
            v[i++] = p.getZ();
        }
        output.putIntArray("vein", v);
        net.minecraft.world.ContainerHelper.saveAllItems(output, cargo.getItems());
    }
}
