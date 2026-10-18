package com.powerfactor.pocketvoid;

import com.powerfactor.registry.ModVoid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * TNT set off right against bedrock can chip a Bedrock Fragment loose (the bedrock itself stays). The closer
 * the blast, the better the odds; at most two fragments a blast.
 */
public final class BedrockFragments {
    public static final double REACH = 2.5, BEST_CHANCE = 0.35;
    public static final int MAX_PER_BLAST = 2;

    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var ex = event.getExplosion();
        var src = ex.getDirectSourceEntity();
        if (!(src instanceof PrimedTnt) && !(src instanceof MinecartTNT)) return;
        Vec3 c = ex.center();
        BlockPos centre = BlockPos.containing(c);
        int found = 0;
        for (BlockPos p : BlockPos.betweenClosed(centre.offset(-2, -2, -2), centre.offset(2, 2, 2))) {
            if (found >= MAX_PER_BLAST) break;
            if (!level.getBlockState(p).is(Blocks.BEDROCK)) continue;
            double d = Vec3.atCenterOf(p).distanceTo(c);
            if (d > REACH || level.getRandom().nextDouble() > BEST_CHANCE * (1 - d / (REACH + 0.5))) continue;
            // The chip flies off the face toward the blast.
            Vec3 out = c.subtract(Vec3.atCenterOf(p)).normalize();
            Vec3 at = Vec3.atCenterOf(p).add(out.scale(0.7));
            ItemEntity chip = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(ModVoid.BEDROCK_FRAGMENT.get()));
            chip.setDeltaMovement(out.scale(0.35).add(0, 0.25, 0));
            chip.setPickUpDelay(10);
            level.addFreshEntity(chip);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BEDROCK.defaultBlockState()), at.x, at.y, at.z, 24, 0.2, 0.2, 0.2, 0.15);
            level.playSound(null, p, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.6F, 0.5F);
            found++;
        }
    }

    private BedrockFragments() {}
}
