package com.powerfactor.ender;

import com.mojang.serialization.MapCodec;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class EnderLinkBlock extends MachineBlock {
    private final boolean transmitter;
    private final MapCodec<EnderLinkBlock> codec;

    public EnderLinkBlock(boolean transmitter, Properties properties) {
        super(properties);
        this.transmitter = transmitter;
        this.codec = simpleCodec(p -> new EnderLinkBlock(transmitter, p));
    }

    public boolean transmitter() {
        return transmitter;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    @Override
    protected BlockEntityType<? extends MachineBlockEntity> blockEntityType() {
        return ModBlockEntities.ENDER_LINK.get();
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int n = state.getValue(RUNNING) ? 4 : 1;
        for (int i = 0; i < n; i++) {
            double x = pos.getX() + 0.5, y = pos.getY() + 1.1, z = pos.getZ() + 0.5;
            double vx = (random.nextDouble() - 0.5) * 2, vy = transmitter ? random.nextDouble() * 1.5 : -random.nextDouble(),
                    vz = (random.nextDouble() - 0.5) * 2;
            level.addParticle(ParticleTypes.PORTAL, x, y, z, vx, vy, vz);
        }
        if (state.getValue(RUNNING) && random.nextFloat() < 0.15F) {
            level.addParticle(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 0, transmitter ? 0.2 : -0.05, 0);
        }
    }
}
