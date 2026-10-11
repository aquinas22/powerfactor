package com.powerfactor.registry;

import com.powerfactor.PowerFactor;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Hydrogen: a gas, so it has no bucket and no block; it lives in tanks and pipes. */
public final class ModFluids {
    public static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, PowerFactor.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, PowerFactor.MODID);

    public static final DeferredHolder<FluidType, FluidType> HYDROGEN_TYPE = TYPES.register("hydrogen", () -> new FluidType(
            FluidType.Properties.create().descriptionId("fluid.powerfactor.hydrogen").density(-90).viscosity(9).temperature(293)));

    public static final DeferredHolder<Fluid, FlowingFluid> HYDROGEN = FLUIDS.register("hydrogen",
            () -> new BaseFlowingFluid.Source(hydrogenProperties()));
    public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_HYDROGEN = FLUIDS.register("flowing_hydrogen",
            () -> new BaseFlowingFluid.Flowing(hydrogenProperties()));

    private static BaseFlowingFluid.Properties hydrogenProperties() {
        return new BaseFlowingFluid.Properties(HYDROGEN_TYPE, HYDROGEN, FLOWING_HYDROGEN);
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        FLUIDS.register(bus);
    }

    private ModFluids() {}
}
