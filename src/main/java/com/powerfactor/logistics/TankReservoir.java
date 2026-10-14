package com.powerfactor.logistics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** A live aggregate over individually saved cells. No controller owns somebody else's fluid. */
public final class TankReservoir implements ResourceHandler<FluidResource> {
    public record Group(List<FluidTankBlockEntity> cells, FluidResource resource, long amount) {
        public long capacity() { return (long) cells.size() * FluidTankBlockEntity.CAPACITY; }
    }
    private final FluidTankBlockEntity owner;
    public TankReservoir(FluidTankBlockEntity owner) { this.owner = owner; }

    public Group group() {
        var level = owner.getLevel();
        if (level == null || owner.isRemoved() || !level.hasChunkAt(owner.getBlockPos())
                || level.getBlockEntity(owner.getBlockPos()) != owner) return new Group(List.of(), FluidResource.EMPTY, 0);
        var queue = new ArrayDeque<BlockPos>();
        var seen = new HashSet<BlockPos>();
        var cells = new ArrayList<FluidTankBlockEntity>();
        FluidResource kind = owner.identity();
        long amount = 0;
        queue.add(owner.getBlockPos());
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            if (!seen.add(pos) || !level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof FluidTankBlockEntity tank)) continue;
            FluidResource identity = tank.identity();
            if (!kind.isEmpty() && !identity.isEmpty() && !kind.equals(identity)) continue;
            if (kind.isEmpty() && !identity.isEmpty()) kind = identity;
            cells.add(tank);
            amount += tank.getTank().getAmountAsLong(0);
            for (Direction direction : Direction.values()) queue.addLast(pos.relative(direction));
        }
        cells.sort(Comparator.comparingInt((FluidTankBlockEntity tank) -> tank.getBlockPos().getY())
                .thenComparingInt(tank -> tank.getBlockPos().getX()).thenComparingInt(tank -> tank.getBlockPos().getZ()));
        return new Group(List.copyOf(cells), kind, amount);
    }

    @Override public int size() { return 1; }
    private static void slot(int index) { if (index != 0) throw new IndexOutOfBoundsException(index); }
    @Override public FluidResource getResource(int index) { slot(index); var group = group(); return group.amount() > 0 ? group.resource() : FluidResource.EMPTY; }
    @Override public long getAmountAsLong(int index) { slot(index); return group().amount(); }
    @Override public long getCapacityAsLong(int index, FluidResource resource) { slot(index); return group().capacity(); }
    @Override public boolean isValid(int index, FluidResource resource) {
        slot(index); var group = group();
        return !resource.isEmpty() && !group.cells().isEmpty() && (group.amount() == 0 || group.resource().equals(resource));
    }
    @Override public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        slot(index); if (amount <= 0 || resource.isEmpty()) return 0;
        Group group = group();
        if (group.amount() > 0 && !group.resource().equals(resource)) return 0;
        int inserted = 0;
        for (var cell : group.cells()) {
            inserted += cell.getTank().insert(0, resource, amount - inserted, transaction);
            if (inserted == amount) break;
        }
        return inserted;
    }
    @Override public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        slot(index); if (amount <= 0 || resource.isEmpty()) return 0;
        Group group = group();
        if (!group.resource().equals(resource)) return 0;
        int extracted = 0;
        for (int i = group.cells().size()-1; i >= 0 && extracted < amount; i--)
            extracted += group.cells().get(i).getTank().extract(0, resource, amount - extracted, transaction);
        return extracted;
    }
}
