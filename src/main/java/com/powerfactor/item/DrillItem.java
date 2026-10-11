package com.powerfactor.item;

import com.powerfactor.PowerFactorConfig;
import com.powerfactor.registry.ModComponents;
import org.jspecify.annotations.Nullable;
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
    /** Pickaxe blocks. */
    public static final float SPEED = 12F;
    /** Shovel blocks: fast enough to break dirt, sand and grass instantly (speed >= 30 x hardness). */
    public static final float DIG_SPEED = 30F;
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
                        Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_SHOVEL), DIG_SPEED)),
                        1.0F, 0, true));
    }

    public static int capacity() {
        return PowerFactorConfig.SPEC.isLoaded() ? PowerFactorConfig.DRILL_CAPACITY.getAsInt() : 100_000;
    }

    /** Battery modules add the base capacity again per module. */
    public static int capacity(ItemStack stack) {
        return capacity() * (1 + modules(stack, DrillModule.Type.BATTERY));
    }

    /** How many of a module type are installed. */
    public static int modules(ItemStack stack, DrillModule.Type type) {
        return ToolModules.count(stack, type);
    }

    /** Silk Touch and Fortune modules become real enchantments, so every block and loot table honors them. */
    public static void applyModules(ItemStack stack, net.minecraft.core.RegistryAccess registries) {
        var enchantments = registries.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        var silk = enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH);
        var fortune = enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FORTUNE);
        int silkLevel = modules(stack, DrillModule.Type.SILK) > 0 ? 1 : 0;
        int fortuneLevel = silkLevel > 0 ? 0 : Math.min(3, modules(stack, DrillModule.Type.FORTUNE));
        net.minecraft.world.item.enchantment.EnchantmentHelper.updateEnchantments(stack, mutable -> {
            mutable.set(silk, silkLevel);
            mutable.set(fortune, fortuneLevel);
        });
        int energy = energy(stack);
        if (energy > capacity(stack)) {
            stack.set(ModComponents.ENERGY.get(), capacity(stack));
        }
    }

    @Override
    public net.minecraft.world.InteractionResult use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                sp.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, p) -> new DrillMenu(id, inv, hand),
                        Component.translatable("gui.powerfactor.drill.modules")), buf -> buf.writeEnum(hand));
            }
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        return super.use(level, player, hand);
    }

    /** Magnet module: pull nearby drops toward whoever's carrying the drill. */
    @Override
    public void inventoryTick(ItemStack stack, net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.Entity owner,
            net.minecraft.world.entity.@Nullable EquipmentSlot slot) {
        // Keep the item model's "running" flag in step with the charge: a charged drill's bit spins.
        if (level.getGameTime() % 10 == 0) {
            boolean on = powered(stack);
            var cmd = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
            boolean shown = cmd != null && !cmd.flags().isEmpty() && cmd.flags().getFirst();
            if (on != shown) {
                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                        new net.minecraft.world.item.component.CustomModelData(List.of(), List.of(on), List.of(), List.of()));
            }
        }
        if (level.getGameTime() % 4 != 0 || !(owner instanceof Player player) || modules(stack, DrillModule.Type.MAGNET) == 0) {
            return;
        }
        ToolModules.magnet(level, player);
    }

    public static int costPerBlock() {
        return PowerFactorConfig.SPEC.isLoaded() ? PowerFactorConfig.DRILL_FE_PER_BLOCK.getAsInt() : 100;
    }

    public static int energy(ItemStack stack) {
        return stack.getOrDefault(ModComponents.ENERGY.get(), 0);
    }

    public static int costPerBlock(ItemStack stack) {
        return ToolModules.cost(stack, costPerBlock());
    }

    public static boolean powered(ItemStack stack) {
        return energy(stack) >= costPerBlock(stack);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity owner) {
        if (!level.isClientSide() && state.getDestroySpeed(level, pos) != 0
                && !(owner instanceof Player player && player.getAbilities().instabuild)) {
            stack.set(ModComponents.ENERGY.get(), Math.max(0, energy(stack) - costPerBlock(stack)));
        }
        return true;
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
        return 0xFF5A4A; // FE red
    }

    @Override
    public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
        return !newStack.is(oldStack.getItem());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.powerfactor.energy", String.format("%,d", energy(stack)), String.format("%,d", capacity(stack)))
                .withColor(0xFF5A4A));
        builder.accept(Component.translatable("tooltip.powerfactor.drill.cost", costPerBlock(stack)).withStyle(ChatFormatting.GRAY));
        ToolModules.tooltip(stack, builder);
        builder.accept(Component.translatable("tooltip.powerfactor.drill.open").withStyle(ChatFormatting.DARK_GRAY));
    }
}
