package com.powerfactor.storage.craft;

import com.google.common.primitives.Ints;
import com.powerfactor.registry.ModStorage;
import com.powerfactor.storage.DataCableBlock;
import com.powerfactor.storage.ItemIO;
import com.powerfactor.storage.NodeBlockEntity;
import com.powerfactor.storage.StorageNetwork;
import java.util.ArrayList;
import java.util.List;
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
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Hands processing patterns to real machines. Each execution pushes every input into one adjacent
 * inventory (all or nothing). Machines eject their products back into the provider (it accepts
 * items on every face) or anywhere else on the network; the job claims what it is waiting for.
 */
public class PatternProviderBlockEntity extends NodeBlockEntity implements PatternHolder {
    public static final int PATTERNS = 9, RETURN_SLOTS = 9;

    private final SimpleContainer patternSlots = new SimpleContainer(PATTERNS) {
        @Override
        public boolean canPlaceItem(int index, ItemStack stack) {
            return EncodedPatternItem.processing(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(PatternProviderBlockEntity.this, player);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            cached = null;
            PatternProviderBlockEntity.this.setChanged();
            StorageNetwork n = network();
            if (n != null) n.markPatternsDirty();
        }
    };
    /** Products coming back from machines, moved into the network each tick. */
    private final ItemStacksResourceHandler returns = new ItemStacksResourceHandler(RETURN_SLOTS) {
        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            setChanged();
        }
    };
    private List<PatternData> cached;
    private int pushed;
    private boolean blocking;

    public PatternProviderBlockEntity(BlockPos pos, BlockState state) {
        super(ModStorage.PATTERN_PROVIDER_BE.get(), pos, state);
    }

    public Container patternSlots() {
        return patternSlots;
    }

    public ResourceHandler<ItemResource> returnHandler() {
        return returns;
    }

    public boolean blocking() {
        return blocking;
    }

    public void toggleBlocking() {
        blocking = !blocking;
        setChanged();
    }

    @Override
    public List<PatternData> patterns() {
        if (cached == null) {
            List<PatternData> list = new ArrayList<>();
            for (int i = 0; i < PATTERNS; i++) {
                PatternData p = EncodedPatternItem.data(patternSlots.getItem(i));
                if (p != null && p.processing() && p.valid()) list.add(p);
            }
            cached = list;
        }
        return cached;
    }

    /** Adjacent inventories that aren't part of the network itself. */
    private List<ResourceHandler<ItemResource>> targets(ServerLevel level) {
        List<ResourceHandler<ItemResource>> list = new ArrayList<>();
        for (Direction d : Direction.values()) {
            BlockPos p = worldPosition.relative(d);
            if (level.getBlockEntity(p) instanceof NodeBlockEntity || level.getBlockState(p).getBlock() instanceof DataCableBlock) continue;
            ResourceHandler<ItemResource> h = ItemIO.handler(level, p, d.getOpposite());
            if (h != null) list.add(h);
        }
        return list;
    }

    @Override
    public boolean ready(PatternData pattern) {
        return active() && level instanceof ServerLevel server && !targets(server).isEmpty();
    }

    @Override
    public boolean accept(ServerLevel level, PatternData pattern, List<ItemStack> inputs) {
        for (ResourceHandler<ItemResource> h : targets(level)) {
            if (blocking && holdsAny(h, pattern)) continue;
            try (Transaction tx = Transaction.openRoot()) {
                boolean all = true;
                for (ItemStack s : inputs) {
                    if (s.isEmpty()) continue;
                    if (h.insert(ItemResource.of(s), s.getCount(), tx) < s.getCount()) {
                        all = false;
                        break;
                    }
                }
                if (all) {
                    tx.commit();
                    pushed++;
                    setChanged();
                    return true;
                }
            }
        }
        return false;
    }

    /** Blocking mode: wait until the machine has used up the previous batch's inputs. */
    private static boolean holdsAny(ResourceHandler<ItemResource> h, PatternData p) {
        for (var in : p.inputMap().keySet()) {
            for (int i = 0; i < h.size(); i++) {
                if (in.equals(h.getResource(i)) && h.getAmountAsLong(i) > 0) return true;
            }
        }
        return false;
    }

    @Override
    public void networkTick(ServerLevel level, StorageNetwork network) {
        for (int i = 0; i < returns.size(); i++) {
            ItemResource r = returns.getResource(i);
            if (r.isEmpty()) continue;
            long amount = returns.getAmountAsLong(i);
            long accepted = network.insert(r, amount, true);
            if (accepted <= 0) continue;
            try (Transaction tx = Transaction.openRoot()) {
                int took = returns.extract(i, r, Ints.saturatedCast(accepted), tx);
                tx.commit();
                long put = network.insert(r, took, false);
                if (put < took) {
                    try (Transaction back = Transaction.openRoot()) {
                        returns.insert(i, r, (int) (took - put), back);
                        back.commit();
                    }
                }
            }
        }
    }

    @Override
    public int idleDraw() {
        return 2;
    }

    @Override
    public void writeMenuData(net.minecraft.network.RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(worldPosition);
        buf.writeBoolean(false);
    }

    @Override
    public boolean hasMenu() {
        return true;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new PatternRackMenu(containerId, inventory, this);
    }

    @Override
    public void dropContents(ServerLevel level, BlockPos pos) {
        Containers.dropContents(level, pos, patternSlots);
        for (int i = 0; i < returns.size(); i++) {
            ItemResource r = returns.getResource(i);
            if (!r.isEmpty()) Containers.dropItemStack(level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, r.toStack(returns.getAmountAsInt(i)));
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        patternSlots.clearContent();
        input.child("patterns").ifPresent(c -> ContainerHelper.loadAllItems(c, patternSlots.getItems()));
        input.child("returns").ifPresent(returns::deserialize);
        cached = null;
        blocking = input.getBooleanOr("blocking", false);
        pushed = input.getIntOr("pushed", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output.child("patterns"), patternSlots.getItems());
        returns.serialize(output.child("returns"));
        output.putBoolean("blocking", blocking);
        output.putInt("pushed", pushed);
    }
}
