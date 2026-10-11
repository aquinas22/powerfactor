package com.powerfactor.registry;

import com.mojang.serialization.MapCodec;
import com.powerfactor.PowerFactor;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModConditions {
    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, PowerFactor.MODID);

    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<WorldgenEnabledCondition>> WORLDGEN_ENABLED =
            CONDITIONS.register("worldgen_enabled", () -> WorldgenEnabledCondition.CODEC);

    private ModConditions() {}
}
