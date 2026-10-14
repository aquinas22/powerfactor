package com.powerfactor.storage.fluid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/** What a fluid cell holds (amounts in mB), saved on the cell item so it survives being moved. */
public record FluidCellContents(List<Entry> entries) {
    public static final FluidCellContents EMPTY = new FluidCellContents(List.of());

    public record Entry(FluidResource fluid, long amount) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                FluidResource.CODEC.fieldOf("fluid").forGetter(Entry::fluid),
                Codec.LONG.fieldOf("amount").forGetter(Entry::amount)).apply(i, Entry::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                FluidResource.STREAM_CODEC, Entry::fluid, ByteBufCodecs.VAR_LONG, Entry::amount, Entry::new);
    }

    public static final Codec<FluidCellContents> CODEC = Entry.CODEC.listOf().xmap(FluidCellContents::new, FluidCellContents::entries);
    public static final StreamCodec<RegistryFriendlyByteBuf, FluidCellContents> STREAM_CODEC =
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list(256)).map(FluidCellContents::new, FluidCellContents::entries);

    public FluidCellContents {
        entries = List.copyOf(entries);
    }

    public Map<FluidResource, Long> asMap() {
        Map<FluidResource, Long> m = new LinkedHashMap<>();
        for (Entry e : entries) if (!e.fluid().isEmpty() && e.amount() > 0) m.merge(e.fluid(), e.amount(), Long::sum);
        return m;
    }

    public static FluidCellContents of(Map<FluidResource, Long> map) {
        return new FluidCellContents(map.entrySet().stream().map(e -> new Entry(e.getKey(), e.getValue())).toList());
    }

    public long total() {
        long n = 0;
        for (Entry e : entries) n += e.amount();
        return n;
    }
}
