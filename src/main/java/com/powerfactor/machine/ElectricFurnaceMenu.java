package com.powerfactor.machine;

import com.powerfactor.registry.ModMenus;
import com.powerfactor.upgrade.UpgradeInventory;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

public class ElectricFurnaceMenu extends ProcessingMenu {
    public ElectricFurnaceMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, new SimpleContainer(2), UpgradeInventory.client(), new SimpleContainerData(DATA_COUNT), null);
        this.pos = extraData.readBlockPos();
    }

    public ElectricFurnaceMenu(int containerId, Inventory inventory, Container items, UpgradeInventory upgrades, ContainerData data,
            @Nullable ProcessingBlockEntity machine) {
        super(ModMenus.ELECTRIC_FURNACE.get(), containerId, items, upgrades, data, machine);
        addSlot(new Slot(items, ElectricFurnaceBlockEntity.INPUT, 71, 35));
        addSlot(new OutputSlot(items, ElectricFurnaceBlockEntity.OUTPUT, 122, 35));
        addUpgradesAndInventory(inventory, INVENTORY_V2);
    }
}
