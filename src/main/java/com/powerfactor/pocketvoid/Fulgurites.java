package com.powerfactor.pocketvoid;

import com.powerfactor.registry.ModVoid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LightningBolt;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Lightning fuses sand into glassy tubes. Where a bolt lands (on a lightning rod standing in sand, or on open
 * desert), up to three sand blocks within two blocks turn to Fused Sand, which breaks into Fulgurite.
 */
public final class Fulgurites {
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof LightningBolt bolt) || !(event.getLevel() instanceof ServerLevel level) || event.loadedFromDisk()) return;
        BlockPos hit = bolt.blockPosition();
        int fused = 0;
        for (BlockPos p : BlockPos.betweenClosed(hit.offset(-2, -3, -2), hit.offset(2, 0, 2))) {
            if (fused >= 3) break;
            if (!level.getBlockState(p).is(BlockTags.SAND) || p.distSqr(hit) > 6.5 || level.getRandom().nextFloat() > 0.6F) continue;
            level.setBlockAndUpdate(p, ModVoid.FUSED_SAND.get().defaultBlockState());
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5, 12, 0.3, 0.1, 0.3, 0.2);
            fused++;
        }
    }

    private Fulgurites() {}
}
