package com.powerfactor.machine;

import net.minecraft.core.Direction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Which faces of a machine take items in and which give them out, set per face relative to the
 * way the machine is facing (so rotating the block keeps its plumbing). Thermal Expansion style.
 */
public final class SideConfig {
    public enum Face { FRONT, BACK, LEFT, RIGHT, TOP, BOTTOM }

    public enum Mode {
        NONE(0xFF4A4F54), INPUT(0xFF3A7BFF), OUTPUT(0xFFFF8A2A), BOTH(0xFF3CC94A);

        public final int color;

        Mode(int color) {
            this.color = color;
        }

        public boolean input() {
            return this == INPUT || this == BOTH;
        }

        public boolean output() {
            return this == OUTPUT || this == BOTH;
        }

        public Mode next(int step) {
            return values()[Math.floorMod(ordinal() + step, values().length)];
        }
    }

    private final Mode[] modes = new Mode[Face.values().length];

    public SideConfig() {
        for (Face f : Face.values()) {
            modes[f.ordinal()] = f == Face.BOTTOM ? Mode.OUTPUT : f == Face.FRONT ? Mode.NONE : Mode.BOTH;
        }
    }

    public static Face face(Direction facing, Direction side) {
        if (side == Direction.UP) return Face.TOP;
        if (side == Direction.DOWN) return Face.BOTTOM;
        if (side == facing) return Face.FRONT;
        if (side == facing.getOpposite()) return Face.BACK;
        return side == facing.getClockWise() ? Face.LEFT : Face.RIGHT;
    }

    public Mode mode(Face face) {
        return modes[face.ordinal()];
    }

    public Mode mode(Direction facing, Direction side) {
        return mode(face(facing, side));
    }

    public void cycle(Face face, int step) {
        modes[face.ordinal()] = modes[face.ordinal()].next(step);
    }

    /** Two bits per face, for syncing in one data slot. */
    public int pack() {
        int bits = 0;
        for (Face f : Face.values()) {
            bits |= modes[f.ordinal()].ordinal() << (f.ordinal() * 2);
        }
        return bits;
    }

    public static Mode unpack(int bits, Face face) {
        return Mode.values()[(bits >> (face.ordinal() * 2)) & 3];
    }

    public void load(ValueInput input) {
        input.getInt("sides").ifPresent(bits -> {
            for (Face f : Face.values()) {
                modes[f.ordinal()] = unpack(bits, f);
            }
        });
    }

    public void save(ValueOutput output) {
        output.putInt("sides", pack());
    }
}
