package com.powerfactor.item;

import com.powerfactor.grid.PylonBlockEntity;
import com.powerfactor.registry.ModComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Strings a three-phase HV conductor between two pylons: use on the first, then on the second.
 * Sneak-use on a pylon cuts all its conductors.
 */
public class ConductorSpoolItem extends Item {
    public ConductorSpoolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (!(level.getBlockEntity(pos) instanceof PylonBlockEntity pylon) || player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            for (BlockPos other : java.util.List.copyOf(pylon.links())) {
                pylon.unlink(other);
                if (level.getBlockEntity(other) instanceof PylonBlockEntity p) {
                    p.unlink(pos);
                }
            }
            player.sendOverlayMessage(Component.translatable("message.powerfactor.spool.cut"));
            return InteractionResult.SUCCESS;
        }
        BlockPos first = stack.get(ModComponents.LINK_START.get());
        if (first == null || first.equals(pos)) {
            stack.set(ModComponents.LINK_START.get(), pos.immutable());
            player.sendOverlayMessage(Component.translatable("message.powerfactor.spool.start", pos.toShortString()));
            return InteractionResult.SUCCESS;
        }
        stack.remove(ModComponents.LINK_START.get());
        double dist = Math.sqrt(first.distSqr(pos));
        if (dist > PylonBlockEntity.MAX_SPAN || !(level.getBlockEntity(first) instanceof PylonBlockEntity a)) {
            player.sendOverlayMessage(Component.translatable("message.powerfactor.spool.too_far", PylonBlockEntity.MAX_SPAN));
            return InteractionResult.FAIL;
        }
        if (a.link(pos) && pylon.link(first)) {
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
            player.sendOverlayMessage(Component.translatable("message.powerfactor.spool.linked", (int) dist));
        } else {
            a.unlink(pos);
            player.sendOverlayMessage(Component.translatable("message.powerfactor.spool.full"));
        }
        return InteractionResult.SUCCESS;
    }
}
