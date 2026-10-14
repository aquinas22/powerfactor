package com.powerfactor.storage.fluid;

import com.powerfactor.storage.StorageNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * The network's fluids as a transactional fluid handler: one index per stored fluid, plus an empty
 * index that accepts any fluid. Changes apply to the network immediately and are journalled, so an
 * aborted transaction undoes them in reverse order; the journal clears on the root commit.
 */
public final class NetworkFluidHandler extends SnapshotJournal<Integer> implements ResourceHandler<FluidResource> {
    private record Op(FluidResource fluid, long delta) {}

    private final Supplier<@Nullable StorageNetwork> network;
    private final List<Op> log = new ArrayList<>();

    public NetworkFluidHandler(Supplier<@Nullable StorageNetwork> network) {
        this.network = network;
    }

    private @Nullable StorageNetwork net() {
        StorageNetwork n = network.get();
        return n != null && n.online() ? n : null;
    }

    private List<FluidResource> fluids() {
        StorageNetwork n = net();
        return n == null ? List.of() : new ArrayList<>(n.fluidTotals().keySet());
    }

    @Override
    public int size() {
        return fluids().size() + 1;
    }

    @Override
    public FluidResource getResource(int index) {
        List<FluidResource> f = fluids();
        return index >= 0 && index < f.size() ? f.get(index) : FluidResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        StorageNetwork n = net();
        FluidResource f = getResource(index);
        return n == null || f.isEmpty() ? 0 : n.fluidCount(f);
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        StorageNetwork n = net();
        if (n == null || resource.isEmpty()) return 0;
        return n.fluidCount(resource) + n.insertFluid(resource, Integer.MAX_VALUE, true);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return net() != null;
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        StorageNetwork n = net();
        if (n == null || resource.isEmpty() || amount <= 0) return 0;
        // Only the matching index or the trailing empty index accepts a fluid.
        FluidResource here = getResource(index);
        if (!here.isEmpty() && !here.equals(resource)) return 0;
        long moved = n.insertFluid(resource, amount, true);
        if (moved <= 0) return 0;
        updateSnapshots(transaction);
        moved = n.insertFluid(resource, moved, false);
        log.add(new Op(resource, moved));
        return (int) moved;
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        StorageNetwork n = net();
        if (n == null || resource.isEmpty() || amount <= 0 || !getResource(index).equals(resource)) return 0;
        long moved = n.extractFluid(resource, amount, true);
        if (moved <= 0) return 0;
        updateSnapshots(transaction);
        moved = n.extractFluid(resource, moved, false);
        log.add(new Op(resource, -moved));
        return (int) moved;
    }

    @Override
    protected Integer createSnapshot() {
        return log.size();
    }

    @Override
    protected void revertToSnapshot(Integer snapshot) {
        StorageNetwork n = network.get();
        for (int i = log.size() - 1; i >= snapshot; i--) {
            Op op = log.remove(i);
            if (n != null) n.revertFluid(op.fluid(), -op.delta());
        }
    }

    @Override
    protected void onRootCommit(Integer originalState) {
        log.clear();
    }
}
