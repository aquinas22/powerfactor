package com.powerfactor.world;

import com.powerfactor.PowerFactorConfig;
import com.powerfactor.registry.ModQuantum;
import com.powerfactor.weapon.FxPayload;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A falling meteor. It streaks in on a steep line trailing fire and smoke; on impact it blasts a
 * small crater (if the server allows craters) lined with scorched rock around a core of Meteorite
 * studded with exotic ore. With craters off it shatters into loose meteorite and ore instead.
 */
public class MeteorEntity extends Entity {
    private static final EntityDataAccessor<Integer> SIZE = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.INT);
    private int life;

    public MeteorEntity(EntityType<? extends MeteorEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public MeteorEntity(Level level, Vec3 from, Vec3 velocity, int size) {
        this(ModQuantum.METEOR.get(), level);
        setPos(from);
        setDeltaMovement(velocity);
        entityData.set(SIZE, Math.clamp(size, 1, 3));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SIZE, 2);
    }

    public int size() {
        return entityData.get(SIZE);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 512 * 512;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement();
        Vec3 from = position(), to = from.add(v);
        if (level() instanceof ServerLevel level) {
            if (++life > 1200 || to.y < level.getMinY()) {
                discard();
                return;
            }
            HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
            if (hit.getType() != HitResult.Type.MISS) {
                impact(level, hit.getLocation());
                return;
            }
            // A burning trail, thicker for bigger rocks.
            int n = 2 + size();
            level.sendParticles(ParticleTypes.FLAME, from.x, from.y, from.z, n, 0.25, 0.25, 0.25, 0.02);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, from.x, from.y, from.z, n, 0.3, 0.3, 0.3, 0.01);
            if (life % 3 == 0) level.sendParticles(ParticleTypes.LAVA, from.x, from.y, from.z, 1, 0.2, 0.2, 0.2, 0);
            if (life % 20 == 0) level.playSound(null, BlockPos.containing(from), SoundEvents.FIRECHARGE_USE, SoundSource.AMBIENT, 2F, 0.4F);
        }
        setPos(to);
        setDeltaMovement(v.add(0, -0.01, 0));
    }

    private void impact(ServerLevel level, Vec3 at) {
        int size = size();
        BlockPos centre = BlockPos.containing(at);
        level.explode(this, at.x, at.y, at.z, 1.5F + size, false, Level.ExplosionInteraction.NONE);
        level.playSound(null, centre, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.AMBIENT, 4F, 0.5F);
        level.playSound(null, centre, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.AMBIENT, 2F, 0.4F);
        FxPayload.send(level, new FxPayload(FxPayload.SHOCKWAVE, at, at, 0xFFFF8A3A, 2F + size, -1));
        FxPayload.send(level, new FxPayload(FxPayload.COLLAPSE, at, at, 0xFFFF6A2A, 1F + size * 0.5F, -1));
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 40, 1.5, 0.5, 1.5, 0);
        level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, at.x, at.y + 1, at.z, 20, 1.5, 0.5, 1.5, 0.05);
        if (PowerFactorConfig.meteorCraters()) crater(level, centre, size);
        else shatter(level, at, size);
        discard();
    }

    /** Ores a meteor can carry, rarest last. */
    private static List<BlockState> ores() {
        return List.of(ModQuantum.ADAMANTIUM_ORE.get().defaultBlockState(), ModQuantum.FARADAENIUM_ORE.get().defaultBlockState(),
                ModQuantum.QUBIT_ORE.get().defaultBlockState(), ModQuantum.CORTANIUM_ORE.get().defaultBlockState());
    }

    static void crater(ServerLevel level, BlockPos centre, int size) {
        int r = 2 + size;
        var rnd = level.getRandom();
        // Bowl: clear a squashed sphere, never touching unbreakable blocks or block entities.
        for (BlockPos p : BlockPos.betweenClosed(centre.offset(-r - 1, -r, -r - 1), centre.offset(r + 1, r, r + 1))) {
            double dx = p.getX() - centre.getX(), dy = (p.getY() - centre.getY()) * 1.6, dz = p.getZ() - centre.getZ();
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            BlockState s = level.getBlockState(p);
            if (s.isAir() || s.getDestroySpeed(level, p) < 0 || level.getBlockEntity(p) != null) continue;
            if (d <= r) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            } else if (d <= r + 1.2 && p.getY() <= centre.getY() && rnd.nextFloat() < 0.6F) {
                // Scorched lining.
                level.setBlock(p, rnd.nextFloat() < 0.25F ? Blocks.MAGMA_BLOCK.defaultBlockState()
                        : rnd.nextBoolean() ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.BASALT.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        // Core: a lump of Meteorite at the bottom of the bowl with exotic ore studded through it.
        BlockPos floor = centre.below((int) Math.floor(r / 1.6));
        List<BlockState> ores = ores();
        int studs = 1 + size + rnd.nextInt(2);
        for (BlockPos p : BlockPos.betweenClosed(floor.offset(-1, -1, -1), floor.offset(1, 0, 1))) {
            BlockState s = level.getBlockState(p);
            if (s.getDestroySpeed(level, p) < 0 || level.getBlockEntity(p) != null) continue;
            boolean edge = Math.abs(p.getX() - floor.getX()) + Math.abs(p.getZ() - floor.getZ()) == 2 && p.getY() == floor.getY();
            if (edge && rnd.nextFloat() < 0.5F) continue;
            BlockState put = ModQuantum.METEORITE.get().defaultBlockState();
            if (studs > 0 && rnd.nextFloat() < 0.4F) {
                // Rarer ores are less likely: each step down the list halves the odds.
                int i = 0;
                while (i < ores.size() - 1 && rnd.nextBoolean()) i++;
                put = ores.get(i);
                studs--;
            }
            level.setBlock(p.immutable(), put, Block.UPDATE_ALL);
        }
        // A few fires on the rim.
        for (int i = 0; i < 4 + size * 2; i++) {
            BlockPos p = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                    centre.offset(rnd.nextInt(2 * r + 3) - r - 1, 0, rnd.nextInt(2 * r + 3) - r - 1));
            if (level.getBlockState(p).isAir() && Blocks.FIRE.defaultBlockState().canSurvive(level, p)) {
                level.setBlock(p, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    private static void shatter(ServerLevel level, Vec3 at, int size) {
        var rnd = level.getRandom();
        List<BlockState> ores = ores();
        drop(level, at, new ItemStack(ModQuantum.METEORITE.get(), 2 + size + rnd.nextInt(2)));
        int studs = 1 + rnd.nextInt(size + 1);
        for (int k = 0; k < studs; k++) {
            int i = 0;
            while (i < ores.size() - 1 && rnd.nextBoolean()) i++;
            drop(level, at, new ItemStack(ores.get(i).getBlock()));
        }
    }

    private static void drop(ServerLevel level, Vec3 at, ItemStack stack) {
        ItemEntity e = new ItemEntity(level, at.x, at.y + 0.5, at.z, stack);
        e.setDeltaMovement((level.getRandom().nextDouble() - 0.5) * 0.4, 0.4, (level.getRandom().nextDouble() - 0.5) * 0.4);
        level.addFreshEntity(e);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        entityData.set(SIZE, Math.clamp(input.getIntOr("size", 2), 1, 3));
        life = input.getIntOr("life", 0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("size", size());
        output.putInt("life", life);
    }
}
