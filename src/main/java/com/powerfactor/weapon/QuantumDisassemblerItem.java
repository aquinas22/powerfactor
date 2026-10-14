package com.powerfactor.weapon;

import com.powerfactor.item.PoweredItem;
import com.powerfactor.registry.ModComponents;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The Quantum Disassembler: pickaxe, axe, shovel and hoe in one FE-powered emitter. Sneak-use in the
 * air cycles Single, 3x3, 5x5 and Vein modes; the targeted area is outlined before you mine. Drops
 * rematerialise in your inventory.
 */
public class QuantumDisassemblerItem extends PoweredItem {
    public enum Mode { SINGLE, AREA_3, AREA_5, VEIN }

    public static final int FE_PER_BLOCK = 120, VEIN_LIMIT = 96;
    private static boolean breaking;

    public QuantumDisassemblerItem(Properties properties) {
        super(properties.component(DataComponents.TOOL, tool()).component(ModComponents.TOOL_MODE.get(), 0).fireResistant());
    }

    private static Tool tool() {
        var blocks = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK);
        return new Tool(List.of(
                Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_PICKAXE), 28F),
                Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_AXE), 28F),
                Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_SHOVEL), 28F),
                Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_HOE), 28F)), 1.0F, 1, true);
    }

    @Override
    public int capacity() {
        return 2_000_000;
    }

    public static Mode mode(ItemStack stack) {
        return Mode.values()[Math.clamp(stack.getOrDefault(ModComponents.TOOL_MODE.get(), 0), 0, Mode.values().length - 1)];
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return energy(stack) < FE_PER_BLOCK ? 1F : super.getDestroySpeed(stack, state) / (mode(stack) == Mode.AREA_5 ? 2.5F : mode(stack) == Mode.AREA_3 ? 1.5F : 1F);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);
        Mode next = Mode.values()[(mode(stack).ordinal() + 1) % Mode.values().length];
        stack.set(ModComponents.TOOL_MODE.get(), next.ordinal());
        if (!level.isClientSide()) {
            player.sendOverlayMessage(Component.translatable("gui.powerfactor.disassembler.mode",
                    Component.translatable("gui.powerfactor.disassembler.mode." + next.name().toLowerCase(java.util.Locale.ROOT))).withColor(0xB18CFF));
        }
        level.playSound(player, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.2F + next.ordinal() * 0.2F);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity owner) {
        if (!(owner instanceof ServerPlayer player) || !(level instanceof ServerLevel server)) return true;
        drain(stack, FE_PER_BLOCK, player);
        if (breaking || player.isShiftKeyDown()) return true;
        List<BlockPos> extra = positions(server, player, pos, state, mode(stack));
        breaking = true;
        try {
            for (BlockPos p : extra) {
                if (p.equals(pos)) continue;
                BlockState s = server.getBlockState(p);
                if (s.isAir() || s.getDestroySpeed(server, p) < 0 || !stack.isCorrectToolForDrops(s) || !drain(stack, FE_PER_BLOCK, player)) continue;
                if (player.gameMode.destroyBlock(p)) {
                    server.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX() + .5, p.getY() + .5, p.getZ() + .5, 3, .25, .25, .25, .02);
                }
            }
        } finally {
            breaking = false;
        }
        return true;
    }

    /** The blocks a mode would take, given what's targeted; the client uses this for the outline preview. */
    public static List<BlockPos> positions(BlockGetter level, Player player, BlockPos pos, BlockState state, Mode mode) {
        List<BlockPos> list = new ArrayList<>();
        switch (mode) {
            case SINGLE -> list.add(pos);
            case AREA_3, AREA_5 -> {
                int r = mode == Mode.AREA_3 ? 1 : 2;
                Vec3 look = player.getViewVector(1);
                Direction.Axis axis = Math.abs(look.y) > 0.75 ? Direction.Axis.Y : Math.abs(look.x) > Math.abs(look.z) ? Direction.Axis.X : Direction.Axis.Z;
                for (int a = -r; a <= r; a++) {
                    for (int b = -r; b <= r; b++) {
                        BlockPos p = switch (axis) {
                            case X -> pos.offset(0, a, b);
                            case Y -> pos.offset(a, 0, b);
                            case Z -> pos.offset(a, b, 0);
                        };
                        if (!level.getBlockState(p).isAir()) list.add(p);
                    }
                }
            }
            case VEIN -> {
                Set<BlockPos> seen = new HashSet<>();
                ArrayDeque<BlockPos> queue = new ArrayDeque<>();
                queue.add(pos);
                seen.add(pos);
                list.add(pos);
                while (!queue.isEmpty() && list.size() < VEIN_LIMIT) {
                    BlockPos p = queue.poll();
                    for (BlockPos n : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
                        BlockPos q = n.immutable();
                        if (seen.add(q) && level.getBlockState(q).is(state.getBlock())) {
                            list.add(q);
                            queue.add(q);
                            if (list.size() >= VEIN_LIMIT) break;
                        }
                    }
                }
            }
        }
        return list;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        builder.accept(Component.translatable("gui.powerfactor.disassembler.mode",
                Component.translatable("gui.powerfactor.disassembler.mode." + mode(stack).name().toLowerCase(java.util.Locale.ROOT))).withStyle(ChatFormatting.LIGHT_PURPLE));
        builder.accept(Component.translatable("tooltip.powerfactor.disassembler").withStyle(ChatFormatting.DARK_GRAY));
    }
}
