package com.powerfactor.storage.fluid;

import java.util.Map;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/** One place the network can keep fluids (amounts in mB): a fluid cell in a drive. */
public interface NetworkFluidStorage {
    int priority();

    long insert(FluidResource fluid, long amount, boolean simulate);

    long extract(FluidResource fluid, long amount, boolean simulate);

    long count(FluidResource fluid);

    void collect(Map<FluidResource, Long> totals);
}
