package com.powerfactor.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A device on a storage network. The controller discovers nodes by walking data cables, hands each
 * one the network, ticks it, and bills its idle draw against the controller's FE buffer.
 */
public abstract class NodeBlockEntity extends BlockEntity implements MenuProvider {
    private @Nullable StorageNetwork network;
    /** Client-side animation clock, advanced only while the node is online. */
    protected float clientClock, prevClientClock;

    protected NodeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public @Nullable StorageNetwork network() {
        return network != null && network.valid() ? network : null;
    }

    /** The network, only when powered and healthy. */
    public @Nullable StorageNetwork onlineNetwork() {
        StorageNetwork n = network();
        return n != null && n.online() ? n : null;
    }

    void attach(@Nullable StorageNetwork network) {
        if (this.network != network) {
            this.network = network;
            onNetworkChanged();
        }
    }

    protected void onNetworkChanged() {}

    /** FE per tick while connected. */
    public int idleDraw() {
        return 1;
    }

    /** Called by the network every tick while online. */
    public void networkTick(ServerLevel level, StorageNetwork network) {}

    /** Called by the network when this node's storages, patterns or similar may need re-reading. */
    public void addStorages(java.util.List<NetworkStorage> out) {}

    /** Fluid storages this node contributes (fluid cells in a drive bay). */
    public void addFluidStorages(java.util.List<com.powerfactor.storage.fluid.NetworkFluidStorage> out) {}

    public Direction facing() {
        BlockState s = getBlockState();
        return s.hasProperty(NodeBlock.FACING) ? s.getValue(NodeBlock.FACING) : Direction.NORTH;
    }

    void setActive(boolean active) {
        BlockState s = getBlockState();
        if (level != null && s.hasProperty(NodeBlock.ACTIVE) && s.getValue(NodeBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, s.setValue(NodeBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
    }

    public boolean active() {
        BlockState s = getBlockState();
        return s.hasProperty(NodeBlock.ACTIVE) && s.getValue(NodeBlock.ACTIVE);
    }

    public void clientTick() {
        prevClientClock = clientClock;
        if (active()) clientClock += 1;
    }

    public float clock(float partial) {
        return prevClientClock + (clientClock - prevClientClock) * partial;
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel server) StorageNetworks.invalidate(server, worldPosition);
        super.setRemoved();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) StorageNetworks.invalidate(server, worldPosition);
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    public boolean hasMenu() {
        return false;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return null;
    }

    /** Items to drop when the block is broken. */
    public void dropContents(ServerLevel level, BlockPos pos) {}

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel server) dropContents(server, pos);
    }

    /** Extra data the client menu constructor reads; the position by default. */
    public void writeMenuData(net.minecraft.network.RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(worldPosition);
    }
}
