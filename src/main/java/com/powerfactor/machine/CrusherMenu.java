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

public class CrusherMenu extends ProcessingMenu {
    public CrusherMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, new SimpleContainer(3), UpgradeInventory.client(), new SimpleContainerData(DATA_COUNT), null);
    }

    public CrusherMenu(int containerId, Inventory inventory, Container items, UpgradeInventory upgrades, ContainerData data,
            @Nullable ProcessingBlockEntity machine) {
        super(ModMenus.CRUSHER.get(), containerId, items, upgrades, data, machine);
        addSlot(new Slot(items, CrusherBlockEntity.INPUT, 44, 35));
        addSlot(new OutputSlot(items, CrusherBlockEntity.OUTPUT, 104, 35));
        addSlot(new OutputSlot(items, CrusherBlockEntity.BONUS, 130, 35));
        addUpgradesAndInventory(inventory, 84);
    }
}
