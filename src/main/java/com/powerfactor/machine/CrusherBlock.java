package com.powerfactor.machine;

import com.mojang.serialization.MapCodec;
import com.powerfactor.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Motor-driven jaw crusher: ores to two dusts, cobble to gravel to sand, and so on. */
public class CrusherBlock extends MachineBlock {
    public static final MapCodec<CrusherBlock> CODEC = simpleCodec(CrusherBlock::new);

    public CrusherBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected BlockEntityType<? extends MachineBlockEntity> blockEntityType() {
        return ModBlockEntities.CRUSHER.get();
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(RUNNING)) {
            return;
        }
        if (random.nextDouble() < 0.15) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS,
                    0.25F, 0.6F + random.nextFloat() * 0.2F, false);
        }
        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5 + facing.getStepX() * 0.52;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.52;
        level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GRAVEL.defaultBlockState()),
                x + random.nextDouble() * 0.4 - 0.2, pos.getY() + 0.6, z + random.nextDouble() * 0.4 - 0.2, 0, 0, 0);
    }
}
