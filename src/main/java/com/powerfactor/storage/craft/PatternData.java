package com.powerfactor.storage.craft;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * An encoded recipe. Crafting patterns keep the nine grid cells (empty cells are {@link ItemResource#EMPTY})
 * and run in an Assembler, which re-checks the actual crafting recipe; processing patterns list
 * arbitrary inputs and the outputs a machine is expected to return to the network.
 */
public record PatternData(boolean processing, List<Entry> inputs, List<Entry> outputs) {
    public record Entry(ItemResource resource, int count) {
        public static final Entry EMPTY = new Entry(ItemResource.EMPTY, 0);
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemResource.OPTIONAL_CODEC.fieldOf("item").forGetter(Entry::resource),
                Codec.intRange(0, 1 << 20).fieldOf("count").forGetter(Entry::count)).apply(i, Entry::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ItemResource.STREAM_CODEC, Entry::resource, ByteBufCodecs.VAR_INT, Entry::count, Entry::new);

        public static Entry of(ItemStack stack) {
            return stack.isEmpty() ? EMPTY : new Entry(ItemResource.of(stack), stack.getCount());
        }

        public boolean isEmpty() {
            return resource.isEmpty() || count <= 0;
        }

        public ItemStack stack() {
            return isEmpty() ? ItemStack.EMPTY : resource.toStack(count);
        }
    }

    public static final Codec<PatternData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.fieldOf("processing").forGetter(PatternData::processing),
            Entry.CODEC.listOf(0, 9).fieldOf("inputs").forGetter(PatternData::inputs),
            Entry.CODEC.listOf(0, 9).fieldOf("outputs").forGetter(PatternData::outputs)).apply(i, PatternData::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, PatternData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, PatternData::processing,
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list(9)), PatternData::inputs,
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list(9)), PatternData::outputs,
            PatternData::new);

    public PatternData {
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
    }

    /** Merged input totals per execution. */
    public Map<ItemResource, Long> inputMap() {
        Map<ItemResource, Long> m = new LinkedHashMap<>();
        for (Entry e : inputs) if (!e.isEmpty()) m.merge(e.resource(), (long) e.count(), Long::sum);
        return m;
    }

    public Map<ItemResource, Long> outputMap() {
        Map<ItemResource, Long> m = new LinkedHashMap<>();
        for (Entry e : outputs) if (!e.isEmpty()) m.merge(e.resource(), (long) e.count(), Long::sum);
        return m;
    }

    public long produces(ItemResource r) {
        long n = 0;
        for (Entry e : outputs) if (!e.isEmpty() && e.resource().equals(r)) n += e.count();
        return n;
    }

    public ItemStack primaryOutput() {
        for (Entry e : outputs) if (!e.isEmpty()) return e.stack();
        return ItemStack.EMPTY;
    }

    /** A stable id for the planner (patterns compare by value). */
    public String id() {
        return (processing ? "p" : "c") + inputs + "->" + outputs;
    }

    public CraftingPlanner.Pattern<ItemResource> toPlanner() {
        return new CraftingPlanner.Pattern<>(id(), inputMap(), outputMap());
    }

    public boolean valid() {
        return !outputMap().isEmpty() && !inputMap().isEmpty();
    }
}
