package com.powerfactor.client;

import com.powerfactor.logistics.PipeTraffic;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/** What's travelling through the glass pipes right now, for their renderer. Client only. */
public final class PipeTrafficClient {
    /** Blocks per tick an item glides through a pipe. */
    public static final float SPEED = 0.3F;
    /** How long a fluid pipe keeps showing its fluid after a transfer, in ticks. */
    public static final int FLUID_HOLD = 14;

    public record Traveler(List<BlockPos> path, ItemStack item, long start) {
        /** Position along the path at a time, or null once it has arrived. */
        Vec3 at(float time) {
            float d = (time - start) * SPEED;
            int i = (int) d;
            if (i + 1 >= path.size()) {
                return null;
            }
            return Vec3.atCenterOf(path.get(i)).lerp(Vec3.atCenterOf(path.get(i + 1)), d - i);
        }
    }

    public record Flow(Fluid fluid, long until) {}

    private static final List<Traveler> TRAVELERS = new ArrayList<>();
    private static final Map<BlockPos, Flow> FLOWS = new HashMap<>();

    public static void receive(PipeTraffic t) {
        var level = Minecraft.getInstance().level;
        if (level == null || t.path().size() < 3) {
            return;
        }
        long now = level.getGameTime();
        if (!t.item().isEmpty()) {
            if (TRAVELERS.size() < 512) {
                TRAVELERS.add(new Traveler(t.path(), t.item(), now));
            }
        } else {
            Fluid fluid = BuiltInRegistries.FLUID.getValue(t.fluid());
            if (fluid != null && fluid != Fluids.EMPTY) {
                for (int i = 1; i < t.path().size() - 1; i++) {
                    FLOWS.put(t.path().get(i), new Flow(fluid, now + FLUID_HOLD));
                }
            }
        }
    }

    public static void clientTick() {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            TRAVELERS.clear();
            FLOWS.clear();
            return;
        }
        long now = level.getGameTime();
        TRAVELERS.removeIf(t -> t.at(now) == null);
        FLOWS.values().removeIf(f -> f.until < now);
    }

    /** Items currently inside the pipe at {@code pos}, with their world positions. */
    public static List<Map.Entry<ItemStack, Vec3>> itemsIn(BlockPos pos, float time) {
        List<Map.Entry<ItemStack, Vec3>> out = new ArrayList<>();
        for (Iterator<Traveler> it = TRAVELERS.iterator(); it.hasNext(); ) {
            Traveler t = it.next();
            Vec3 p = t.at(time);
            if (p != null && BlockPos.containing(p).equals(pos)) {
                out.add(Map.entry(t.item(), p));
            }
        }
        return out;
    }

    public static Flow flowIn(BlockPos pos) {
        return FLOWS.get(pos);
    }

    private PipeTrafficClient() {}
}
