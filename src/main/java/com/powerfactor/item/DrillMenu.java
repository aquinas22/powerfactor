package com.powerfactor.item;

import com.powerfactor.registry.ModComponents;
import com.powerfactor.registry.ModMenus;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/** Edits the modules inside the drill in the player's hand. */
public class DrillMenu extends AbstractContainerMenu {
    public static final int SLOTS = 4;

    private final Player player;
    private final InteractionHand hand;
    private final SimpleContainer modules = new SimpleContainer(SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            save();
        }
    };
    private final int lockedSlot;

    public DrillMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readEnum(InteractionHand.class));
    }

    public DrillMenu(int id, Inventory inventory, InteractionHand hand) {
        super(ModMenus.DRILL.get(), id);
        this.player = inventory.player;
        this.hand = hand;
        ItemStack drill = player.getItemInHand(hand);
        NonNullList<ItemStack> list = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        drill.getOrDefault(ModComponents.DRILL_MODULES.get(), ItemContainerContents.EMPTY).copyInto(list);
        for (int i = 0; i < SLOTS; i++) {
            modules.setItem(i, list.get(i));
        }
        for (int i = 0; i < SLOTS; i++) {
            addSlot(new Slot(modules, i, 53 + i * 18, 35) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    if (!(stack.getItem() instanceof DrillModule m)) {
                        return false;
                    }
                    for (int j = 0; j < SLOTS; j++) {
                        if (j != getContainerSlot() && modules.getItem(j).getItem() instanceof DrillModule o && o.type() == m.type()) {
                            return false;
                        }
                    }
                    return true;
                }
            });
        }
        addStandardInventorySlots(inventory, 8, 84);
        lockedSlot = hand == InteractionHand.MAIN_HAND ? SLOTS + 27 + inventory.getSelectedSlot() : -1;
    }

    private void save() {
        ItemStack drill = player.getItemInHand(hand);
        if (!(drill.getItem() instanceof DrillItem)) {
            return;
        }
        drill.set(ModComponents.DRILL_MODULES.get(), ItemContainerContents.fromItems(modules.getItems()));
        if (!player.level().isClientSide()) {
            DrillItem.applyModules(drill, player.level().registryAccess());
        }
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player player) {
        // The drill itself can't be moved while its menu is open.
        if (slotId == lockedSlot) {
            return;
        }
        super.clicked(slotId, button, input, player);
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getItemInHand(hand).getItem() instanceof DrillItem;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || index == lockedSlot) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < SLOTS) {
            if (!moveItemStackTo(stack, SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, SLOTS, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }
}
