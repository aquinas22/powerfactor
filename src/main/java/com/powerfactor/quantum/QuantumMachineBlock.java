package com.powerfactor.quantum;

import com.mojang.serialization.MapCodec;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.machine.MachineBlockEntity;
import java.util.function.Supplier;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** A horizontal machine block for the late-game quantum machines. */
public class QuantumMachineBlock extends MachineBlock {
    private final Supplier<? extends BlockEntityType<? extends MachineBlockEntity>> type;

    public QuantumMachineBlock(Supplier<? extends BlockEntityType<? extends MachineBlockEntity>> type, Properties properties) {
        super(properties);
        this.type = type;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        throw new UnsupportedOperationException("not data-driven");
    }

    @Override
    protected BlockEntityType<? extends MachineBlockEntity> blockEntityType() {
        return type.get();
    }
}
