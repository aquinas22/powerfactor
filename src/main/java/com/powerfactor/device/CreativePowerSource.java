package com.powerfactor.device;

import com.mojang.serialization.MapCodec;
import com.powerfactor.registry.ModStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/** Creative-only: pushes up to a million FE per tick into every neighbour that accepts energy. */
public class CreativePowerSource extends BaseEntityBlock {
    public static final int RATE = 1_000_000;

    public CreativePowerSource(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(CreativePowerSource::new);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModStorage.CREATIVE_POWER_BE.get()) return null;
        return (l, pos, s, be) -> {
            for (Direction d : Direction.values()) {
                var target = l.getCapability(Capabilities.Energy.BLOCK, pos.relative(d), d.getOpposite());
                if (target == null) continue;
                try (Transaction tx = Transaction.openRoot()) {
                    target.insert(RATE, tx);
                    tx.commit();
                }
            }
        };
    }

    public static class Entity extends BlockEntity {
        public Entity(BlockPos pos, BlockState state) {
            super(ModStorage.CREATIVE_POWER_BE.get(), pos, state);
        }
    }
}
