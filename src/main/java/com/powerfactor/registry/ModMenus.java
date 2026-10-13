package com.powerfactor.registry;

import com.powerfactor.PowerFactor;
import com.powerfactor.engine.EngineMenu;
import com.powerfactor.machine.AutoCapacitorMenu;
import com.powerfactor.machine.ChargerMenu;
import com.powerfactor.machine.CoalTurbineMenu;
import com.powerfactor.machine.CrusherMenu;
import com.powerfactor.machine.RecipeMachine;
import com.powerfactor.machine.RecipeMachineMenu;
import com.powerfactor.machine.ElectricFurnaceMenu;
import com.powerfactor.scada.ScadaTerminalMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, PowerFactor.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<CoalTurbineMenu>> COAL_TURBINE = MENUS.register("coal_turbine",
            () -> IMenuTypeExtension.create(CoalTurbineMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<EngineMenu>> ENGINE = MENUS.register("engine",
            () -> IMenuTypeExtension.create(EngineMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<CrusherMenu>> CRUSHER = MENUS.register("crusher",
            () -> IMenuTypeExtension.create(CrusherMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<ElectricFurnaceMenu>> ELECTRIC_FURNACE = MENUS.register("electric_furnace",
            () -> IMenuTypeExtension.create(ElectricFurnaceMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<ChargerMenu>> CHARGER = MENUS.register("charger",
            () -> IMenuTypeExtension.create(ChargerMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<AutoCapacitorMenu>> AUTO_CAPACITOR_BANK = MENUS.register("auto_capacitor_bank",
            () -> IMenuTypeExtension.create(AutoCapacitorMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<ScadaTerminalMenu>> SCADA_TERMINAL = MENUS.register("scada_terminal",
            () -> IMenuTypeExtension.create(ScadaTerminalMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<com.powerfactor.ender.EnderLinkMenu>> ENDER_LINK = MENUS.register("ender_link",
            () -> IMenuTypeExtension.create(com.powerfactor.ender.EnderLinkMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<com.powerfactor.generator.DieselMenu>> DIESEL_GENERATOR = MENUS.register(
            "diesel_generator", () -> IMenuTypeExtension.create(com.powerfactor.generator.DieselMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<com.powerfactor.generator.DieselMenu.Battery>> BATTERY_BANK = MENUS.register(
            "battery_bank", () -> IMenuTypeExtension.create(com.powerfactor.generator.DieselMenu.Battery::new));
    public static final DeferredHolder<MenuType<?>, MenuType<com.powerfactor.generator.DieselMenu.Meter>> POWER_METER = MENUS.register(
            "power_meter", () -> IMenuTypeExtension.create(com.powerfactor.generator.DieselMenu.Meter::new));
    public static final DeferredHolder<MenuType<?>, MenuType<com.powerfactor.generator.DieselMenu.Electrolyzer>> ELECTROLYZER = MENUS.register(
            "electrolyzer", () -> IMenuTypeExtension.create(com.powerfactor.generator.DieselMenu.Electrolyzer::new));
    public static final DeferredHolder<MenuType<?>, MenuType<com.powerfactor.generator.DieselMenu.FuelCell>> FUEL_CELL = MENUS.register(
            "fuel_cell", () -> IMenuTypeExtension.create(com.powerfactor.generator.DieselMenu.FuelCell::new));
    public static final DeferredHolder<MenuType<?>, MenuType<com.powerfactor.upgrade.UpgradeTableMenu>> UPGRADE_TABLE = MENUS.register(
            "upgrade_table", () -> IMenuTypeExtension.create(com.powerfactor.upgrade.UpgradeTableMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<com.powerfactor.generator.DieselMenu.Pumpjack>> PUMPJACK = MENUS.register(
            "pumpjack", () -> IMenuTypeExtension.create(com.powerfactor.generator.DieselMenu.Pumpjack::new));
    public static final DeferredHolder<MenuType<?>, MenuType<com.powerfactor.generator.DieselMenu.Refinery>> REFINERY = MENUS.register(
            "refinery", () -> IMenuTypeExtension.create(com.powerfactor.generator.DieselMenu.Refinery::new));
    public static final DeferredHolder<MenuType<?>, MenuType<com.powerfactor.item.DrillMenu>> DRILL = MENUS.register("drill",
            () -> IMenuTypeExtension.create(com.powerfactor.item.DrillMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<com.powerfactor.logistics.PumpMenu>> ELECTRIC_PUMP = MENUS.register("electric_pump",
            () -> IMenuTypeExtension.create(com.powerfactor.logistics.PumpMenu::new));

    public static final java.util.Map<RecipeMachine, DeferredHolder<MenuType<?>, MenuType<RecipeMachineMenu>>> RECIPE_MACHINES =
            new java.util.EnumMap<>(RecipeMachine.class);

    static {
        for (RecipeMachine m : RecipeMachine.values()) {
            RECIPE_MACHINES.put(m, MENUS.register(m.id,
                    () -> IMenuTypeExtension.create((id, inv, buf) -> new RecipeMachineMenu(m, id, inv, buf))));
        }
    }

    private ModMenus() {}

    public static final DeferredHolder<MenuType<?>, MenuType<com.powerfactor.steam.SteamBoilerMenu>> STEAM_BOILER = MENUS.register("steam_boiler",
            () -> IMenuTypeExtension.create(com.powerfactor.steam.SteamBoilerMenu::new));
    public static final java.util.Map<com.powerfactor.steam.ThermalMenu.Device, DeferredHolder<MenuType<?>, MenuType<com.powerfactor.steam.ThermalMenu>>> THERMAL =
            new java.util.EnumMap<>(com.powerfactor.steam.ThermalMenu.Device.class);
    static {
        for (var device : com.powerfactor.steam.ThermalMenu.Device.values()) {
            THERMAL.put(device, MENUS.register(device.id, () -> IMenuTypeExtension.create((id, inventory, data) ->
                    new com.powerfactor.steam.ThermalMenu(device, id, inventory, data))));
        }
    }
}
