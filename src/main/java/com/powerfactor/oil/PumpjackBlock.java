package com.powerfactor.oil;

import com.mojang.serialization.MapCodec;
import com.powerfactor.machine.TallMachineBlock;
import com.powerfactor.multiblock.CasingBlock;
import com.powerfactor.registry.ModBlockEntities;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** The two-block mechanism above the pumpjack's structural foundation. */
public final class PumpjackBlock extends TallMachineBlock {
    public static final MapCodec<PumpjackBlock> CODEC = simpleCodec(PumpjackBlock::new);

    public PumpjackBlock(Properties properties) {
        super(() -> ModBlockEntities.PUMPJACK.get(), Block.box(1, 0, 1, 15, 16, 15), Block.box(5, 0, 5, 11, 12, 11), properties);
        registerDefaultState(defaultBlockState().setValue(CasingBlock.FORMED, false));
    }

    @Override
    protected MapCodec<PumpjackBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CasingBlock.FORMED);
    }
}
