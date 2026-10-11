package com.powerfactor.logistics;

import com.powerfactor.machine.ProcessingBlockEntity;
import com.powerfactor.machine.ProcessingMenu;
import com.powerfactor.registry.ModMenus;
import com.powerfactor.upgrade.UpgradeInventory;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import org.jspecify.annotations.Nullable;

public class PumpMenu extends ProcessingMenu {
    public PumpMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, new SimpleContainer(0), UpgradeInventory.client(), new SimpleContainerData(DATA_COUNT), null);
    }

    public PumpMenu(int containerId, Inventory inventory, Container items, UpgradeInventory upgrades, ContainerData data,
            @Nullable ProcessingBlockEntity machine) {
        super(ModMenus.ELECTRIC_PUMP.get(), containerId, items, upgrades, data, machine);
        addUpgradesAndInventory(inventory, 84);
    }
}
