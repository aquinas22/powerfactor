package com.powerfactor.grid;

import com.powerfactor.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/** Remembers which pylons this one has conductors strung to (both ends store the link). */
public class PylonBlockEntity extends BlockEntity {
    public static final int MAX_SPAN = 32, MAX_LINKS = 4;
    private final List<BlockPos> links = new ArrayList<>();

    public PylonBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PYLON.get(), pos, state);
    }

    public List<BlockPos> links() {
        return links;
    }

    public boolean link(BlockPos other) {
        if (links.contains(other) || links.size() >= MAX_LINKS) {
            return false;
        }
        links.add(other.immutable());
        changed();
        return true;
    }

    public void unlink(BlockPos other) {
        if (links.remove(other)) {
            changed();
        }
    }

    private void changed() {
        setChanged();
        if (level instanceof ServerLevel server) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            GridManager.get(server).invalidate(worldPosition);
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) {
            for (BlockPos other : List.copyOf(links)) {
                if (level.getBlockEntity(other) instanceof PylonBlockEntity p) {
                    p.unlink(pos);
                }
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        links.clear();
        input.getIntArray("links").ifPresent(a -> {
            for (int i = 0; i + 2 < a.length; i += 3) {
                links.add(new BlockPos(a[i], a[i + 1], a[i + 2]));
            }
        });
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        int[] a = new int[links.size() * 3];
        for (int i = 0; i < links.size(); i++) {
            a[i * 3] = links.get(i).getX();
            a[i * 3 + 1] = links.get(i).getY();
            a[i * 3 + 2] = links.get(i).getZ();
        }
        output.putIntArray("links", a);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    public AABB renderBox() {
        AABB box = new AABB(worldPosition).expandTowards(0, 2, 0);
        for (BlockPos l : links) {
            box = box.minmax(new AABB(l));
        }
        return box;
    }
}
