package com.powerfactor.steam;

import com.powerfactor.generator.DieselMenu;
import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import org.jspecify.annotations.Nullable;

/** Shared telemetry menus for the steam, cooling and passive water/heat machines. */
public final class ThermalMenu extends DieselMenu {
    public enum Device {
        STEAM_TURBINE("steam_turbine"), COOLING_TOWER("cooling_tower"), GEOTHERMAL("geothermal_generator"),
        THERMOELECTRIC("thermoelectric_generator"), RAIN_COLLECTOR("rain_collector");
        public final String id;
        Device(String id) { this.id = id; }
    }
    public static final int DATA_RATE = 9;
    public final Device device;
    public ThermalMenu(Device device, int id, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(device, id, inventory, data.readBlockPos(), new SimpleContainerData(DATA_COUNT), null);
    }
    public ThermalMenu(Device device, int id, Inventory inventory, BlockPos pos, ContainerData data, @Nullable MachineBlockEntity owner) {
        super(ModMenus.THERMAL.get(device).get(), id, inventory, pos, data, owner);
        this.device = device;
    }
}
