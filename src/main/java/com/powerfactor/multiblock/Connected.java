package com.powerfactor.multiblock;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Connected textures for multiblock shells. Once a structure forms, each of its blocks records which
 * neighbours belong to the same structure (six booleans), and its formed model draws trim only on the
 * edges that face outside, so the casing reads as one machine instead of a grid of cubes.
 *
 * <p>Hosts (casing) join every formed part of their structure. Panels (glass, coils, magnets) join only
 * their own material, so a window or coil bank keeps a frame where it meets the casing, drawn on the
 * panel's side. Modules (ports, injectors) keep their own framed face, and everything joins them, as it
 * does the controller and any other blocks the structure embeds (rod drives, cores).
 */
public final class Connected {
    public static final Map<Direction, BooleanProperty> SIDES = PipeBlock.PROPERTY_BY_DIRECTION;

    public enum Kind { HOST, PANEL, GLASS, MODULE }

    /** {@code joins}: the controller and other blocks the structure embeds, which everything connects to. */
    public record Style(String group, String material, Kind kind, List<Supplier<? extends Block>> joins) {
        @SafeVarargs
        public static Style host(String group, Supplier<? extends Block>... joins) {
            return new Style(group, "casing", Kind.HOST, List.of(joins));
        }

        @SafeVarargs
        public static Style panel(String group, String material, Supplier<? extends Block>... joins) {
            return new Style(group, material, Kind.PANEL, List.of(joins));
        }

        @SafeVarargs
        public static Style glass(String group, Supplier<? extends Block>... joins) {
            return new Style(group, "glass", Kind.GLASS, List.of(joins));
        }

        public static Style module(String group) {
            return new Style(group, "module", Kind.MODULE, List.of());
        }
    }

    /** A block that takes part in connected textures. */
    public interface Part {
        Style style();
    }

    public static void addProperties(StateDefinition.Builder<Block, BlockState> builder) {
        SIDES.values().forEach(builder::add);
    }

    public static BlockState unconnected(BlockState state) {
        for (BooleanProperty p : SIDES.values()) if (state.hasProperty(p)) state = state.setValue(p, false);
        return state;
    }

    private static boolean formed(BlockState s) {
        return !s.hasProperty(CasingBlock.FORMED) || s.getValue(CasingBlock.FORMED);
    }

    public static boolean connects(Style self, BlockState state, BlockState other) {
        if (!formed(state)) return false;
        for (var join : self.joins()) if (other.is(join.get())) return true;
        if (!(other.getBlock() instanceof Part p) || !p.style().group().equals(self.group()) || !formed(other)) return false;
        return self.kind() == Kind.HOST || p.style().kind() == Kind.MODULE || p.style().material().equals(self.material());
    }

    /** Recomputes all six connections. */
    public static BlockState refresh(BlockState state, BlockGetter level, BlockPos pos) {
        if (!(state.getBlock() instanceof Part part) || part.style().kind() == Kind.MODULE) return state;
        for (var e : SIDES.entrySet()) {
            state = state.setValue(e.getValue(), connects(part.style(), state, level.getBlockState(pos.relative(e.getKey()))));
        }
        return state;
    }

    /** One neighbour changed: recompute that side (call from updateShape). */
    public static BlockState update(BlockState state, Direction side, BlockState neighbour) {
        if (!(state.getBlock() instanceof Part part) || part.style().kind() == Kind.MODULE) return state;
        return state.setValue(SIDES.get(side), connects(part.style(), state, neighbour));
    }

    /** Glass hides the faces between two panes of itself. */
    public static boolean hidesFace(BlockState state, BlockState adjacent) {
        return state.getBlock() instanceof Part p && p.style().kind() == Kind.GLASS && adjacent.is(state.getBlock());
    }

    /**
     * Joins or leaves a formed structure: sets FORMED and the block's connections. Neighbours pick the
     * change up through their own shape updates.
     */
    public static void setFormed(Level level, BlockPos pos, boolean formed) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof Part) || !state.hasProperty(CasingBlock.FORMED) || state.getValue(CasingBlock.FORMED) == formed) return;
        level.setBlock(pos, refresh(state.setValue(CasingBlock.FORMED, formed), level, pos), Block.UPDATE_ALL);
    }

    private Connected() {}
}
