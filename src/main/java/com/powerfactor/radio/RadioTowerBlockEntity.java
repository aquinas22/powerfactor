package com.powerfactor.radio;

import com.powerfactor.api.ReactiveLoad;
import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.scada.ScadaReading;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/**
 * Radio tower: while powered it relays walkie-talkie calls. Two players each within reach of a
 * powered tower can talk at any distance in that dimension. A blackout takes it off the air.
 */
public class RadioTowerBlockEntity extends com.powerfactor.machine.MachineBlockEntity implements ReactiveLoad {
    public static final int FE_PER_TICK = 40;
    /** Towers on the air, by dimension; read from the voice thread, so concurrent. */
    private static final Map<ResourceKey<Level>, Map<BlockPos, Boolean>> ON_AIR = new ConcurrentHashMap<>();

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(8_000, 400, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private boolean onAir;

    public RadioTowerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RADIO_TOWER.get(), pos, state);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        onAir = enabled(level, pos) && energy.getAmountAsInt() >= FE_PER_TICK;
        if (onAir) {
            energy.set(energy.getAmountAsInt() - FE_PER_TICK);
        }
        var map = ON_AIR.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>());
        if (onAir) {
            map.put(pos.immutable(), true);
        } else {
            map.remove(pos);
        }
        setRunning(level, pos, state, onAir);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) {
            var map = ON_AIR.get(level.dimension());
            if (map != null) {
                map.remove(worldPosition);
            }
        }
    }

    /** Whether a powered tower is within reach of this point. */
    public static boolean nearTower(ResourceKey<Level> dimension, Vec3 at) {
        var map = ON_AIR.get(dimension);
        if (map == null) {
            return false;
        }
        double r2 = RadioLink.TOWER_REACH * RadioLink.TOWER_REACH;
        for (BlockPos p : map.keySet()) {
            if (Vec3.atCenterOf(p).distanceToSqr(at) <= r2) {
                return true;
            }
        }
        return false;
    }

    public EnergyHandler getEnergy() {
        return energy;
    }

    @Override
    public double eeDemand() {
        return onAir ? 12 : 0;
    }

    @Override
    public boolean hasMenu() {
        return false;
    }

    @Override
    public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), onAir ? ScadaReading.Status.RUNNING : ScadaReading.Status.WARNING,
                onAir ? FE_PER_TICK : 0, (int) eeDemand(), Component.translatable(onAir ? "scada.powerfactor.radio_tower.on" : "scada.powerfactor.radio_tower.off"),
                true, remoteEnabled(), false, 1F, 60F);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
    }
}
