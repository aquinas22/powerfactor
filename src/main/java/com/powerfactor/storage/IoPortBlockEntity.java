package com.powerfactor.storage;

import com.powerfactor.registry.ModStorage;
import com.powerfactor.upgrade.UpgradeItem;
import com.powerfactor.upgrade.UpgradeType;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * The three network ports that face another block: the Storage Bus exposes that block's inventory
 * as network storage, the Importer pulls items from it, and the Exporter keeps it stocked.
 * All three share nine filter references, allow/deny, exact-component matching and speed cards.
 */
public class IoPortBlockEntity extends NodeBlockEntity {
    public enum Kind { STORAGE_BUS, IMPORTER, EXPORTER }

    public static final int FILTERS = 9, UPGRADES = 4;

    private final Kind kind;
    /** Ghost references: one item each, never real inventory. */
    private final SimpleContainer filters = new SimpleContainer(FILTERS) {
        @Override
        public void setChanged() {
            super.setChanged();
            IoPortBlockEntity.this.setChanged();
            StorageNetwork n = network();
            if (n != null && kind == Kind.STORAGE_BUS) n.markStoragesDirty();
        }
    };
    private final SimpleContainer upgrades = new SimpleContainer(UPGRADES) {
        @Override
        public boolean canPlaceItem(int index, ItemStack stack) {
            return stack.getItem() instanceof UpgradeItem u && u.type() == UpgradeType.SPEED;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            IoPortBlockEntity.this.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(IoPortBlockEntity.this, player);
        }
    };
    private boolean deny, exact;
    private int priority, cursor;
    /** Items moved in the last operation, for the activity light. */
    private int lastMoved;

