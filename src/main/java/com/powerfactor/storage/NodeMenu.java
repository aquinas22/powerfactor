package com.powerfactor.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Base for storage-device menus: machine slots first, then the player inventory, with shift-click routing. */
public abstract class NodeMenu extends AbstractContainerMenu {
    protected final BlockPos pos;
    protected final Player player;
    private int machineSlots;

    protected NodeMenu(@Nullable MenuType<?> type, int containerId, Inventory inventory, BlockPos pos) {
        super(type, containerId);
        this.pos = pos;
        this.player = inventory.player;
    }

    public BlockPos pos() {
        return pos;
    }

    protected void addPlayerInventory(Inventory inventory, int x, int top) {
        machineSlots = slots.size();
        addStandardInventorySlots(inventory, x, top);
    }

    protected int machineSlots() {
        return machineSlots;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof NodeBlockEntity && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) < 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int hotbar = machineSlots + 27, end = hotbar + 9;
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, end, true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, machineSlots, false)) {
            if (index < hotbar) {
                if (!moveItemStackTo(stack, hotbar, end, false)) return ItemStack.EMPTY;
            } else if (!moveItemStackTo(stack, machineSlots, hotbar, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
}
