package com.powerfactor.generator;

import com.powerfactor.api.GridGenerator;
import com.powerfactor.api.MachineAudio;
import com.powerfactor.device.HydrogenTanks;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * PEM fuel cell stack: hydrogen in, FE out, water vapour from the vent. Its grid-forming inverter
 * behaves like a synchronous machine to the grid: it sets the frequency and supplies EE from its
 * spare capacity, so wind-plus-hydrogen can run a grid with no spinning generators at all.
 */
public class FuelCellBlockEntity extends PushingGenerator implements GridGenerator, MachineAudio.Looping {
    public static final int RATED_FE = 160, TANK = 16_000, WARMUP = 20;
    public static final double RATED_CE = 200, FE_PER_MB = 11.25;

    private final HydrogenTanks tank = new HydrogenTanks(TANK, new net.minecraft.world.level.material.Fluid[] {ModFluids.HYDROGEN.get()},
            new boolean[] {false}, this::setChanged);
    private double owed;
    private int warm;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case DieselMenu.DATA_FE -> lastFe;
                case DieselMenu.DATA_EE -> (int) Math.round(eeCapacity());
                case DieselMenu.DATA_TANK2 -> tank.amount(0);
                case DieselMenu.DATA_STORED -> buffer.getAmountAsInt() / 10;
                case DieselMenu.DATA_FLAGS -> online() ? 1 : 0;
                case DieselMenu.DATA_REDSTONE -> redstoneMode().ordinal();
                case DieselMenu.DATA_RPM -> warm * 100 / WARMUP;
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

    public FuelCellBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FUEL_CELL.get(), pos, state, 4_000);
    }

    private boolean online() {
        return warm >= WARMUP;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        boolean fuelled = tank.amount(0) > 0 && enabled(level, pos);
        warm = fuelled ? Math.min(WARMUP, warm + 1) : Math.max(0, warm - 2);
        if (online()) {
            int maxByFuel = (int) Math.floor((tank.amount(0) - owed) * FE_PER_MB);
            generate(Math.max(0, Math.min(RATED_FE, maxByFuel)));
            owed += lastFe / FE_PER_MB;
            if (owed >= 1) {
                int whole = (int) owed;
                tank.add(0, -whole);
                owed -= whole;
            }
        } else {
            lastFe = 0;
        }
        push(level, pos);
        setRunning(level, pos, state, online());
    }

    @Override
    public double ratedCe() {
        return online() ? RATED_CE : 0;
    }

    @Override
    public double hertz() {
        return online() ? 60.0 : 0;
    }

    @Override
    public double eeCapacity() {
        return online() ? Math.sqrt(Math.max(0, RATED_CE * RATED_CE - (double) lastFe * lastFe)) : 0;
    }

    public HydrogenTanks getTank() {
        return tank;
    }

    @Override
    public boolean hasMenu() {
        return true;
    }

    @Override
    public boolean soundActive() {
        return isRunning();
    }

    @Override
    public SoundEvent loopSound() {
        return ModSounds.FUEL_CELL_RUN.get();
    }

    @Override
    public float loopVolume() {
        return 0.25F + 0.3F * lastFe / RATED_FE;
    }

    @Override
    public int comparatorSignal() {
        return (int) (15L * tank.amount(0) / TANK);
    }

    @Override
    public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), online() ? ScadaReading.Status.RUNNING
                : tank.amount(0) == 0 ? ScadaReading.Status.WARNING : ScadaReading.Status.OFF, lastFe, 0,
                Component.translatable("scada.powerfactor.fuel_cell", tank.amount(0), Math.round(100.0 * lastFe / RATED_FE)), true,
                remoteEnabled(), true, 1F, (float) hertz());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tank.deserialize(input.childOrEmpty("tank"));
        owed = input.getDoubleOr("owed", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tank.serialize(output.child("tank"));
        output.putDouble("owed", owed);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new DieselMenu.FuelCell(id, inventory, worldPosition, data, this);
    }
}
