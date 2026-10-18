package com.powerfactor.chem;

import com.powerfactor.machine.MachineMenu;
import com.powerfactor.registry.ModChem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ChemicalReactorMenu extends MachineMenu {
    public static final int SULFUR_X = 9, SULFUR_Y = 49;

    public ChemicalReactorMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, null, new SimpleContainer(1), new SimpleContainerData(ChemicalReactorBlockEntity.DATA_COUNT));
    }

    ChemicalReactorMenu(int id, Inventory inventory, ChemicalReactorBlockEntity be, Container slots, ContainerData data) {
        super(ModChem.CHEMICAL_REACTOR_MENU.get(), id, slots, data);
        addSlot(new Slot(slots, 0, SULFUR_X, SULFUR_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModChem.SULFUR_DUST.get());
            }
        });
        addUpgradeBay(be == null ? com.powerfactor.upgrade.UpgradeInventory.client(com.powerfactor.upgrade.UpgradeInventory.CONSOLE) : be.upgrades());
        addPlayerInventory(inventory, 108);
    }

    public float progress() {
        return data.get(ChemicalReactorBlockEntity.DATA_PROGRESS) / (float) ChemicalReactorBlockEntity.TIME;
    }

    public int water() {
        return unsigned(ChemicalReactorBlockEntity.DATA_WATER);
    }

    public int hydrogen() {
        return unsigned(ChemicalReactorBlockEntity.DATA_HYDROGEN);
    }

    public int acid() {
        return unsigned(ChemicalReactorBlockEntity.DATA_ACID);
    }

    public int energy() {
        return unsigned(ChemicalReactorBlockEntity.DATA_ENERGY) * 100;
    }

    public int state() {
        return data.get(ChemicalReactorBlockEntity.DATA_STATE);
    }
}
