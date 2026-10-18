package com.powerfactor.charging;

import com.powerfactor.quantum.QuantumMachineBlock;
import com.powerfactor.registry.ModUtility;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Right-click: status (and for the dimensional charger, bind/unbind yourself). Range Upgrades widen the wireless one. */
public class WirelessChargerBlock extends QuantumMachineBlock {
    public WirelessChargerBlock(Supplier<? extends BlockEntityType<? extends com.powerfactor.machine.MachineBlockEntity>> type, Properties properties) {
        super(type, properties);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModUtility.RANGE_UPGRADE.get())) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof WirelessChargerBlockEntity be && be.addCard()) {
            stack.consume(1, player);
            player.sendOverlayMessage(be.describe());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof WirelessChargerBlockEntity be) {
            if (be.dimensional()) {
                boolean now = be.toggleBound(player.getUUID());
                player.sendOverlayMessage(Component.translatable(now ? "gui.powerfactor.charger.bound" : "gui.powerfactor.charger.unbound"));
            } else {
                player.sendOverlayMessage(be.describe());
            }
        }
        return InteractionResult.SUCCESS;
    }
}
