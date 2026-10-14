package com.powerfactor.storage;

import com.powerfactor.registry.ModStorage;
import com.powerfactor.storage.core.CellInventory;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * Ten storage cells behind a rack door. Each cell is a separate network storage at the drive's
 * priority; contents are written straight back to the cell item, so pulling a cell keeps its items.
 */
public class DriveBayBlockEntity extends NodeBlockEntity {
    public static final int SLOTS = 10;

    private final SimpleContainer cells = new SimpleContainer(SLOTS) {
        @Override
        public boolean canPlaceItem(int index, ItemStack stack) {
            return stack.getItem() instanceof StorageCellItem || stack.getItem() instanceof com.powerfactor.storage.fluid.FluidCellItem;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(DriveBayBlockEntity.this, player);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            cellsChanged();
        }
    };
    private final CellStorage[] open = new CellStorage[SLOTS];
    private final FluidCellStorage[] openFluid = new FluidCellStorage[SLOTS];
    private int priority;
    /** Client copy of each slot's state for the front LEDs: 0 empty, else tier ordinal + 1, and fill. */
    private final int[] ledTier = new int[SLOTS];
    private final float[] ledFill = new float[SLOTS];

    public DriveBayBlockEntity(BlockPos pos, BlockState state) {
        super(ModStorage.DRIVE_BAY_BE.get(), pos, state);
    }

    public Container cells() {
        return cells;
    }

    private void cellsChanged() {
        java.util.Arrays.fill(open, null);
        java.util.Arrays.fill(openFluid, null);
        setChanged();
        StorageNetwork n = network();
        if (n != null) n.markStoragesDirty();
        syncLeds();
    }

    private void syncLeds() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public int idleDraw() {
        int n = 2;
        for (int i = 0; i < SLOTS; i++) if (!cells.getItem(i).isEmpty()) n++;
        return n;
    }

