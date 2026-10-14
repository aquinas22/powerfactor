package com.powerfactor.registry;

import com.powerfactor.PowerFactor;
import com.powerfactor.world.QuantumLabFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Code-driven worldgen features; their placement lives in data (configured/placed features, biome modifiers). */
public final class ModWorldgen {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, PowerFactor.MODID);

    public static final DeferredHolder<Feature<?>, QuantumLabFeature> QUANTUM_LAB = FEATURES.register("quantum_lab",
            () -> new QuantumLabFeature(NoneFeatureConfiguration.CODEC));

    private ModWorldgen() {}
}
