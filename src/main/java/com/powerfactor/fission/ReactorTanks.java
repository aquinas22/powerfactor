package com.powerfactor.fission;

import com.powerfactor.device.HydrogenTanks;
import com.powerfactor.registry.ModFluids;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

/**
 * The reactor's two tanks: coolant in (water, heavy water, liquid sodium or molten salt, one at a time) and
 * what comes out (steam from water, or the hot side of a loop coolant). What's in the first tank sets how
 * the core is cooled.
 */
public final class ReactorTanks extends HydrogenTanks {
    public static final int COOLANT = 0, OUTPUT = 1;

    public ReactorTanks(int capacity, Runnable changed) {
        super(capacity, new Fluid[] {Fluids.WATER, ModFluids.STEAM.get()}, new boolean[] {false, true}, changed);
    }

    /** The coolant type a fluid is, or null. */
    public static ReactorCore.@Nullable Coolant coolant(Fluid f) {
        if (f.isSame(Fluids.WATER)) return ReactorCore.Coolant.WATER;
        if (f.isSame(ModFluids.HEAVY_WATER.source().get())) return ReactorCore.Coolant.HEAVY_WATER;
        if (f.isSame(ModFluids.LIQUID_SODIUM.source().get())) return ReactorCore.Coolant.LIQUID_SODIUM;
        if (f.isSame(ModFluids.MOLTEN_SALT.source().get())) return ReactorCore.Coolant.MOLTEN_SALT;
        return null;
    }

    /** The cold side of a coolant, as a fluid. */
    public static Fluid cold(ReactorCore.Coolant c) {
        return switch (c) {
            case WATER -> Fluids.WATER;
            case HEAVY_WATER -> ModFluids.HEAVY_WATER.source().get();
            case LIQUID_SODIUM -> ModFluids.LIQUID_SODIUM.source().get();
            case MOLTEN_SALT -> ModFluids.MOLTEN_SALT.source().get();
        };
    }

    /** What a coolant comes out of the core as. */
    public static Fluid hot(ReactorCore.Coolant c) {
        return switch (c) {
            case WATER -> ModFluids.STEAM.get();
            case HEAVY_WATER -> ModFluids.HOT_HEAVY_WATER.source().get();
            case LIQUID_SODIUM -> ModFluids.HOT_SODIUM.source().get();
            case MOLTEN_SALT -> ModFluids.HOT_MOLTEN_SALT.source().get();
        };
    }

    /** A loop coolant's hot side back to its type (for the Heat Exchanger), or null. */
    public static ReactorCore.@Nullable Coolant fromHot(Fluid f) {
        for (var c : ReactorCore.Coolant.values()) if (c.loop && hot(c).isSame(f)) return c;
        return null;
    }

    /** The coolant in the tank; when it has run dry, the loop coolant still in the hot side; otherwise water. */
    public ReactorCore.Coolant coolant() {
        FluidResource r = getResource(COOLANT);
        var c = r.isEmpty() ? null : coolant(r.getFluid());
        if (c == null) {
            FluidResource out = getResource(OUTPUT);
            c = out.isEmpty() ? null : fromHot(out.getFluid());
        }
        return c == null ? ReactorCore.Coolant.WATER : c;
    }

    /** Room for this coolant's output: none while the output tank holds something else. */
    public int outputRoom(ReactorCore.Coolant c) {
        FluidResource r = getResource(OUTPUT);
        if (!r.isEmpty() && !r.getFluid().isSame(hot(c))) return 0;
        return getCapacityAsInt(OUTPUT, FluidResource.of(hot(c))) - amount(OUTPUT);
    }

    public void addOutput(ReactorCore.Coolant c, int amount) {
        if (amount <= 0) return;
        int now = amount(OUTPUT) + amount;
        set(OUTPUT, FluidResource.of(hot(c)), now);
    }

    /** Empties both tanks (to change coolant). */
    public void purge() {
        set(COOLANT, FluidResource.EMPTY, 0);
        set(OUTPUT, FluidResource.EMPTY, 0);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return index == COOLANT ? coolant(resource.getFluid()) != null : fromHot(resource.getFluid()) != null || resource.getFluid().isSame(ModFluids.STEAM.get());
    }
}
