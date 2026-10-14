package com.powerfactor.storage;

import static org.junit.jupiter.api.Assertions.*;

import com.powerfactor.storage.craft.CraftingPlanner;
import com.powerfactor.storage.craft.CraftingPlanner.Pattern;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CraftingPlannerTest {
    private final Map<String, List<Pattern<String>>> book = new HashMap<>();

    private void pattern(String id, Map<String, Long> in, Map<String, Long> out) {
        Pattern<String> p = new Pattern<>(id, in, out);
        for (String o : out.keySet()) book.computeIfAbsent(o, k -> new ArrayList<>()).add(p);
    }

    private CraftingPlanner.Plan<String> plan(String target, long n, Map<String, Long> stock) {
        return CraftingPlanner.plan(target, n, stock, k -> book.getOrDefault(k, List.of()));
    }

    @Test void simpleCraftUsesStock() {
        pattern("planks", Map.of("log", 1L), Map.of("planks", 4L));
        var plan = plan("planks", 8, Map.of("log", 5L));
        assertTrue(plan.complete());
        assertEquals(2L, plan.used().get("log"));
        assertEquals(1, plan.steps().size());
        assertEquals(2L, plan.steps().getFirst().times());
    }

    @Test void subCraftsComeBeforeTheirConsumers() {
        pattern("planks", Map.of("log", 1L), Map.of("planks", 4L));
        pattern("stick", Map.of("planks", 2L), Map.of("stick", 4L));
        pattern("pick", Map.of("planks", 3L, "stick", 2L), Map.of("pick", 1L));
        var plan = plan("pick", 1, Map.of("log", 10L));
        assertTrue(plan.complete(), plan.missing().toString());
        List<String> order = plan.steps().stream().map(s -> s.pattern().id()).toList();
        assertTrue(order.indexOf("planks") < order.indexOf("stick"));
        assertTrue(order.indexOf("stick") < order.indexOf("pick"));
        // 3 planks + 2 for sticks = 5 planks -> 2 logs (8 planks, 3 spare).
        assertEquals(2L, plan.used().get("log"));
    }

    @Test void byproductsSatisfyLaterNeeds() {
        pattern("planks", Map.of("log", 1L), Map.of("planks", 4L));
        pattern("pair", Map.of("planks", 1L, "stick", 1L), Map.of("pair", 1L));
        pattern("stick", Map.of("planks", 2L), Map.of("stick", 4L));
        var plan = plan("pair", 2, Map.of("log", 1L));
        assertTrue(plan.complete(), plan.missing().toString());
        assertEquals(1L, plan.used().get("log"));
    }

    @Test void missingLeavesAreReported() {
        pattern("circuit", Map.of("copper", 2L, "silicon", 1L), Map.of("circuit", 1L));
        var plan = plan("circuit", 3, Map.of("copper", 4L));
        assertFalse(plan.complete());
        assertEquals(2L, plan.missing().get("copper"));
        assertEquals(3L, plan.missing().get("silicon"));
        assertNull(plan.missing().get("circuit"));
    }

    @Test void cyclesTerminate() {
        pattern("block", Map.of("ingot", 9L), Map.of("block", 1L));
        pattern("ingot", Map.of("block", 1L), Map.of("ingot", 9L));
        var plan = plan("block", 1, Map.of());
        assertFalse(plan.complete());
        assertTrue(plan.missing().containsKey("ingot") || plan.missing().containsKey("block"));
    }

    @Test void targetIsAlwaysCraftedNotTakenFromStock() {
        pattern("planks", Map.of("log", 1L), Map.of("planks", 4L));
        var plan = plan("planks", 4, Map.of("planks", 64L, "log", 1L));
        assertTrue(plan.complete());
        assertEquals(1L, plan.used().get("log"));
        assertNull(plan.used().get("planks"));
    }

    @Test void prefersPatternWhoseInputsAreInStock() {
        pattern("dust_from_ore", Map.of("ore", 1L), Map.of("dust", 2L));
        pattern("dust_from_ingot", Map.of("ingot", 1L), Map.of("dust", 1L));
        var plan = plan("dust", 2, Map.of("ingot", 5L));
        assertTrue(plan.complete());
        assertEquals("dust_from_ingot", plan.steps().getFirst().pattern().id());
    }

    @Test void rejectsSillyAmounts() {
        assertThrows(IllegalArgumentException.class, () -> plan("x", 0, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> plan("x", -5, Map.of()));
    }
}
