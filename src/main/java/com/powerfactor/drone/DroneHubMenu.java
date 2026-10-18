package com.powerfactor.drone;

import com.powerfactor.machine.MachineMenu;
import com.powerfactor.registry.ModDrone;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Drone Hub console: four drone bays, the haul, the upgrade bay and the live AI feed. */
public class DroneHubMenu extends MachineMenu {
    public static final int BAY_X = 8, BAY_Y = 121, OUT_X = 86, OUT_Y = 121;
    public final BlockPos pos;

    public DroneHubMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, null, new SimpleContainer(DroneHubBlockEntity.BAY + DroneHubBlockEntity.OUT), buf.readBlockPos());
    }

    DroneHubMenu(int id, Inventory inventory, DroneHubBlockEntity be, Container slots) {
        this(id, inventory, be, slots, be.getBlockPos());
    }

    private DroneHubMenu(int id, Inventory inventory, DroneHubBlockEntity be, Container slots, BlockPos pos) {
        super(ModDrone.HUB_MENU.get(), id, slots, new SimpleContainerData(1));
        this.pos = pos;
        for (int i = 0; i < DroneHubBlockEntity.BAY; i++) {
            addSlot(new Slot(slots, i, BAY_X + i * 18, BAY_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.is(ModDrone.DRONE_ITEM.get());
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        for (int i = 0; i < DroneHubBlockEntity.OUT; i++) {
            addSlot(new OutputSlot(slots, DroneHubBlockEntity.BAY + i, OUT_X + i * 18, OUT_Y));
        }
        addUpgradeBay(be == null ? com.powerfactor.upgrade.UpgradeInventory.client(com.powerfactor.upgrade.UpgradeInventory.CONSOLE) : be.upgrades());
        addPlayerInventory(inventory, 164);
    }
}
