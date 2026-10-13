package com.powerfactor.machine;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Moving FE out of a machine's own buffer. Chargers' buffers refuse external extraction (so cables
 * can't drain them), which also blocks {@code EnergyHandlerUtil.move}; the machine itself goes
 * around that limit here.
 */
public final class EnergyOut {
    /** Share a machine's output across distinct neighboring capabilities, rotating the first recipient. */
    public static int distribute(SimpleEnergyHandler from, java.util.List<EnergyHandler> raw, int rotation) {
        var targets = new java.util.ArrayList<EnergyHandler>();
        var seen = new java.util.IdentityHashMap<EnergyHandler, Boolean>();
        for (var target : raw) if (target != null && target != from && seen.put(target, true) == null) targets.add(target);
        if (targets.isEmpty()) return 0;
        java.util.Collections.rotate(targets, Math.floorMod(rotation, targets.size()));
        int total = 0;
        for (int pass = 0; pass < 2 && from.getAmountAsInt() > 0; pass++) {
            for (int i = 0; i < targets.size() && from.getAmountAsInt() > 0; i++) {
                int offer = Math.max(1, from.getAmountAsInt() / (targets.size() - i));
                total += give(from, targets.get(i), offer);
            }
        }
        return total;
    }
    /** Send up to {@code max} FE from {@code from} into {@code to}; returns what moved. */
    public static int give(SimpleEnergyHandler from, EnergyHandler to, int max) {
        int offer = Math.min(max, from.getAmountAsInt());
        if (offer <= 0) {
            return 0;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int moved = to.insert(offer, tx);
            tx.commit();
            from.set(from.getAmountAsInt() - moved);
            return moved;
        }
    }

    private EnergyOut() {}
}
