package com.powerfactor.device;

import com.powerfactor.api.MachineAudio;
import com.powerfactor.api.ReactiveLoad;
import com.powerfactor.generator.DieselMenu;
import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.registry.ModFluids;
import com.powerfactor.registry.ModSounds;
import com.powerfactor.scada.ScadaReading;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import org.jspecify.annotations.Nullable;

/**
 * Electrolyser: splits water into hydrogen with grid power. Pipe water in, pipe hydrogen out to a
 * tank or a fuel cell. A way to store the wind for a calm day. DC rectifier load: PF about 0.9.
 */
public class ElectrolyzerBlockEntity extends com.powerfactor.machine.MachineBlockEntity implements ReactiveLoad, MachineAudio.Looping {
    public static final int FE_PER_TICK = 100, WATER_PER_TICK = 2, HYDROGEN_PER_TICK = 4, TANK = 16_000;
    public static final int WATER = 0, HYDROGEN = 1;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(20_000, 400, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private final HydrogenTanks tanks = new HydrogenTanks(TANK, new net.minecraft.world.level.material.Fluid[] {Fluids.WATER,
            ModFluids.HYDROGEN.get()}, new boolean[] {false, true}, this::setChanged);
    private int lastFe;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case DieselMenu.DATA_FE -> lastFe;
                case DieselMenu.DATA_EE -> (int) Math.round(eeDemand());
                case DieselMenu.DATA_TANK -> tanks.amount(WATER);
                case DieselMenu.DATA_TANK2 -> tanks.amount(HYDROGEN);
                case DieselMenu.DATA_STORED -> energy.getAmountAsInt() / 10;
                case DieselMenu.DATA_FLAGS -> lastFe > 0 ? 1 : 0;
                case DieselMenu.DATA_REDSTONE -> redstoneMode().ordinal();
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

    public ElectrolyzerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ELECTROLYZER.get(), pos, state);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        lastFe = 0;
        if (enabled(level, pos) && energy.getAmountAsInt() >= FE_PER_TICK && tanks.amount(WATER) >= WATER_PER_TICK
                && tanks.amount(HYDROGEN) + HYDROGEN_PER_TICK <= TANK) {
            energy.set(energy.getAmountAsInt() - FE_PER_TICK);
            tanks.add(WATER, -WATER_PER_TICK);
            tanks.add(HYDROGEN, HYDROGEN_PER_TICK);
            lastFe = FE_PER_TICK;
        }
        setRunning(level, pos, state, lastFe > 0);
    }

    @Override
    public double eeDemand() {
        return lastFe > 0 ? 0.48 * lastFe : 0;
    }

    public EnergyHandler getEnergy() {
        return energy;
    }

    public HydrogenTanks getTanks() {
        return tanks;
    }

    @Override
    public boolean soundActive() {
        return isRunning();
    }

    @Override
    public SoundEvent loopSound() {
        return ModSounds.ELECTROLYZER_RUN.get();
    }

    @Override
    public float loopVolume() {
        return 0.4F;
    }

    @Override
    public int comparatorSignal() {
        return (int) (15L * tanks.amount(HYDROGEN) / TANK);
    }

    @Override
    public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), lastFe > 0 ? ScadaReading.Status.RUNNING
                : tanks.amount(WATER) < WATER_PER_TICK ? ScadaReading.Status.WARNING : ScadaReading.Status.OFF, lastFe, (int) Math.round(eeDemand()),
                Component.translatable("scada.powerfactor.electrolyzer", tanks.amount(WATER), tanks.amount(HYDROGEN)), true, remoteEnabled(), false,
                1F, 60F);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        tanks.deserialize(input.childOrEmpty("tanks"));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        tanks.serialize(output.child("tanks"));
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new DieselMenu.Electrolyzer(id, inventory, worldPosition, data, this);
    }
}
