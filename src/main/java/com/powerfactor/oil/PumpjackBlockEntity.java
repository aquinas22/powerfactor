package com.powerfactor.oil;

import com.powerfactor.api.MachineAudio;
import com.powerfactor.api.ReactiveLoad;
import com.powerfactor.device.HydrogenTanks;
import com.powerfactor.generator.DieselMenu;
import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.registry.ModFluids;
import com.powerfactor.registry.ModSounds;
import com.powerfactor.scada.ScadaReading;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import org.jspecify.annotations.Nullable;

/**
 * Pumpjack: a nodding donkey over an oil reservoir. It finds the crude oil underground below it
 * (any depth, within {@link #REACH} blocks sideways) and lifts it, block by block, into its tank;
 * pipe it to a refinery. Reservoirs run dry.
 */
public class PumpjackBlockEntity extends com.powerfactor.machine.MachineBlockEntity implements ReactiveLoad, MachineAudio.Looping {
    public static final int FE_PER_TICK = 60, MB_PER_TICK = 25, TANK = 16_000, REACH = 8, SCAN_LIMIT = 4096;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(12_000, 400, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private final HydrogenTanks tank = new HydrogenTanks(TANK, new net.minecraft.world.level.material.Fluid[] {ModFluids.CRUDE_OIL.source().get()},
            new boolean[] {true}, this::setChanged);
    private final List<BlockPos> reservoir = new ArrayList<>();
    private int drawn, lastFe, scanCooldown;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case DieselMenu.DATA_FE -> lastFe;
                case DieselMenu.DATA_EE -> (int) Math.round(eeDemand());
                case DieselMenu.DATA_TANK -> tank.amount(0);
                case DieselMenu.DATA_STORED -> energy.getAmountAsInt() / 10;
                case DieselMenu.DATA_FLAGS -> (lastFe > 0 ? 1 : 0) | (reservoir.isEmpty() ? 2 : 0);
                case DieselMenu.DATA_REDSTONE -> redstoneMode().ordinal();
                case DieselMenu.DATA_TANK2 -> Math.min(32767, reservoir.size());
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return DieselMenu.DATA_COUNT;
        }
    };

    public PumpjackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PUMPJACK.get(), pos, state);
    }

    private static boolean isOil(FluidState f) {
        return f.isSource() && f.getType().isSame(ModFluids.CRUDE_OIL.source().get());
    }

    /** Every crude oil source block connected to the column below us, deepest first. */
    private void scan(ServerLevel level, BlockPos pos) {
        reservoir.clear();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos p = pos.below(); p.getY() > level.getMinY(); p = p.below()) {
            if (isOil(level.getFluidState(p))) {
                queue.add(p.immutable());
                seen.add(p.immutable());
                break;
            }
        }
        while (!queue.isEmpty() && seen.size() < SCAN_LIMIT) {
            BlockPos p = queue.poll();
            reservoir.add(p);
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (Math.abs(n.getX() - pos.getX()) <= REACH && Math.abs(n.getZ() - pos.getZ()) <= REACH && n.getY() < pos.getY()
                        && seen.add(n) && isOil(level.getFluidState(n))) {
                    queue.add(n);
                }
            }
        }
        reservoir.sort(Comparator.comparingInt(BlockPos::getY));
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        lastFe = 0;
        if (reservoir.isEmpty() && --scanCooldown <= 0) {
            scan(level, pos);
            scanCooldown = 100;
        }
        boolean run = !reservoir.isEmpty() && enabled(level, pos) && energy.getAmountAsInt() >= FE_PER_TICK
                && tank.amount(0) + MB_PER_TICK <= TANK;
        if (run) {
            energy.set(energy.getAmountAsInt() - FE_PER_TICK);
            lastFe = FE_PER_TICK;
            tank.add(0, MB_PER_TICK);
            drawn += MB_PER_TICK;
            if (drawn >= 1000) {
                drawn -= 1000;
                // Lift the top of the reservoir first, so it drains like a real one.
                BlockPos top = reservoir.removeLast();
                if (isOil(level.getFluidState(top))) {
                    level.setBlock(top, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        setRunning(level, pos, state, run);
    }

    @Override
    public double eeDemand() {
        return lastFe > 0 ? 45 : 0;
    }

    public EnergyHandler getEnergy() {
        return energy;
    }

    public HydrogenTanks getTank() {
        return tank;
    }

    @Override
    public boolean soundActive() {
        return isRunning();
    }

    @Override
    public SoundEvent loopSound() {
        return ModSounds.PUMPJACK_RUN.get();
    }

    @Override
    public float loopVolume() {
        return 0.5F;
    }

    @Override
    public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), lastFe > 0 ? ScadaReading.Status.RUNNING
                : reservoir.isEmpty() ? ScadaReading.Status.WARNING : ScadaReading.Status.OFF, lastFe, (int) eeDemand(),
                Component.translatable(reservoir.isEmpty() ? "scada.powerfactor.pumpjack.dry" : "scada.powerfactor.pumpjack",
                        reservoir.size(), tank.amount(0)), true, remoteEnabled(), false, 1F, 60F);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        tank.deserialize(input.childOrEmpty("tank"));
        drawn = input.getIntOr("drawn", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        tank.serialize(output.child("tank"));
        output.putInt("drawn", drawn);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new DieselMenu.Pumpjack(id, inventory, worldPosition, data, this);
    }
}
