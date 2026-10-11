package com.powerfactor.item;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Electric chainsaw: an axe that runs on FE and fells a whole tree from one cut (sneak to cut a
 * single log). Out of charge, it's a lump of plastic.
 */
public class ChainsawItem extends PoweredItem {
    public static final int CAPACITY = 120_000, PER_LOG = 60, PER_HIT = 100;
    public static final float SPEED = 14F, UNPOWERED_SPEED = 0.6F;

    public ChainsawItem(Properties properties) {
        super(properties);
    }

    public static Properties properties(Properties p) {
        HolderGetter<Block> blocks = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK);
        return p.component(DataComponents.TOOL, new Tool(List.of(
                        Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_AXE), SPEED),
                        Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.LEAVES), SPEED * 1.5F)), 1.0F, 0, true))
                .attributes(ItemAttributeModifiers.builder()
                        .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 7.0, AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND)
                        .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -3.0, AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND)
                        .build());
    }

    @Override
    public int capacity() {
        return CAPACITY;
    }

    public static int perLog(ItemStack stack) {
        return ToolModules.cost(stack, PER_LOG);
    }

    public static boolean powered(ItemStack stack) {
        return energy(stack) >= perLog(stack);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity owner) {
        if (!level.isClientSide() && state.getDestroySpeed(level, pos) != 0 && owner instanceof Player player) {
            drain(stack, perLog(stack), player);
        }
        return true;
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker instanceof Player player) {
            drain(stack, ToolModules.cost(stack, PER_HIT), player);
        }
    }

    /** Keeps the item model's "running" flag in step with the charge, for the moving chain; and the magnet module. */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        if (level.getGameTime() % 4 == 0 && owner instanceof Player player && ToolModules.count(stack, DrillModule.Type.MAGNET) > 0) {
            ToolModules.magnet(level, player);
        }
        if (level.getGameTime() % 10 != 0) {
            return;
        }
        boolean on = powered(stack);
        CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        boolean shown = cmd != null && !cmd.flags().isEmpty() && cmd.flags().getFirst();
        if (on != shown) {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(on), List.of(), List.of()));
        }
    }

    @Override
    public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
        return !newStack.is(oldStack.getItem());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        builder.accept(Component.translatable("tooltip.powerfactor.chainsaw", perLog(stack)).withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.powerfactor.chainsaw.sneak").withStyle(ChatFormatting.DARK_GRAY));
    }
}
