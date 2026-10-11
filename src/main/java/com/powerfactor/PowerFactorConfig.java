package com.powerfactor;

import com.powerfactor.power.TurbineModel;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config (per world, synced to clients): turbine balance numbers for modpack makers. */
public final class PowerFactorConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.comment("Coal Turbine").push("coal_turbine");
    }

    public static final ModConfigSpec.IntValue TURBINE_RATED_FE = BUILDER
            .comment("Maximum real power output in FE/t.")
            .defineInRange("rated_fe", 200, 1, 1_000_000);
    public static final ModConfigSpec.IntValue TURBINE_RATED_CE = BUILDER
            .comment("Generator nameplate in CE/t (apparent power). FE output is capped at sqrt(CE^2 - EE^2), so",
                    "reactive (EE) loads reduce the FE available. Must be at least rated_fe; 1.25x gives a 0.8 power factor rating.")
            .defineInRange("rated_ce", 250, 1, 1_000_000);
    public static final ModConfigSpec.IntValue TURBINE_FE_PER_BURN_TICK = BUILDER
            .comment("FE of steam per furnace burn tick of fuel. Coal burns 1600 ticks, so 40 makes one coal worth 64,000 FE.")
            .defineInRange("fe_per_burn_tick", 40, 1, 100_000);
    public static final ModConfigSpec.IntValue TURBINE_IDLE_LOSS = BUILDER
            .comment("FE of steam per tick spent just keeping the rotor at speed, loaded or not.")
            .defineInRange("idle_loss_fe", 10, 0, 1_000_000);
    public static final ModConfigSpec.IntValue TURBINE_SPIN_UP_TICKS = BUILDER
            .comment("Ticks to spin from standstill to 3600 RPM (60 Hz).")
            .defineInRange("spin_up_ticks", 200, 1, 72_000);
    public static final ModConfigSpec.IntValue TURBINE_COAST_DOWN_TICKS = BUILDER
            .comment("Ticks to coast from 3600 RPM to a stop without steam.")
            .defineInRange("coast_down_ticks", 600, 1, 72_000);
    public static final ModConfigSpec.IntValue TURBINE_BUFFER = BUILDER
            .comment("Internal FE buffer.")
            .defineInRange("buffer_fe", 20_000, 0, 10_000_000);

    static {
        BUILDER.pop().comment("Machines").push("machines");
    }

    public static final ModConfigSpec.IntValue CRUSHER_FE_PER_TICK = BUILDER
            .comment("FE/t the crusher draws while running.")
            .defineInRange("crusher_fe_per_tick", 20, 0, 100_000);
    public static final ModConfigSpec.IntValue CRUSHER_EE_PER_TICK = BUILDER
            .comment("EE/t (reactive power) the crusher's motor draws while running. 15 EE on 20 FE is a 0.8 power factor.")
            .defineInRange("crusher_ee_per_tick", 15, 0, 100_000);
    public static final ModConfigSpec.IntValue CAPACITOR_BANK_EE = BUILDER
            .comment("EE/t a capacitor bank supplies to its grid, cancelling motor EE.")
            .defineInRange("capacitor_bank_ee", 60, 0, 100_000);
    public static final ModConfigSpec.IntValue CHARGER_RATE = BUILDER
            .comment("FE/t a charger pushes into the item in its slot.")
            .defineInRange("charger_rate", 1_000, 1, 1_000_000);
    public static final ModConfigSpec.IntValue DRILL_CAPACITY = BUILDER
            .comment("FE an electric drill holds.")
            .defineInRange("drill_capacity", 100_000, 1, 100_000_000);
    public static final ModConfigSpec.IntValue DRILL_FE_PER_BLOCK = BUILDER
            .comment("FE an electric drill uses per block mined.")
            .defineInRange("drill_fe_per_block", 100, 0, 1_000_000);

    static {
        BUILDER.pop().comment("BuildCraft-style engines").push("engines");
    }

    public static final ModConfigSpec.IntValue REDSTONE_ENGINE_FE = BUILDER
            .comment("FE/t a redstone engine makes. It runs on a redstone signal alone and never overheats.")
            .defineInRange("redstone_engine_fe", 4, 0, 100_000);
    public static final ModConfigSpec.IntValue STIRLING_ENGINE_FE = BUILDER
            .comment("FE/t a stirling engine makes while burning fuel and powered by redstone.")
            .defineInRange("stirling_engine_fe", 30, 0, 100_000);
    public static final ModConfigSpec.BooleanValue ENGINES_EXPLODE = BUILDER
            .comment("If true, a stirling engine left at full heat with nowhere to send its power explodes, BuildCraft style.",
                    "If false it just seizes up until it cools down.")
            .define("engines_explode", false);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static TurbineModel.Spec turbineSpec() {
        int fe = TURBINE_RATED_FE.getAsInt();
        return new TurbineModel.Spec(fe, Math.max(fe, TURBINE_RATED_CE.getAsInt()), TURBINE_IDLE_LOSS.getAsInt(),
                TURBINE_SPIN_UP_TICKS.getAsInt(), TURBINE_COAST_DOWN_TICKS.getAsInt());
    }

    private PowerFactorConfig() {}
}
