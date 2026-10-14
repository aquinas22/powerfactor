package com.powerfactor.storage;

import com.powerfactor.grid.ConnectorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

/**
 * Storage network cable: braided data bus between controllers, drives, terminals and ports. Carries
 * no FE; the controller pays for the whole network. Tiers only change the look (and glow).
 */
public class DataCableBlock extends ConnectorBlock {
    private final int tier;

    public DataCableBlock(Properties properties, int tier) {
        super(properties, tier >= 1 ? 6 : 4);
        this.tier = tier;
    }

    public int tier() {
        return tier;
    }

    @Override
    protected boolean connectsTo(BlockGetter level, BlockPos pos, Direction side) {
        BlockPos other = pos.relative(side);
        return level.getBlockState(other).getBlock() instanceof DataCableBlock || level.getBlockEntity(other) instanceof NodeBlockEntity;
    }

    @Override
    protected void onTopologyChanged(Level level, BlockPos pos) {
        if (level instanceof ServerLevel server) StorageNetworks.invalidate(server, pos);
    }
}
