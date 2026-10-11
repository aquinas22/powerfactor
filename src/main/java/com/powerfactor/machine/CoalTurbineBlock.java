package com.powerfactor.machine;

import com.mojang.serialization.MapCodec;
import com.powerfactor.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A small coal-fired steam turbine-generator. Burns anything in {@code #powerfactor:turbine_fuels},
 * spins up to 60 Hz, then pushes FE out of every face except the front.
 */
public class CoalTurbineBlock extends MachineBlock {
    public static final MapCodec<CoalTurbineBlock> CODEC = simpleCodec(CoalTurbineBlock::new);

    public CoalTurbineBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected BlockEntityType<? extends MachineBlockEntity> blockEntityType() {
        return ModBlockEntities.COAL_TURBINE.get();
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(RUNNING)) {
            return;
        }
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        if (random.nextDouble() < 0.08) {
            level.playLocalSound(x, pos.getY(), z, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.8F, 0.9F, false);
        }
        // Exhaust from the stack on top, embers behind the firebox grate.
        level.addParticle(ParticleTypes.SMOKE, x + random.nextDouble() * 0.2 - 0.1, pos.getY() + 1.05, z + random.nextDouble() * 0.2 - 0.1, 0, 0.04, 0);
        Direction facing = state.getValue(FACING);
        if (random.nextDouble() < 0.3) {
            double side = random.nextDouble() * 0.5 - 0.25;
            double dx = facing.getAxis() == Direction.Axis.X ? facing.getStepX() * 0.52 : side;
            double dz = facing.getAxis() == Direction.Axis.Z ? facing.getStepZ() * 0.52 : side;
            level.addParticle(ParticleTypes.FLAME, x + dx, pos.getY() + 0.2 + random.nextDouble() * 0.2, z + dz, 0, 0, 0);
        }
    }
}
