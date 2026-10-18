package com.powerfactor.client.fission;

import com.powerfactor.fission.ReactorControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * Particles in a running reactor's pool, once a tick from the renderer: water coolant boils off the rods in
 * streams of bubbles as the core runs hot; a loop-cooled core's pool only fizzes (radiolysis), more with power.
 */
final class ReactorEffects {
    static void tick(ReactorControllerBlockEntity be, long now) {
        Level level = be.getLevel();
        int[] b = be.clientBounds, cols = be.clientColumns;
        if (level == null || b.length < 6 || cols.length < 2) return;
        RandomSource r = level.getRandom();
        float power = Mth.clamp(be.clientPower, 0, 1.5F);
        boolean boiling = be.clientCoolant == 0;
        float rate = boiling ? Mth.clamp((be.clientTemp - 420) / 380, 0, 1) * 6 : power * 1.2F;
        int columns = cols.length / 2;
        int count = (int) rate + (r.nextFloat() < rate % 1 ? 1 : 0);
        for (int k = 0; k < count; k++) {
            int c = r.nextInt(columns);
            if (c < be.clientWet.length && be.clientWet[c] == 0) continue;
            double x = cols[c * 2] + 0.5 + (r.nextDouble() - 0.5) * 1.1, z = cols[c * 2 + 1] + 0.5 + (r.nextDouble() - 0.5) * 1.1;
            double y = b[1] + 1 + r.nextDouble() * (b[4] - b[1] - 1);
            if (!level.getBlockState(BlockPos.containing(x, y, z)).getFluidState().isSource()) continue;
            level.addParticle(boiling && r.nextFloat() < 0.6F ? ParticleTypes.BUBBLE_COLUMN_UP : ParticleTypes.BUBBLE, x, y, z, 0, 0.04, 0);
        }
    }

    private ReactorEffects() {}
}
