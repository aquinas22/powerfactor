package com.powerfactor.item;

import com.powerfactor.PowerFactorConfig;
import com.powerfactor.registry.ModComponents;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Battery-powered mining drill: diamond-tier pickaxe and shovel in one, running on FE instead of
 * durability. Flat, it's barely faster than a fist.
 */
public class DrillItem extends Item {
    public static final float SPEED = 10F;
    public static final float UNPOWERED_SPEED = 0.5F;

    public DrillItem(Properties properties) {
        super(properties);
    }

    public static Properties properties(Properties p) {
        HolderGetter<Block> blocks = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK);
        return p.stacksTo(1)
                .component(ModComponents.ENERGY.get(), 0)
                .component(DataComponents.TOOL, new Tool(List.of(
                        Tool.Rule.deniesDrops(blocks.getOrThrow(BlockTags.INCORRECT_FOR_DIAMOND_TOOL)),
                        Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_PICKAXE), SPEED),
                        Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_SHOVEL), SPEED)),
                        1.0F, 0, true));
    }

    public static int capacity() {
        return PowerFactorConfig.SPEC.isLoaded() ? PowerFactorConfig.DRILL_CAPACITY.getAsInt() : 100_000;
    }

    public static int costPerBlock() {
        return PowerFactorConfig.SPEC.isLoaded() ? PowerFactorConfig.DRILL_FE_PER_BLOCK.getAsInt() : 100;
    }

    public static int energy(ItemStack stack) {
        return stack.getOrDefault(ModComponents.ENERGY.get(), 0);
    }

    public static boolean powered(ItemStack stack) {
        return energy(stack) >= costPerBlock();
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity owner) {
        if (!level.isClientSide() && state.getDestroySpeed(level, pos) != 0
                && !(owner instanceof Player player && player.getAbilities().instabuild)) {
            stack.set(ModComponents.ENERGY.get(), Math.max(0, energy(stack) - costPerBlock()));
        }
        return true;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13F * Mth.clamp((float) energy(stack) / capacity(), 0, 1));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xFF5A4A; // FE red
    }

    @Override
    public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
        return !newStack.is(oldStack.getItem());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.powerfactor.energy", String.format("%,d", energy(stack)), String.format("%,d", capacity()))
                .withColor(0xFF5A4A));
        builder.accept(Component.translatable("tooltip.powerfactor.drill.cost", costPerBlock()).withStyle(ChatFormatting.GRAY));
    }
}
