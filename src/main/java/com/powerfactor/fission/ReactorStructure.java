package com.powerfactor.fission;

import com.powerfactor.registry.ModFission;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/**
 * Finds and checks a fission reactor of any size from 5x5x5 to 18x18x18 (outside). The controller sits in
 * one wall. The edges and corners are Reactor Casing, a dark frame; the walls between them Casing or Reactor Glass, with
 * Ports and Logic Adapters; the roof carries a Control Rod Drive above every fuel column. Inside: Fuel Assembly columns
 * (each running from the floor up to its drive), and around them water (the pool, which moderates and
 * cools and glows blue) or Graphite Moderator blocks.
 */
public final class ReactorStructure {
    public static final int MIN = 5, MAX = 18;

    /** A formed reactor: its bounds and what's inside. */
    public record Shape(BlockPos min, BlockPos max, List<Column> columns, List<BlockPos> ports, List<BlockPos> adapters, List<BlockPos> shell,
                        int water, int graphite) {
        public int interior() {
            return (max.getX() - min.getX() - 1) * (max.getY() - min.getY() - 1) * (max.getZ() - min.getZ() - 1);
        }
    }

    /** A fuel column: its x/z, the drive above it, and its assembly blocks bottom to top. */
    public record Column(int x, int z, BlockPos drive, List<BlockPos> assemblies, int moderators) {}

    public record Result(@Nullable Shape shape, @Nullable Component problem, @Nullable BlockPos at) {}

    public record Component(String key, Object... args) {}

    static boolean isShell(BlockState s) {
        return s.is(ModFission.REACTOR_CASING.get()) || s.is(ModFission.REACTOR_GLASS.get()) || s.is(ModFission.REACTOR_PORT.get())
                || s.is(ModFission.LOGIC_ADAPTER.get()) || s.is(ModFission.CONTROL_ROD_DRIVE.get()) || s.is(ModFission.REACTOR_CONTROLLER.get());
    }

    static boolean isWater(BlockState s) {
        return s.getFluidState().is(Fluids.WATER) && s.getFluidState().isSource() || s.is(Blocks.WATER);
    }

    /** Scans outward from the controller, facing into the reactor, to find the box and check it. */
    public static Result scan(Level level, BlockPos controller, Direction inward) {
        // Walk inward to the far wall.
        int depth = 1;
        while (depth < MAX && !isShell(level.getBlockState(controller.relative(inward, depth)))) depth++;
        if (depth >= MAX || depth < MIN - 1) return new Result(null, new Component("gui.powerfactor.fission.problem.size"), controller);
        // The controller's wall: extend sideways and up/down along the wall plane.
        Direction side = inward.getClockWise();
        int left = 0, right = 0, down = 0, up = 0;
        while (left < MAX && isShell(level.getBlockState(controller.relative(side.getOpposite(), left + 1)))) left++;
        while (right < MAX && isShell(level.getBlockState(controller.relative(side, right + 1)))) right++;
        while (down < MAX && isShell(level.getBlockState(controller.below(down + 1)))) down++;
        while (up < MAX && isShell(level.getBlockState(controller.above(up + 1)))) up++;
        BlockPos a = controller.relative(side.getOpposite(), left).below(down);
        BlockPos b = controller.relative(side, right).above(up).relative(inward, depth);
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        int sx = max.getX() - min.getX() + 1, sy = max.getY() - min.getY() + 1, sz = max.getZ() - min.getZ() + 1;
        if (sx < MIN || sy < MIN || sz < MIN || sx > MAX || sy > MAX || sz > MAX) {
            return new Result(null, new Component("gui.powerfactor.fission.problem.size"), controller);
        }
        List<BlockPos> ports = new ArrayList<>(), adapters = new ArrayList<>(), shell = new ArrayList<>();
        List<Column> columns = new ArrayList<>();
        int water = 0, graphite = 0;
        int controllers = 0;
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState s = level.getBlockState(p);
                    boolean bx = x == min.getX() || x == max.getX(), by = y == min.getY() || y == max.getY(), bz = z == min.getZ() || z == max.getZ();
                    int faces = (bx ? 1 : 0) + (by ? 1 : 0) + (bz ? 1 : 0);
                    if (faces >= 2) {
                        // Edges and corners: casing only, so even a glass reactor has a frame.
                        if (!s.is(ModFission.REACTOR_CASING.get())) {
                            return new Result(null, new Component("gui.powerfactor.fission.problem.edge"), p);
                        }
                        shell.add(p);
                    } else if (faces == 1) {
                        if (!isShell(s)) return new Result(null, new Component("gui.powerfactor.fission.problem.wall"), p);
                        if (s.is(ModFission.CONTROL_ROD_DRIVE.get()) && y != max.getY()) return new Result(null, new Component("gui.powerfactor.fission.problem.drive"), p);
                        if (s.is(ModFission.REACTOR_CONTROLLER.get()) && ++controllers > 1) return new Result(null, new Component("gui.powerfactor.fission.problem.controllers"), p);
                        if (s.is(ModFission.REACTOR_PORT.get())) ports.add(p);
                        if (s.is(ModFission.LOGIC_ADAPTER.get())) adapters.add(p);
                        shell.add(p);
                    } else {
                        if (s.is(ModFission.FUEL_ASSEMBLY.get())) continue;   // checked per column below
                        if (s.is(ModFission.GRAPHITE.get())) graphite++;
                        else if (isWater(s)) water++;
                        else if (!s.isAir()) return new Result(null, new Component("gui.powerfactor.fission.problem.inside"), p);
                    }
                }
            }
        }
        // Columns: every drive in the roof has a solid run of assemblies beneath it to the floor.
        for (int x = min.getX() + 1; x < max.getX(); x++) {
            for (int z = min.getZ() + 1; z < max.getZ(); z++) {
                BlockPos top = new BlockPos(x, max.getY(), z);
                boolean drive = level.getBlockState(top).is(ModFission.CONTROL_ROD_DRIVE.get());
                List<BlockPos> run = new ArrayList<>();
                for (int y = min.getY() + 1; y < max.getY(); y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (level.getBlockState(p).is(ModFission.FUEL_ASSEMBLY.get())) run.add(p);
                }
                if (run.isEmpty() && !drive) continue;
                if (!drive) return new Result(null, new Component("gui.powerfactor.fission.problem.no_drive"), run.getFirst());
                if (run.size() != max.getY() - min.getY() - 1) return new Result(null, new Component("gui.powerfactor.fission.problem.column"), top.below());
                int mods = 0;
                for (BlockPos p : run) {
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        BlockState n = level.getBlockState(p.relative(d));
                        if (n.is(ModFission.GRAPHITE.get()) || isWater(n)) mods++;
                    }
                }
                columns.add(new Column(x, z, top, run, mods));
            }
        }
        if (columns.isEmpty()) return new Result(null, new Component("gui.powerfactor.fission.problem.no_fuel"), controller);
        return new Result(new Shape(min, max, columns, ports, adapters, shell, water, graphite), null, null);
    }

    private ReactorStructure() {}
}
