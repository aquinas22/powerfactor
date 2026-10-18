package com.powerfactor.quantum;

import com.powerfactor.machine.MachineMenu;
import com.powerfactor.registry.ModQuantum;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;

public class StarforgeMenu extends MachineMenu {
    public static final int[][] SLOT_XY = {{30, 26}, {30, 50}, {30, 74}, {80, 50}, {132, 50}};   // tools left, catalyst on the centre line, output right

    public StarforgeMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, null, new SimpleContainer(5), new SimpleContainerData(StarforgeBlockEntity.DATA_COUNT));
    }

    StarforgeMenu(int id, Inventory inventory, StarforgeBlockEntity be, Container slots, ContainerData data) {
        super(ModQuantum.STARFORGE_MENU.get(), id, slots, data);
        for (int i = 0; i < 4; i++) {
            addSlot(new Slot(slots, i, SLOT_XY[i][0], SLOT_XY[i][1]) {
                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        addSlot(new OutputSlot(slots, StarforgeBlockEntity.OUTPUT, SLOT_XY[4][0], SLOT_XY[4][1]));
        addUpgradeBay(be == null ? com.powerfactor.upgrade.UpgradeInventory.client(com.powerfactor.upgrade.UpgradeInventory.CONSOLE) : be.upgrades());
        addPlayerInventory(inventory, 108);
    }

    public float progress() {
        return data.get(StarforgeBlockEntity.DATA_PROGRESS) / 1000F;
    }

    public boolean formed() {
        return data.get(StarforgeBlockEntity.DATA_FORMED) == 1;
    }

    public long energy() {
        return (long) unsigned(StarforgeBlockEntity.DATA_ENERGY_HI) << 14 | (data.get(StarforgeBlockEntity.DATA_ENERGY_LO) & 0x3FFF);
    }

    public int rate() {
        return unsigned(StarforgeBlockEntity.DATA_RATE) * 100;
    }

    public int recipe() {
        return data.get(StarforgeBlockEntity.DATA_RECIPE) - 1;
    }
}
