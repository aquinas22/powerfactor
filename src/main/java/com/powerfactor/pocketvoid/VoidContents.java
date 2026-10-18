package com.powerfactor.pocketvoid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

/**
 * What a Pocket Void holds: nine slots, each one kind of item in a large count, optionally locked to its
 * kind even when empty; which slot builds when the void is used on a block; and the pickup mode.
 * Immutable: every change returns a new value to write back onto the item.
 */
public record VoidContents(List<Slot> slots, int selected, Mode mode) {
    public static final int SLOTS = 9;

    public enum Mode {
        /** Picks nothing up. */
        OFF,
        /** Pulls in only the kinds already stored (or locked). */
        MATCHING,
        /** Pulls in anything it has room for. */
        ALL,
        /** Matching, and deletes the overflow once a slot is full (cobblestone, dirt...). */
        VOID;

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public record Slot(Optional<ItemStackTemplate> item, int count, boolean locked) {
        public static final Slot EMPTY = new Slot(Optional.empty(), 0, false);
        public static final Codec<Slot> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemStackTemplate.CODEC.optionalFieldOf("item").forGetter(Slot::item),
                Codec.INT.optionalFieldOf("count", 0).forGetter(Slot::count),
                Codec.BOOL.optionalFieldOf("locked", false).forGetter(Slot::locked)).apply(i, Slot::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Slot> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.optional(ItemStackTemplate.STREAM_CODEC), Slot::item, ByteBufCodecs.VAR_INT, Slot::count, ByteBufCodecs.BOOL, Slot::locked,
                Slot::new);

        public boolean holds(ItemStack stack) {
            return item.isPresent() && ItemStack.isSameItemSameComponents(item.get().create(), stack);
        }

        public ItemStack display() {
            return item.map(ItemStackTemplate::create).orElse(ItemStack.EMPTY);
        }
    }

    public static final Codec<VoidContents> CODEC = RecordCodecBuilder.create(i -> i.group(
            Slot.CODEC.listOf().fieldOf("slots").forGetter(VoidContents::slots),
            Codec.INT.optionalFieldOf("selected", 0).forGetter(VoidContents::selected),
            Codec.STRING.xmap(s -> { try { return Mode.valueOf(s); } catch (IllegalArgumentException e) { return Mode.OFF; } }, Mode::name)
                    .optionalFieldOf("mode", Mode.MATCHING).forGetter(VoidContents::mode)).apply(i, VoidContents::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, VoidContents> STREAM_CODEC = StreamCodec.composite(
            Slot.STREAM_CODEC.apply(ByteBufCodecs.list(SLOTS)), VoidContents::slots, ByteBufCodecs.VAR_INT, VoidContents::selected,
            ByteBufCodecs.VAR_INT.map(i -> Mode.values()[Math.floorMod(i, Mode.values().length)], Mode::ordinal), VoidContents::mode,
            VoidContents::new);

    public static final VoidContents EMPTY = new VoidContents(java.util.Collections.nCopies(SLOTS, Slot.EMPTY), 0, Mode.MATCHING);

    public VoidContents {
        slots = List.copyOf(slots.size() == SLOTS ? slots : pad(slots));
        selected = Math.floorMod(selected, SLOTS);
    }

    private static List<Slot> pad(List<Slot> in) {
        List<Slot> out = new ArrayList<>(in.subList(0, Math.min(in.size(), SLOTS)));
        while (out.size() < SLOTS) out.add(Slot.EMPTY);
        return out;
    }

    public Slot slot(int i) {
        return slots.get(i);
    }

    private VoidContents with(int i, Slot s) {
        List<Slot> copy = new ArrayList<>(slots);
        // An unlocked slot forgets its kind once empty.
        copy.set(i, s.count() <= 0 && !s.locked() ? Slot.EMPTY : s);
        return new VoidContents(copy, selected, mode);
    }

    public VoidContents withMode(Mode m) {
        return new VoidContents(slots, selected, m);
    }

    public VoidContents withSelected(int i) {
        return new VoidContents(slots, i, mode);
    }

    public VoidContents toggleLock(int i) {
        Slot s = slot(i);
        if (s.item().isEmpty()) return this;
        return with(i, new Slot(s.item(), s.count(), !s.locked()));
    }

    /** Result of an insert: the new contents and how many items it took (voided ones count as taken). */
    public record Inserted(VoidContents contents, int taken) {}

    /**
     * Puts as much of {@code stack} in as fits. {@code empties}: may start a new kind in an empty slot;
     * {@code voidExcess}: a full matching slot swallows the rest.
     */
    public Inserted insert(ItemStack stack, int capacity, boolean empties, boolean voidExcess) {
        if (stack.isEmpty() || stack.getItem() instanceof PocketVoidItem) return new Inserted(this, 0);
        int left = stack.getCount();
        VoidContents c = this;
        for (int i = 0; i < SLOTS && left > 0; i++) {
            Slot s = c.slot(i);
            if (!s.holds(stack)) continue;
            int move = Math.min(left, capacity - s.count());
            if (move > 0) {
                c = c.with(i, new Slot(s.item(), s.count() + move, s.locked()));
                left -= move;
            }
            if (left > 0 && voidExcess) left = 0;
        }
        if (left > 0 && empties) {
            for (int i = 0; i < SLOTS && left > 0; i++) {
                if (c.slot(i).item().isPresent()) continue;
                int move = Math.min(left, capacity);
                c = c.with(i, new Slot(Optional.of(ItemStackTemplate.fromNonEmptyStack(stack.copyWithCount(1))), move, false));
                left -= move;
            }
        }
        return new Inserted(c, stack.getCount() - left);
    }

    /** Takes up to {@code max} items from slot {@code i}. */
    public record Taken(VoidContents contents, ItemStack stack) {}

    public Taken take(int i, int max) {
        Slot s = slot(i);
        if (s.item().isEmpty() || s.count() <= 0 || max <= 0) return new Taken(this, ItemStack.EMPTY);
        int n = Math.min(max, s.count());
        ItemStack out = s.item().get().create();
        out.setCount(n);
        return new Taken(with(i, new Slot(s.item(), s.count() - n, s.locked())), out);
    }

    public int total() {
        int n = 0;
        for (Slot s : slots) n += s.count();
        return n;
    }
}
