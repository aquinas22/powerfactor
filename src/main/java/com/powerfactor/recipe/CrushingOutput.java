package com.powerfactor.recipe;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.powerfactor.PowerFactor;
import java.util.Comparator;
import java.util.stream.StreamSupport;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A crusher output: {@code {"item": "minecraft:gravel"}} or {@code {"tag": "c:dusts/tin", "count": 2}}.
 * Tags let one recipe target whichever mod provides a material; they resolve to PowerFactor's item
 * first, then vanilla's, then the alphabetically first. A {@code chance} below 1 makes it a bonus roll.
 */
public record CrushingOutput(Either<Holder<Item>, TagKey<Item>> item, int count, float chance) {
    private static final StreamCodec<RegistryFriendlyByteBuf, TagKey<Item>> TAG_STREAM_CODEC =
            Identifier.STREAM_CODEC.map(id -> TagKey.create(Registries.ITEM, id), TagKey::location).cast();

    public static final Codec<CrushingOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.mapEither(Item.CODEC.fieldOf("item"), TagKey.codec(Registries.ITEM).fieldOf("tag")).forGetter(CrushingOutput::item),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(CrushingOutput::count),
            Codec.floatRange(0, 1).optionalFieldOf("chance", 1F).forGetter(CrushingOutput::chance)
    ).apply(i, CrushingOutput::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CrushingOutput> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.either(Item.STREAM_CODEC, TAG_STREAM_CODEC), CrushingOutput::item,
            ByteBufCodecs.VAR_INT, CrushingOutput::count,
            ByteBufCodecs.FLOAT, CrushingOutput::chance,
            CrushingOutput::new);

    /** A fresh stack of this output, or empty if it names a tag no installed mod fills. */
    public ItemStack resolve() {
        return item.map(holder -> new ItemStack(holder, count), tag ->
                StreamSupport.stream(BuiltInRegistries.ITEM.getTagOrEmpty(tag).spliterator(), false)
                        .min(Comparator.comparingInt(CrushingOutput::rank).thenComparing(Holder::getRegisteredName))
                        .map(h -> new ItemStack(h, count))
                        .orElse(ItemStack.EMPTY));
    }

    private static int rank(Holder<Item> holder) {
        String ns = holder.unwrapKey().map(k -> k.identifier().getNamespace()).orElse("");
        return ns.equals(PowerFactor.MODID) ? 0 : ns.equals("minecraft") ? 1 : 2;
    }
}
