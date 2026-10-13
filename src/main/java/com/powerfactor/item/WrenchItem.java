package com.powerfactor.item;

import com.powerfactor.PowerFactor;
import com.powerfactor.grid.WireBlock;
import com.powerfactor.logistics.PipeBlock;
import com.powerfactor.registry.ModSounds;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wrench: turns PowerFactor machines to face another way, strips the rubber coat off a wire, and
 * (sneaking) dismantles PowerFactor blocks straight into your inventory, contents and all.
 * Pipes it leaves to their own right-click (cycling arms).
 */
public class WrenchItem extends Item {
    public WrenchItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = context.getPlayer();
        if (player == null || !PowerFactor.MODID.equals(state.getBlock().builtInRegistryHolder().key().identifier().getNamespace())) {
            return InteractionResult.PASS;
        }
        if (!level.mayInteract(player, pos) || player.blockActionRestricted(level, pos, player.gameMode())) return InteractionResult.FAIL;
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide() && !dismantle(level, pos, state, player)) return InteractionResult.FAIL;
            return InteractionResult.SUCCESS;
        }
        if (state.getBlock() instanceof PipeBlock) {
            return InteractionResult.PASS;
        }
        if (state.getBlock() instanceof WireBlock wire) {
            if (!level.isClientSide() && wire.strip(level, pos, state, player)) {
                level.playSound(null, pos, ModSounds.WRENCH.get(), SoundSource.BLOCKS, 0.7F, 1.2F);
            }
            return InteractionResult.SUCCESS;
        }
        BlockState turned = state.rotate(level, pos, Rotation.CLOCKWISE_90);
        if (turned == state) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            level.setBlock(pos, turned, Block.UPDATE_ALL);
            level.playSound(null, pos, ModSounds.WRENCH.get(), SoundSource.BLOCKS, 0.7F, 0.9F + level.getRandom().nextFloat() * 0.2F);
        }
        return InteractionResult.SUCCESS;
    }

    /** Break the block as if mined, with its drops going straight to the player. */
    private static boolean dismantle(Level level, BlockPos pos, BlockState state, Player player) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return false;
        }
        var half = net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF;
        if (state.hasProperty(half) && state.getValue(half) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
            pos = pos.below();
            state = level.getBlockState(pos);
        }
        if (!level.mayInteract(player, pos) || net.neoforged.neoforge.common.CommonHooks.fireBlockBreak(level, player.gameMode(), player, pos, state).isCanceled()) return false;
        var be = level.getBlockEntity(pos);
        var drops = Block.getDrops(state, server, pos, be, player, player.getMainHandItem());
        if (!level.destroyBlock(pos, false, player)) return false;
        for (ItemStack drop : drops) {
            if (!player.getInventory().add(drop)) {
                player.drop(drop, false);
            }
        }
        level.playSound(null, pos, ModSounds.WRENCH.get(), SoundSource.BLOCKS, 0.9F, 0.75F);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.powerfactor.wrench.rotate").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.powerfactor.wrench.strip").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.powerfactor.wrench.dismantle").withStyle(ChatFormatting.DARK_GRAY));
    }
}
