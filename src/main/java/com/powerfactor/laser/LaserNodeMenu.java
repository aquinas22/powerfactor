package com.powerfactor.laser;

import com.powerfactor.registry.ModLaser;
import com.powerfactor.storage.IoPortMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Laser Node settings: three channel modes, an item filter (ghost slots), deny toggle and priority. */
public class LaserNodeMenu extends AbstractContainerMenu {
    public static final int BUTTON_CHANNEL = 0, BUTTON_DENY = 3, BUTTON_PRIORITY = 4;   // 0..2 channels; 4..7: -10 -1 +1 +10
    public static final int DATA_MODES = 0, DATA_DENY = 3, DATA_PRIORITY = 4, DATA_LINKS = 5, DATA_ACTIVITY = 6, DATA_COUNT = 7;
    public static final int FILTER_X = 8, FILTER_Y = 95, INV_Y = 150;

    private final @Nullable LaserNodeBlockEntity node;
    private final ContainerData data;
    private final BlockPos pos;

    public LaserNodeMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, null, buf.readBlockPos(), new SimpleContainer(LaserNodeBlockEntity.FILTER_SLOTS),
                new SimpleContainerData(DATA_COUNT));
    }

    LaserNodeMenu(int id, Inventory inventory, LaserNodeBlockEntity node) {
        this(id, inventory, node, node.getBlockPos(), node.filter(), new ContainerData() {
            @Override
            public int get(int i) {
                return switch (i) {
                    case 0, 1, 2 -> node.mode(LaserNodeBlockEntity.Channel.values()[i]);
                    case DATA_DENY -> node.deny() ? 1 : 0;
                    case DATA_PRIORITY -> node.priority() + 100;
                    case DATA_LINKS -> node.links().size();
                    case DATA_ACTIVITY -> node.clientActivity() | activityOf(node);
                    default -> 0;
                };
            }

            @Override
            public void set(int i, int v) {}

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        });
    }

    private static int activityOf(LaserNodeBlockEntity node) {
        return node.serverActivity();
    }

    private LaserNodeMenu(int id, Inventory inventory, @Nullable LaserNodeBlockEntity node, BlockPos pos, SimpleContainer filter, ContainerData data) {
        super(ModLaser.LASER_NODE_MENU.get(), id);
        this.node = node;
        this.data = data;
        this.pos = pos;
        for (int i = 0; i < LaserNodeBlockEntity.FILTER_SLOTS; i++) {
            addSlot(new IoPortMenu.GhostSlot(filter, i, FILTER_X + i * 18, FILTER_Y));
        }
        addStandardInventorySlots(inventory, 8, INV_Y);
        addDataSlots(data);
    }

    public BlockPos pos() {
        return pos;
    }

    public int mode(int channel) {
        return data.get(DATA_MODES + channel);
    }

    public boolean deny() {
        return data.get(DATA_DENY) != 0;
    }

    public int priority() {
        return data.get(DATA_PRIORITY) - 100;
    }

    public int links() {
        return data.get(DATA_LINKS);
    }

    public int activity() {
        return data.get(DATA_ACTIVITY);
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player player) {
        // Ghost filter slots: click with an item to set a copy, click empty-handed to clear.
        if (slotId >= 0 && slotId < LaserNodeBlockEntity.FILTER_SLOTS && slots.get(slotId) instanceof IoPortMenu.GhostSlot ghost) {
            ItemStack carried = getCarried();
            ghost.set(carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            ghost.setChanged();
            if (node != null) node.setChanged();
            return;
        }
        super.clicked(slotId, button, input, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (node == null) return false;
        if (id >= BUTTON_CHANNEL && id < BUTTON_CHANNEL + 3) node.cycle(LaserNodeBlockEntity.Channel.values()[id - BUTTON_CHANNEL]);
        else if (id == BUTTON_DENY) node.toggleDeny();
        else if (id >= BUTTON_PRIORITY && id < BUTTON_PRIORITY + 4) node.addPriority(new int[] {-10, -1, 1, 10}[id - BUTTON_PRIORITY]);
        else return false;
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < LaserNodeBlockEntity.FILTER_SLOTS) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), copy = stack.copy();
        int inv = LaserNodeBlockEntity.FILTER_SLOTS;
        if (index < inv + 27 ? !moveItemStackTo(stack, inv + 27, inv + 36, false) : !moveItemStackTo(stack, inv, inv + 27, false)) return ItemStack.EMPTY;
        slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return node == null || (!node.isRemoved() && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) < 64);
    }
}
