package com.powerfactor.machine;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Machine slots first, then the player inventory; shift-click routes to whichever slot accepts. */
public abstract class MachineMenu extends AbstractContainerMenu {
    protected final Container container;
    protected final ContainerData data;
    private int machineSlots;

    protected MachineMenu(@Nullable MenuType<?> type, int containerId, Container container, ContainerData data) {
        super(type, containerId);
        checkContainerDataCount(data, data.getCount());
        this.container = container;
        this.data = data;
        addDataSlots(data);
    }

    /** The upgrade bay: a column of card slots on a panel clipped to the screen's right edge (see StorageScreens.Base). */
    public static final int BAY_X = 182, BAY_Y = 24;

    public static final class BaySlot extends Slot {
        private final com.powerfactor.upgrade.UpgradeInventory upgrades;

        public BaySlot(com.powerfactor.upgrade.UpgradeInventory upgrades, int index) {
            super(upgrades, index, BAY_X, BAY_Y + index * 18);
            this.upgrades = upgrades;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return upgrades.accepts(getContainerSlot(), stack);
        }
    }

    /** Adds the machine's upgrade bay; call before addPlayerInventory so shift-click routes cards into it. */
    protected void addUpgradeBay(com.powerfactor.upgrade.UpgradeInventory upgrades) {
        for (int i = 0; i < com.powerfactor.upgrade.UpgradeInventory.SLOTS; i++) addSlot(new BaySlot(upgrades, i));
    }

    /** Call after adding the machine's slots. */
    protected void addPlayerInventory(Inventory inventory, int top) {
        machineSlots = slots.size();
        addStandardInventorySlots(inventory, 8, top);
    }

    protected int unsigned(int index) {
        return data.get(index) & 0xFFFF;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int hotbarStart = machineSlots + 27;
        int end = hotbarStart + 9;
        if (slotIndex < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, end, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, machineSlots, false)) {
            if (slotIndex < hotbarStart) {
                if (!moveItemStackTo(stack, hotbarStart, end, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, machineSlots, hotbarStart, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    /** A slot players can only take from. */
    /** Marks slots the GUI should draw as product wells. */
    public interface OutputSlotMarker {}

    protected static class OutputSlot extends Slot implements OutputSlotMarker {
        public OutputSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
