package com.powerfactor.fission;

import com.powerfactor.registry.ModFission;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A fuel rod. Its burnup (energy released, FE) is kept on the rod; at its rated burnup it becomes a Spent
 * Fuel Rod. MOX rods (plutonium in depleted uranium) are a touch more reactive and hold a little more.
 */
public class FuelRodItem extends Item {
    /** Thermal FE a uranium rod releases over its life (about 40 minutes at full power). */
    public static final double LIFE = 4000 * 20 * 60 * 40;
    private final boolean mox;

    public FuelRodItem(boolean mox, Properties properties) {
        super(properties.stacksTo(1));
        this.mox = mox;
    }

    public static boolean fresh(ItemStack stack) {
        return stack.getItem() instanceof FuelRodItem;
    }

    public static boolean mox(ItemStack stack) {
        return stack.getItem() instanceof FuelRodItem f && f.mox;
    }

    public static double life(ItemStack stack) {
        return mox(stack) ? LIFE * 1.2 : LIFE;
    }

    public static double burnup(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getDoubleOr("burnup", 0);
    }

    /** Reactivity contribution: fresh fuel is hottest; fission products drag it down as it burns. */
    public static double reactivity(ItemStack stack) {
        double f = burnup(stack) / life(stack);
        return (mox(stack) ? 1.04 : 1.0) * (1 - 0.08 * f);
    }

    /** Adds burnup; true when the rod is spent. */
    public static boolean burn(ItemStack stack, double fe) {
        double b = burnup(stack) + fe;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.putDouble("burnup", b));
        return b >= life(stack);
    }

    public static ItemStack spent(ItemStack rod) {
        return new ItemStack(mox(rod) ? ModFission.SPENT_MOX_ROD.get() : ModFission.SPENT_FUEL_ROD.get());
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return burnup(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return (int) Math.round(13 * (1 - burnup(stack) / life(stack)));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return mox ? 0xB18CFF : 0x7CFF8A;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("tooltip.powerfactor.fuel_rod.burnup", String.format("%.1f", 100 * burnup(stack) / life(stack)))
                .withStyle(ChatFormatting.GRAY));
    }
}
