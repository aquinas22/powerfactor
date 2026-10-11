package com.powerfactor.grid;

import com.powerfactor.api.ScadaDevice;
import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.registry.ModSounds;
import com.powerfactor.scada.ScadaReading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class BreakerBlockEntity extends BlockEntity implements ScadaDevice {
    /** Trips after this long above the pickup current. */
    private static final double PICKUP = 1.15;
    private static final int DELAY = 40;

    private int overTicks;
    private boolean tripped;
    private double current;

    public BreakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BREAKER.get(), pos, state);
    }

    void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        boolean closed = state.getValue(BreakerBlock.CLOSED);
        if (level.hasNeighborSignal(pos) && closed) {
            operate(level, false, false);
            return;
        }
        PowerGrid grid = closed ? GridManager.get(level).gridAt(pos) : null;
        current = grid == null ? 0 : grid.state().current();
        overTicks = current > PICKUP ? overTicks + 1 : 0;
        if (overTicks > DELAY) {
            overTicks = 0;
            operate(level, false, true);
        }
    }

    /** Opens or closes, with the big clunk; {@code trip} marks it as a protection trip. */
    public void operate(ServerLevel level, boolean close, boolean trip) {
        BlockState state = getBlockState();
        if (state.getValue(BreakerBlock.CLOSED) == close) {
            return;
        }
        tripped = trip;
        level.setBlock(worldPosition, state.setValue(BreakerBlock.CLOSED, close), Block.UPDATE_ALL);
        GridManager.get(level).invalidate(worldPosition);
        level.playSound(null, worldPosition, close ? ModSounds.TURBINE_BREAKER_CLOSE.get() : ModSounds.BREAKER_TRIP.get(), SoundSource.BLOCKS,
                1.0F, close ? 1.1F : 1.0F);
        setChanged();
    }

    @Override
    public ScadaReading scadaReading() {
        boolean closed = getBlockState().getValue(BreakerBlock.CLOSED);
        ScadaReading.Status status = tripped ? ScadaReading.Status.ALARM : closed ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF;
        Component detail = Component.translatable(tripped ? "scada.powerfactor.breaker.tripped" : closed ? "scada.powerfactor.breaker.closed"
                : "scada.powerfactor.breaker.open", Math.round(current * 100));
        return new ScadaReading(worldPosition, getBlockState().getBlock().getName(), status, 0, 0, detail, true, closed, false, 1F, 60F);
    }

    @Override
    public boolean remoteEnabled() {
        return getBlockState().getValue(BreakerBlock.CLOSED);
    }

    @Override
    public void setRemoteEnabled(boolean enabled) {
        if (level instanceof ServerLevel server) {
            operate(server, enabled, false);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tripped = input.getBooleanOr("tripped", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("tripped", tripped);
    }
}
