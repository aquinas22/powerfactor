package com.powerfactor.generator;

import com.powerfactor.api.GridGenerator;
import com.powerfactor.api.MachineAudio;
import com.powerfactor.grid.GridManager;
import com.powerfactor.grid.PowerGrid;
import com.powerfactor.grid.WireBlock;
import com.powerfactor.power.PowerTriangle;
import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.registry.ModSounds;
import com.powerfactor.scada.ScadaReading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Run-of-river hydro: every face with moving water around it adds output, falling water from above
 * counts double. It's a synchronous machine, so unlike solar or wind it can supply EE.
 */
public class HydroTurbineBlockEntity extends PushingGenerator implements GridGenerator, MachineAudio.Looping {
    public static final int PER_FACE = 20, RATED_CE = 120;
    private int flowFaces;
    private double eeShare;
    private float syncedSpeed, angle, prevAngle;

    public HydroTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HYDRO_TURBINE.get(), pos, state, 4_000);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getGameTime() % 20 == 0) {
            flowFaces = 0;
            for (Direction d : Direction.values()) {
                FluidState f = level.getFluidState(pos.relative(d));
                if (f.is(FluidTags.WATER) && (!f.isSource() || d == Direction.UP)) {
                    flowFaces += d == Direction.UP ? 2 : 1;
                }
            }
        }
        eeShare = 0;
        for (Direction d : Direction.values()) {
            if (level.getBlockState(pos.relative(d)).getBlock() instanceof WireBlock) {
                PowerGrid grid = GridManager.get(level).gridAt(pos.relative(d));
                if (grid != null) {
                    eeShare = grid.eeShareFor(this);
                    break;
                }
            }
        }
        int fe = enabled(level, pos) ? Math.min(flowFaces * PER_FACE, (int) PowerTriangle.maxFeFor(RATED_CE, eeShare)) : 0;
        generate(fe);
        push(level, pos);
        setRunning(level, pos, state, lastFe > 0);
        float speed = Math.min(1F, flowFaces / 4F);
        if (Math.abs(speed - syncedSpeed) > 0.05F) {
            syncedSpeed = speed;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public double ratedCe() {
        return RATED_CE;
    }

    @Override
    public double hertz() {
        return flowFaces > 0 ? 60.0 : 0;
    }

    @Override
    public double eeCapacity() {
        return flowFaces > 0 ? Math.sqrt(Math.max(0, RATED_CE * RATED_CE - (double) lastFe * lastFe)) : 0;
    }

    @Override
    public void clientTick() {
        super.clientTick();
        prevAngle = angle;
        angle = (angle + syncedSpeed * 18F) % 360F;
        if (angle < prevAngle) {
            prevAngle -= 360F;
        }
    }

    public float angle(float partial) {
        return prevAngle + (angle - prevAngle) * partial;
    }

    @Override
    public boolean soundActive() {
        return syncedSpeed > 0;
    }

    @Override
    public SoundEvent loopSound() {
        return ModSounds.HYDRO_TURBINE_RUN.get();
    }

    @Override
    public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), lastFe > 0 ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF, lastFe,
                (int) Math.round(eeShare), Component.translatable("scada.powerfactor.hydro", flowFaces), true, remoteEnabled(), true, 1F,
                (float) hertz());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        syncedSpeed = input.getFloatOr("speed", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putFloat("speed", syncedSpeed);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("speed", syncedSpeed);
        return tag;
    }
}
