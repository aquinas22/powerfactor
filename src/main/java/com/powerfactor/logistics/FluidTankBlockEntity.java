package com.powerfactor.logistics;

import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.registry.ModComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

public class FluidTankBlockEntity extends BlockEntity {
    public static final int CAPACITY = 32_000;

    private final TankReservoir reservoir = new TankReservoir(this);
    private FluidResource affinity = FluidResource.EMPTY;
    private boolean dirty = true;
    private int lastSignal = -1;
    private long balancedAt = Long.MIN_VALUE;

    private final FluidStacksResourceHandler tank = new FluidStacksResourceHandler(1, CAPACITY) {
        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            setChanged();
            dirty = true;
            if (level != null && !level.isClientSide()
                    && (previousContents.getAmount() / 500 != copyToList().getFirst().getAmount() / 500
                    || previousContents.isEmpty() != fluid().isEmpty()
                    || (previousContents.getAmount() == CAPACITY) != (fluid().getAmount() == CAPACITY)
                    || !FluidStack.isSameFluidSameComponents(previousContents, copyToList().getFirst()))) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    };

    public FluidTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLUID_TANK.get(), pos, state);
    }

    public FluidStacksResourceHandler getTank() {
        return tank;
    }

    public TankReservoir getReservoir() { return reservoir; }
    FluidResource identity() { return tank.getAmountAsInt(0) > 0 ? tank.getResource(0) : affinity; }
    public void markStructureDirty() { dirty = true; }

    public void serverTick() {
        if (level != null && !level.isClientSide() && balancedAt != level.getGameTime()
                && (dirty || level.getGameTime() % 20 == 0)) balance();
    }

    /** Equal surfaces within a layer; lower layers fill first. Local saves and local drops stay exact. */
    public void balance() {
        if (level == null || level.isClientSide()) return;
        var group = reservoir.group();
        long remaining = group.amount();
        boolean gas = !group.resource().isEmpty() && group.resource().getFluid().getFluidType().getDensity(group.resource().toStack(1)) < 0;
        var cells = group.cells();
        var positions = new java.util.HashSet<BlockPos>();
        cells.forEach(cell -> positions.add(cell.getBlockPos()));
        for (int start = 0; start < cells.size();) {
            int end = start + 1, y = cells.get(start).getBlockPos().getY();
            while (end < cells.size() && cells.get(end).getBlockPos().getY() == y) end++;
            int count = end - start;
            long layerAmount = Math.min(remaining, (long) count * CAPACITY);
            for (int i = start; i < end; i++) {
                var cell = cells.get(i);
                int share = gas ? (int) (group.amount() / cells.size() + (i < group.amount() % cells.size() ? 1 : 0))
                        : (int) (layerAmount / count + (i-start < layerAmount % count ? 1 : 0));
                if (cell.tank.getAmountAsInt(0) != share || (share > 0 && !cell.tank.getResource(0).equals(group.resource())))
                    cell.tank.set(0, share == 0 ? FluidResource.EMPTY : group.resource(), share);
                FluidResource lock = group.amount() > 0 ? group.resource() : FluidResource.EMPTY;
                if (!cell.affinity.equals(lock)) { cell.affinity = lock; cell.setChanged(); }
                BlockState before = cell.getBlockState(), after = before;
                for (var direction : net.minecraft.core.Direction.values())
                    after = after.setValue(FluidTankBlock.JOINED.get(direction), positions.contains(cell.getBlockPos().relative(direction)));
                if (before != after) level.setBlock(cell.getBlockPos(), after, Block.UPDATE_ALL);
                int signal = group.capacity() == 0 ? 0 : (int) Math.ceil(15.0 * group.amount() / group.capacity());
                if (cell.lastSignal != signal) {
                    cell.lastSignal = signal;
                    level.updateNeighbourForOutputSignal(cell.getBlockPos(), cell.getBlockState().getBlock());
                }
            }
            remaining -= layerAmount;
            start = end;
        }
        for (var cell : cells) { cell.dirty = false; cell.balancedAt = level.getGameTime(); }
    }

    public FluidStack fluid() {
        return tank.copyToList().getFirst();
    }

    public int comparator() {
        var group = reservoir.group();
        return group.capacity() == 0 ? 0 : (int) Math.ceil(15.0 * group.amount() / group.capacity());
    }

    public Component describe() {
        var group = reservoir.group();
        return Component.translatable(group.amount() == 0 ? "message.powerfactor.tank.reservoir_empty" : "message.powerfactor.tank.reservoir",
                group.resource().isEmpty() ? Component.literal("—") : group.resource().toStack(1).getHoverName(),
                String.format(java.util.Locale.ROOT, "%,d", group.amount()), String.format(java.util.Locale.ROOT, "%,d", group.capacity()), group.cells().size());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tank.deserialize(input);
        affinity = input.read("reservoir_affinity", FluidStack.CODEC).map(FluidResource::of).orElse(FluidResource.EMPTY);
        dirty = true;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tank.serialize(output);
        if (!affinity.isEmpty()) output.store("reservoir_affinity", FluidStack.CODEC, affinity.toStack(1));
    }

    // The tank item carries its contents.
    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        SimpleFluidContent content = components.get(ModComponents.FLUID.get());
        if (content != null && !content.isEmpty()) {
            FluidStack stack = content.copy();
            tank.set(0, FluidResource.of(stack), Math.clamp(stack.getAmount(), 0, CAPACITY));
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!fluid().isEmpty()) {
            components.set(ModComponents.FLUID.get(), SimpleFluidContent.copyOf(fluid()));
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }
}
