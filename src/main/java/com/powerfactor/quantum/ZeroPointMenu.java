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

public class ZeroPointMenu extends MachineMenu {
    public static final int FUEL_X = 86, FUEL_Y = 78;

    public ZeroPointMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, null, new SimpleContainer(1), new SimpleContainerData(ZeroPointBlockEntity.DATA_COUNT));
    }

    ZeroPointMenu(int id, Inventory inventory, ZeroPointBlockEntity be, Container fuel, ContainerData data) {
        super(ModQuantum.ZERO_POINT_MENU.get(), id, fuel, data);
        addSlot(new Slot(fuel, 0, FUEL_X, FUEL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModQuantum.EXOTIC_MATTER.get());
            }
        });
        addPlayerInventory(inventory, 108);
    }

    public float stability() {
        return data.get(ZeroPointBlockEntity.DATA_STABILITY) / 1000F;
    }

    public float fuel() {
        return data.get(ZeroPointBlockEntity.DATA_FUEL) / 1000F;
    }

    public int output() {
        return unsigned(ZeroPointBlockEntity.DATA_OUT_HI) << 16 | unsigned(ZeroPointBlockEntity.DATA_OUT_LO);
    }

    public long stored() {
        return unsigned(ZeroPointBlockEntity.DATA_STORED) * 1000L;
    }
}
