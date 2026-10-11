package com.powerfactor.item;

import com.powerfactor.registry.ModComponents;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A tool with an FE battery: the energy bar, the tooltip line and draining. Charges in any FE charger. */
public abstract class PoweredItem extends Item {
    protected PoweredItem(Properties properties) {
        super(properties.stacksTo(1).component(ModComponents.ENERGY.get(), 0));
    }

    /** Base capacity, before battery modules. */
    public abstract int capacity();

    public int capacity(ItemStack stack) {
        return ToolModules.capacity(stack, capacity());
    }

    public static int energy(ItemStack stack) {
        return stack.getOrDefault(ModComponents.ENERGY.get(), 0);
    }

    /** Take up to {@code amount} FE; creative players run free. Returns whether the full amount was there. */
    public static boolean drain(ItemStack stack, int amount, Player player) {
        if (player != null && player.getAbilities().instabuild) {
            return true;
        }
        int have = energy(stack);
        stack.set(ModComponents.ENERGY.get(), Math.max(0, have - amount));
        return have >= amount;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13F * Mth.clamp((float) energy(stack) / capacity(stack), 0, 1));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xFF5A4A;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.powerfactor.energy", String.format("%,d", energy(stack)), String.format("%,d", capacity(stack)))
                .withColor(0xFF5A4A));
        ToolModules.tooltip(stack, builder);
    }
}
