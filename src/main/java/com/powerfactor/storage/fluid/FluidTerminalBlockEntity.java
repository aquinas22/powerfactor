package com.powerfactor.storage.fluid;

import com.powerfactor.registry.ModStorage;
import com.powerfactor.storage.NodeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Fluid Terminal: browse the network's fluids and fill or empty buckets and tanks against them. */
public class FluidTerminalBlockEntity extends NodeBlockEntity {
    private final NetworkFluidHandler handler = new NetworkFluidHandler(this::onlineNetwork);

    public FluidTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModStorage.FLUID_TERMINAL_BE.get(), pos, state);
    }

    public NetworkFluidHandler handler() {
        return handler;
    }

    public boolean usable(Player player) {
        return !isRemoved() && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(worldPosition)) < 64;
    }

    @Override
    public int idleDraw() {
        return 2;
    }

    @Override
    public boolean hasMenu() {
        return true;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new FluidTerminalMenu(containerId, inventory, this);
    }
}