    public IoPortBlockEntity(Kind kind, BlockPos pos, BlockState state) {
        super(switch (kind) {
            case STORAGE_BUS -> ModStorage.STORAGE_BUS_BE.get();
            case IMPORTER -> ModStorage.IMPORTER_BE.get();
            case EXPORTER -> ModStorage.EXPORTER_BE.get();
        }, pos, state);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public Container filters() {
        return filters;
    }

    public Container upgrades() {
        return upgrades;
    }

    public boolean deny() {
        return deny;
    }

    public boolean exact() {
        return exact;
    }

    public int priority() {
        return priority;
    }

    public void toggleDeny() {
        deny = !deny;
        filters.setChanged();
    }

    public void toggleExact() {
        exact = !exact;
        filters.setChanged();
    }

    public void setPriority(int p) {
        priority = Math.clamp(p, -99, 99);
        filters.setChanged();
    }

    private boolean emptyFilter() {
        for (int i = 0; i < FILTERS; i++) if (!filters.getItem(i).isEmpty()) return false;
        return true;
    }

    /** Whether the filter lets {@code r} through. An empty allow-list allows everything (except for exporters). */
    public boolean accepts(ItemResource r) {
        if (emptyFilter()) return kind != Kind.EXPORTER;
        boolean listed = false;
        for (int i = 0; i < FILTERS; i++) {
            if (ItemIO.matches(filters.getItem(i), r, exact)) {
                listed = true;
                break;
            }
        }
        return listed != deny;
    }

    private int speedCards() {
        int n = 0;
        for (int i = 0; i < UPGRADES; i++) if (!upgrades.getItem(i).isEmpty()) n++;
        return n;
    }

    /** Items per operation: 8, doubling per speed card up to 128. */
    public int batch() {
        return 8 << speedCards();
    }

    /** Ticks between operations: 10, down to 2 with four cards. */
    public int interval() {
        return Math.max(2, 10 - speedCards() * 2);
    }

    private @Nullable ResourceHandler<ItemResource> target(ServerLevel level) {
        Direction f = facing();
        return ItemIO.handler(level, worldPosition.relative(f), f.getOpposite());
    }

    @Override
    public int idleDraw() {
        return kind == Kind.STORAGE_BUS ? 2 : 1 + speedCards();
    }

    @Override
    public void networkTick(ServerLevel level, StorageNetwork network) {
        if (kind == Kind.STORAGE_BUS || (level.getGameTime() + worldPosition.asLong()) % interval() != 0) return;
        ResourceHandler<ItemResource> h = target(level);
        lastMoved = 0;
        if (h == null) return;
        if (kind == Kind.IMPORTER) importFrom(network, h);
        else exportTo(network, h);
    }

    private void importFrom(StorageNetwork network, ResourceHandler<ItemResource> h) {
        int budget = batch();
        int size = h.size();
        for (int k = 0; k < size && budget > 0; k++) {
            int slot = (cursor + k) % size;
            ItemResource r = h.getResource(slot);
            if (r.isEmpty() || !accepts(r)) continue;
            long can = Math.min(budget, ItemIO.extract(h, r, budget, true));
            if (can <= 0) continue;
            long fits = network.insert(r, can, true);
            if (fits <= 0) continue;
            long took = ItemIO.extract(h, r, fits, false);
            long put = network.insert(r, took, false);
            if (put < took) ItemIO.insert(h, r, took - put, false);
            budget -= (int) put;
            lastMoved += (int) put;
        }
        cursor = size == 0 ? 0 : (cursor + 1) % size;
    }

    private void exportTo(StorageNetwork network, ResourceHandler<ItemResource> h) {
        int budget = batch();
        for (int i = 0; i < FILTERS && budget > 0; i++) {
            int idx = (cursor + i) % FILTERS;
            ItemStack f = filters.getItem(idx);
            if (f.isEmpty()) continue;
            // Exact references export exactly that item; otherwise any variant the network has.
            for (ItemResource r : candidates(network, f)) {
                long avail = network.extract(r, budget, true);
                if (avail <= 0) continue;
                long fits = ItemIO.insert(h, r, avail, true);
                if (fits <= 0) continue;
                long got = network.extract(r, fits, false);
                long put = ItemIO.insert(h, r, got, false);
                if (put < got) network.insert(r, got - put, false);
                budget -= (int) put;
                lastMoved += (int) put;
                if (budget <= 0) break;
            }
        }
        cursor = (cursor + 1) % FILTERS;
    }

    private List<ItemResource> candidates(StorageNetwork network, ItemStack filter) {
        if (exact) return List.of(ItemResource.of(filter));
        return network.totals().keySet().stream().filter(r -> r.is(filter.getItem())).toList();
    }

    @Override
    public void addStorages(List<NetworkStorage> out) {
        if (kind == Kind.STORAGE_BUS && level instanceof ServerLevel server) out.add(new BusStorage(server));
    }

    /** The adjacent inventory, looked up fresh on each use so it follows chests being swapped. */
    private final class BusStorage implements NetworkStorage {
        private final ServerLevel level;

        BusStorage(ServerLevel level) {
            this.level = level;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public boolean external() {
            return true;
        }

        @Override
        public long insert(ItemResource r, long amount, boolean simulate) {
            ResourceHandler<ItemResource> h = target(level);
            return h == null || !accepts(r) ? 0 : ItemIO.insert(h, r, amount, simulate);
        }

        @Override
        public long extract(ItemResource r, long amount, boolean simulate) {
            ResourceHandler<ItemResource> h = target(level);
            return h == null ? 0 : ItemIO.extract(h, r, amount, simulate);
        }

        @Override
        public long count(ItemResource r) {
            ResourceHandler<ItemResource> h = target(level);
            if (h == null) return 0;
            long n = 0;
            for (int i = 0; i < h.size(); i++) if (r.equals(h.getResource(i))) n += h.getAmountAsLong(i);
            return n;
        }

        @Override
        public void collect(Map<ItemResource, Long> totals) {
            ResourceHandler<ItemResource> h = target(level);
            if (h == null) return;
            for (int i = 0; i < h.size(); i++) {
                ItemResource r = h.getResource(i);
                if (!r.isEmpty()) totals.merge(r, h.getAmountAsLong(i), Long::sum);
            }
        }
    }

    @Override
    public void writeMenuData(net.minecraft.network.RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(worldPosition);
        buf.writeVarInt(kind.ordinal());
    }

    @Override
    public boolean hasMenu() {
        return true;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new IoPortMenu(containerId, inventory, this);
    }

    @Override
    public void dropContents(ServerLevel level, BlockPos pos) {
        Containers.dropContents(level, pos, upgrades);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        filters.clearContent();
        upgrades.clearContent();
        input.child("filters").ifPresent(c -> ContainerHelper.loadAllItems(c, filters.getItems()));
        input.child("upgrades").ifPresent(c -> ContainerHelper.loadAllItems(c, upgrades.getItems()));
        deny = input.getBooleanOr("deny", false);
        exact = input.getBooleanOr("exact", false);
        priority = input.getIntOr("priority", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output.child("filters"), filters.getItems());
        ContainerHelper.saveAllItems(output.child("upgrades"), upgrades.getItems());
        output.putBoolean("deny", deny);
        output.putBoolean("exact", exact);
        output.putInt("priority", priority);
    }
}
