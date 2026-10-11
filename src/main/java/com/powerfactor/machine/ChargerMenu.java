package com.powerfactor.machine;

import com.powerfactor.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ChargerMenu extends MachineMenu {
    // Stored/capacity are in tens of FE so they fit in a synced short.
    public static final int DATA_FE = 0, DATA_STORED = 1, DATA_CAPACITY = 2, DATA_ITEM_PERMILLE = 3;
    public static final int DATA_COUNT = 4;

    public ChargerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, new SimpleContainer(1), new SimpleContainerData(DATA_COUNT));
    }

    public ChargerMenu(int containerId, Inventory inventory, Container slot, ContainerData data) {
        super(ModMenus.CHARGER.get(), containerId, slot, data);
        addSlot(new Slot(slot, 0, 80, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return ChargerBlockEntity.isChargeable(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addPlayerInventory(inventory, 84);
    }

    public int fe() {
        return unsigned(DATA_FE);
    }

    public int stored() {
        return unsigned(DATA_STORED) * 10;
    }

    public int capacity() {
        return unsigned(DATA_CAPACITY) * 10;
    }

    public float itemCharge() {
        return unsigned(DATA_ITEM_PERMILLE) / 1000F;
    }
}
