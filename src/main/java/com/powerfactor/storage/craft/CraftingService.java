package com.powerfactor.storage.craft;

import com.powerfactor.storage.StorageNetwork;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** Plans and starts autocrafts on a network; shared by terminals, tests and anything else that orders crafts. */
public final class CraftingService {
    public enum Result { PREVIEW, STARTED, NO_PROCESSOR, STORAGE_CHANGED, OFFLINE }

    public record Outcome(Result result, CraftingPlanner.Plan<ItemResource> plan) {}

    public static CraftingPlanner.Plan<ItemResource> plan(StorageNetwork network, ItemResource target, long amount) {
        Map<ItemResource, List<CraftingPlanner.Pattern<ItemResource>>> index = new HashMap<>();
        for (PatternData p : network.patterns().keySet()) {
            CraftingPlanner.Pattern<ItemResource> pp = p.toPlanner();
            for (ItemResource out : p.outputMap().keySet()) index.computeIfAbsent(out, k -> new ArrayList<>()).add(pp);
        }
        return CraftingPlanner.plan(target, amount, network.totals(), k -> index.getOrDefault(k, List.of()));
    }

    /** Plan, and if complete and {@code start}, reserve ingredients and hand the job to a free processor. */
    public static Outcome order(StorageNetwork network, ItemResource target, long amount, String requester, boolean start) {
        CraftingPlanner.Plan<ItemResource> plan = plan(network, target, Math.min(amount, 1_000_000));
        if (!start || !plan.complete()) return new Outcome(Result.PREVIEW, plan);
        CraftingProcessorBlockEntity cpu = network.processors().stream().filter(CraftingProcessorBlockEntity::hasRoom)
                .sorted((a, b) -> Boolean.compare(b.quantum(), a.quantum())).findFirst().orElse(null);
        if (cpu == null) return new Outcome(Result.NO_PROCESSOR, plan);
        Map<String, PatternData> byId = new HashMap<>();
        for (PatternData p : network.patterns().keySet()) byId.put(p.id(), p);
        CraftingJob job = CraftingJob.of(plan, byId, requester);
        if (job.reserve(network, plan.used())) {
            cpu.start(job);
            return new Outcome(Result.STARTED, plan);
        }
        // Anything that couldn't go back into storage stays with a finishing job, never lost.
        if (!job.buffer().isEmpty()) cpu.start(job);
        return new Outcome(Result.STORAGE_CHANGED, plan);
    }

    private CraftingService() {}
}
