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

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(PowerFactor.id(name)));
    }

    private ModSounds() {}
}
