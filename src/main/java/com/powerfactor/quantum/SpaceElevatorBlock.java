package com.powerfactor.quantum;

import com.powerfactor.registry.ModQuantum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Space Elevator anchor. Use to open the console; sneak-use a finished elevator to ride the climber. */
public class SpaceElevatorBlock extends QuantumMachineBlock {
    public SpaceElevatorBlock(Properties properties) {
        super(() -> ModQuantum.SPACE_ELEVATOR_BE.get(), properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown() && level.getBlockEntity(pos) instanceof SpaceElevatorBlockEntity anchor && anchor.complete()) {
            if (!level.isClientSide()) anchor.ride(player);
            return InteractionResult.SUCCESS;
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }
}
