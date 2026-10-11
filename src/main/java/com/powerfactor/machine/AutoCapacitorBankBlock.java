package com.powerfactor.machine;

import com.mojang.serialization.MapCodec;
import com.powerfactor.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Power-factor controller with six switched capacitor steps; the model lights one can per step. */
public class AutoCapacitorBankBlock extends MachineBlock {
    public static final MapCodec<AutoCapacitorBankBlock> CODEC = simpleCodec(AutoCapacitorBankBlock::new);
    public static final IntegerProperty STEPS = IntegerProperty.create("steps", 0, AutoCapacitorBankBlockEntity.STEPS);

    public AutoCapacitorBankBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(STEPS, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(STEPS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CapacitorBankBlock.SHAPE;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected BlockEntityType<? extends MachineBlockEntity> blockEntityType() {
        return ModBlockEntities.AUTO_CAPACITOR_BANK.get();
    }
}
