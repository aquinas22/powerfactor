package com.powerfactor.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** The PowerFactor Handbook: opens the in-game manual. */
public class HandbookItem extends Item {
    public static Runnable open = () -> {};

    public HandbookItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            open.run();
        }
        return InteractionResult.SUCCESS;
    }
}
