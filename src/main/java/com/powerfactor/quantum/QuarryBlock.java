package com.powerfactor.quantum;

import com.mojang.serialization.MapCodec;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.registry.ModQuantum;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** The Industrial and Quantum Quarry blocks; the mined square opens in front of the controller. */
public class QuarryBlock extends MachineBlock {
    private final QuarryBlockEntity.Tier tier;

    public QuarryBlock(QuarryBlockEntity.Tier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        throw new UnsupportedOperationException("not data-driven");
    }

    @Override
    protected BlockEntityType<? extends MachineBlockEntity> blockEntityType() {
        return tier == QuarryBlockEntity.Tier.QUANTUM ? ModQuantum.QUANTUM_QUARRY_BE.get() : ModQuantum.QUARRY_BE.get();
    }
}
