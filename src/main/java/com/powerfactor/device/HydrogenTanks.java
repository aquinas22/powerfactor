package com.powerfactor.device;

import com.powerfactor.PowerFactor;
import com.powerfactor.registry.ModFluids;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Material-specific storage for hydrogen, oil and steam machines: input-only and output-only slots.
 * Inputs accept matching common tags and retain the original fluid and components while consumed.
 */
public class HydrogenTanks extends FluidStacksResourceHandler {
    private static final Map<String, TagKey<Fluid>> COMMON_FLUIDS = java.util.stream.Stream
            .of("hydrogen", "crude_oil", "steam", "hot_water", "diesel", "jet_fuel")
            .collect(java.util.stream.Collectors.toUnmodifiableMap(name -> name,
                    name -> TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath("c", name))));
    private final net.minecraft.world.level.material.Fluid[] fluids;
    private final boolean[] output;
    private final Runnable changed;

    /** One slot per fluid; {@code output[i]} slots can be drained by pipes but not filled, the rest the reverse. */
    public HydrogenTanks(int capacity, net.minecraft.world.level.material.Fluid[] fluids, boolean[] output, Runnable changed) {
        super(fluids.length, capacity);
        this.fluids = fluids;
        this.output = output;
        this.changed = changed;
    }

    public static boolean isHydrogen(FluidResource resource) {
        return matchesFluid(ModFluids.HYDROGEN.get(), resource);
    }

    public static boolean isSteam(FluidResource resource) {
        return matchesFluid(ModFluids.STEAM.get(), resource);
    }

    /** Specific material tags allow external fluids; broad fuel/oil tags are not interchangeable. */
    private static boolean matchesFluid(Fluid expected, FluidResource resource) {
        if (resource.getFluid().isSame(expected)) return true;
        Identifier id = BuiltInRegistries.FLUID.getKey(expected);
        TagKey<Fluid> tag = id.getNamespace().equals(PowerFactor.MODID) ? COMMON_FLUIDS.get(id.getPath()) : null;
        return tag != null && resource.getFluid().defaultFluidState().is(tag);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return matchesFluid(fluids[index], resource);
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return output[index] ? 0 : super.insert(index, resource, amount, transaction);
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return output[index] ? super.extract(index, resource, amount, transaction) : 0;
    }

    public int amount(int index) {
        return getAmountAsInt(index);
    }

    /** Machine-internal change, bypassing the in/out rules. */
    public void add(int index, int delta) {
        int now = Math.max(0, amount(index) + delta);
        // Consuming a foreign fluid must retain its identity and components until fully drained.
        FluidResource stored = getResource(index);
        set(index, now == 0 ? FluidResource.EMPTY : stored.isEmpty() ? FluidResource.of(fluids[index]) : stored, now);
    }

    @Override
    protected void onContentsChanged(int index, FluidStack previousContents) {
        changed.run();
    }
}
