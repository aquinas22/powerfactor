package com.powerfactor.machine;

import com.powerfactor.api.Bus;
import com.powerfactor.api.ReactiveLoad;
import com.powerfactor.grid.BusLocator;
import com.powerfactor.grid.PowerGrid;
import com.powerfactor.power.BusSolver;
import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.scada.ScadaReading;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * An automatic power-factor controller, like the ones in real switchboards. Every two seconds it
 * looks at the net EE on its wire grid and switches one 20 EE capacitor step in (lagging) or out
 * (leading), so the grid settles close to unity power factor as motors start and stop. The delay
 * keeps it from hunting. Overvoltage drops every step at once.
 */
public class AutoCapacitorBankBlockEntity extends MachineBlockEntity implements ReactiveLoad {
    public static final int STEPS = 6;
    public static final int STEP_EE = 20;
    private static final int INTERVAL = 40;
    private static final double TRIP_VOLTS = 1.12;

    private int steps;
    private int timer;
    private boolean tripped;
    private boolean onGrid;
    private BusSolver.Result grid = BusSolver.Result.NOMINAL;
    private double hertz = 60;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case AutoCapacitorMenu.DATA_STEPS -> steps;
                case AutoCapacitorMenu.DATA_VOLTS -> (int) Math.round(grid.volts() * 1000);
                case AutoCapacitorMenu.DATA_HERTZ -> (int) Math.round(hertz * 100);
                case AutoCapacitorMenu.DATA_FE -> (int) Math.round(grid.fe());
                case AutoCapacitorMenu.DATA_EE -> (int) Math.round(grid.netEe());
                case AutoCapacitorMenu.DATA_FLAGS -> (onGrid ? 1 : 0) | (tripped ? 2 : 0) | (remoteEnabled() ? 4 : 0);
                case AutoCapacitorMenu.DATA_TIMER -> timer;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return AutoCapacitorMenu.DATA_COUNT;
        }
    };

    public AutoCapacitorBankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AUTO_CAPACITOR_BANK.get(), pos, state);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        Bus bus = BusLocator.find(level, pos);
        onGrid = bus instanceof PowerGrid g && g.energized();
        grid = bus.state();
        hertz = bus.hertz();
        int before = steps;

        if (!enabled(level, pos) || !onGrid) {
            steps = 0;
        } else if (grid.volts() > TRIP_VOLTS && steps > 0) {
            steps = 0;
            tripped = true;
            level.playSound(null, pos, com.powerfactor.registry.ModSounds.CAPACITOR_STEP_OUT.get(), SoundSource.BLOCKS, 1.0F, 0.7F);
        } else if (--timer <= 0) {
            timer = INTERVAL;
            if (grid.volts() < 1.05) {
                tripped = false;
            }
            // The net EE already includes our switched-in steps.
            if (!tripped && grid.netEe() > STEP_EE * 0.7 && steps < STEPS) {
                steps++;
            } else if (grid.netEe() < -STEP_EE * 0.7 && steps > 0) {
                steps--;
            }
        }
        if (steps != before) {
            level.playSound(null, pos, steps > before ? com.powerfactor.registry.ModSounds.CAPACITOR_STEP_IN.get()
                    : com.powerfactor.registry.ModSounds.CAPACITOR_STEP_OUT.get(), SoundSource.BLOCKS, 0.8F, 0.9F + steps * 0.04F);
            setChanged();
        }
        setRunning(level, pos, state, steps > 0);
        BlockState current = level.getBlockState(pos);
        if (current.hasProperty(AutoCapacitorBankBlock.STEPS) && current.getValue(AutoCapacitorBankBlock.STEPS) != steps) {
            level.setBlock(pos, current.setValue(AutoCapacitorBankBlock.STEPS, steps), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public double eeDemand() {
        return -steps * STEP_EE;
    }

    @Override
    public ScadaReading scadaReading() {
        ScadaReading.Status status = tripped ? ScadaReading.Status.ALARM
                : steps == STEPS && grid.netEe() > STEP_EE ? ScadaReading.Status.WARNING
                : steps > 0 ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF;
        Component detail = tripped ? Component.translatable("scada.powerfactor.capacitor.tripped")
                : Component.translatable("scada.powerfactor.auto_capacitor", steps, STEPS, String.format("%.2f", grid.powerFactor()),
                        Component.translatable(grid.lagging() ? "gui.powerfactor.lag" : "gui.powerfactor.lead"));
        double v = grid.volts();
        return new ScadaReading(worldPosition, getDisplayName(), status, 0, (int) Math.round(eeDemand() * v * v), detail, true,
                remoteEnabled(), false, (float) v, (float) hertz);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new AutoCapacitorMenu(containerId, inventory, worldPosition, data);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        steps = Math.clamp(input.getIntOr("steps", 0), 0, STEPS);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("steps", steps);
    }
}
