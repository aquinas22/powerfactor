package com.powerfactor.engine;

import com.powerfactor.machine.MachineMenu;
import com.powerfactor.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class EngineMenu extends MachineMenu {
    public static final int DATA_BURN_PERMILLE = 0, DATA_HEAT = 1, DATA_STORED = 2, DATA_FE = 3, DATA_FLAGS = 4;
    public static final int DATA_COUNT = 5;
    public static final int CAPACITY = 2_000;

    public EngineMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, new SimpleContainer(1), new SimpleContainerData(DATA_COUNT));
    }

    public EngineMenu(int containerId, Inventory inventory, Container fuel, ContainerData data) {
        super(ModMenus.ENGINE.get(), containerId, fuel, data);
        addSlot(new Slot(fuel, 0, 44, 53) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getBurnTime(null, inventory.player.level().fuelValues()) > 0;
            }
        });
        addPlayerInventory(inventory, 84);
    }

    public float burnProgress() {
        return unsigned(DATA_BURN_PERMILLE) / 1000F;
    }

    public int heat() {
        return unsigned(DATA_HEAT);
    }

    public int stored() {
        return unsigned(DATA_STORED);
    }

    public int fe() {
        return unsigned(DATA_FE);
    }

    public boolean running() {
        return (data.get(DATA_FLAGS) & 1) != 0;
    }

    public boolean seized() {
        return (data.get(DATA_FLAGS) & 2) != 0;
    }
}
