package com.powerfactor.quantum;

import com.mojang.serialization.MapCodec;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.registry.ModQuantum;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Teleporter pad. Use an Entangled Qubit Pair on one pad, then on another: the pair is consumed and
 * the two pads are linked. Right-click with an empty hand to read the link, cost and charge.
 */
public class TeleporterBlock extends MachineBlock {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 4, 16);
    private static final String DIM = "teleporter_dim", POS = "teleporter_pos";

    public TeleporterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        throw new UnsupportedOperationException("not data-driven");
    }

    @Override
    protected BlockEntityType<? extends MachineBlockEntity> blockEntityType() {
        return ModQuantum.TELEPORTER_BE.get();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (!stack.is(ModQuantum.ENTANGLED_PAIR.get())) return super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (!(level instanceof ServerLevel server) || !(level.getBlockEntity(pos) instanceof TeleporterBlockEntity here)) return InteractionResult.SUCCESS;
        GlobalPos self = GlobalPos.of(level.dimension(), pos);
        GlobalPos bound = bound(stack);
        if (bound == null || bound.equals(self)) {
            // First half: remember this pad on the pair.
            CompoundTag tag = new CompoundTag();
            tag.putString(DIM, level.dimension().identifier().toString());
            tag.putLong(POS, pos.asLong());
            CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.merge(tag));
            player.sendOverlayMessage(Component.translatable("gui.powerfactor.teleporter.bound").withStyle(ChatFormatting.LIGHT_PURPLE));
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1F, 1.4F);
            return InteractionResult.SUCCESS;
        }
        ServerLevel other = server.getServer().getLevel(bound.dimension());
        if (other == null) return InteractionResult.FAIL;
        other.getChunk(bound.pos());
        if (!(other.getBlockEntity(bound.pos()) instanceof TeleporterBlockEntity there)) {
            clear(stack);
            player.sendOverlayMessage(Component.translatable("gui.powerfactor.teleporter.missing").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        // Each pad drops any older link; the old partners are told the link is gone when used.
        here.link(bound);
        there.link(self);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        else clear(stack);
        player.sendOverlayMessage(Component.translatable("gui.powerfactor.teleporter.linked", bound.pos().toShortString())
                .withStyle(ChatFormatting.AQUA));
        level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.BLOCKS, 1F, 1.5F);
        return InteractionResult.SUCCESS;
    }

    static GlobalPos bound(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(DIM) || !tag.contains(POS)) return null;
        return GlobalPos.of(ResourceKey.create(Registries.DIMENSION, Identifier.parse(tag.getStringOr(DIM, "minecraft:overworld"))),
                BlockPos.of(tag.getLongOr(POS, 0)));
    }

    private static void clear(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> {
            t.remove(DIM);
            t.remove(POS);
        });
    }
}
