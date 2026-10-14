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
import net.minecraft.world.item.ItemStack;

public class ColliderMenu extends MachineMenu {
    public static final int IN_X = 26, IN_Y = 52, OUT_X = 128, OUT_Y = 52;

    public ColliderMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, null, new SimpleContainer(2), new SimpleContainerData(ColliderBlockEntity.DATA_COUNT));
    }

    ColliderMenu(int id, Inventory inventory, ColliderBlockEntity be, Container slots, ContainerData data) {
        super(ModQuantum.COLLIDER_MENU.get(), id, slots, data);
        addSlot(new Slot(slots, 0, IN_X, IN_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return ColliderRecipes.find(stack) != null;
            }
        });
        addSlot(new OutputSlot(slots, 1, OUT_X, OUT_Y));
        addPlayerInventory(inventory, 108);
    }

    public float progress() {
        return data.get(ColliderBlockEntity.DATA_PROGRESS) / 1000F;
    }

    public int rate() {
        return unsigned(ColliderBlockEntity.DATA_RATE_HI) << 16 | unsigned(ColliderBlockEntity.DATA_RATE_LO);
    }

    public long energy() {
        return (long) unsigned(ColliderBlockEntity.DATA_ENERGY_HI) << 12 | (data.get(ColliderBlockEntity.DATA_ENERGY_LO) & 0xFFF);
    }

    public int recipe() {
        return data.get(ColliderBlockEntity.DATA_RECIPE) - 1;
    }

    public int flash() {
        return data.get(ColliderBlockEntity.DATA_FLASH);
    }
}
