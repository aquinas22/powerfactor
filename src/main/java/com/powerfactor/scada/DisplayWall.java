package com.powerfactor.scada;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A rectangle of SCADA displays facing the same way. The master is the top-left block as seen from
 * the front; it does the polling and the drawing for the whole wall.
 */
public record DisplayWall(BlockPos master, Direction facing, int width, int height) {
    public static final int MAX = 8;

    /** Screen-right as seen by someone looking at the display. */
    public static Direction right(Direction facing) {
        return facing.getCounterClockWise();
    }

    public static DisplayWall find(BlockGetter level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(ScadaDisplayBlock.FACING);
        Direction right = right(facing);
        int left = 0, top = 0;
        while (left < MAX && same(level, pos.relative(right, -(left + 1)), facing)) left++;
        while (top < MAX && same(level, pos.above(top + 1), facing)) top++;
        BlockPos master = pos.relative(right, -left).above(top);
        int width = 1, height = 1;
        while (width < MAX && same(level, master.relative(right, width), facing)) width++;
        while (height < MAX && same(level, master.below(height), facing)) height++;
        // Only a full rectangle counts; otherwise every block stands alone.
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (!same(level, master.relative(right, x).below(y), facing)) {
                    return new DisplayWall(pos.immutable(), facing, 1, 1);
                }
            }
        }
        if (!contains(master, right, width, height, pos)) {
            return new DisplayWall(pos.immutable(), facing, 1, 1);
        }
        return new DisplayWall(master.immutable(), facing, width, height);
    }

    private static boolean contains(BlockPos master, Direction right, int w, int h, BlockPos pos) {
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (master.relative(right, x).below(y).equals(pos)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean same(BlockGetter level, BlockPos pos, Direction facing) {
        BlockState s = level.getBlockState(pos);
        return s.getBlock() instanceof ScadaDisplayBlock && s.getValue(ScadaDisplayBlock.FACING) == facing;
    }

    public java.util.List<BlockPos> blocks() {
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        Direction r = right(facing);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                out.add(master.relative(r, x).below(y));
            }
        }
        return out;
    }
}
