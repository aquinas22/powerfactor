package com.powerfactor.registry;

import com.powerfactor.PowerFactor;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Every event here has a synthesized .ogg from tools/gen_sounds.py and an entry in sounds.json. */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, PowerFactor.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> TURBINE_RUN = register("turbine_run");
    public static final DeferredHolder<SoundEvent, SoundEvent> TURBINE_BREAKER_CLOSE = register("turbine_breaker_close");
    public static final DeferredHolder<SoundEvent, SoundEvent> TURBINE_BREAKER_OPEN = register("turbine_breaker_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRUSHER_RUN = register("crusher_run");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRUSHER_IDLE = register("crusher_idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> MOTOR_TRIP = register("motor_trip");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELECTRIC_FURNACE_RUN = register("electric_furnace_run");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHARGER_RUN = register("charger_run");
    public static final DeferredHolder<SoundEvent, SoundEvent> ENGINE_CHUFF = register("engine_chuff");
    public static final DeferredHolder<SoundEvent, SoundEvent> ENGINE_SEIZE = register("engine_seize");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAPACITOR_STEP_IN = register("capacitor_step_in");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAPACITOR_STEP_OUT = register("capacitor_step_out");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCADA_CLICK = register("scada_click");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCADA_ALARM = register("scada_alarm");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARC_ZAP = register("arc_zap");
    // Second batch (tools/gen_sounds.py).
    public static final DeferredHolder<SoundEvent, SoundEvent> ALLOY_FURNACE_RUN = register("alloy_furnace_run");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARC_FURNACE_RUN = register("arc_furnace_run");
    public static final DeferredHolder<SoundEvent, SoundEvent> PUMP_RUN = register("pump_run");
    public static final DeferredHolder<SoundEvent, SoundEvent> ORE_WASHER_RUN = register("ore_washer_run");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROLLING_MILL_RUN = register("rolling_mill_run");
    public static final DeferredHolder<SoundEvent, SoundEvent> WIRE_DRAWER_RUN = register("wire_drawer_run");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWORD_HUM = register("sword_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRILL_RUN = register("drill_run");
    public static final DeferredHolder<SoundEvent, SoundEvent> WIRELESS_HUM = register("wireless_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> TRANSFORMER_HUM = register("transformer_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> WIND_TURBINE_RUN = register("wind_turbine_run");
    public static final DeferredHolder<SoundEvent, SoundEvent> HYDRO_TURBINE_RUN = register("hydro_turbine_run");
    public static final DeferredHolder<SoundEvent, SoundEvent> DIESEL_RUN = register("diesel_run");
    public static final DeferredHolder<SoundEvent, SoundEvent> CONDENSER_RUN = register("condenser_run");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWORD_IGNITE = register("sword_ignite");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWORD_RETRACT = register("sword_retract");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWORD_SWING = register("sword_swing");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWORD_HIT = register("sword_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARC_GUN_CHARGE = register("arc_gun_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARC_GUN_FIRE = register("arc_gun_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRILL_START = register("drill_start");
    public static final DeferredHolder<SoundEvent, SoundEvent> BREAKER_TRIP = register("breaker_trip");
    public static final DeferredHolder<SoundEvent, SoundEvent> WIRELESS_SURGE = register("wireless_surge");
    public static final DeferredHolder<SoundEvent, SoundEvent> TRANSFORMER_OVERLOAD = register("transformer_buzz_overload");
    public static final DeferredHolder<SoundEvent, SoundEvent> PIPE_CLICK = register("pipe_click");
    public static final DeferredHolder<SoundEvent, SoundEvent> TANK_SLOSH = register("tank_slosh");
    public static final DeferredHolder<SoundEvent, SoundEvent> WIND_TURBINE_SWOOSH = register("wind_turbine_swoosh");
    public static final DeferredHolder<SoundEvent, SoundEvent> WIND_TURBINE_BRAKE = register("wind_turbine_brake");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHAINSAW_IDLE = register("chainsaw_idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHAINSAW_CUT = register("chainsaw_cut");
    public static final DeferredHolder<SoundEvent, SoundEvent> TREE_FALL = register("tree_fall");
    public static final DeferredHolder<SoundEvent, SoundEvent> JETPACK_THRUST = register("jetpack_thrust");
    public static final DeferredHolder<SoundEvent, SoundEvent> METER_BEEP = register("meter_beep");
    public static final DeferredHolder<SoundEvent, SoundEvent> TESLA_ZAP = register("tesla_zap");
    public static final DeferredHolder<SoundEvent, SoundEvent> TESLA_HUM = register("tesla_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> INDUCTION_HUM = register("induction_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELECTROLYZER_RUN = register("electrolyzer_run");
    public static final DeferredHolder<SoundEvent, SoundEvent> FUEL_CELL_RUN = register("fuel_cell_run");
    public static final DeferredHolder<SoundEvent, SoundEvent> UPGRADE_INSTALL = register("upgrade_install");
    public static final DeferredHolder<SoundEvent, SoundEvent> UPGRADE_REMOVE = register("upgrade_remove");
    public static final DeferredHolder<SoundEvent, SoundEvent> WIRE_SHOCK = register("wire_shock");
    public static final DeferredHolder<SoundEvent, SoundEvent> WRENCH = register("wrench");
    public static final DeferredHolder<SoundEvent, SoundEvent> LASER_ENGRAVER_RUN = register("laser_engraver_run");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(PowerFactor.id(name)));
    }

    private ModSounds() {}
}
