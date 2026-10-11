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

    private final FluidStacksResourceHandler tank = new FluidStacksResourceHandler(1, CAPACITY) {
        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            setChanged();
            if (level != null && !level.isClientSide()
                    && (previousContents.getAmount() / 500 != copyToList().getFirst().getAmount() / 500
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

    public FluidStack fluid() {
        return tank.copyToList().getFirst();
    }

    public int comparator() {
        return (int) Math.ceil(15.0 * fluid().getAmount() / CAPACITY);
    }

    public Component describe() {
        FluidStack f = fluid();
        return f.isEmpty() ? Component.translatable("message.powerfactor.tank.empty")
                : Component.translatable("message.powerfactor.tank", f.getHoverName(), String.format("%,d", f.getAmount()), String.format("%,d", CAPACITY));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tank.deserialize(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tank.serialize(output);
    }

    // The tank item carries its contents.
    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        SimpleFluidContent content = components.get(ModComponents.FLUID.get());
        if (content != null && !content.isEmpty()) {
            FluidStack stack = content.copy();
            tank.set(0, FluidResource.of(stack), stack.getAmount());
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
