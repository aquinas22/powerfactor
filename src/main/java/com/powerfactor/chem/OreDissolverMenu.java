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

public class OreDissolverMenu extends MachineMenu {
    /** Ore in left, crystals out right, mirrored about the vat on the centre line; the catalyst sits under the vat. */
    public static final int ORE_X = 28, ORE_Y = 34, CATALYST_X = 80, CATALYST_Y = 75, OUT_X = 132, OUT_Y = 34;

    public OreDissolverMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, null, new SimpleContainer(3), new SimpleContainerData(OreDissolverBlockEntity.DATA_COUNT));
    }

    OreDissolverMenu(int id, Inventory inventory, OreDissolverBlockEntity be, Container slots, ContainerData data) {
        super(ModChem.ORE_DISSOLVER_MENU.get(), id, slots, data);
        addSlot(new Slot(slots, OreDissolverBlockEntity.SLOT_ORE, ORE_X, ORE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return OreMetal.of(stack) != null;
            }
        });
        addSlot(new Slot(slots, OreDissolverBlockEntity.SLOT_CATALYST, CATALYST_X, CATALYST_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModChem.QUANTUM_CATALYST.get());
            }
        });
        addSlot(new OutputSlot(slots, OreDissolverBlockEntity.SLOT_OUT, OUT_X, OUT_Y));
        addUpgradeBay(be == null ? com.powerfactor.upgrade.UpgradeInventory.client(com.powerfactor.upgrade.UpgradeInventory.CONSOLE) : be.upgrades());
        addPlayerInventory(inventory, 108);
    }

    public float progress() {
        return data.get(OreDissolverBlockEntity.DATA_PROGRESS) / (float) OreDissolverBlockEntity.TIME;
    }

    public int acid() {
        return unsigned(OreDissolverBlockEntity.DATA_ACID);
    }

    public int energy() {
        return unsigned(OreDissolverBlockEntity.DATA_ENERGY) * 100;
    }

    public int state() {
        return data.get(OreDissolverBlockEntity.DATA_STATE);
    }

    public int metal() {
        return data.get(OreDissolverBlockEntity.DATA_METAL) - 1;
    }
}
