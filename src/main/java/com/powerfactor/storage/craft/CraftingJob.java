package com.powerfactor.storage.craft;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.powerfactor.storage.StorageNetwork;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * A running autocraft on a Crafting Processor. At the start the plan's stored ingredients move from
 * the network into the job's buffer (so they can't be spent elsewhere). Each tick the job hands
 * executions to Assemblers and Pattern Providers that hold the right pattern, and claims returning
 * products from network inserts. When the steps are done and the product is in the buffer, the whole
 * buffer flushes back into storage. Cancelling flushes the buffer at once; nothing is ever deleted.
 */
public final class CraftingJob {
    public enum State { RUNNING, FINISHING, DONE }

    public static final class Step {
        public final PatternData pattern;
        public long remaining;
        public final long total;

        Step(PatternData pattern, long remaining, long total) {
            this.pattern = pattern;
            this.remaining = remaining;
            this.total = total;
        }
    }

    private final ItemResource target;
    private final long amount;
    private final String requester;
    private final List<Step> steps;
    private final Map<ItemResource, Long> buffer = new LinkedHashMap<>();
    private final Map<ItemResource, Long> awaiting = new LinkedHashMap<>();
    private State state = State.RUNNING;
    private int idleTicks;
    private long elapsed;

    public CraftingJob(ItemResource target, long amount, String requester, List<Step> steps) {
        this.target = target;
        this.amount = amount;
        this.requester = requester;
        this.steps = steps;
    }

    public static CraftingJob of(CraftingPlanner.Plan<ItemResource> plan, Map<String, PatternData> byId, String requester) {
        List<Step> steps = new ArrayList<>();
        for (var s : plan.steps()) steps.add(new Step(byId.get(s.pattern().id()), s.times(), s.times()));
        return new CraftingJob(plan.target(), plan.amount(), requester, steps);
    }

    /** Move the plan's stored ingredients into the buffer. Returns false (and undoes) if storage changed underneath. */
    public boolean reserve(StorageNetwork network, Map<ItemResource, Long> used) {
        Map<ItemResource, Long> taken = new LinkedHashMap<>();
        for (var e : used.entrySet()) {
            long got = network.extract(e.getKey(), e.getValue(), false);
            if (got > 0) taken.merge(e.getKey(), got, Long::sum);
            if (got < e.getValue()) {
                for (var t : taken.entrySet()) {
                    long back = network.insert(t.getKey(), t.getValue(), false);
                    if (back < t.getValue()) buffer.merge(t.getKey(), t.getValue() - back, Long::sum);
                }
                if (!buffer.isEmpty()) state = State.FINISHING;
                return false;
            }
        }
        for (var t : taken.entrySet()) buffer.merge(t.getKey(), t.getValue(), Long::sum);
        return true;
    }

    // ------------------------------------------------------------------ running

    /** Up to {@code parallel} dispatches this tick. */
    public void tick(ServerLevel level, StorageNetwork network, int parallel) {
        elapsed++;
        if (state == State.FINISHING || state == State.DONE) {
            flush(network);
            return;
        }
        int sent = 0;
        for (Step step : steps) {
            while (step.remaining > 0 && sent < parallel && step.pattern != null && has(step.pattern)) {
                PatternHolder holder = findHolder(network, step.pattern);
                if (holder == null) break;
                List<ItemStack> inputs = take(step.pattern);
                if (!holder.accept(level, step.pattern, inputs)) {
                    give(inputs);
                    break;
                }
                step.remaining--;
                sent++;
                for (var out : step.pattern.outputMap().entrySet()) awaiting.merge(out.getKey(), out.getValue(), Long::sum);
            }
        }
        idleTicks = sent > 0 ? 0 : idleTicks + 1;
        boolean dispatched = steps.stream().allMatch(s -> s.remaining <= 0);
        if (dispatched && buffer.getOrDefault(target, 0L) >= amount) {
            awaiting.clear();
            state = State.FINISHING;
            flush(network);
        }
    }

    private PatternHolder findHolder(StorageNetwork network, PatternData p) {
        List<PatternHolder> holders = network.patterns().get(p);
        if (holders == null) return null;
        for (PatternHolder h : holders) if (h.ready(p)) return h;
        return null;
    }

    private boolean has(PatternData p) {
        for (var in : p.inputMap().entrySet()) {
            if (buffer.getOrDefault(in.getKey(), 0L) < in.getValue()) return false;
        }
        return true;
    }

    /** Pull one execution's inputs out of the buffer, laid out like the pattern's entries. */
    private List<ItemStack> take(PatternData p) {
        List<ItemStack> list = new ArrayList<>();
        for (PatternData.Entry e : p.inputs()) {
            if (e.isEmpty()) {
                list.add(ItemStack.EMPTY);
                continue;
            }
            buffer.merge(e.resource(), -(long) e.count(), Long::sum);
            if (buffer.get(e.resource()) <= 0) buffer.remove(e.resource());
            list.add(e.stack());
        }
        return list;
    }

