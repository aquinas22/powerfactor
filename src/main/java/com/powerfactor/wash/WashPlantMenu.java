package com.powerfactor.wash;

import com.powerfactor.machine.MachineMenu;
import com.powerfactor.registry.ModWash;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class WashPlantMenu extends MachineMenu {
    /** The plant schematic runs across the top; feed (2x2) and catch (3x2) sit beneath it, either side of the centre line. */
    public static final int IN_X = 30, IN_Y = 56, OUT_X = 98, OUT_Y = 56;

    public WashPlantMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, null, new SimpleContainer(WashPlantBlockEntity.INPUTS + WashPlantBlockEntity.OUTPUTS),
                new SimpleContainerData(WashPlantBlockEntity.DATA_COUNT));
    }

    WashPlantMenu(int id, Inventory inventory, WashPlantBlockEntity be, Container slots, ContainerData data) {
        super(ModWash.WASH_PLANT_MENU.get(), id, slots, data);
        for (int i = 0; i < WashPlantBlockEntity.INPUTS; i++) {
            addSlot(new Slot(slots, i, IN_X + (i % 2) * 18, IN_Y + (i / 2) * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return WashPlantBlockEntity.washable(stack);
                }
            });
        }
        for (int i = 0; i < WashPlantBlockEntity.OUTPUTS; i++) {
            addSlot(new OutputSlot(slots, WashPlantBlockEntity.INPUTS + i, OUT_X + (i % 3) * 18, OUT_Y + (i / 3) * 18));
        }
        addUpgradeBay(be == null ? com.powerfactor.upgrade.UpgradeInventory.client(com.powerfactor.upgrade.UpgradeInventory.CONSOLE) : be.upgrades());
        addPlayerInventory(inventory, 108);
    }

    public float progress() {
        return data.get(WashPlantBlockEntity.DATA_PROGRESS) / (float) WashPlantBlockEntity.TIME;
    }

    public int water() {
        return unsigned(WashPlantBlockEntity.DATA_WATER);
    }

    public int energy() {
        return unsigned(WashPlantBlockEntity.DATA_ENERGY) * 100;
    }

    public int state() {
        return data.get(WashPlantBlockEntity.DATA_STATE);
    }

    public int metal() {
        return data.get(WashPlantBlockEntity.DATA_METAL) - 1;
    }

    public boolean formed() {
        return data.get(WashPlantBlockEntity.DATA_FORMED) != 0;
    }

    public int washed() {
        return data.get(WashPlantBlockEntity.DATA_WASHED);
    }
}
