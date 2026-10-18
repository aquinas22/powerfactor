package com.powerfactor.fission;

import com.powerfactor.PowerFactor;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/**
 * Running reactors as seen from outside: the gamma that leaks through their glass, and their Cherenkov glow as a
 * light source for Luminous: a Cherenkov Well standing in water within
 * three blocks of a running reactor gathers blue light in proportion to its power. Registered into
 * Luminous's {@code Collectors.CHERENKOV} hook by reflection when Luminous is present, so neither mod
 * needs the other to build.
 */
public final class FissionCherenkov {
    /** A running reactor: where its light reaches, its power, its shell and how much of that shell is glass. */
    private record Source(AABB reach, double power, AABB box, double glass) {}

    private static final Map<ServerLevel, Map<BlockPos, Source>> ACTIVE = new WeakHashMap<>();

    static void update(ServerLevel level, BlockPos controller, ReactorStructure.@org.jspecify.annotations.Nullable Shape shape, double power) {
        var map = ACTIVE.computeIfAbsent(level, l -> new ConcurrentHashMap<>());
        if (shape == null || power < 0.01) {
            map.remove(controller);
        } else {
            AABB box = AABB.encapsulatingFullBlocks(shape.min(), shape.max());
            int glass = 0;
            for (BlockPos p : shape.shell()) if (level.getBlockState(p).is(com.powerfactor.registry.ModFission.REACTOR_GLASS.get())) glass++;
            map.put(controller, new Source(box.inflate(3), power, box, glass / (double) Math.max(1, shape.shell().size())));
        }
    }

    static void remove(ServerLevel level, BlockPos controller) {
        var map = ACTIVE.get(level);
        if (map != null) map.remove(controller);
    }

    /** Blue units for a well at {@code pos}: up to 24 from a reactor at full power. */
    public static int yield(ServerLevel level, BlockPos pos) {
        var map = ACTIVE.get(level);
        if (map == null) return 0;
        double best = 0;
        for (Source s : map.values()) if (s.reach().contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) best = Math.max(best, s.power());
        return (int) Math.round(Math.min(24, best * 20));
    }

    /**
     * Gamma leaking from running reactors: casing and the pool stop nearly all of it, glass lets some through. Up to
     * about 0.6 against an all-glass reactor at full power, falling away within a few blocks.
     */
    public static double leak(ServerLevel level, BlockPos pos) {
        var map = ACTIVE.get(level);
        if (map == null) return 0;
        double dose = 0;
        var at = net.minecraft.world.phys.Vec3.atCenterOf(pos);
        for (Source s : map.values()) {
            if (s.glass() <= 0) continue;
            double dx = Math.max(0, Math.max(s.box().minX - at.x, at.x - s.box().maxX)), dy = Math.max(0, Math.max(s.box().minY - at.y, at.y - s.box().maxY)),
                    dz = Math.max(0, Math.max(s.box().minZ - at.z, at.z - s.box().maxZ));
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < 14) dose += Math.min(1.5, s.power()) * s.glass() * 0.6 / (1 + (d / 2.5) * (d / 2.5));
        }
        return dose;
    }

    /** Hooks into Luminous if it's installed. */
    @SuppressWarnings("unchecked")
    public static void register() {
        if (!net.neoforged.fml.ModList.get().isLoaded("luminous")) return;
        try {
            var list = (java.util.List<java.util.function.BiFunction<ServerLevel, BlockPos, Integer>>) Class.forName("com.luminous.energis.Collectors")
                    .getField("CHERENKOV").get(null);
            list.add(FissionCherenkov::yield);
            PowerFactor.LOGGER.info("Fission reactors now feed Luminous Cherenkov Wells");
        } catch (ReflectiveOperationException | ClassCastException e) {
            PowerFactor.LOGGER.warn("Couldn't hook fission into Luminous's Cherenkov collectors: {}", e.toString());
        }
    }

    private FissionCherenkov() {}
}
