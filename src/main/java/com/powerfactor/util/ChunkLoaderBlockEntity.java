package com.powerfactor.util;

import com.powerfactor.PowerFactor;
import com.powerfactor.registry.ModUtility;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.world.chunk.TicketController;

/**
 * Chunk Loader: keeps the 3x3 chunks around it loaded and ticking; each Range Upgrade (up to three)
 * adds a ring, to 9x9. Tickets are owned by this block's
 * position, persist across restarts, and are dropped on load for any loader that no longer exists.
 */
public class ChunkLoaderBlockEntity extends BlockEntity {
    public static final int MAX_CARDS = 3;
    public static final TicketController TICKETS = new TicketController(PowerFactor.id("chunk_loader"), (level, helper) -> {
        for (BlockPos owner : java.util.List.copyOf(helper.getBlockTickets().keySet())) {
            if (!(level.getBlockEntity(owner) instanceof ChunkLoaderBlockEntity)) helper.removeAllTickets(owner);
        }
    });

    private boolean active;
    private int cards, forcedRadius = -1;

    public ChunkLoaderBlockEntity(BlockPos pos, BlockState state) {
        super(ModUtility.CHUNK_LOADER_BE.get(), pos, state);
    }

    public int radius() {
        return 1 + cards;
    }

    public int cards() {
        return cards;
    }

    public void serverTick(ServerLevel level) {
        if (!active || forcedRadius != radius() || level.getGameTime() % 600 == 0) {
            if (forcedRadius >= 0 && forcedRadius != radius()) force(level, false, forcedRadius);
            force(level, true, radius());
            forcedRadius = radius();
            active = true;
        }
    }

    void force(ServerLevel level, boolean add, int radius) {
        ChunkPos c = ChunkPos.containing(worldPosition);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                TICKETS.forceChunk(level, worldPosition, c.x() + dx, c.z() + dz, add, true);
            }
        }
    }

    /** Adds a card (true if taken) or, with {@code remove}, takes one out (true if there was one). */
    public boolean changeCards(boolean remove) {
        if (remove ? cards == 0 : cards >= MAX_CARDS) return false;
        cards += remove ? -1 : 1;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        return true;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel server) {
            force(server, false, Math.max(forcedRadius, radius()));
            if (cards > 0) net.minecraft.world.Containers.dropItemStack(server, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5,
                    new net.minecraft.world.item.ItemStack(ModUtility.RANGE_UPGRADE.get(), cards));
        }
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        super.loadAdditional(input);
        cards = Math.clamp(input.getIntOr("cards", 0), 0, MAX_CARDS);
    }

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("cards", cards);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("cards", cards);
        return tag;
    }
}
