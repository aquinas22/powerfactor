package com.powerfactor.tesseract;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/** Which loaded tesseracts share a frequency: a private frequency is keyed by its owner, a public one is shared by all. */
public final class TesseractNetwork {
    public record Key(@Nullable UUID owner, int frequency) {}

    private static final Map<Key, Set<GlobalPos>> MEMBERS = new HashMap<>();
    private static MinecraftServer server;

    private static void bind(MinecraftServer s) {
        if (server != s) {
            MEMBERS.clear();
            server = s;
        }
    }

    static void join(MinecraftServer s, Key key, GlobalPos pos) {
        bind(s);
        MEMBERS.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(pos);
    }

    static void leave(MinecraftServer s, Key key, GlobalPos pos) {
        bind(s);
        Set<GlobalPos> set = MEMBERS.get(key);
        if (set != null) set.remove(pos);
    }

    /** Every loaded tesseract on this key, excluding {@code self}. */
    static List<TesseractBlockEntity> peers(MinecraftServer s, Key key, GlobalPos self) {
        bind(s);
        List<TesseractBlockEntity> out = new ArrayList<>();
        Set<GlobalPos> set = MEMBERS.get(key);
        if (set == null) return out;
        for (GlobalPos g : List.copyOf(set)) {
            if (g.equals(self)) continue;
            ServerLevel level = s.getLevel(g.dimension());
            if (level != null && level.isLoaded(g.pos()) && level.getBlockEntity(g.pos()) instanceof TesseractBlockEntity t && !t.isRemoved()) out.add(t);
        }
        return out;
    }

    static int count(MinecraftServer s, Key key) {
        bind(s);
        Set<GlobalPos> set = MEMBERS.get(key);
        return set == null ? 0 : set.size();
    }

    private TesseractNetwork() {}
}
