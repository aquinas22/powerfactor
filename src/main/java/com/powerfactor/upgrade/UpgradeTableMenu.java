package com.powerfactor.upgrade;

import com.powerfactor.item.DrillModule;
import com.powerfactor.item.ToolKind;
import com.powerfactor.item.ToolModules;
import com.powerfactor.registry.ModMenus;
import com.powerfactor.registry.ModSounds;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The upgrade table: one tool socket in the middle, six module sockets around it. The module
 * sockets are a live view of the modules stored inside the tool, so taking the tool away takes its
 * modules with it.
 */
public class UpgradeTableMenu extends AbstractContainerMenu {
    public static final int TOOL_X = 76, TOOL_Y = 58;
    /** Module socket item origins, clockwise from the top. */
    public static final int[][] SOCKETS = {{76, 24}, {112, 45}, {112, 87}, {76, 108}, {40, 87}, {40, 45}};
    public static final int INV_X = 44, INV_Y = 136;
    public static final int TOOL_SLOT = 0, FIRST_MODULE = 1, FIRST_INV = 1 + ToolModules.SLOTS;

    private final Container tool;
    private final SimpleContainer modules = new SimpleContainer(ToolModules.SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            save();
        }
    };
    private final ContainerLevelAccess access;
    private final @Nullable UpgradeTableBlockEntity table;
    private boolean loading;
    private ItemStack loaded = ItemStack.EMPTY;
    private int installed = -1;

    public UpgradeTableMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new SimpleContainer(1), ContainerLevelAccess.NULL, null);
    }

    public UpgradeTableMenu(int id, Inventory inventory, UpgradeTableBlockEntity table) {
        this(id, inventory, table.tool(), ContainerLevelAccess.create(table.getLevel(), table.getBlockPos()), table);
    }

    private UpgradeTableMenu(int id, Inventory inventory, Container tool, ContainerLevelAccess access, @Nullable UpgradeTableBlockEntity table) {
        super(ModMenus.UPGRADE_TABLE.get(), id);
        this.tool = tool;
        this.access = access;
        this.table = table;
        addSlot(new Slot(tool, 0, TOOL_X, TOOL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return ToolKind.of(stack) != null;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int i = 0; i < ToolModules.SLOTS; i++) {
            addSlot(new ModuleSlot(i));
        }
        addStandardInventorySlots(inventory, INV_X, INV_Y);
        reload();
    }

    /** A socket: only active with a tool in the bench, only takes modules that fit it, no duplicates. */
    private class ModuleSlot extends Slot {
        ModuleSlot(int index) {
            super(modules, index, SOCKETS[index][0], SOCKETS[index][1]);
        }

        @Override
        public boolean isActive() {
            return kind() != null;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return accepts(stack, getContainerSlot());
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return stack.getItem() instanceof DrillModule m ? m.type().max : 1;
        }
    }

    public @Nullable ToolKind kind() {
        return ToolKind.of(tool.getItem(0));
    }

    public ItemStack toolStack() {
        return tool.getItem(0);
    }

    public Container modules() {
        return modules;
    }

    /** Whether {@code stack} could go in socket {@code index} of the current tool. */
    public boolean accepts(ItemStack stack, int index) {
        if (!(stack.getItem() instanceof DrillModule m) || !m.type().fits(kind())) {
            return false;
        }
        for (int j = 0; j < ToolModules.SLOTS; j++) {
            if (j != index && modules.getItem(j).getItem() instanceof DrillModule o && o.type() == m.type()) {
                return false;
            }
        }
        return true;
    }

    /** Fill the sockets from the tool now in the bench. */
    private void reload() {
        loading = true;
        loaded = tool.getItem(0);
        NonNullList<ItemStack> list = ToolModules.list(loaded);
        for (int i = 0; i < ToolModules.SLOTS; i++) {
            modules.setItem(i, loaded.isEmpty() ? ItemStack.EMPTY : list.get(i));
        }
        installed = total();
        loading = false;
    }

    private int total() {
        int n = 0;
        for (int i = 0; i < ToolModules.SLOTS; i++) {
            n += modules.getItem(i).getCount();
        }
        return n;
    }

    /** Write the sockets back into the tool. */
    private void save() {
        if (loading) {
            return;
        }
        ItemStack stack = tool.getItem(0);
        if (stack.isEmpty() || stack != loaded) {
            return;
        }
        access.execute((level, pos) -> {
            ToolModules.install(stack, modules.getItems(), level.registryAccess());
            tool.setChanged();
            int now = total();
            if (now != installed) {
                level.playSound(null, pos, now > installed ? ModSounds.UPGRADE_INSTALL.get() : ModSounds.UPGRADE_REMOVE.get(),
                        SoundSource.BLOCKS, 0.8F, 1.0F);
            }
            installed = now;
        });
    }

    @Override
    public void broadcastChanges() {
        // The tool was swapped or removed: show the new tool's modules.
        if (tool.getItem(0) != loaded) {
            reload();
        }
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        return table == null || tool.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < FIRST_INV) {
            if (!moveItemStackTo(stack, FIRST_INV, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (ToolKind.of(stack) != null && tool.getItem(0).isEmpty()) {
            if (!moveItemStackTo(stack, TOOL_SLOT, TOOL_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof DrillModule) {
            boolean moved = false;
            for (int i = 0; i < ToolModules.SLOTS && !stack.isEmpty(); i++) {
                Slot s = slots.get(FIRST_MODULE + i);
                if (s.isActive() && (s.hasItem() ? ItemStack.isSameItemSameComponents(s.getItem(), stack) : accepts(stack, i))) {
                    moved |= moveItemStackTo(stack, FIRST_MODULE + i, FIRST_MODULE + i + 1, false);
                }
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        } else {
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
