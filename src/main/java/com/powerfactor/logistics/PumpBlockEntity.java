package com.powerfactor.logistics;

import com.powerfactor.machine.MotorDrive;
import com.powerfactor.machine.ProcessingBlockEntity;
import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.registry.ModSounds;
import com.powerfactor.upgrade.UpgradeType;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import org.jspecify.annotations.Nullable;

/** Motor-driven pump: draws water from a water source below it (without using it up) into its tank. */
public class PumpBlockEntity extends ProcessingBlockEntity {
    public static final int CAPACITY = 8_000, PER_STROKE = 100, STROKE_TICKS = 10;

    private final FluidStacksResourceHandler tank = new FluidStacksResourceHandler(1, CAPACITY) {
        @Override
        protected void onContentsChanged(int index, FluidStack previous) {
            setChanged();
        }
    };

    public PumpBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ELECTRIC_PUMP.get(), pos, state, new int[0], new int[0], 4_000, 10, new MotorDrive(8),
                EnumSet.allOf(UpgradeType.class));
    }

    @Override
    protected @Nullable Job findJob(ServerLevel level) {
        BlockPos below = worldPosition.below();
        if (!level.getFluidState(below).isSourceOfType(Fluids.WATER) || CAPACITY - tank.getAmountAsInt(0) < PER_STROKE) {
            return null;
        }
        return new Job(STROKE_TICKS, Component.translatable("block.minecraft.water"),
                () -> tank.set(0, FluidResource.of(Fluids.WATER), tank.getAmountAsInt(0) + PER_STROKE));
    }

    @Override
    protected boolean isValidInput(int slot, ItemStack stack) {
        return false;
    }

    @Override
    protected int tankAmount() {
        return tank.getAmountAsInt(0);
    }

    public FluidStacksResourceHandler getTank() {
        return tank;
    }

    @Override
    public SoundEvent loopSound() {
        return ModSounds.PUMP_RUN.get();
    }

    @Override
    public float loopVolume() {
        return 0.3F;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tank.deserialize(input.childOrEmpty("tank"));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tank.serialize(output.child("tank"));
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new PumpMenu(containerId, inventory, items, upgrades, data, this);
    }
}
