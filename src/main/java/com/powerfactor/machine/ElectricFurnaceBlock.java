package com.powerfactor.machine;

import com.mojang.serialization.MapCodec;
import com.powerfactor.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class ElectricFurnaceBlock extends MachineBlock {
    public static final MapCodec<ElectricFurnaceBlock> CODEC = simpleCodec(ElectricFurnaceBlock::new);

    public ElectricFurnaceBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected BlockEntityType<? extends MachineBlockEntity> blockEntityType() {
        return ModBlockEntities.ELECTRIC_FURNACE.get();
    }

    /** Heat shimmer off the vents on top while the elements are glowing. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(RUNNING) || random.nextFloat() > 0.3F) {
            return;
        }
        Direction facing = state.getValue(FACING);
        level.addParticle(ParticleTypes.WHITE_SMOKE, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 1.02,
                pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.02, 0);
        if (random.nextFloat() < 0.2F) {
            level.addParticle(ParticleTypes.SMALL_FLAME, pos.getX() + 0.5 + facing.getStepX() * 0.52, pos.getY() + 0.35,
                    pos.getZ() + 0.5 + facing.getStepZ() * 0.52, 0, 0, 0);
        }
    }
}
