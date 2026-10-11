package com.powerfactor.grid;

import com.powerfactor.api.GridGenerator;
import com.powerfactor.api.MachineAudio;
import com.powerfactor.api.ReactiveLoad;
import com.powerfactor.api.SidedGridDevice;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.registry.ModSounds;
import com.powerfactor.scada.ScadaReading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

public class TransformerBlockEntity extends MachineBlockEntity implements ReactiveLoad, GridGenerator, SidedGridDevice, MachineAudio.Looping {
    public static final int RATING = 2_048;
    private static final double EFFICIENCY = 0.98, MAGNETIZING = 0.01;
    private static final int TRIP_TICKS = 200;

    private final SimpleEnergyHandler primary = new SimpleEnergyHandler(RATING * 2, RATING * 2, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private int lastFe;
    private double lastEe, hertz;
    private int overload;
    private boolean tripped;

    public TransformerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRANSFORMER.get(), pos, state);
    }

    private Direction front() {
        return getBlockState().getValue(MachineBlock.FACING);
    }

    @Override
    public Role role(Direction face) {
        return face == front().getOpposite() ? Role.LOAD : face == front() ? Role.SOURCE : Role.NONE;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        GridManager grids = GridManager.get(level);
        PowerGrid in = grids.gridAt(pos.relative(front().getOpposite()));
        PowerGrid out = grids.gridAt(pos.relative(front()));
        hertz = in != null && in.energized() && !tripped ? in.hertz() : 0;
        lastFe = 0;
        lastEe = 0;
        if (out != null && hertz > 0 && enabled(level, pos)) {
            int available = (int) (primary.getAmountAsInt() * EFFICIENCY);
            try (Transaction tx = Transaction.openRoot()) {
                int moved = out.handler().insert(Math.min(available, RATING), tx);
                tx.commit();
                primary.set(primary.getAmountAsInt() - (int) Math.ceil(moved / EFFICIENCY));
                lastFe = moved;
            }
            lastEe = out.eeShareFor(this);
        }
        double loading = Math.hypot(lastFe, lastEe) / RATING;
        overload = loading > 1.1 ? overload + 1 : 0;
        if (overload == 1) {
            level.playSound(null, pos, ModSounds.TRANSFORMER_OVERLOAD.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        if (overload > TRIP_TICKS) {
            tripped = true;
            overload = 0;
            level.playSound(null, pos, ModSounds.BREAKER_TRIP.get(), SoundSource.BLOCKS, 1.0F, 0.8F);
        }
        setRunning(level, pos, state, lastFe > 0);
    }

    public void reset() {
        tripped = false;
        overload = 0;
        setChanged();
    }

    public Component describe() {
        return Component.translatable(tripped ? "message.powerfactor.transformer.tripped" : "message.powerfactor.transformer",
                lastFe, (int) Math.round(lastEe), (int) Math.round(100 * Math.hypot(lastFe, lastEe) / RATING));
    }

    /** EE drawn from the primary: what it supplies downstream plus its own magnetizing current. */
    @Override
    public double eeDemand() {
        return hertz > 0 ? lastEe + RATING * MAGNETIZING : 0;
    }

    @Override
    public double ratedCe() {
        return RATING;
    }

    @Override
    public double hertz() {
        return hertz;
    }

    @Override
    public double eeCapacity() {
        return hertz > 0 ? Math.sqrt(Math.max(0, (double) RATING * RATING - (double) lastFe * lastFe)) : 0;
    }

    public @Nullable EnergyHandler primaryHandler(@Nullable Direction side) {
        return side == null || side == front().getOpposite() ? primary : null;
    }

    @Override
    public boolean soundActive() {
        return isRunning();
    }

    @Override
    public SoundEvent loopSound() {
        return ModSounds.TRANSFORMER_HUM.get();
    }

    @Override
    public float loopVolume() {
        return 0.35F;
    }

    @Override
    public ScadaReading scadaReading() {
        double loading = Math.hypot(lastFe, lastEe) / RATING;
        ScadaReading.Status status = tripped ? ScadaReading.Status.ALARM : loading > 1.0 ? ScadaReading.Status.WARNING
                : lastFe > 0 ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF;
        return new ScadaReading(worldPosition, getDisplayName(), status, lastFe, (int) Math.round(lastEe),
                Component.translatable(tripped ? "scada.powerfactor.transformer.tripped" : "scada.powerfactor.transformer",
                        (int) Math.round(loading * 100)), true, remoteEnabled(), false, 1F, (float) hertz);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        primary.deserialize(input);
        tripped = input.getBooleanOr("tripped", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        primary.serialize(output);
        output.putBoolean("tripped", tripped);
    }
}