    private void give(List<ItemStack> stacks) {
        for (ItemStack s : stacks) if (!s.isEmpty()) buffer.merge(ItemResource.of(s), (long) s.getCount(), Long::sum);
    }

    /** A network insert: claims up to what this job is waiting for. */
    public long offer(ItemResource r, long amount) {
        if (state != State.RUNNING) return 0;
        long want = awaiting.getOrDefault(r, 0L);
        long n = Math.min(want, amount);
        if (n <= 0) return 0;
        if (want - n <= 0) awaiting.remove(r);
        else awaiting.put(r, want - n);
        buffer.merge(r, n, Long::sum);
        return n;
    }

    public void cancel(StorageNetwork network) {
        awaiting.clear();
        state = State.FINISHING;
        flush(network);
    }

    private void flush(StorageNetwork network) {
        for (var e : List.copyOf(buffer.entrySet())) {
            long put = network.insert(e.getKey(), e.getValue(), false);
            long left = e.getValue() - put;
            if (left <= 0) buffer.remove(e.getKey());
            else buffer.put(e.getKey(), left);
        }
        if (buffer.isEmpty()) state = State.DONE;
    }

    /** When the network is gone, the processor drops what the job held. */
    public Map<ItemResource, Long> buffer() {
        return buffer;
    }

    public State state() {
        return state;
    }

    public ItemResource target() {
        return target;
    }

    public long amount() {
        return amount;
    }

    public String requester() {
        return requester;
    }

    public List<Step> steps() {
        return steps;
    }

    public Map<ItemResource, Long> awaiting() {
        return awaiting;
    }

    public boolean stalled() {
        return idleTicks > 100 && awaiting.isEmpty();
    }

    public long elapsed() {
        return elapsed;
    }

    public float progress() {
        long total = 0, done = 0;
        for (Step s : steps) {
            total += s.total;
            done += s.total - s.remaining;
        }
        return total == 0 ? 1 : (float) done / total;
    }

    // ------------------------------------------------------------------ saving

    private record Saved(ItemResource target, long amount, String requester, List<SavedStep> steps, List<PatternEntryL> buffer,
            List<PatternEntryL> awaiting, int state, long elapsed) {}

    private record SavedStep(PatternData pattern, long remaining, long total) {
        static final Codec<SavedStep> CODEC = RecordCodecBuilder.create(i -> i.group(
                PatternData.CODEC.fieldOf("pattern").forGetter(SavedStep::pattern),
                Codec.LONG.fieldOf("remaining").forGetter(SavedStep::remaining),
                Codec.LONG.fieldOf("total").forGetter(SavedStep::total)).apply(i, SavedStep::new));
    }

    private record PatternEntryL(ItemResource resource, long count) {
        static final Codec<PatternEntryL> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemResource.CODEC.fieldOf("item").forGetter(PatternEntryL::resource),
                Codec.LONG.fieldOf("count").forGetter(PatternEntryL::count)).apply(i, PatternEntryL::new));
    }

    private static final Codec<Saved> SAVED = RecordCodecBuilder.create(i -> i.group(
            ItemResource.CODEC.fieldOf("target").forGetter(Saved::target),
            Codec.LONG.fieldOf("amount").forGetter(Saved::amount),
            Codec.STRING.optionalFieldOf("requester", "").forGetter(Saved::requester),
            SavedStep.CODEC.listOf().fieldOf("steps").forGetter(Saved::steps),
            PatternEntryL.CODEC.listOf().fieldOf("buffer").forGetter(Saved::buffer),
            PatternEntryL.CODEC.listOf().fieldOf("awaiting").forGetter(Saved::awaiting),
            Codec.INT.fieldOf("state").forGetter(Saved::state),
            Codec.LONG.optionalFieldOf("elapsed", 0L).forGetter(Saved::elapsed)).apply(i, Saved::new));

    public static final Codec<CraftingJob> CODEC = SAVED.xmap(CraftingJob::fromSaved, CraftingJob::toSaved);

    private static CraftingJob fromSaved(Saved s) {
        List<Step> steps = new ArrayList<>();
        for (SavedStep st : s.steps()) steps.add(new Step(st.pattern(), st.remaining(), st.total()));
        CraftingJob job = new CraftingJob(s.target(), s.amount(), s.requester(), steps);
        for (PatternEntryL e : s.buffer()) job.buffer.merge(e.resource(), e.count(), Long::sum);
        for (PatternEntryL e : s.awaiting()) job.awaiting.merge(e.resource(), e.count(), Long::sum);
        job.state = State.values()[Math.clamp(s.state(), 0, 2)];
        job.elapsed = s.elapsed();
        return job;
    }

    private Saved toSaved() {
        return new Saved(target, amount, requester,
                steps.stream().map(st -> new SavedStep(st.pattern, st.remaining, st.total)).toList(),
                buffer.entrySet().stream().map(e -> new PatternEntryL(e.getKey(), e.getValue())).toList(),
                awaiting.entrySet().stream().map(e -> new PatternEntryL(e.getKey(), e.getValue())).toList(),
                state.ordinal(), elapsed);
    }
}
