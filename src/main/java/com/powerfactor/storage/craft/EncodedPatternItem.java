package com.powerfactor.storage.craft;

import com.powerfactor.registry.ModComponents;
import com.powerfactor.registry.ModStorage;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** An encoded pattern. Sneak-use in the air wipes it back to a blank pattern. */
public class EncodedPatternItem extends Item {
    private final boolean processing;

    public EncodedPatternItem(boolean processing, Properties properties) {
        super(properties.stacksTo(1));
        this.processing = processing;
    }

    public static @Nullable PatternData data(ItemStack stack) {
        return stack.getItem() instanceof EncodedPatternItem ? stack.get(ModComponents.PATTERN.get()) : null;
    }

    public static boolean crafting(ItemStack stack) {
        PatternData p = data(stack);
        return p != null && !p.processing();
    }

    public static boolean processing(ItemStack stack) {
        PatternData p = data(stack);
        return p != null && p.processing();
    }

    public static ItemStack encode(PatternData data) {
        ItemStack stack = new ItemStack(data.processing() ? ModStorage.PROCESSING_PATTERN.get() : ModStorage.CRAFTING_PATTERN.get());
        stack.set(ModComponents.PATTERN.get(), data);
        return stack;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!level.isClientSide()) player.setItemInHand(hand, new ItemStack(ModStorage.BLANK_PATTERN.get()));
        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getName(ItemStack stack) {
        PatternData p = data(stack);
        if (p == null || p.primaryOutput().isEmpty()) return super.getName(stack);
        return Component.translatable(processing ? "item.powerfactor.processing_pattern.of" : "item.powerfactor.crafting_pattern.of",
                p.primaryOutput().getHoverName());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        PatternData p = data(stack);
        if (p == null) {
            builder.accept(Component.translatable("tooltip.powerfactor.pattern.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        builder.accept(Component.translatable("tooltip.powerfactor.pattern.makes").withStyle(ChatFormatting.GOLD));
        for (var e : p.outputMap().entrySet()) {
            builder.accept(Component.literal("  " + e.getValue() + " × ").append(e.getKey().getHoverName()).withStyle(ChatFormatting.YELLOW));
        }
        builder.accept(Component.translatable("tooltip.powerfactor.pattern.from").withStyle(ChatFormatting.DARK_AQUA));
        for (var e : p.inputMap().entrySet()) {
            builder.accept(Component.literal("  " + e.getValue() + " × ").append(e.getKey().getHoverName()).withStyle(ChatFormatting.AQUA));
        }
        builder.accept(Component.translatable(processing ? "tooltip.powerfactor.pattern.processing" : "tooltip.powerfactor.pattern.crafting")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
