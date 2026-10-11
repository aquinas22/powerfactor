package com.powerfactor.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.powerfactor.PowerFactorCommonConfig;
import net.neoforged.neoforge.common.conditions.ICondition;

/** {@code {"type": "powerfactor:worldgen_enabled", "feature": "tin"}}: gates biome modifiers on the common config. */
public record WorldgenEnabledCondition(String feature) implements ICondition {
    public static final MapCodec<WorldgenEnabledCondition> CODEC = Codec.STRING.fieldOf("feature").xmap(WorldgenEnabledCondition::new,
            WorldgenEnabledCondition::feature);

    @Override
    public boolean test(IContext context) {
        return PowerFactorCommonConfig.enabled(feature);
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
