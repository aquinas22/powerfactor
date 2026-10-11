package com.powerfactor.scada;

import com.mojang.serialization.MapCodec;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.registry.ModBlockEntities;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Operator workstation: monitors and controls every device on its fiber network. */
public class ScadaTerminalBlock extends MachineBlock {
    public static final MapCodec<ScadaTerminalBlock> CODEC = simpleCodec(ScadaTerminalBlock::new);

    public ScadaTerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected BlockEntityType<? extends MachineBlockEntity> blockEntityType() {
        return ModBlockEntities.SCADA_TERMINAL.get();
    }
}
