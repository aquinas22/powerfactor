package com.powerfactor.pocketvoid;

import com.powerfactor.registry.ModVoid;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Pocket Void's screen: its nine slots are drawn and clicked through {@link PocketVoidPayloads}; the player's
 * inventory is ordinary slots, except the one holding this void, which can't be picked up while it's open.
 */
public class PocketVoidMenu extends AbstractContainerMenu {
    public static final int INV_Y = 84;
    public final int voidSlot;
    private final Player player;

    public PocketVoidMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readVarInt());
    }

    public PocketVoidMenu(int id, Inventory inventory, int voidSlot) {
        super(ModVoid.MENU.get(), id);
        this.voidSlot = voidSlot;
        this.player = inventory.player;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) addSlot(new Slot(inventory, 9 + r * 9 + c, 8 + c * 18, INV_Y + r * 18));
        }
        for (int c = 0; c < 9; c++) {
            addSlot(new Slot(inventory, c, 8 + c * 18, INV_Y + 58) {
                @Override
                public boolean mayPickup(Player p) {
                    return getContainerSlot() != voidSlot;
                }
            });
        }
    }

    public ItemStack voidStack() {
        return player.getInventory().getItem(voidSlot);
    }

    public boolean valid() {
        return voidStack().getItem() instanceof PocketVoidItem;
    }

    /** Shift-click from the inventory pours the stack into the void. */
    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = slots.get(index);
        ItemStack stack = slot.getItem();
        if (!valid() || stack.isEmpty() || slot.getContainerSlot() == voidSlot || stack.getItem() instanceof PocketVoidItem) return ItemStack.EMPTY;
        ItemStack self = voidStack();
        var r = PocketVoidItem.contents(self).insert(stack, ((PocketVoidItem) self.getItem()).capacity(), true, false);
        if (r.taken() > 0) {
            PocketVoidItem.set(self, r.contents());
            stack.shrink(r.taken());
            slot.setChanged();
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player p) {
        return valid();
    }
}
