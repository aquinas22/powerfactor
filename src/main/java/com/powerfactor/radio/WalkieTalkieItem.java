package com.powerfactor.radio;

import com.powerfactor.item.PoweredItem;
import com.powerfactor.registry.ModComponents;
import com.powerfactor.registry.ModSounds;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * Walkie-talkie. Hold it and talk (with Simple Voice Chat) to be heard by everyone else carrying a
 * walkie-talkie on the same channel, through a radio speaker. Use to change channel; sneak-use to
 * turn it on or off. Transmitting uses a little charge.
 */
public class WalkieTalkieItem extends PoweredItem {
    public static final int CAPACITY = 40_000, CHANNELS = 16, PER_PACKET = 2;

    public WalkieTalkieItem(Properties properties) {
        super(properties.component(ModComponents.RADIO_CHANNEL.get(), 1).component(ModComponents.RADIO_ON.get(), true));
    }

    @Override
    public int capacity() {
        return CAPACITY;
    }

    public static int channel(ItemStack stack) {
        return stack.getOrDefault(ModComponents.RADIO_CHANNEL.get(), 1);
    }

    public static boolean on(ItemStack stack) {
        return stack.getOrDefault(ModComponents.RADIO_ON.get(), true);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            if (player.isShiftKeyDown()) {
                boolean now = !on(stack);
                stack.set(ModComponents.RADIO_ON.get(), now);
                player.sendOverlayMessage(Component.translatable(now ? "message.powerfactor.walkie.on" : "message.powerfactor.walkie.off",
                        channel(stack)));
            } else {
                int ch = channel(stack) % CHANNELS + 1;
                stack.set(ModComponents.RADIO_CHANNEL.get(), ch);
                stack.set(ModComponents.RADIO_ON.get(), true);
                player.sendOverlayMessage(Component.translatable("message.powerfactor.walkie.channel", ch));
            }
            level.playSound(null, player.blockPosition(), ModSounds.RADIO_KEY.get(), SoundSource.PLAYERS, 0.5F, 1.4F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        builder.accept(Component.translatable(on(stack) ? "tooltip.powerfactor.walkie.channel" : "tooltip.powerfactor.walkie.off", channel(stack))
                .withStyle(on(stack) ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
        builder.accept(Component.translatable("tooltip.powerfactor.walkie.help").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.powerfactor.walkie.needs_voicechat").withStyle(ChatFormatting.DARK_GRAY));
    }
}
