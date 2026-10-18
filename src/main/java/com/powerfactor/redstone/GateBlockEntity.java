package com.powerfactor.redstone;

import com.powerfactor.registry.ModRedstone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A gate's settings and memory: which inputs it uses, its delay or interval, a counter, a latch, the last back input. */
public class GateBlockEntity extends BlockEntity {
    /** Inputs used: 1 left, 2 back, 4 right. */
    int mask = 7;
    int delay = 2;
    int count, max = 4;
    boolean latch, lastBack, lastLeft, lastRight;
    /** Pending output for a repeater's delay line and a pulse former's off-time. */
    boolean pulseOff;

    public GateBlockEntity(BlockPos pos, BlockState state) {
        super(ModRedstone.GATE_BE.get(), pos, state);
        if (state.getBlock() instanceof GateBlock g) {
            if (g.type() == GateBlock.Type.TIMER) delay = 20;
            // AND and NAND start as two-input gates (left and right); the screwdriver adds the back.
            if (g.type() == GateBlock.Type.AND || g.type() == GateBlock.Type.NAND) mask = 5;
        }
    }

    void evaluate(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        GateBlock block = (GateBlock) state.getBlock();
        Direction front = state.getValue(GateBlock.FACING);
        boolean l = GateBlock.input(level, pos, GateBlock.left(front)), b = GateBlock.input(level, pos, front.getOpposite()),
                r = GateBlock.input(level, pos, GateBlock.right(front));
        boolean le = (mask & 1) != 0, be = (mask & 2) != 0, re = (mask & 4) != 0;
        boolean out = state.getValue(GateBlock.OUT);
        switch (block.type()) {
            case AND -> out = (!le || l) && (!be || b) && (!re || r);
            case NAND -> out = !((!le || l) && (!be || b) && (!re || r));
            case OR -> out = le && l || be && b || re && r;
            case NOR -> out = !(le && l || be && b || re && r);
            case NOT -> out = !b;
            case BUFFER -> out = b;
            case XOR -> out = l ^ r;
            case XNOR -> out = !(l ^ r);
            case MULTIPLEXER -> out = b ? l : r;
            case REPEATER -> out = b;
            case PULSE -> {
                if (pulseOff) {
                    out = false;
                    pulseOff = false;
                } else if (b && !lastBack) {
                    out = true;
                    pulseOff = true;
                    level.scheduleTick(pos, block, 2);
                }
            }
            case RANDOMIZER -> {
                out = b && random.nextBoolean();
                if (b) level.scheduleTick(pos, block, 2);
            }
            case RS_LATCH -> {
                if (r) latch = false;
                else if (l) latch = true;
                out = latch;
            }
            case TOGGLE_LATCH -> {
                if (b && !lastBack || l && !lastLeft || r && !lastRight) latch = !latch;
                out = latch;
            }
            case TIMER -> {
                // Runs while the back input is off: a short pulse every interval.
                if (b) {
                    out = false;
                    pulseOff = false;
                } else if (pulseOff) {
                    out = false;
                    pulseOff = false;
                    level.scheduleTick(pos, block, Math.max(1, delay - 2));
                } else {
                    out = true;
                    pulseOff = true;
                    level.scheduleTick(pos, block, 2);
                }
            }
            case COUNTER -> {
                if (b && !lastBack) count = 0;
                if (l && !lastLeft) count = Math.min(max, count + 1);
                if (r && !lastRight) count = Math.max(0, count - 1);
                out = count >= max;
            }
        }
        lastBack = b;
        lastLeft = l;
        lastRight = r;
        setChanged();
        // Unused inputs show dark.
        block.show(level, pos, state, l && le, b && (be || !block.type().maskable()), r && re, out);
    }

    public int count() {
        return count;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        mask = input.getIntOr("mask", mask);
        delay = input.getIntOr("delay", delay);
        count = input.getIntOr("count", 0);
        max = input.getIntOr("max", 4);
        latch = input.getBooleanOr("latch", false);
        lastBack = input.getBooleanOr("last_back", false);
        lastLeft = input.getBooleanOr("last_left", false);
        lastRight = input.getBooleanOr("last_right", false);
        pulseOff = input.getBooleanOr("pulse_off", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("mask", mask);
        output.putInt("delay", delay);
        output.putInt("count", count);
        output.putInt("max", max);
        output.putBoolean("latch", latch);
        output.putBoolean("last_back", lastBack);
        output.putBoolean("last_left", lastLeft);
        output.putBoolean("last_right", lastRight);
        output.putBoolean("pulse_off", pulseOff);
    }
}
