package com.powerfactor.storage.fluid;

import com.powerfactor.registry.ModComponents;
import com.powerfactor.storage.core.CellInventory;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/** A portable fluid cell. Goes in a Drive Bay beside item cells; its contents travel with the item. */
public class FluidCellItem extends Item {
    private final FluidCellTier tier;

    public FluidCellItem(FluidCellTier tier, Properties properties) {
        super(properties.stacksTo(1).rarity(tier.rarity));
        this.tier = tier;
    }

    public FluidCellTier tier() {
        return tier;
    }

    public static FluidCellContents contents(ItemStack stack) {
        return stack.getOrDefault(ModComponents.FLUID_CELL_CONTENTS.get(), FluidCellContents.EMPTY);
    }

    public CellInventory<FluidResource> open(ItemStack stack) {
        CellInventory<FluidResource> inv = new CellInventory<>(tier.bytes, tier.types, FluidCellTier.MB_PER_BYTE);
        inv.load(contents(stack).asMap());
        return inv;
    }

    public static void save(ItemStack stack, CellInventory<FluidResource> inv) {
        if (inv.types() == 0) stack.remove(ModComponents.FLUID_CELL_CONTENTS.get());
        else stack.set(ModComponents.FLUID_CELL_CONTENTS.get(), FluidCellContents.of(inv.contents()));
    }

    public float fill(ItemStack stack) {
        FluidCellContents c = contents(stack);
        long used = c.entries().size() * CellInventory.BYTES_PER_TYPE + (c.total() + FluidCellTier.MB_PER_BYTE - 1) / FluidCellTier.MB_PER_BYTE;
        return Mth.clamp((float) used / tier.bytes, 0, 1);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return stack.has(ModComponents.FLUID_CELL_CONTENTS.get());
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13 * fill(stack));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float f = fill(stack);
        return f > 0.9F ? 0xFF5050 : f > 0.7F ? 0xFFC040 : tier.color & 0xFFFFFF;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        FluidCellContents c = contents(stack);
        builder.accept(Component.translatable("tooltip.powerfactor.fluid_cell.capacity", String.format("%,d", tier.buckets()), tier.types)
                .withColor(tier.color));
        int shown = 0;
        for (FluidCellContents.Entry e : c.entries()) {
            if (shown++ >= 5) {
                builder.accept(Component.translatable("tooltip.powerfactor.cell.more", c.entries().size() - 5).withStyle(ChatFormatting.DARK_GRAY));
                break;
            }
            builder.accept(Component.literal(String.format("%,d mB ", e.amount())).append(e.fluid().toStack(1).getHoverName())
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}
