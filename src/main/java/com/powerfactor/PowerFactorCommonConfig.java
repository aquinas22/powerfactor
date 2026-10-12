package com.powerfactor;

import java.util.LinkedHashMap;
import java.util.Map;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Common config (loaded before worlds and datapacks): world generation switches. */
public final class PowerFactorCommonConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final Map<String, ModConfigSpec.BooleanValue> ORE_GEN = new LinkedHashMap<>();

    static {
        BUILDER.comment("Ore generation. Turn one off if another mod in your pack already provides that metal;",
                "PowerFactor's machines and recipes use common c: tags, so other mods' ores work everywhere.").push("worldgen");
        for (String ore : new String[] {"zinc", "tin", "lead", "silver", "bauxite"}) {
            ORE_GEN.put(ore, BUILDER.define("generate_" + ore + "_ore", true));
        }
    }

    public static final ModConfigSpec.BooleanValue RUBBER_TREES = BUILDER.define("generate_rubber_trees", true);
    public static final ModConfigSpec.BooleanValue OIL = BUILDER.comment("Underground crude oil reservoirs and desert tar pits")
            .define("generate_oil", true);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean enabled(String feature) {
        if (!SPEC.isLoaded()) {
            return true;
        }
        if (feature.equals("rubber_trees")) {
            return RUBBER_TREES.get();
        }
        if (feature.equals("oil")) {
            return OIL.get();
        }
        var value = ORE_GEN.get(feature);
        return value == null || value.get();
    }

    private PowerFactorCommonConfig() {}
}
