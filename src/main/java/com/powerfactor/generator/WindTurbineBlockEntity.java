package com.powerfactor.generator;

import com.powerfactor.api.MachineAudio;
import com.powerfactor.machine.MachineBlock;
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
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Wind turbine nacelle. Needs a clear disc of air for its blades in front; output rises with height
 * above sea level and with weather, and gusts come and go. Inverter-fed: FE only, no EE support.
 */
public class WindTurbineBlockEntity extends PushingGenerator implements MachineAudio.Looping {
    public static final int PEAK = 96, RADIUS = 2;
    private double gust = 0.6, gustTarget = 0.6;
    private float wind;
    private boolean blocked;
    // Synced for the rotor.
    private float syncedSpeed;
    private float angle, prevAngle;

    public WindTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WIND_TURBINE.get(), pos, state, 4_000);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getGameTime() % 100 == 0) {
            gustTarget = 0.3 + level.getRandom().nextDouble() * 0.9;
            blocked = !clear(level, pos, state.getValue(MachineBlock.FACING));
        }
        gust += (gustTarget - gust) * 0.01;
        double height = Mth.clamp((pos.getY() - 62) / 80.0, 0.15, 1.3);
        double weather = level.isThundering() ? 1.6 : level.isRaining() ? 1.25 : 1.0;
        wind = blocked || !enabled(level, pos) ? 0 : (float) Math.min(1.5, gust * height * weather);
        generate((int) Math.round(PEAK * Math.min(1, wind)));
        push(level, pos);
        setRunning(level, pos, state, lastFe > 0);
        if (Math.abs(wind - syncedSpeed) > 0.08) {
            syncedSpeed = wind;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    /** The blade disc in front of the nacelle must be air (or other turbines' blades would clash). */
    private static boolean clear(ServerLevel level, BlockPos pos, Direction facing) {
        BlockPos center = pos.relative(facing);
        Direction side = facing.getClockWise();
        for (int a = -RADIUS; a <= RADIUS; a++) {
            for (int b = -RADIUS; b <= RADIUS; b++) {
                BlockPos p = center.relative(side, a).above(b);
                if (!level.getBlockState(p).isAir() && !(a == 0 && b < 0)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void clientTick() {
        super.clientTick();
        prevAngle = angle;
        angle = (angle + syncedSpeed * 9F) % 360F;
        if (angle < prevAngle) {
            prevAngle -= 360F;
        }
    }

    public float angle(float partial) {
        return prevAngle + (angle - prevAngle) * partial;
    }

    @Override
    public boolean soundActive() {
        return syncedSpeed > 0.05F;
    }

    @Override
    public SoundEvent loopSound() {
        return ModSounds.WIND_TURBINE_RUN.get();
    }

    @Override
    public float loopPitch() {
        return 0.7F + syncedSpeed * 0.3F;
    }

    @Override
    public float loopVolume() {
        return 0.25F + syncedSpeed * 0.3F;
    }

    @Override
    public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), blocked ? ScadaReading.Status.WARNING
                : lastFe > 0 ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF, lastFe, 0,
                Component.translatable(blocked ? "scada.powerfactor.wind.blocked" : "scada.powerfactor.wind", Math.round(wind * 100)),
                true, remoteEnabled(), true, 1F, 0F);
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
