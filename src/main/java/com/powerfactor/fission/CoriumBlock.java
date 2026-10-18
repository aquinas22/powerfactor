package com.powerfactor.fission;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Corium: the slumped, glowing remains of a melted core. It burns what stands on it and keeps the ground around it hot with radiation. */
public class CoriumBlock extends Block {
    public CoriumBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (level instanceof ServerLevel s && entity instanceof LivingEntity l && !l.fireImmune()) {
            l.hurtServer(s, s.damageSources().hotFloor(), 4F);
            l.igniteForSeconds(4);
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.LAVA, pos.getX() + random.nextDouble(), pos.getY() + 1, pos.getZ() + random.nextDouble(), 0, 0, 0);
        }
        if (random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + random.nextDouble(), pos.getY() + 1.1, pos.getZ() + random.nextDouble(), 0, 0.04, 0);
        }
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Radiation.contaminate(level, pos, 6, 20 * 60 * 5);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }
}
