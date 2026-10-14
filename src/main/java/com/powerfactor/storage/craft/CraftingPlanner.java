package com.powerfactor.storage.craft;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Works out how to make a requested item from what the network holds and the patterns it knows,
 * like an AE2 crafting calculation. Plain Java over any key type so it is unit-tested directly.
 *
 * <p>The planner simulates the network inventory: stock is spent first, then the first pattern that
 * makes the item is run as many times as needed, recursively planning its inputs. Byproducts of a
 * craft (extra outputs, overshoot from batch sizes) go back into the simulated stock and can satisfy
 * later needs. A pattern already on the recursion stack is skipped, so cycles (ingot to block to
 * ingot) cannot loop; if nothing else makes the item it is reported missing.
 */
public final class CraftingPlanner<K> {
    /** One way to make things: consume {@code inputs}, produce {@code outputs}, per execution. */
    public record Pattern<K>(String id, Map<K, Long> inputs, Map<K, Long> outputs) {
        public Pattern {
            inputs = Map.copyOf(inputs);
            outputs = Map.copyOf(outputs);
        }

        public long produces(K key) {
            return outputs.getOrDefault(key, 0L);
        }
    }

    /** One pattern scheduled {@code times} times. Steps are in a valid execution order. */
    public record Step<K>(Pattern<K> pattern, long times) {}

    /**
     * The result. {@code used} is taken from network storage at the start, {@code crafted} is the
     * total produced by steps, {@code missing} is what neither storage nor any pattern could supply.
     */
    public record Plan<K>(K target, long amount, List<Step<K>> steps, Map<K, Long> used, Map<K, Long> crafted, Map<K, Long> missing) {
        public boolean complete() {
            return missing.isEmpty();
        }

        public long totalCrafts() {
            long n = 0;
            for (Step<K> s : steps) n += s.times();
            return n;
        }
    }

    public static final int MAX_DEPTH = 32;
    /** Guards against absurd requests overflowing the arithmetic. */
    public static final long MAX_AMOUNT = 1L << 40;

    private final Map<K, Long> stock;
    private final Function<K, List<Pattern<K>>> patternsFor;
    private final Map<K, Long> used = new LinkedHashMap<>();
    private final Map<K, Long> crafted = new LinkedHashMap<>();
    private final Map<K, Long> missing = new LinkedHashMap<>();
    /** Steps in completion order (inputs before the things made from them), merged by pattern. */
    private final LinkedHashMap<String, Step<K>> steps = new LinkedHashMap<>();
    private final Set<String> active = new HashSet<>();

    private CraftingPlanner(Map<K, Long> available, Function<K, List<Pattern<K>>> patternsFor) {
        this.stock = new HashMap<>(available);
        this.patternsFor = patternsFor;
    }

    /**
     * Plan {@code amount} of {@code target}. The target itself is always crafted, never taken from
     * stock (asking for a craft means making new ones), but its ingredients use stock first.
     */
    public static <K> Plan<K> plan(K target, long amount, Map<K, Long> available, Function<K, List<Pattern<K>>> patternsFor) {
        if (amount <= 0 || amount > MAX_AMOUNT) {
            throw new IllegalArgumentException("amount " + amount);
        }
        CraftingPlanner<K> p = new CraftingPlanner<>(available, patternsFor);
        long made = p.craft(target, amount, 0);
        if (made < amount) {
            p.missing.merge(target, amount - made, Long::sum);
        }
        // The requested product is delivered, not left over: take it back out of the simulation.
        return new Plan<>(target, amount, List.copyOf(p.steps.values()), Map.copyOf(p.used), Map.copyOf(p.crafted), Map.copyOf(p.missing));
    }

    /** Ensure {@code amount} of {@code key} is available to consume: stock first, then crafting. */
    private void need(K key, long amount, int depth) {
        long have = stock.getOrDefault(key, 0L);
        long take = Math.min(have, amount);
        if (take > 0) {
            stock.put(key, have - take);
            // Stock created by an earlier step in this plan is not "used from storage".
            amount -= take;
            long fromStorage = take - Math.min(take, producedSurplus.getOrDefault(key, 0L));
            if (take - fromStorage > 0) producedSurplus.merge(key, -(take - fromStorage), Long::sum);
            if (fromStorage > 0) used.merge(key, fromStorage, Long::sum);
        }
        if (amount <= 0) return;
        long made = craft(key, amount, depth);
        // craft() leaves its output in stock; consume what we asked for.
        long nowHave = stock.getOrDefault(key, 0L);
        long consume = Math.min(nowHave, amount);
        stock.put(key, nowHave - consume);
        producedSurplus.merge(key, -consume, Long::sum);
        if (made < amount) {
            missing.merge(key, amount - made, Long::sum);
        }
    }

    /** Units of each key sitting in {@link #stock} that were made by this plan rather than stored. */
    private final Map<K, Long> producedSurplus = new HashMap<>();

    /**
     * Run patterns to make at least {@code amount} of {@code key} (outputs land in stock).
     * Returns how many were actually produced toward the amount.
     */
    private long craft(K key, long amount, int depth) {
        if (depth >= MAX_DEPTH) return 0;
        List<Pattern<K>> options = patternsFor.apply(key);
        if (options == null || options.isEmpty()) return 0;
        // Prefer a pattern whose inputs are all directly in stock; otherwise the first usable one.
        Pattern<K> chosen = null;
        for (Pattern<K> p : options) {
            if (p.produces(key) <= 0 || active.contains(p.id())) continue;
            if (chosen == null) chosen = p;
            if (inStock(p, ceilDiv(amount, p.produces(key)))) { chosen = p; break; }
        }
        if (chosen == null) return 0;
        long per = chosen.produces(key);
        long times = ceilDiv(amount, per);
        active.add(chosen.id());
        Map<K, Long> missingBefore = new HashMap<>(missing);
        for (var in : chosen.inputs().entrySet()) {
            need(in.getKey(), Math.multiplyExact(in.getValue(), times), depth + 1);
        }
        active.remove(chosen.id());
        // If any input went missing, the steps still run as far as they can in a partial plan;
        // the caller sees the missing list and refuses to start.
        boolean inputsMissing = !missing.equals(missingBefore);
        for (var out : chosen.outputs().entrySet()) {
            long n = Math.multiplyExact(out.getValue(), times);
            stock.merge(out.getKey(), n, Long::sum);
            producedSurplus.merge(out.getKey(), n, Long::sum);
            crafted.merge(out.getKey(), n, Long::sum);
        }
        // Merged runs keep the first position: every consumer was added after its first producer run.
        Step<K> prev = steps.get(chosen.id());
        steps.put(chosen.id(), new Step<>(chosen, times + (prev == null ? 0 : prev.times())));
        return inputsMissing ? Math.min(amount, per * times) : per * times;
    }

    private boolean inStock(Pattern<K> p, long times) {
        for (var in : p.inputs().entrySet()) {
            if (stock.getOrDefault(in.getKey(), 0L) < in.getValue() * times) return false;
        }
        return true;
    }

    static long ceilDiv(long a, long b) {
        return (a + b - 1) / b;
    }

    /** Sum of every input a list of steps consumes, for display. */
    public static <K> Map<K, Long> totalInputs(List<Step<K>> steps) {
        Map<K, Long> m = new LinkedHashMap<>();
        for (Step<K> s : steps) {
            for (var in : s.pattern().inputs().entrySet()) m.merge(in.getKey(), in.getValue() * s.times(), Long::sum);
        }
        return m;
    }
}
