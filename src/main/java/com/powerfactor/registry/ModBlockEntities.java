package com.powerfactor.registry;

import com.powerfactor.PowerFactor;
import com.powerfactor.logistics.FluidTankBlockEntity;
import com.powerfactor.logistics.PipeBlockEntity;
import com.powerfactor.logistics.PumpBlockEntity;
import com.powerfactor.engine.EngineBlockEntity;
import com.powerfactor.machine.AutoCapacitorBankBlockEntity;
import com.powerfactor.machine.CapacitorBankBlockEntity;
import com.powerfactor.machine.ChargerBlockEntity;
import com.powerfactor.machine.CoalTurbineBlockEntity;
import com.powerfactor.machine.CrusherBlockEntity;
import com.powerfactor.machine.RecipeMachine;
import com.powerfactor.machine.RecipeMachineBlockEntity;
import com.powerfactor.machine.ElectricFurnaceBlockEntity;
import com.powerfactor.scada.ScadaTerminalBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PowerFactor.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CoalTurbineBlockEntity>> COAL_TURBINE = BLOCK_ENTITIES.register("coal_turbine",
            () -> new BlockEntityType<>(CoalTurbineBlockEntity::new, ModBlocks.COAL_TURBINE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EngineBlockEntity>> ENGINE = BLOCK_ENTITIES.register("engine",
            () -> new BlockEntityType<>(EngineBlockEntity::new, ModBlocks.REDSTONE_ENGINE.get(), ModBlocks.STIRLING_ENGINE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrusherBlockEntity>> CRUSHER = BLOCK_ENTITIES.register("crusher",
            () -> new BlockEntityType<>(CrusherBlockEntity::new, ModBlocks.CRUSHER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectricFurnaceBlockEntity>> ELECTRIC_FURNACE = BLOCK_ENTITIES.register("electric_furnace",
            () -> new BlockEntityType<>(ElectricFurnaceBlockEntity::new, ModBlocks.ELECTRIC_FURNACE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChargerBlockEntity>> CHARGER = BLOCK_ENTITIES.register("charger",
            () -> new BlockEntityType<>(ChargerBlockEntity::new, ModBlocks.CHARGER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CapacitorBankBlockEntity>> CAPACITOR_BANK = BLOCK_ENTITIES.register("capacitor_bank",
            () -> new BlockEntityType<>(CapacitorBankBlockEntity::new, ModBlocks.CAPACITOR_BANK.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutoCapacitorBankBlockEntity>> AUTO_CAPACITOR_BANK = BLOCK_ENTITIES.register("auto_capacitor_bank",
            () -> new BlockEntityType<>(AutoCapacitorBankBlockEntity::new, ModBlocks.AUTO_CAPACITOR_BANK.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ScadaTerminalBlockEntity>> SCADA_TERMINAL = BLOCK_ENTITIES.register("scada_terminal",
            () -> new BlockEntityType<>(ScadaTerminalBlockEntity::new, ModBlocks.SCADA_TERMINAL.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.powerfactor.scada.ScadaDisplayBlockEntity>> SCADA_DISPLAY =
            BLOCK_ENTITIES.register("scada_display",
                    () -> new BlockEntityType<>(com.powerfactor.scada.ScadaDisplayBlockEntity::new, ModBlocks.SCADA_DISPLAY.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.powerfactor.ender.EnderLinkBlockEntity>> ENDER_LINK =
            BLOCK_ENTITIES.register("ender_link", () -> new BlockEntityType<>(com.powerfactor.ender.EnderLinkBlockEntity::new,
                    ModBlocks.ENDER_TRANSMITTER.get(), ModBlocks.ENDER_RECEIVER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.powerfactor.grid.BreakerBlockEntity>> BREAKER =
            BLOCK_ENTITIES.register("circuit_breaker", () -> new BlockEntityType<>(com.powerfactor.grid.BreakerBlockEntity::new,
                    ModBlocks.CIRCUIT_BREAKER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.powerfactor.grid.TransformerBlockEntity>> TRANSFORMER =
            BLOCK_ENTITIES.register("transformer", () -> new BlockEntityType<>(com.powerfactor.grid.TransformerBlockEntity::new,
                    ModBlocks.TRANSFORMER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.powerfactor.grid.PylonBlockEntity>> PYLON =
            BLOCK_ENTITIES.register("hv_pylon", () -> new BlockEntityType<>(com.powerfactor.grid.PylonBlockEntity::new, ModBlocks.HV_PYLON.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.powerfactor.generator.SolarPanelBlockEntity>> SOLAR_PANEL =
            BLOCK_ENTITIES.register("solar_panel", () -> new BlockEntityType<>(com.powerfactor.generator.SolarPanelBlockEntity::new, ModBlocks.SOLAR_PANEL.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.powerfactor.generator.WindTurbineBlockEntity>> WIND_TURBINE =
            BLOCK_ENTITIES.register("wind_turbine", () -> new BlockEntityType<>(com.powerfactor.generator.WindTurbineBlockEntity::new, ModBlocks.WIND_TURBINE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.powerfactor.generator.HydroTurbineBlockEntity>> HYDRO_TURBINE =
            BLOCK_ENTITIES.register("hydro_turbine", () -> new BlockEntityType<>(com.powerfactor.generator.HydroTurbineBlockEntity::new, ModBlocks.HYDRO_TURBINE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.powerfactor.generator.DieselGeneratorBlockEntity>> DIESEL_GENERATOR =
            BLOCK_ENTITIES.register("diesel_generator", () -> new BlockEntityType<>(com.powerfactor.generator.DieselGeneratorBlockEntity::new, ModBlocks.DIESEL_GENERATOR.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.powerfactor.generator.SynchronousCondenserBlockEntity>> SYNCHRONOUS_CONDENSER =
            BLOCK_ENTITIES.register("synchronous_condenser", () -> new BlockEntityType<>(com.powerfactor.generator.SynchronousCondenserBlockEntity::new, ModBlocks.SYNCHRONOUS_CONDENSER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.powerfactor.generator.BatteryBankBlockEntity>> BATTERY_BANK =
            BLOCK_ENTITIES.register("battery_bank", () -> new BlockEntityType<>(com.powerfactor.generator.BatteryBankBlockEntity::new, ModBlocks.BATTERY_BANK.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.powerfactor.generator.PowerMeterBlockEntity>> POWER_METER =
            BLOCK_ENTITIES.register("power_meter", () -> new BlockEntityType<>(com.powerfactor.generator.PowerMeterBlockEntity::new, ModBlocks.POWER_METER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.powerfactor.device.TeslaCoilBlockEntity>> TESLA_COIL =
            BLOCK_ENTITIES.register("tesla_coil", () -> new BlockEntityType<>(com.powerfactor.device.TeslaCoilBlockEntity::new, ModBlocks.TESLA_COIL.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.powerfactor.device.GrowLampBlockEntity>> GROW_LAMP =
            BLOCK_ENTITIES.register("grow_lamp", () -> new BlockEntityType<>(com.powerfactor.device.GrowLampBlockEntity::new, ModBlocks.GROW_LAMP.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.powerfactor.device.ChargingPadBlockEntity>> CHARGING_PAD =
            BLOCK_ENTITIES.register("charging_pad", () -> new BlockEntityType<>(com.powerfactor.device.ChargingPadBlockEntity::new, ModBlocks.CHARGING_PAD.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.powerfactor.device.ElectrolyzerBlockEntity>> ELECTROLYZER =
            BLOCK_ENTITIES.register("electrolyzer", () -> new BlockEntityType<>(com.powerfactor.device.ElectrolyzerBlockEntity::new, ModBlocks.ELECTROLYZER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.powerfactor.generator.FuelCellBlockEntity>> FUEL_CELL =
            BLOCK_ENTITIES.register("fuel_cell", () -> new BlockEntityType<>(com.powerfactor.generator.FuelCellBlockEntity::new, ModBlocks.FUEL_CELL.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.powerfactor.upgrade.UpgradeTableBlockEntity>> UPGRADE_TABLE =
            BLOCK_ENTITIES.register("upgrade_table", () -> new BlockEntityType<>(com.powerfactor.upgrade.UpgradeTableBlockEntity::new, ModBlocks.UPGRADE_TABLE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.powerfactor.radio.RadioTowerBlockEntity>> RADIO_TOWER =
            BLOCK_ENTITIES.register("radio_tower", () -> new BlockEntityType<>(com.powerfactor.radio.RadioTowerBlockEntity::new, ModBlocks.RADIO_TOWER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.powerfactor.oil.PumpjackBlockEntity>> PUMPJACK =
            BLOCK_ENTITIES.register("pumpjack", () -> new BlockEntityType<>(com.powerfactor.oil.PumpjackBlockEntity::new, ModBlocks.PUMPJACK.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.powerfactor.oil.RefineryBlockEntity>> REFINERY =
            BLOCK_ENTITIES.register("refinery", () -> new BlockEntityType<>(com.powerfactor.oil.RefineryBlockEntity::new, ModBlocks.REFINERY.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.powerfactor.multiblock.CasingBlockEntity>> CASING =
            BLOCK_ENTITIES.register("casing", () -> new BlockEntityType<>(com.powerfactor.multiblock.CasingBlockEntity::new,
                    ModBlocks.REFINERY_CASING.get(), ModBlocks.DISTILLATION_COLUMN.get(), ModBlocks.PUMPJACK_CASING.get(),
                    ModBlocks.BOILER_CASING.get(), ModBlocks.BOILER_FIREBOX.get(), ModBlocks.STEAM_DRUM.get(), ModBlocks.TURBINE_CASING.get(),
                    ModBlocks.STEAM_ROTOR.get(), ModBlocks.GENERATOR_MAGNET.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidTankBlockEntity>> FLUID_TANK = BLOCK_ENTITIES.register("fluid_tank",
            () -> new BlockEntityType<>(FluidTankBlockEntity::new, ModBlocks.FLUID_TANK.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PumpBlockEntity>> ELECTRIC_PUMP = BLOCK_ENTITIES.register("electric_pump",
            () -> new BlockEntityType<>(PumpBlockEntity::new, ModBlocks.ELECTRIC_PUMP.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PipeBlockEntity>> PIPE = BLOCK_ENTITIES.register("pipe",
            () -> new BlockEntityType<>(PipeBlockEntity::new, ModBlocks.FLUID_PIPE.get(), ModBlocks.ITEM_PIPE.get(), ModBlocks.STEAM_PIPE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.powerfactor.steam.SteamBoilerBlockEntity>> STEAM_BOILER =
            BLOCK_ENTITIES.register("steam_boiler", () -> new BlockEntityType<>(com.powerfactor.steam.SteamBoilerBlockEntity::new, ModBlocks.STEAM_BOILER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.powerfactor.steam.SteamTurbineBlockEntity>> STEAM_TURBINE =
            BLOCK_ENTITIES.register("steam_turbine", () -> new BlockEntityType<>(com.powerfactor.steam.SteamTurbineBlockEntity::new, ModBlocks.STEAM_TURBINE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.powerfactor.steam.CoolingTowerBlockEntity>> COOLING_TOWER =
            BLOCK_ENTITIES.register("cooling_tower", () -> new BlockEntityType<>(com.powerfactor.steam.CoolingTowerBlockEntity::new, ModBlocks.COOLING_TOWER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.powerfactor.generator.AmbientGeneratorBlockEntity>> GEOTHERMAL_GENERATOR =
            BLOCK_ENTITIES.register("geothermal_generator", () -> new BlockEntityType<>((pos, state) -> new com.powerfactor.generator.AmbientGeneratorBlockEntity(false, pos, state), ModBlocks.GEOTHERMAL_GENERATOR.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.powerfactor.generator.AmbientGeneratorBlockEntity>> THERMOELECTRIC_GENERATOR =
            BLOCK_ENTITIES.register("thermoelectric_generator", () -> new BlockEntityType<>((pos, state) -> new com.powerfactor.generator.AmbientGeneratorBlockEntity(true, pos, state), ModBlocks.THERMOELECTRIC_GENERATOR.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.powerfactor.device.RainCollectorBlockEntity>> RAIN_COLLECTOR =
            BLOCK_ENTITIES.register("rain_collector", () -> new BlockEntityType<>(com.powerfactor.device.RainCollectorBlockEntity::new, ModBlocks.RAIN_COLLECTOR.get()));

    public static final java.util.Map<RecipeMachine, DeferredHolder<BlockEntityType<?>, BlockEntityType<RecipeMachineBlockEntity>>> RECIPE_MACHINES =
            new java.util.EnumMap<>(RecipeMachine.class);

    static {
        for (RecipeMachine m : RecipeMachine.values()) {
            RECIPE_MACHINES.put(m, BLOCK_ENTITIES.register(m.id,
                    () -> new BlockEntityType<>((pos, state) -> new RecipeMachineBlockEntity(m, pos, state), ModBlocks.RECIPE_MACHINES.get(m).get())));
        }
    }

    private ModBlockEntities() {}
}
