package com.powerfactor.registry;

import com.powerfactor.PowerFactor;
import com.powerfactor.movement.GrappleHookEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, PowerFactor.MODID);
    public static final DeferredHolder<EntityType<?>, EntityType<GrappleHookEntity>> GRAPPLE_HOOK = ENTITIES.register("grapple_hook",
            () -> EntityType.Builder.<GrappleHookEntity>of(GrappleHookEntity::new, MobCategory.MISC).sized(0.2F, 0.2F)
                    .clientTrackingRange(12).updateInterval(1).build(ResourceKey.create(Registries.ENTITY_TYPE, PowerFactor.id("grapple_hook"))));
    public static final DeferredHolder<EntityType<?>, EntityType<com.powerfactor.vehicle.RoverEntity>> ELECTRIC_ROVER = ENTITIES.register("electric_rover",
            () -> EntityType.Builder.<com.powerfactor.vehicle.RoverEntity>of(com.powerfactor.vehicle.RoverEntity::new, MobCategory.MISC).sized(2.3F, 1.7F)
                    .clientTrackingRange(12).updateInterval(1).build(ResourceKey.create(Registries.ENTITY_TYPE, PowerFactor.id("electric_rover"))));
    private ModEntities() {}
}
