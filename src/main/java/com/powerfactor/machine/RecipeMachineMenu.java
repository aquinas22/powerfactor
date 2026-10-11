package com.powerfactor.machine;

import com.powerfactor.registry.ModItems;
import com.powerfactor.registry.ModMenus;
import com.powerfactor.upgrade.UpgradeInventory;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public class RecipeMachineMenu extends ProcessingMenu {
    private final RecipeMachine machine;

    public RecipeMachineMenu(RecipeMachine machine, int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(machine, containerId, inventory, new SimpleContainer(machine.slotCount()), UpgradeInventory.client(),
                new SimpleContainerData(DATA_COUNT), null);
    }

    public RecipeMachineMenu(RecipeMachine machine, int containerId, Inventory inventory, Container items, UpgradeInventory upgrades,
            ContainerData data, @Nullable ProcessingBlockEntity owner) {
        super(ModMenus.RECIPE_MACHINES.get(machine).get(), containerId, items, upgrades, data, owner);
        this.machine = machine;
        for (int i = 0; i < machine.inputs; i++) {
            addSlot(new Slot(items, i, machine.inputPos[i][0], machine.inputPos[i][1]) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return !stack.is(ModItems.GRAPHITE_ELECTRODE.get()) || !machine.electrode;
                }
            });
        }
        for (int i = 0; i < machine.outputs; i++) {
            addSlot(new OutputSlot(items, machine.inputs + i, machine.outputPos[i][0], machine.outputPos[i][1]));
        }
        if (machine.electrode) {
            addSlot(new Slot(items, machine.electrodeSlot(), machine.electrodePos[0], machine.electrodePos[1]) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.is(ModItems.GRAPHITE_ELECTRODE.get());
                }
            });
        }
        addUpgradesAndInventory(inventory, 84);
    }

    public RecipeMachine machine() {
        return machine;
    }
}
