package com.powerfactor.generator;

import com.powerfactor.api.GridGenerator;
import com.powerfactor.api.MachineAudio;
import com.powerfactor.grid.BusLocator;
import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.registry.ModSounds;
import com.powerfactor.scada.ScadaReading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/**
 * A synchronous motor with nothing on its shaft. Spun up from the grid, it makes or soaks up EE on
 * demand (like a generator) while only drawing a little FE for its losses. The classic way to hold
 * up voltage on a grid full of motors, solar and wind.
 */
public class SynchronousCondenserBlockEntity extends MachineBlockEntity implements GridGenerator, MachineAudio.Looping {
    public static final int RATED_CE = 400, LOSSES = 6, SPIN_UP = 25;
    private static final int SYNC = 3600;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(4_000, 400, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private int rpm, syncedRpm;
    private double gridHertz = 60;
    private float angle, prevAngle;

    public SynchronousCondenserBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SYNCHRONOUS_CONDENSER.get(), pos, state);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        double hz = BusLocator.find(level, pos).hertz();
        if (hz > 1) {
            gridHertz = hz;
        }
        boolean running = enabled(level, pos) && energy.getAmountAsInt() >= (synced() ? LOSSES : SPIN_UP);
        if (running) {
            energy.set(energy.getAmountAsInt() - (synced() ? LOSSES : SPIN_UP));
            rpm = Math.min(SYNC, rpm + 18);
        } else {
            rpm = Math.max(0, rpm - 6);
        }
        setRunning(level, pos, state, synced());
        if (Math.abs(rpm - syncedRpm) > 150 || (rpm == 0) != (syncedRpm == 0)) {
            syncedRpm = rpm;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public boolean synced() {
        return rpm >= SYNC * 0.95;
    }

    @Override
    public double ratedCe() {
        return RATED_CE;
    }

    /** It follows the grid rather than setting it. */
    @Override
    public double hertz() {
        return synced() ? gridHertz : 0;
    }

    @Override
    public double eeCapacity() {
        return synced() ? RATED_CE : 0;
    }

    public EnergyHandler getEnergy() {
        return energy;
    }

    @Override
    public void clientTick() {
        super.clientTick();
        prevAngle = angle;
        angle = (angle + syncedRpm / 3600F * 30F) % 360F;
        if (angle < prevAngle) {
            prevAngle -= 360F;
        }
    }

    public float angle(float partial) {
        return prevAngle + (angle - prevAngle) * partial;
    }

    @Override
    public boolean hasMenu() {
        return false;
    }

    @Override
    public boolean soundActive() {
        return syncedRpm > 200;
    }

    @Override
    public SoundEvent loopSound() {
        return ModSounds.CONDENSER_RUN.get();
    }

    @Override
    public float loopPitch() {
        return 0.5F + 0.5F * syncedRpm / SYNC;
    }

    @Override
    public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), synced() ? ScadaReading.Status.RUNNING : rpm > 0 ? ScadaReading.Status.WARNING
                : ScadaReading.Status.OFF, 0, 0, Component.translatable(synced() ? "scada.powerfactor.condenser.synced"
                : "scada.powerfactor.condenser.spinning", rpm), true, remoteEnabled(), true, 1F, (float) hertz());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        rpm = input.getIntOr("rpm", 0);
        syncedRpm = rpm;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        output.putInt("rpm", rpm);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("rpm", syncedRpm);
        return tag;
    }
}
