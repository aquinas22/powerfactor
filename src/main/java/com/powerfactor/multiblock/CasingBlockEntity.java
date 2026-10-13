package com.powerfactor.multiblock;

import com.powerfactor.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** Remembers which controller this casing belongs to while the structure is formed. */
public class CasingBlockEntity extends BlockEntity implements MultiblockPart {
    private @Nullable BlockPos controllerPos;

    public CasingBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CASING.get(), pos, state);
    }

    /** Join (or, with null, leave) a formed structure. */
    public void link(@Nullable BlockPos controller) {
        boolean changed = !java.util.Objects.equals(controller, controllerPos);
        if (!changed && getBlockState().getValue(CasingBlock.FORMED) == (controller != null)) {
            return;
        }
        controllerPos = controller;
        setChanged();
        if (level != null && getBlockState().hasProperty(CasingBlock.FORMED)) {
            level.setBlock(worldPosition, getBlockState().setValue(CasingBlock.FORMED, controller != null), Block.UPDATE_ALL);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public @Nullable BlockPos controllerPos() {
        return controllerPos;
    }

    @Override
    public @Nullable BlockEntity controller() {
        return controllerPos == null || level == null || !level.hasChunkAt(controllerPos)
                || !getBlockState().getValue(CasingBlock.FORMED) ? null : level.getBlockEntity(controllerPos);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        controllerPos = input.getLong("controller").map(BlockPos::of).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (controllerPos != null) {
            output.putLong("controller", controllerPos.asLong());
        }
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        var tag = new net.minecraft.nbt.CompoundTag();
        if (controllerPos != null) tag.putLong("controller", controllerPos.asLong());
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
}
