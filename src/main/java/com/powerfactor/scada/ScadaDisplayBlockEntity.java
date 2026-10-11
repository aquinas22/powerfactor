package com.powerfactor.scada;

import com.powerfactor.registry.ModBlockEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.network.PacketDistributor;

public class ScadaDisplayBlockEntity extends BlockEntity {
    public enum Page { AUTO, SUMMARY, LIST, TREND, DIAGRAM, DEVICE }

    private static final int POLL_TICKS = 10;
    private Page page = Page.AUTO;
    /** Client: the wall this block belongs to, recomputed every second. */
    private DisplayWall wall;

    public ScadaDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SCADA_DISPLAY.get(), pos, state);
    }

    public Page page() {
        return page;
    }

    public Page cyclePage(int step) {
        page = Page.values()[Math.floorMod(page.ordinal() + step, Page.values().length)];
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        return page;
    }

    public DisplayWall wall() {
        if (wall == null && level != null) {
            wall = DisplayWall.find(level, worldPosition, getBlockState());
        }
        return wall;
    }

    void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if ((level.getGameTime() + pos.asLong()) % POLL_TICKS != 0) {
            return;
        }
        DisplayWall wall = DisplayWall.find(level, pos, state);
        if (!wall.master().equals(pos)) {
            return;
        }
        boolean watched = level.players().stream().anyMatch(p -> p.blockPosition().closerThan(pos, 96));
        if (!watched) {
            return;
        }
        List<ScadaReading> readings = ScadaNetwork.poll(level, wall.blocks());
        PacketDistributor.sendToPlayersTrackingChunk(level, ChunkPos.containing(pos), new ScadaPayloads.DisplaySnapshot(pos, readings));
    }

    void clientTick(Level level, BlockPos pos, BlockState state) {
        if (level.getGameTime() % 20 == 0) {
            wall = DisplayWall.find(level, pos, state);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        page = Page.values()[Math.clamp(input.getIntOr("page", 0), 0, Page.values().length - 1)];
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("page", page.ordinal());
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
