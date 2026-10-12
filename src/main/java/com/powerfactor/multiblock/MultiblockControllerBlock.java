package com.powerfactor.multiblock;

import com.mojang.serialization.MapCodec;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.machine.MachineBlockEntity;
import java.util.function.Supplier;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** The controller block of a multiblock machine: a normal machine block plus a FORMED state. */
public class MultiblockControllerBlock extends MachineBlock {
    private final Supplier<BlockEntityType<? extends MachineBlockEntity>> type;
    private final MapCodec<MultiblockControllerBlock> codec;

    public MultiblockControllerBlock(Supplier<BlockEntityType<? extends MachineBlockEntity>> type, Properties properties) {
        super(properties);
        this.type = type;
        this.codec = simpleCodec(p -> new MultiblockControllerBlock(type, p));
        registerDefaultState(defaultBlockState().setValue(CasingBlock.FORMED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    @Override
    protected BlockEntityType<? extends MachineBlockEntity> blockEntityType() {
        return type.get();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CasingBlock.FORMED);
    }
}
