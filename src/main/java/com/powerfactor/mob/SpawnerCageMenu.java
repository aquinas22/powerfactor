package com.powerfactor.mob;

import com.powerfactor.machine.MachineMenu;
import com.powerfactor.registry.ModMob;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class SpawnerCageMenu extends MachineMenu {
    public static final int SNARE_X = 88, SNARE_Y = 24;

    public SpawnerCageMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, null, new SimpleContainer(1), new SimpleContainerData(SpawnerCageBlockEntity.DATA_COUNT));
    }

    SpawnerCageMenu(int id, Inventory inventory, SpawnerCageBlockEntity be, Container slots, ContainerData data) {
        super(ModMob.SPAWNER_CAGE_MENU.get(), id, slots, data);
        addSlot(new Slot(slots, 0, SNARE_X, SNARE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof SoulSnareItem && SoulSnareItem.soul(stack) != null;
            }
        });
        addUpgradeBay(be == null ? com.powerfactor.upgrade.UpgradeInventory.client(com.powerfactor.upgrade.UpgradeInventory.CONSOLE) : be.upgrades());
        addPlayerInventory(inventory, 108);
    }

    public float progress() {
        return data.get(SpawnerCageBlockEntity.DATA_PROGRESS) / 1000F;
    }

    public int energy() {
        return unsigned(SpawnerCageBlockEntity.DATA_ENERGY) * 100;
    }

    public int spawned() {
        return data.get(SpawnerCageBlockEntity.DATA_SPAWNED);
    }

    public int state() {
        return data.get(SpawnerCageBlockEntity.DATA_STATE);
    }

    public int cost() {
        return unsigned(SpawnerCageBlockEntity.DATA_COST);
    }

    public ItemStack snare() {
        return getSlot(0).getItem();
    }
}
