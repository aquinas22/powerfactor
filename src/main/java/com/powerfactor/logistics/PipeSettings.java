package com.powerfactor.logistics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

/** Immutable routing settings carried by a packed pipe; sample stacks are references, never inventory. */
public record PipeSettings(int disabledMask, List<Face> faces) {
    public static final int SAMPLES = 4;
    public static final PipeSettings DEFAULT = new PipeSettings(0, java.util.Collections.nCopies(6, Face.EMPTY));
    public static final Codec<PipeSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, 63).optionalFieldOf("disabled", 0).forGetter(PipeSettings::disabledMask),
            Face.CODEC.listOf(6, 6).fieldOf("faces").forGetter(PipeSettings::faces)
    ).apply(instance, PipeSettings::new));

    public PipeSettings { faces = List.copyOf(faces); }
    public Face face(Direction side) { return faces.get(side.ordinal()); }
    public PipeSettings withFace(Direction side, Face filter) {
        var copy = new ArrayList<>(faces); copy.set(side.ordinal(), filter);
        return new PipeSettings(disabledMask, copy);
    }
    public PipeSettings withDisabled(Direction side, boolean disabled) {
        int mask = disabled ? disabledMask | 1 << side.ordinal() : disabledMask & ~(1 << side.ordinal());
        return new PipeSettings(mask, faces);
    }

    /** Mode: 0 unrestricted, 1 allow listed resources, 2 block listed resources. */
    public record Face(int mode, boolean exact, int priority, List<ItemStack> samples) {
        public static final Face EMPTY = new Face(0, false, 0, java.util.Collections.nCopies(SAMPLES, ItemStack.EMPTY));
        public static final Codec<Face> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(0, 2).optionalFieldOf("mode", 0).forGetter(Face::mode),
                Codec.BOOL.optionalFieldOf("exact", false).forGetter(Face::exact),
                Codec.intRange(-5, 5).optionalFieldOf("priority", 0).forGetter(Face::priority),
                ItemStack.OPTIONAL_CODEC.listOf(SAMPLES, SAMPLES).fieldOf("samples").forGetter(Face::samples)
        ).apply(instance, Face::new));
        public Face {
            mode = Math.clamp(mode, 0, 2); priority = Math.clamp(priority, -5, 5);
            if (samples.size() != SAMPLES) throw new IllegalArgumentException("Pipe faces require four references");
            samples = samples.stream().map(stack -> stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1)).toList();
        }
        @Override public boolean equals(Object other) {
            if (other == this) return true;
            if (!(other instanceof Face face) || mode != face.mode || exact != face.exact || priority != face.priority) return false;
            for (int i = 0; i < SAMPLES; i++) if (!ItemStack.matches(samples.get(i), face.samples.get(i))) return false;
            return true;
        }
        @Override public int hashCode() {
            int hash = java.util.Objects.hash(mode, exact, priority);
            for (ItemStack sample : samples) hash = 31 * hash + net.neoforged.neoforge.transfer.item.ItemResource.of(sample).hashCode();
            return hash;
        }
        public Face withMode(int value) { return new Face(value, exact, priority, samples); }
        public Face withExact(boolean value) { return new Face(mode, value, priority, samples); }
        public Face withPriority(int value) { return new Face(mode, exact, value, samples); }
        public Face withSample(int index, ItemStack stack) {
            var copy = new ArrayList<>(samples); copy.set(index, stack);
            return new Face(mode == 0 && !stack.isEmpty() ? 1 : mode, exact, priority, copy);
        }
        public Face clear() { return new Face(mode, exact, priority, EMPTY.samples); }
    }
}
