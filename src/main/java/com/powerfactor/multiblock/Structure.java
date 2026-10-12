package com.powerfactor.multiblock;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A multiblock's shape: which block goes where, relative to the controller. Offsets are written
 * for a controller facing north (its front toward -Z, the structure extending behind it to +Z);
 * they're turned to match the controller's actual facing.
 */
public final class Structure {
    /** One position of the structure: offset from the controller and the block that belongs there. */
    public record Part(BlockPos offset, Supplier<? extends Block> block) {
        public boolean matches(BlockState state) {
            return state.is(block.get());
        }
    }

    private final List<Part> parts;

    public Structure(List<Part> parts) {
        this.parts = List.copyOf(parts);
    }

    public List<Part> parts() {
        return parts;
    }

    public static Rotation rotation(Direction facing) {
        return switch (facing) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    public BlockPos worldPos(BlockPos controller, Direction facing, Part part) {
        return controller.offset(part.offset().rotate(rotation(facing)));
    }

    /** Parts whose block isn't there yet (or is wrong), as world positions. */
    public List<BlockPos> missing(BlockGetter level, BlockPos controller, Direction facing) {
        List<BlockPos> out = new ArrayList<>();
        for (Part p : parts) {
            BlockPos at = worldPos(controller, facing, p);
            if (!p.matches(level.getBlockState(at))) {
                out.add(at);
            }
        }
        return out;
    }

    public boolean complete(BlockGetter level, BlockPos controller, Direction facing) {
        for (Part p : parts) {
            if (!p.matches(level.getBlockState(worldPos(controller, facing, p)))) {
                return false;
            }
        }
        return true;
    }

    /** Builder in rows: {@code layer(y, rows...)} where each row is a string along X (-1..1) and rows go along Z (0, 1, 2...). */
    public static class Builder {
        private final List<Part> parts = new ArrayList<>();
        private final java.util.Map<Character, Supplier<? extends Block>> key = new java.util.HashMap<>();

        public Builder key(char c, Supplier<? extends Block> block) {
            key.put(c, block);
            return this;
        }

        /** {@code '.'} is "anything", {@code 'C'} is the controller itself (skipped). */
        public Builder layer(int y, String... rows) {
            for (int z = 0; z < rows.length; z++) {
                String row = rows[z];
                int half = row.length() / 2;
                for (int i = 0; i < row.length(); i++) {
                    char c = row.charAt(i);
                    if (c == '.' || c == 'C') {
                        continue;
                    }
                    parts.add(new Part(new BlockPos(i - half, y, z), key.get(c)));
                }
            }
            return this;
        }

        public Structure build() {
            return new Structure(parts);
        }
    }
}
