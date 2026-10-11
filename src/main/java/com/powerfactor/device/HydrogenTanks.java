package com.powerfactor.device;

import com.powerfactor.registry.ModFluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Fluid storage for the hydrogen machines, seen from outside through one handler: slots that only
 * accept from pipes (inputs) and slots that only give (outputs).
 */
public class HydrogenTanks extends FluidStacksResourceHandler {
    private final net.minecraft.world.level.material.Fluid[] fluids;
    private final boolean[] output;
    private final Runnable changed;

    /** One slot per fluid; {@code output[i]} slots can be drained by pipes but not filled, the rest the reverse. */
    public HydrogenTanks(int capacity, net.minecraft.world.level.material.Fluid[] fluids, boolean[] output, Runnable changed) {
        super(fluids.length, capacity);
        this.fluids = fluids;
        this.output = output;
        this.changed = changed;
    }

    public static boolean isHydrogen(FluidResource resource) {
        return resource.getFluid().isSame(ModFluids.HYDROGEN.get());
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return resource.getFluid().isSame(fluids[index]);
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return output[index] ? 0 : super.insert(index, resource, amount, transaction);
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return output[index] ? super.extract(index, resource, amount, transaction) : 0;
    }

    public int amount(int index) {
        return getAmountAsInt(index);
    }

    /** Machine-internal change, bypassing the in/out rules. */
    public void add(int index, int delta) {
        int now = Math.max(0, amount(index) + delta);
        set(index, now == 0 ? FluidResource.EMPTY : FluidResource.of(fluids[index]), now);
    }

    @Override
    protected void onContentsChanged(int index, FluidStack previousContents) {
        changed.run();
    }
}
