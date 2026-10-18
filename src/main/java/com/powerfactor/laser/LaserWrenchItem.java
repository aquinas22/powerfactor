package com.powerfactor.laser;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Laser Wrench: click a node to pick it up, click another to fire the link between them (or cut it,
 * if they're already linked). Sneak-use in the air to drop the selection.
 */
public class LaserWrenchItem extends Item {
    private static final String KEY = "laser_from";

    public LaserWrenchItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static @Nullable BlockPos selected(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains(KEY) ? BlockPos.of(tag.getLongOr(KEY, 0)) : null;
    }

    private static void select(ItemStack stack, @Nullable BlockPos pos) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> {
            if (pos == null) t.remove(KEY);
            else t.putLong(KEY, pos.asLong());
        });
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        BlockPos pos = ctx.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof LaserNodeBlockEntity)) return InteractionResult.PASS;
        if (level.isClientSide() || player == null) return InteractionResult.SUCCESS;
        ItemStack stack = ctx.getItemInHand();
        BlockPos from = selected(stack);
        if (from == null || from.equals(pos) || !(level.getBlockEntity(from) instanceof LaserNodeBlockEntity)) {
            select(stack, pos);
            player.sendOverlayMessage(Component.translatable("gui.powerfactor.laser.selected").withStyle(ChatFormatting.AQUA));
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1F, 1.6F);
            return InteractionResult.SUCCESS;
        }
        Component why = LaserNodeBlockEntity.canLink(level, from, pos);
        if (why != null) {
            player.sendOverlayMessage(why.copy().withStyle(ChatFormatting.RED));
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1F, 0.6F);
            return InteractionResult.FAIL;
        }
        boolean linked = LaserNodeBlockEntity.toggleLink(level, from, pos);
        select(stack, null);
        player.sendOverlayMessage(Component.translatable(linked ? "gui.powerfactor.laser.linked" : "gui.powerfactor.laser.unlinked")
                .withStyle(linked ? ChatFormatting.GREEN : ChatFormatting.GOLD));
        level.playSound(null, pos, linked ? SoundEvents.BEACON_POWER_SELECT : SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1F, 1.8F);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && selected(stack) != null) {
            if (!level.isClientSide()) {
                select(stack, null);
                player.sendOverlayMessage(Component.translatable("gui.powerfactor.laser.cleared"));
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return selected(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("tooltip.powerfactor.laser_wrench").withStyle(ChatFormatting.GRAY));
        BlockPos sel = selected(stack);
        if (sel != null) out.accept(Component.translatable("tooltip.powerfactor.laser_wrench.selected", sel.toShortString()).withStyle(ChatFormatting.AQUA));
    }
}
