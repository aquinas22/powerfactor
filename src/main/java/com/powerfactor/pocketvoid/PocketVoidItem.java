package com.powerfactor.pocketvoid;

import com.powerfactor.registry.ModVoid;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Pocket Void: a pocket-sized hole in the world, held open by Essence of Bedrock. Nine slots, each one kind of
 * item by the thousand. Use it to open; use it on a block to build with the selected slot; it can pull in what
 * you pick up, and swallow the overflow of junk.
 */
public class PocketVoidItem extends Item {
    private final int capacity;

    public PocketVoidItem(int capacity, Properties properties) {
        super(properties);
        this.capacity = capacity;
    }

    public int capacity() {
        return capacity;
    }

    public static VoidContents contents(ItemStack stack) {
        return stack.getOrDefault(ModVoid.CONTENTS.get(), VoidContents.EMPTY);
    }

    public static void set(ItemStack stack, VoidContents c) {
        stack.set(ModVoid.CONTENTS.get(), c);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) {
            int slot = sp.getInventory().getSelectedSlot();
            sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new PocketVoidMenu(id, inv, slot), player.getItemInHand(hand).getHoverName()),
                    buf -> buf.writeVarInt(slot));
        }
        return InteractionResult.SUCCESS;
    }

    /** Builds with the selected slot, if it holds a block. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        ItemStack self = context.getItemInHand();
        VoidContents c = contents(self);
        var s = c.slot(c.selected());
        if (player == null || s.item().isEmpty() || s.count() <= 0 || !(s.item().get().item().value() instanceof BlockItem block)) {
            return InteractionResult.PASS;
        }
        ItemStack one = s.item().get().create();
        var result = block.place(new BlockPlaceContext(context.getLevel(), player, context.getHand(), one,
                new net.minecraft.world.phys.BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), context.isInside())));
        if (result.consumesAction() && !context.getLevel().isClientSide() && !player.getAbilities().instabuild) {
            set(self, c.take(c.selected(), 1).contents());
        }
        return result;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        VoidContents c = contents(stack);
        builder.accept(Component.translatable("tooltip.powerfactor.pocket_void.capacity", String.format("%,d", capacity)).withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.powerfactor.pocket_void.mode",
                Component.translatable("gui.powerfactor.pocket_void.mode." + c.mode().name().toLowerCase(java.util.Locale.ROOT))).withColor(0xB18CFF));
        for (int i = 0; i < VoidContents.SLOTS; i++) {
            var s = c.slot(i);
            if (s.item().isEmpty()) continue;
            builder.accept(Component.literal((i == c.selected() ? "▶ " : "  ") + String.format("%,d", s.count()) + " × ")
                    .append(s.display().getHoverName()).withStyle(s.locked() ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return contents(stack).mode() != VoidContents.Mode.OFF;
    }
}
