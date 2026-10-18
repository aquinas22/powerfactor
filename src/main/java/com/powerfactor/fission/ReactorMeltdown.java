package com.powerfactor.fission;

import com.powerfactor.registry.ModFission;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * A core meltdown: a steam explosion splits the vessel, the fuel slumps into molten corium where the
 * assemblies stood, and the ground around is left radioactive (see {@link Radiation}). Unless the server
 * has turned off reactor explosions, terrain breaks.
 */
public final class ReactorMeltdown {
    public static void melt(ServerLevel level, ReactorControllerBlockEntity reactor, ReactorStructure.Shape shape) {
        BlockPos centre = new BlockPos((shape.min().getX() + shape.max().getX()) / 2, (shape.min().getY() + shape.max().getY()) / 2,
                (shape.min().getZ() + shape.max().getZ()) / 2);
        float size = 3 + (float) Math.sqrt(shape.columns().size()) * 2;
        boolean grief = com.powerfactor.PowerFactorConfig.reactorExplosions();
        // Fuel to corium first, so the blast doesn't scatter it.
        for (var c : shape.columns()) {
            for (BlockPos p : c.assemblies()) level.setBlockAndUpdate(p, ModFission.CORIUM.get().defaultBlockState());
            if (grief) level.setBlockAndUpdate(c.drive(), Blocks.AIR.defaultBlockState());
        }
        reactor.fuel().clearContent();
        level.explode(null, centre.getX() + 0.5, centre.getY() + 0.5, centre.getZ() + 0.5, size, true,
                grief ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, centre.getX() + 0.5, centre.getY() + 1, centre.getZ() + 0.5, 3, 1, 1, 1, 0);
        level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, centre.getX() + 0.5, centre.getY() + 2, centre.getZ() + 0.5, 80, 2, 3, 2, 0.05);
        level.playSound(null, centre, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 8F, 0.5F);
        Radiation.contaminate(level, centre, (int) (size * 3), 20 * 60 * 20);
    }

    private ReactorMeltdown() {}
}
