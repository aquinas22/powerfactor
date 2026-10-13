package com.powerfactor.generator;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** A cached foreign cable still checks that the complete tower exists before every transfer. */
public final class WindTowerEnergy implements EnergyHandler {
    private final Level level;
    private final BlockPos base;
    public WindTowerEnergy(Level level, BlockPos base) { this.level = level; this.base = base.immutable(); }
    private EnergyHandler target() {
        if (!level.hasChunkAt(base)) return null;
        var state = level.getBlockState(base);
        if (!(state.getBlock() instanceof WindTowerBlock) || !state.getValue(WindTowerBlock.BASE)) return null;
        var wind = WindTowerBlock.turbine(level, base); return wind == null ? null : wind.getEnergy();
    }
    @Override public long getAmountAsLong() { var h = target(); return h == null ? 0 : h.getAmountAsLong(); }
    @Override public long getCapacityAsLong() { var h = target(); return h == null ? 0 : h.getCapacityAsLong(); }
    @Override public int insert(int amount, TransactionContext tx) { return 0; }
    @Override public int extract(int amount, TransactionContext tx) { var h = target(); return h == null ? 0 : h.extract(amount, tx); }
}
