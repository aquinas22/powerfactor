package com.powerfactor.chem;

import com.powerfactor.PowerFactor;
import com.powerfactor.registry.ModFluids;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Sulfuric acid burns: creatures in it take a heart a second and fizz; dropped items dissolve in about ten seconds. */
@EventBusSubscriber(modid = PowerFactor.MODID)
public final class AcidEffects {
    @SubscribeEvent
    public static void tick(EntityTickEvent.Post event) {
        var e = event.getEntity();
        if (!(e.level() instanceof ServerLevel level) || e.tickCount % 20 != 0) return;
        if (!e.isInFluidType(ModFluids.SULFURIC_ACID.type().get())) return;
        if (e instanceof LivingEntity living) {
            living.hurtServer(level, level.damageSources().magic(), 2F);
        } else if (e instanceof ItemEntity item && item.tickCount > 200) {
            item.discard();
        } else {
            return;
        }
        level.sendParticles(ParticleTypes.SMOKE, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 4, 0.2, 0.2, 0.2, 0.01);
        level.playSound(null, e.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 1.6F);
    }

    private AcidEffects() {}
}
