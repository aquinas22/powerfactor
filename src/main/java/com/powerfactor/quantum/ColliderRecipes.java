package com.powerfactor.quantum;

import com.powerfactor.registry.ModQuantum;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.Tags;

/**
 * What the Particle Collider makes: smash enough of something at high enough energy and the
 * fragments re-form as something rarer. Energies are total FE per collision.
 */
public final class ColliderRecipes {
    public record Recipe(String id, Predicate<ItemStack> input, int count, Supplier<ItemStack> output, long energy) {}

    public static final List<Recipe> ALL = List.of(
            new Recipe("qubit", s -> s.is(Tags.Items.GEMS_DIAMOND), 1, () -> new ItemStack(ModQuantum.QUBIT_CRYSTAL.get(), 2), 4_000_000L),
            new Recipe("faradaenium", s -> s.is(Tags.Items.ENDER_PEARLS), 2, () -> new ItemStack(ModQuantum.RAW_FARADAENIUM.get()), 8_000_000L),
            new Recipe("adamantium", s -> s.is(Tags.Items.INGOTS_IRON), 8, () -> new ItemStack(ModQuantum.RAW_ADAMANTIUM.get()), 16_000_000L),
            new Recipe("cortanium", s -> s.is(Items.NETHERITE_SCRAP), 1, () -> new ItemStack(ModQuantum.CORTANIUM_SHARD.get()), 30_000_000L),
            new Recipe("exotic_matter", s -> s.is(ModQuantum.QUBIT_CRYSTAL.get()), 4, () -> new ItemStack(ModQuantum.EXOTIC_MATTER.get()), 25_000_000L),
            new Recipe("singularity", s -> s.is(ModQuantum.EXOTIC_MATTER.get()), 4, () -> new ItemStack(ModQuantum.SINGULARITY.get()), 80_000_000L),
            new Recipe("nether_star", s -> s.is(Items.WITHER_SKELETON_SKULL), 3,
                    () -> new ItemStack(Items.NETHER_STAR), 60_000_000L));

    public static Recipe find(ItemStack stack) {
        if (stack.isEmpty()) return null;
        for (Recipe r : ALL) if (r.input().test(stack)) return r;
        return null;
    }

    private ColliderRecipes() {}
}
