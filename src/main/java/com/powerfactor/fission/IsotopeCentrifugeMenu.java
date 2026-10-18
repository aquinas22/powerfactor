package com.powerfactor.fission;

import com.powerfactor.machine.MachineMenu;
import com.powerfactor.registry.ModFission;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class IsotopeCentrifugeMenu extends MachineMenu {
    public static final int IN_X = 26, IN_Y = 48, OUT_X = 116, OUT_Y = 39;

    public IsotopeCentrifugeMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, null, new SimpleContainer(1 + IsotopeCentrifugeBlockEntity.OUT), new SimpleContainerData(IsotopeCentrifugeBlockEntity.DATA_COUNT));
    }

    IsotopeCentrifugeMenu(int id, Inventory inventory, IsotopeCentrifugeBlockEntity be, Container slots, ContainerData data) {
        super(ModFission.CENTRIFUGE_MENU.get(), id, slots, data);
        addSlot(new Slot(slots, 0, IN_X, IN_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return IsotopeCentrifugeBlockEntity.recipe(stack) != null;
            }
        });
        for (int i = 0; i < IsotopeCentrifugeBlockEntity.OUT; i++) addSlot(new OutputSlot(slots, 1 + i, OUT_X + (i % 2) * 18, OUT_Y + (i / 2) * 18));
        addUpgradeBay(be == null ? com.powerfactor.upgrade.UpgradeInventory.client(com.powerfactor.upgrade.UpgradeInventory.CONSOLE) : be.upgrades());
        addPlayerInventory(inventory, 108);
    }

    public float progress() {
        return data.get(IsotopeCentrifugeBlockEntity.DATA_PROGRESS) / 1000F;
    }

    public int energy() {
        return unsigned(IsotopeCentrifugeBlockEntity.DATA_ENERGY) * 100;
    }

    public int state() {
        return data.get(IsotopeCentrifugeBlockEntity.DATA_STATE);
    }
}
