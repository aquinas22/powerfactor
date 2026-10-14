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

public class SpaceElevatorMenu extends MachineMenu {
    public static final int SLOT_X = 134, SLOT_Y = 74;

    public SpaceElevatorMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, null, new SimpleContainer(1), new SimpleContainerData(SpaceElevatorBlockEntity.DATA_COUNT));
    }

    SpaceElevatorMenu(int id, Inventory inventory, SpaceElevatorBlockEntity be, Container tether, ContainerData data) {
        super(ModQuantum.SPACE_ELEVATOR_MENU.get(), id, tether, data);
        addSlot(new Slot(tether, 0, SLOT_X, SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModQuantum.NANOTUBE_TETHER.get());
            }
        });
        addPlayerInventory(inventory, 108);
    }

    public int segments() {
        return data.get(SpaceElevatorBlockEntity.DATA_SEGMENTS);
    }

    public float spool() {
        return data.get(SpaceElevatorBlockEntity.DATA_SPOOL) / 1000F;
    }

    public long energy() {
        return data.get(SpaceElevatorBlockEntity.DATA_ENERGY) * 1000L;
    }

    public int output() {
        return unsigned(SpaceElevatorBlockEntity.DATA_OUT_HI) << 16 | unsigned(SpaceElevatorBlockEntity.DATA_OUT_LO);
    }

    public boolean sky() {
        return data.get(SpaceElevatorBlockEntity.DATA_SKY) != 0;
    }
}
