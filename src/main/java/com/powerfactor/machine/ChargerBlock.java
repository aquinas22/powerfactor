package com.powerfactor.machine;

import com.mojang.serialization.MapCodec;
import com.powerfactor.registry.ModBlockEntities;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Fills any FE-holding item (PowerFactor's drill or another mod's) from the grid. */
public class ChargerBlock extends MachineBlock {
    public static final MapCodec<ChargerBlock> CODEC = simpleCodec(ChargerBlock::new);

    public ChargerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected BlockEntityType<? extends MachineBlockEntity> blockEntityType() {
        return ModBlockEntities.CHARGER.get();
    }
}
