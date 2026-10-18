package com.powerfactor.elevator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Elevator: stand on it and jump to rise to the next elevator of the same colour above, sneak to drop to the
 * next one below (up to 32 blocks, with headroom at the far end). Dye it to make separate shafts.
 */
public class ElevatorBlock extends Block {
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);
    public static final int RANGE = 32;

    public ElevatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(COLOR, DyeColor.WHITE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COLOR);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        DyeColor dye = stack.get(net.minecraft.core.component.DataComponents.DYE);
        if (dye != null && state.getValue(COLOR) != dye) {
            if (!level.isClientSide()) {
                level.setBlockAndUpdate(pos, state.setValue(COLOR, dye));
                if (!player.getAbilities().instabuild) stack.shrink(1);
                level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1F, 1F);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    /** The next elevator of this colour up (or down) with two clear blocks above it, or null. */
    public static BlockPos target(BlockGetter level, BlockPos from, boolean up) {
        BlockState here = level.getBlockState(from);
        if (!(here.getBlock() instanceof ElevatorBlock)) return null;
        DyeColor color = here.getValue(COLOR);
        for (int d = up ? 1 : -1; Math.abs(d) <= RANGE; d += up ? 1 : -1) {
            BlockPos p = from.above(d);
            if (level.isOutsideBuildHeight(p)) return null;
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof ElevatorBlock && s.getValue(COLOR) == color && clear(level, p.above()) && clear(level, p.above(2))) return p;
        }
        return null;
    }

    private static boolean clear(BlockGetter level, BlockPos p) {
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty();
    }

    /** Server: move the player standing on {@code from} to the next floor. */
    public static boolean ride(ServerPlayer player, boolean up) {
        BlockPos from = BlockPos.containing(player.getX(), player.getY() - 0.3, player.getZ());
        ServerLevel level = player.level();
        if (player.distanceToSqr(from.getX() + 0.5, from.getY() + 1, from.getZ() + 0.5) > 4) return false;
        BlockPos to = target(level, from, up);
        if (to == null) return false;
        int color = level.getBlockState(from).getValue(COLOR).getTextureDiffuseColor();
        player.teleportTo(player.getX(), to.getY() + 1, player.getZ());
        player.setDeltaMovement(player.getDeltaMovement().multiply(1, 0, 1));
        player.resetFallDistance();
        var dust = new DustParticleOptions(color & 0xFFFFFF, 1.2F);
        for (BlockPos at : new BlockPos[] {from, to}) {
            level.sendParticles(dust, at.getX() + 0.5, at.getY() + 1.1, at.getZ() + 0.5, 16, 0.3, 0.05, 0.3, 0);
        }
        level.playSound(null, to, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.7F, up ? 1.5F : 1.0F);
        return true;
    }
}
