package com.powerfactor.multiblock;

import com.mojang.serialization.MapCodec;
import com.powerfactor.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A structural block of a multiblock machine (refinery casing, distillation column). On its own
 * it's just a block; once its controller forms the structure it lights up (FORMED) and passes
 * energy and fluids through to the controller, so cables and pipes can attach anywhere.
 */
public class CasingBlock extends BaseEntityBlock {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    private final VoxelShape shape;
    private final MapCodec<CasingBlock> codec;

    public CasingBlock(VoxelShape shape, Properties properties) {
        super(properties);
        this.shape = shape;
        this.codec = simpleCodec(p -> new CasingBlock(shape, p));
        registerDefaultState(stateDefinition.any().setValue(FORMED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.CASING.get().create(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof CasingBlockEntity casing && casing.controller() instanceof com.powerfactor.machine.MachineBlockEntity machine) {
            if (!level.isClientSide()) {
                if (machine.hasMenu()) player.openMenu(machine, machine.getBlockPos());
                else player.sendOverlayMessage(machine.describe());
            }
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        return net.minecraft.world.InteractionResult.PASS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }
}
