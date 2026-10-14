package com.powerfactor.quantum;

import com.powerfactor.machine.MachineMenu;
import com.powerfactor.registry.ModQuantum;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

public class OrbitalCollectorMenu extends MachineMenu {
    public static final int SLOT_X = 98, SLOT_Y = 22;

    public OrbitalCollectorMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, null, new SimpleContainer(OrbitalCollectorBlockEntity.SLOTS), new SimpleContainerData(OrbitalCollectorBlockEntity.DATA_COUNT));
    }

    OrbitalCollectorMenu(int id, Inventory inventory, OrbitalCollectorBlockEntity be, Container out, ContainerData data) {
        super(ModQuantum.ORBITAL_COLLECTOR_MENU.get(), id, out, data);
        for (int i = 0; i < OrbitalCollectorBlockEntity.SLOTS; i++) {
            addSlot(new OutputSlot(out, i, SLOT_X + (i % 3) * 18, SLOT_Y + (i / 3) * 18));
        }
        addPlayerInventory(inventory, 108);
    }

    public float progress() {
        return data.get(OrbitalCollectorBlockEntity.DATA_PROGRESS) / 1000F;
    }

    public int state() {
        return data.get(OrbitalCollectorBlockEntity.DATA_STATE);
    }

    public long energy() {
        return data.get(OrbitalCollectorBlockEntity.DATA_ENERGY) * 1000L;
    }
}
