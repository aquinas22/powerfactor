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

public class RecipeMachineBlock extends MachineBlock {
    private final RecipeMachine machine;
    private final MapCodec<RecipeMachineBlock> codec;

    public RecipeMachineBlock(RecipeMachine machine, Properties properties) {
        super(properties);
        this.machine = machine;
        this.codec = simpleCodec(p -> new RecipeMachineBlock(machine, p));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    @Override
    protected BlockEntityType<? extends MachineBlockEntity> blockEntityType() {
        return ModBlockEntities.RECIPE_MACHINES.get(machine).get();
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(RUNNING)) {
            return;
        }
        Direction f = state.getValue(FACING);
        double x = pos.getX() + 0.5 + f.getStepX() * 0.52, z = pos.getZ() + 0.5 + f.getStepZ() * 0.52;
        switch (machine) {
            case ARC_FURNACE -> {
                if (random.nextFloat() < 0.6F) {
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK, x + random.nextDouble() * 0.4 - 0.2, pos.getY() + 0.5,
                            z + random.nextDouble() * 0.4 - 0.2, 0, 0.05, 0);
                }
                if (random.nextFloat() < 0.2F) {
                    level.playLocalSound(pos, net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, net.minecraft.sounds.SoundSource.BLOCKS, 0.2F,
                            1.8F, false);
                }
            }
            case ALLOY_FURNACE -> {
                if (random.nextFloat() < 0.3F) {
                    level.addParticle(ParticleTypes.SMALL_FLAME, x, pos.getY() + 0.4, z, 0, 0, 0);
                }
            }
            case ORE_WASHER -> {
                if (random.nextFloat() < 0.4F) {
                    level.addParticle(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 0, 0.1, 0);
                }
            }
            default -> {
            }
        }
    }
}
