package com.powerfactor.logistics;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import org.jspecify.annotations.Nullable;

/** Item pipes move 16 items, fluid pipes 1,000 mB, per extract face every 10 ticks. */
public enum PipeKind {
    ITEM(Capabilities.Item.BLOCK, 16),
    FLUID(Capabilities.Fluid.BLOCK, 1_000);

    public final BlockCapability<? extends ResourceHandler<?>, @Nullable Direction> capability;
    public final int batch;

    PipeKind(BlockCapability<? extends ResourceHandler<?>, @Nullable Direction> capability, int batch) {
        this.capability = capability;
        this.batch = batch;
    }
}
