package com.powerfactor.scada;

import com.powerfactor.api.ScadaDevice;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Walks fiber from a terminal and collects every device touching it. */
public final class ScadaNetwork {
    private static final int MAX_CABLES = 4_096;

    public static Map<BlockPos, ScadaDevice> discover(ServerLevel level, BlockPos terminal) {
        Map<BlockPos, ScadaDevice> devices = new LinkedHashMap<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(terminal);
        queue.add(terminal);
        while (!queue.isEmpty() && visited.size() < MAX_CABLES) {
            BlockPos pos = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos next = pos.relative(d);
                if (!level.isLoaded(next) || !visited.add(next)) {
                    continue;
                }
                if (level.getBlockState(next).getBlock() instanceof FiberCableBlock) {
                    queue.add(next);
                } else {
                    BlockEntity be = level.getBlockEntity(next);
                    if (be instanceof ScadaDevice device && !(be instanceof ScadaTerminalBlockEntity)) {
                        devices.put(next, device);
                    }
                }
            }
        }
        return devices;
    }

    public static List<ScadaReading> poll(ServerLevel level, BlockPos terminal) {
        List<ScadaReading> readings = new ArrayList<>();
        for (ScadaDevice device : discover(level, terminal).values()) {
            readings.add(device.scadaReading());
        }
        return readings;
    }

    private ScadaNetwork() {}
}
