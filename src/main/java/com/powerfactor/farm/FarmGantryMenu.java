package com.powerfactor.farm;

import com.powerfactor.machine.MachineMenu;
import com.powerfactor.registry.ModFarm;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class FarmGantryMenu extends MachineMenu {
    /** Seeds (3x3) on the left, harvest (3x3) on the right; the field map is the hero between them. */
    public static final int SEED_X = 8, OUT_X = 116, GRID_Y = 34;

    public FarmGantryMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, null, new SimpleContainer(FarmGantryBlockEntity.SEEDS + FarmGantryBlockEntity.OUT),
                new SimpleContainerData(FarmGantryBlockEntity.DATA_COUNT));
    }

    FarmGantryMenu(int id, Inventory inventory, FarmGantryBlockEntity be, Container slots, ContainerData data) {
        super(ModFarm.FARM_GANTRY_MENU.get(), id, slots, data);
        for (int i = 0; i < FarmGantryBlockEntity.SEEDS; i++) {
            addSlot(new Slot(slots, i, SEED_X + (i % 3) * 18, GRID_Y + (i / 3) * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return FarmGantryBlockEntity.plantable(stack);
                }
            });
        }
        for (int i = 0; i < FarmGantryBlockEntity.OUT; i++) {
            addSlot(new OutputSlot(slots, FarmGantryBlockEntity.SEEDS + i, OUT_X + (i % 3) * 18, GRID_Y + (i / 3) * 18));
        }
        addPlayerInventory(inventory, 108);
    }

    public int cell() {
        return data.get(FarmGantryBlockEntity.DATA_CELL);
    }

    public int energy() {
        return unsigned(FarmGantryBlockEntity.DATA_ENERGY) * 100;
    }

    public int water() {
        return unsigned(FarmGantryBlockEntity.DATA_WATER);
    }

    public int harvested() {
        return data.get(FarmGantryBlockEntity.DATA_HARVESTED);
    }

    public boolean on() {
        return data.get(FarmGantryBlockEntity.DATA_STATE) != 0;
    }
}
