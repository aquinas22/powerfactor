package com.powerfactor.grid;

import com.powerfactor.api.ReactiveLoad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * Power wire. Connects to wire of the same rating, to anything with an FE handler on that side, and
 * to EE loads/sources such as capacitor banks.
 * Wires of different ratings don't join; that's what transformers will be for.
 */
public class WireBlock extends ConnectorBlock {
    private final int ratingCe;

    public WireBlock(Properties properties, int ratingCe) {
        super(properties, 4);
        this.ratingCe = ratingCe;
    }

    /** Apparent power (CE/t) the whole network can carry. */
    public int ratingCe() {
        return ratingCe;
    }

    @Override
    protected boolean connectsTo(BlockGetter level, BlockPos pos, Direction side) {
        BlockPos other = pos.relative(side);
        if (level.getBlockState(other).getBlock() instanceof ConnectorBlock connector) {
            return connector == this;
        }
        if (level.getBlockEntity(other) instanceof ReactiveLoad) {
            return true;
        }
        return level instanceof Level l && l.getCapability(Capabilities.Energy.BLOCK, other, side.getOpposite()) != null;
    }

    @Override
    protected void onTopologyChanged(Level level, BlockPos pos) {
        if (level instanceof ServerLevel server) {
            GridManager.get(server).invalidate(pos);
        }
    }
}