    @Override
    public void addStorages(List<NetworkStorage> out) {
        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = cells.getItem(i);
            if (stack.getItem() instanceof StorageCellItem cell) {
                if (open[i] == null || open[i].stack != stack) open[i] = new CellStorage(stack, cell.open(stack));
                out.add(open[i]);
            }
        }
    }

    @Override
    public void addFluidStorages(List<com.powerfactor.storage.fluid.NetworkFluidStorage> out) {
        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = cells.getItem(i);
            if (stack.getItem() instanceof com.powerfactor.storage.fluid.FluidCellItem cell) {
                if (openFluid[i] == null || openFluid[i].stack != stack) openFluid[i] = new FluidCellStorage(stack, cell.open(stack));
                out.add(openFluid[i]);
            }
        }
    }

    /** LED code for a slot: 0 empty, 1.. item cell tiers, then fluid cell tiers after them. */
    static int ledCode(ItemStack s) {
        if (s.getItem() instanceof StorageCellItem c) return c.tier().ordinal() + 1;
        if (s.getItem() instanceof com.powerfactor.storage.fluid.FluidCellItem f) return CellTier.values().length + f.tier().ordinal() + 1;
        return 0;
    }

    static float ledFillOf(ItemStack s) {
        if (s.getItem() instanceof StorageCellItem c) return c.fill(s);
        if (s.getItem() instanceof com.powerfactor.storage.fluid.FluidCellItem f) return f.fill(s);
        return 0;
    }

    /** Colour and glow tier for an LED code (see {@link #ledCode}). */
    public static int ledColor(int code) {
        int n = CellTier.values().length;
        return code <= n ? CellTier.values()[code - 1].color : com.powerfactor.storage.fluid.FluidCellTier.values()[code - n - 1].color;
    }

    public static int ledGlow(int code) {
        int n = CellTier.values().length;
        return code <= n ? CellTier.values()[code - 1].glow : com.powerfactor.storage.fluid.FluidCellTier.values()[code - n - 1].glow;
    }

    public int priority() {
        return priority;
    }

    public void setPriority(int p) {
        priority = Math.clamp(p, -99, 99);
        setChanged();
        StorageNetwork n = network();
        if (n != null) n.markStoragesDirty();
    }

    private long lastLedSync;

    /** One cell's live contents; every change is saved to the cell item immediately. */
    private final class CellStorage implements NetworkStorage {
        final ItemStack stack;
        final CellInventory<ItemResource> inv;

        CellStorage(ItemStack stack, CellInventory<ItemResource> inv) {
            this.stack = stack;
            this.inv = inv;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public long insert(ItemResource r, long amount, boolean simulate) {
            long n = inv.insert(r, amount, simulate);
            if (n > 0 && !simulate) saved();
            return n;
        }

        @Override
        public long extract(ItemResource r, long amount, boolean simulate) {
            long n = inv.extract(r, amount, simulate);
            if (n > 0 && !simulate) saved();
            return n;
        }

        private void saved() {
            StorageCellItem.save(stack, inv);
            DriveBayBlockEntity.this.setChanged();
            // Throttle LED updates: the fill bars don't need per-insert precision.
            if (level != null && level.getGameTime() - lastLedSync > 10) {
                lastLedSync = level.getGameTime();
                syncLeds();
            }
        }

        @Override
        public long count(ItemResource r) {
            return inv.count(r);
        }

        @Override
        public void collect(Map<ItemResource, Long> totals) {
            for (var e : inv.contents().entrySet()) totals.merge(e.getKey(), e.getValue(), Long::sum);
        }

        @Override
        public long usedBytes() {
            return inv.usedBytes();
        }

        @Override
        public long totalBytes() {
            return inv.totalBytes();
        }
    }

    /** One fluid cell's live contents; every change is saved to the cell item immediately. */
    private final class FluidCellStorage implements com.powerfactor.storage.fluid.NetworkFluidStorage {
        final ItemStack stack;
        final CellInventory<net.neoforged.neoforge.transfer.fluid.FluidResource> inv;

        FluidCellStorage(ItemStack stack, CellInventory<net.neoforged.neoforge.transfer.fluid.FluidResource> inv) {
            this.stack = stack;
            this.inv = inv;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public long insert(net.neoforged.neoforge.transfer.fluid.FluidResource f, long amount, boolean simulate) {
            long n = inv.insert(f, amount, simulate);
            if (n > 0 && !simulate) saved();
            return n;
        }

        @Override
        public long extract(net.neoforged.neoforge.transfer.fluid.FluidResource f, long amount, boolean simulate) {
            long n = inv.extract(f, amount, simulate);
            if (n > 0 && !simulate) saved();
            return n;
        }

        private void saved() {
            com.powerfactor.storage.fluid.FluidCellItem.save(stack, inv);
            DriveBayBlockEntity.this.setChanged();
            if (level != null && level.getGameTime() - lastLedSync > 10) {
                lastLedSync = level.getGameTime();
                syncLeds();
            }
        }

        @Override
        public long count(net.neoforged.neoforge.transfer.fluid.FluidResource f) {
            return inv.count(f);
        }

        @Override
        public void collect(Map<net.neoforged.neoforge.transfer.fluid.FluidResource, Long> totals) {
            for (var e : inv.contents().entrySet()) totals.merge(e.getKey(), e.getValue(), Long::sum);
        }
    }

    public int ledTier(int slot) {
        return ledTier[slot];
    }

    public float ledFill(int slot) {
        return ledFill[slot];
    }

    @Override
    public boolean hasMenu() {
        return true;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new DriveBayMenu(containerId, inventory, this);
    }

    @Override
    public void dropContents(ServerLevel level, BlockPos pos) {
        Containers.dropContents(level, pos, cells);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        var leds = input.getIntArray("leds");
        if (leds.isPresent()) {
            // Client update: only the LED summary travels, never the cell contents.
            int[] a = leds.get();
            for (int i = 0; i < SLOTS && i * 2 + 1 < a.length; i++) {
                ledTier[i] = a[i * 2];
                ledFill[i] = a[i * 2 + 1] / 1000F;
            }
            return;
        }
        cells.clearContent();
        ContainerHelper.loadAllItems(input, cells.getItems());
        java.util.Arrays.fill(open, null);
        java.util.Arrays.fill(openFluid, null);
        priority = input.getIntOr("priority", 0);
        for (int i = 0; i < SLOTS; i++) {
            ItemStack s = cells.getItem(i);
            ledTier[i] = ledCode(s);
            ledFill[i] = ledFillOf(s);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, cells.getItems());
        output.putInt("priority", priority);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        int[] a = new int[SLOTS * 2];
        for (int i = 0; i < SLOTS; i++) {
            ItemStack s = cells.getItem(i);
            a[i * 2] = ledCode(s);
            a[i * 2 + 1] = Math.round(ledFillOf(s) * 1000);
        }
        tag.putIntArray("leds", a);
        return tag;
    }
}
