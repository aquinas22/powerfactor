package com.powerfactor.client.storage;

import com.powerfactor.storage.GridPayloads;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * The client's copy of a terminal's item list, plus the sorted and filtered view. Search syntax:
 * plain words match names, {@code @mod} the mod, {@code #tag} item tags and {@code $text} tooltips.
 */
public final class ClientGrid {
    public enum Sort { NAME, COUNT, MOD }

    public enum View { ALL, STORED, CRAFTABLE }

    public record Stack(ItemResource resource, long count, boolean craftable, ItemStack display) {}

    private static final Map<ItemResource, Stack> ITEMS = new HashMap<>();
    private static int containerId = -1;
    public static int status = 3, jobs;
    public static long usedBytes, totalBytes;
    private static long revision;

    public static void receive(GridPayloads.Sync sync) {
        if (sync.reset() || sync.containerId() != containerId) {
            ITEMS.clear();
            containerId = sync.containerId();
        }
        status = sync.status();
        jobs = sync.jobs();
        usedBytes = sync.usedBytes();
        totalBytes = sync.totalBytes();
        for (GridPayloads.Entry e : sync.entries()) {
            if (e.count() <= 0 && !e.craftable()) ITEMS.remove(e.resource());
            else ITEMS.put(e.resource(), new Stack(e.resource(), e.count(), e.craftable(), e.resource().toStack(1)));
        }
        revision++;
    }

    public static long revision() {
        return revision;
    }

    public static int types() {
        int n = 0;
        for (Stack s : ITEMS.values()) if (s.count() > 0) n++;
        return n;
    }

    public static long count(ItemResource r) {
        Stack s = ITEMS.get(r);
        return s == null ? 0 : s.count();
    }

    public static List<Stack> view(String search, Sort sort, boolean descending, View view) {
        String q = search.trim().toLowerCase(Locale.ROOT);
        List<Stack> list = new ArrayList<>();
        for (Stack s : ITEMS.values()) {
            if (view == View.STORED && s.count() <= 0) continue;
            if (view == View.CRAFTABLE && !s.craftable()) continue;
            if (!q.isEmpty() && !matches(s, q)) continue;
            list.add(s);
        }
        Comparator<Stack> byName = Comparator.comparing(s -> s.display().getHoverName().getString().toLowerCase(Locale.ROOT));
        Comparator<Stack> c = switch (sort) {
            case NAME -> byName;
            case COUNT -> Comparator.<Stack>comparingLong(Stack::count).thenComparing(byName);
            case MOD -> Comparator.<Stack, String>comparing(s -> namespace(s)).thenComparing(byName);
        };
        // Counts read best biggest-first, names A-Z: "descending" flips each default.
        if (sort == Sort.COUNT ^ descending) c = c.reversed();
        list.sort(c);
        return list;
    }

    private static String namespace(Stack s) {
        return BuiltInRegistries.ITEM.getKey(s.display().getItem()).getNamespace();
    }

    private static boolean matches(Stack s, String query) {
        for (String term : query.split("\\s+")) {
            if (term.isEmpty()) continue;
            boolean ok;
            if (term.startsWith("@")) {
                String ns = namespace(s);
                String name = ModList.get().getModContainerById(ns).map(m -> m.getModInfo().getDisplayName()).orElse(ns).toLowerCase(Locale.ROOT);
                ok = ns.contains(term.substring(1)) || name.contains(term.substring(1));
            } else if (term.startsWith("#")) {
                String t = term.substring(1);
                ok = s.display().getItem().builtInRegistryHolder().tags().anyMatch(k -> k.location().toString().contains(t));
            } else if (term.startsWith("$")) {
                String t = term.substring(1);
                var mc = Minecraft.getInstance();
                ok = mc.level != null && s.display().getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player,
                        TooltipFlag.NORMAL).stream().map(Component::getString).anyMatch(l -> l.toLowerCase(Locale.ROOT).contains(t));
            } else {
                ok = s.display().getHoverName().getString().toLowerCase(Locale.ROOT).contains(term);
            }
            if (!ok) return false;
        }
        return true;
    }

    /** "1.2K", "34M"... always at most four characters, AE2-style. */
    public static String compact(long n) {
        if (n < 1000) return Long.toString(n);
        String[] units = {"K", "M", "G", "T", "P", "E"};
        double v = n;
        int u = -1;
        while (v >= 1000 && u < units.length - 1) {
            v /= 1000;
            u++;
        }
        if (v < 10) {
            String s = String.format(Locale.ROOT, "%.1f", Math.floor(v * 10) / 10);
            return (s.endsWith(".0") ? s.substring(0, s.length() - 2) : s) + units[u];
        }
        return (long) Math.floor(v) + units[u];
    }

    private ClientGrid() {}
}
