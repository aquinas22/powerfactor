package com.powerfactor.device;

import com.mojang.serialization.MapCodec;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.registry.ModQuantum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Logic Controller: a slab-height controller that outputs redstone from its front face. */
public class LogicControllerBlock extends MachineBlock {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 6, 16);

    public LogicControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        throw new UnsupportedOperationException("not data-driven");
    }

    @Override
    protected BlockEntityType<? extends MachineBlockEntity> blockEntityType() {
        return ModQuantum.LOGIC_CONTROLLER_BE.get();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof LogicControllerBlockEntity c ? c.signalToward(direction) : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }
}
