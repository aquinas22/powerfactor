package com.powerfactor.storage;

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
import net.neoforged.neoforge.transfer.item.ItemResource;

/** A portable storage cell. Goes in a Drive Bay; its contents travel with the item. */
public class StorageCellItem extends Item {
    private final CellTier tier;

    public StorageCellItem(CellTier tier, Properties properties) {
        super(properties.stacksTo(1).rarity(tier.rarity));
        this.tier = tier;
    }

    public CellTier tier() {
        return tier;
    }

    public static CellContents contents(ItemStack stack) {
        return stack.getOrDefault(ModComponents.CELL_CONTENTS.get(), CellContents.EMPTY);
    }

    public CellInventory<ItemResource> open(ItemStack stack) {
        CellInventory<ItemResource> inv = new CellInventory<>(tier.bytes, tier.types);
        inv.load(contents(stack).asMap());
        return inv;
    }

    public static void save(ItemStack stack, CellInventory<ItemResource> inv) {
        if (inv.types() == 0) stack.remove(ModComponents.CELL_CONTENTS.get());
        else stack.set(ModComponents.CELL_CONTENTS.get(), CellContents.of(inv.contents()));
    }

    /** Used fraction of the byte budget, for the item bar and drive LEDs. */
    public float fill(ItemStack stack) {
        CellContents c = contents(stack);
        long used = c.entries().size() * CellInventory.BYTES_PER_TYPE + (c.items() + 7) / 8;
        return Mth.clamp((float) used / tier.bytes, 0, 1);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return stack.has(ModComponents.CELL_CONTENTS.get());
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
        CellContents c = contents(stack);
        long used = c.entries().size() * CellInventory.BYTES_PER_TYPE + (c.items() + 7) / 8;
        builder.accept(Component.translatable("tooltip.powerfactor.cell.bytes", String.format("%,d", used), String.format("%,d", tier.bytes))
                .withColor(tier.color));
        builder.accept(Component.translatable("tooltip.powerfactor.cell.types", c.entries().size(), tier.types).withStyle(ChatFormatting.GRAY));
        int shown = 0;
        for (CellContents.Entry e : c.entries()) {
            if (shown++ >= 5) {
                builder.accept(Component.translatable("tooltip.powerfactor.cell.more", c.entries().size() - 5).withStyle(ChatFormatting.DARK_GRAY));
                break;
            }
            builder.accept(Component.literal(String.format("%,d × ", e.count())).append(e.resource().getHoverName()).withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}
