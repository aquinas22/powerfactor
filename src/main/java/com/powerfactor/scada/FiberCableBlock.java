package com.powerfactor.scada;

import com.powerfactor.api.ScadaDevice;
import com.powerfactor.grid.ConnectorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

/** SCADA data cable. Links terminals to devices; carries no power. */
public class FiberCableBlock extends ConnectorBlock {
    public FiberCableBlock(Properties properties) {
        super(properties, 4);
    }

    @Override
    protected boolean connectsTo(BlockGetter level, BlockPos pos, Direction side) {
        BlockPos other = pos.relative(side);
        var block = level.getBlockState(other).getBlock();
        return block == this || block instanceof ScadaTerminalBlock || block instanceof ScadaDisplayBlock
                || level.getBlockEntity(other) instanceof ScadaDevice;
    }

    @Override
    protected void onTopologyChanged(Level level, BlockPos pos) {
        // Terminals rediscover their network on every poll, so nothing is cached.
    }
}
