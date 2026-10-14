package com.powerfactor.storage;

import com.powerfactor.registry.ModStorage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public class DriveBayMenu extends NodeMenu {
    public static final int CELL_X = 44, CELL_Y = 30;
    private final @Nullable DriveBayBlockEntity drive;
    private final DataSlot priority = DataSlot.standalone();

    public DriveBayMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos(), new SimpleContainer(DriveBayBlockEntity.SLOTS), null);
    }

    public DriveBayMenu(int id, Inventory inventory, DriveBayBlockEntity drive) {
        this(id, inventory, drive.getBlockPos(), drive.cells(), drive);
    }

    private DriveBayMenu(int id, Inventory inventory, net.minecraft.core.BlockPos pos, Container cells, @Nullable DriveBayBlockEntity drive) {
        super(ModStorage.DRIVE_BAY_MENU.get(), id, inventory, pos);
        this.drive = drive;
        for (int i = 0; i < DriveBayBlockEntity.SLOTS; i++) {
            addSlot(new Slot(cells, i, CELL_X + (i % 5) * 18, CELL_Y + (i / 5) * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof StorageCellItem || stack.getItem() instanceof com.powerfactor.storage.fluid.FluidCellItem;
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        addPlayerInventory(inventory, 8, 108);
        addDataSlot(priority);
        if (drive != null) priority.set(drive.priority());
    }

    public int priority() {
        return priority.get();
    }

    @Override
    public void broadcastChanges() {
        if (drive != null) priority.set(drive.priority());
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (drive == null) return false;
        int[] deltas = {-10, -1, 1, 10};
        if (id < 0 || id >= deltas.length) return false;
        drive.setPriority(drive.priority() + deltas[id]);
        return true;
    }
}
