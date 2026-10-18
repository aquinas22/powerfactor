package com.powerfactor.storage;

import com.powerfactor.registry.ModStorage;
import com.powerfactor.upgrade.UpgradeItem;
import com.powerfactor.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Filter references (ghost slots), speed cards and the allow/deny, exact and priority controls. */
public class IoPortMenu extends NodeMenu {
    /** Filter row spans the inventory's nine columns; speed upgrades are a row of four in columns 6-9 at the bottom. */
    public static final int FILTER_X = 8, FILTER_Y = 34, UPGRADE_X = 98, UPGRADE_Y = 75;
    public static final int BUTTON_DENY = 0, BUTTON_EXACT = 1, BUTTON_PRIORITY = 2; // +0..3: -10,-1,+1,+10

    private final @Nullable IoPortBlockEntity port;
    private final IoPortBlockEntity.Kind kind;
    private final DataSlot flags = DataSlot.standalone(), priority = DataSlot.standalone();

    public IoPortMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos(), IoPortBlockEntity.Kind.values()[buf.readVarInt()], new SimpleContainer(IoPortBlockEntity.FILTERS),
                new SimpleContainer(IoPortBlockEntity.UPGRADES), null);
    }

    public IoPortMenu(int id, Inventory inventory, IoPortBlockEntity port) {
        this(id, inventory, port.getBlockPos(), port.kind(), port.filters(), port.upgrades(), port);
    }

    private IoPortMenu(int id, Inventory inventory, BlockPos pos, IoPortBlockEntity.Kind kind, Container filters, Container upgrades,
            @Nullable IoPortBlockEntity port) {
        super(ModStorage.IO_PORT_MENU.get(), id, inventory, pos);
        this.port = port;
        this.kind = kind;
        for (int i = 0; i < IoPortBlockEntity.FILTERS; i++) {
            addSlot(new GhostSlot(filters, i, FILTER_X + i * 18, FILTER_Y));
        }
        for (int i = 0; i < IoPortBlockEntity.UPGRADES; i++) {
            addSlot(new Slot(upgrades, i, UPGRADE_X + i * 18, UPGRADE_Y) {
                /** A storage bus moves on its own clock: no speed cards, so no sockets on its screen. */
                @Override
                public boolean isActive() {
                    return IoPortMenu.this.kind != IoPortBlockEntity.Kind.STORAGE_BUS;
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof UpgradeItem u && u.type() == UpgradeType.SPEED;
                }
            });
        }
        addPlayerInventory(inventory, 8, 108);
        addDataSlot(flags);
        addDataSlot(priority);
        sync();
    }

    private void sync() {
        if (port != null) {
            flags.set((port.deny() ? 1 : 0) | (port.exact() ? 2 : 0));
            priority.set(port.priority());
        }
    }

    public IoPortBlockEntity.Kind kind() {
        return kind;
    }

    public boolean deny() {
        return (flags.get() & 1) != 0;
    }

    public boolean exact() {
        return (flags.get() & 2) != 0;
    }

    public int priority() {
        return priority.get();
    }

    @Override
    public void broadcastChanges() {
        sync();
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (port == null) return false;
        if (id == BUTTON_DENY) port.toggleDeny();
        else if (id == BUTTON_EXACT) port.toggleExact();
        else if (id >= BUTTON_PRIORITY && id < BUTTON_PRIORITY + 4) port.setPriority(port.priority() + new int[] {-10, -1, 1, 10}[id - BUTTON_PRIORITY]);
        else return false;
        return true;
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
        if (slotId >= 0 && slotId < slots.size() && slots.get(slotId) instanceof GhostSlot ghost) {
            ItemStack carried = getCarried();
            ghost.set(carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Shift-clicking an inventory item copies it into the first free filter (it never moves).
        if (index >= machineSlots()) {
            ItemStack stack = slots.get(index).getItem();
            if (!stack.isEmpty() && !(stack.getItem() instanceof UpgradeItem)) {
                for (int i = 0; i < IoPortBlockEntity.FILTERS; i++) {
                    Slot s = slots.get(i);
                    if (!s.hasItem()) {
                        s.set(stack.copyWithCount(1));
                        break;
                    }
                }
                return ItemStack.EMPTY;
            }
        } else if (slots.get(index) instanceof GhostSlot) {
            slots.get(index).set(ItemStack.EMPTY);
            return ItemStack.EMPTY;
        }
        return super.quickMoveStack(player, index);
    }

    /** Shows a reference item; clicking copies the carried item instead of taking or placing. */
    public static class GhostSlot extends Slot {
        public GhostSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
