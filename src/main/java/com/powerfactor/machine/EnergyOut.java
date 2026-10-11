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
