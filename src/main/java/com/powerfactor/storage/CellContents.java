package com.powerfactor.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** What a storage cell holds, saved on the cell item so it survives being moved between drives. */
public record CellContents(List<Entry> entries) {
    public static final CellContents EMPTY = new CellContents(List.of());

    public record Entry(ItemResource resource, long count) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemResource.CODEC.fieldOf("item").forGetter(Entry::resource),
                Codec.LONG.fieldOf("count").forGetter(Entry::count)).apply(i, Entry::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ItemResource.STREAM_CODEC, Entry::resource, ByteBufCodecs.VAR_LONG, Entry::count, Entry::new);
    }

    public static final Codec<CellContents> CODEC = Entry.CODEC.listOf().xmap(CellContents::new, CellContents::entries);
    public static final StreamCodec<RegistryFriendlyByteBuf, CellContents> STREAM_CODEC =
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list(1024)).map(CellContents::new, CellContents::entries);

    public CellContents {
        entries = List.copyOf(entries);
    }

    public Map<ItemResource, Long> asMap() {
        Map<ItemResource, Long> m = new LinkedHashMap<>();
        for (Entry e : entries) {
            if (!e.resource().isEmpty() && e.count() > 0) m.merge(e.resource(), e.count(), Long::sum);
        }
        return m;
    }

    public static CellContents of(Map<ItemResource, Long> map) {
        return new CellContents(map.entrySet().stream().map(e -> new Entry(e.getKey(), e.getValue())).toList());
    }

    public long items() {
        long n = 0;
        for (Entry e : entries) n += e.count();
        return n;
    }
}
