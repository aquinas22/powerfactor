package com.powerfactor.device;

import com.powerfactor.machine.MachineBlock;
import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.registry.ModQuantum;
import com.powerfactor.scada.ScadaReading;
import java.util.ArrayDeque;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Logic Controller: a small programmable redstone controller. Inputs on the left, back and right
 * (relative to its face), output on the front. One of eight programs runs every tick; N is the
 * program's count (timer period, delay length, counter target) in ticks or pulses.
 */
public class LogicControllerBlockEntity extends MachineBlockEntity {
    public enum Program { AND, OR, XOR, NAND, LATCH, TIMER, DELAY, COUNTER }

    public static final int DATA_PROGRAM = 0, DATA_N = 1, DATA_INPUTS = 2, DATA_OUTPUT = 3, DATA_STATE = 4, DATA_COUNT = 5;
    public static final int BUTTON_PROGRAM = 0, BUTTON_N = 10;   // 0..7 programs; 10..13: N -10, -1, +1, +10

    private Program program = Program.AND;
    private int n = 20, counter, phase, pulseTicks;
    private boolean output, latch, lastBack;
    private int inputs;
    private final ArrayDeque<Boolean> delayLine = new ArrayDeque<>();

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case DATA_PROGRAM -> program.ordinal();
                case DATA_N -> n;
                case DATA_INPUTS -> inputs;
                case DATA_OUTPUT -> output ? 1 : 0;
                case DATA_STATE -> program == Program.COUNTER ? counter : program == Program.TIMER ? phase : latch ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public LogicControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModQuantum.LOGIC_CONTROLLER_BE.get(), pos, state);
    }

    public Direction front() {
        return getBlockState().getValue(MachineBlock.FACING);
    }

    private boolean in(ServerLevel level, Direction side) {
        return level.getSignal(worldPosition.relative(side), side) > 0;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        Direction f = front();
        boolean left = in(level, f.getClockWise()), back = in(level, f.getOpposite()), right = in(level, f.getCounterClockWise());
        inputs = (left ? 1 : 0) | (back ? 2 : 0) | (right ? 4 : 0);
        boolean next = switch (program) {
            case AND -> left && right;
            case OR -> left || back || right;
            case XOR -> left ^ right;
            case NAND -> !(left && right);
            case LATCH -> {
                if (left) latch = true;
                if (right) latch = false;
                yield latch;
            }
            case TIMER -> {
                // While the back input is on, pulse for 2 ticks every N ticks.
                if (!back) {
                    phase = 0;
                    yield false;
                }
                phase = (phase + 1) % Math.max(2, n);
                yield phase < 2;
            }
            case DELAY -> {
                delayLine.addLast(back);
                while (delayLine.size() > n) delayLine.removeFirst();
                yield delayLine.size() == n && delayLine.peekFirst();
            }
            case COUNTER -> {
                // Count rising edges on the back input; output a pulse on the Nth and start again.
                if (back && !lastBack && ++counter >= n) {
                    counter = 0;
                    pulseTicks = 2;
                }
                yield pulseTicks-- > 0;
            }
        };
        lastBack = back;
        if (next != output) {
            output = next;
            setChanged();
            setRunning(level, pos, state, output);
            level.updateNeighborsAt(pos, state.getBlock());
            level.updateNeighborsAt(pos.relative(f), state.getBlock());
        }
    }

    public int signalToward(Direction direction) {
        return output && direction == front().getOpposite() ? 15 : 0;
    }

    public void button(int id) {
        if (id >= BUTTON_PROGRAM && id < BUTTON_PROGRAM + Program.values().length) {
            program = Program.values()[id - BUTTON_PROGRAM];
            counter = phase = 0;
            latch = false;
            delayLine.clear();
        } else if (id >= BUTTON_N && id < BUTTON_N + 4) {
            int[] step = {-10, -1, 1, 10};
            n = Math.clamp(n + step[id - BUTTON_N], 1, 200);
            delayLine.clear();
        }
        setChanged();
    }

    public Program program() {
        return program;
    }

    public boolean output() {
        return output;
    }

    @Override
    public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), output ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF, 0, 0,
                Component.translatable("gui.powerfactor.logic.program." + program.name().toLowerCase(java.util.Locale.ROOT)), true, remoteEnabled(),
                false, 0, 0);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new LogicControllerMenu(containerId, inventory, this, data);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        program = Program.values()[Math.clamp(input.getIntOr("program", 0), 0, Program.values().length - 1)];
        n = Math.clamp(input.getIntOr("n", 20), 1, 200);
        latch = input.getBooleanOr("latch", false);
        counter = input.getIntOr("counter", 0);
        output = input.getBooleanOr("output", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("program", program.ordinal());
        output.putInt("n", n);
        output.putBoolean("latch", latch);
        output.putInt("counter", counter);
        output.putBoolean("output", this.output);
    }
}
