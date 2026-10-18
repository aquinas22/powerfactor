package com.powerfactor.util;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Wireless redstone pad: a transmitter goes on air while it is powered; a receiver outputs a full signal
 * while anything on its channel is on air. Use a dye on it to pick the channel (16 colours).
 */
public class WirelessBlock extends Block {
    public static final IntegerProperty CHANNEL = IntegerProperty.create("channel", 0, WirelessRedstone.CHANNELS - 1);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 6, 14);
    private final boolean transmitter;

    public WirelessBlock(boolean transmitter, Properties properties) {
        super(properties);
        this.transmitter = transmitter;
        registerDefaultState(stateDefinition.any().setValue(CHANNEL, 0).setValue(LIT, false));
    }

    public boolean transmitter() {
        return transmitter;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        throw new UnsupportedOperationException("not data-driven");
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHANNEL, LIT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        DyeColor color = stack.get(net.minecraft.core.component.DataComponents.DYE);
        if (color == null) return InteractionResult.TRY_WITH_EMPTY_HAND;
        int channel = color.getId();
        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(CHANNEL, channel), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 0.6F + channel * 0.06F);
            player.sendOverlayMessage(Component.translatable("gui.powerfactor.wireless.channel",
                    Component.translatable("color.minecraft." + DyeColor.byId(channel).getName())));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        GlobalPos here = GlobalPos.of(level.dimension(), pos);
        boolean on;
        if (transmitter) {
            on = level.hasNeighborSignal(pos);
            if (on) WirelessRedstone.transmit(level.getServer(), state.getValue(CHANNEL), here);
            else WirelessRedstone.silence(level.getServer(), here);
        } else {
            on = WirelessRedstone.live(level.getServer(), state.getValue(CHANNEL));
        }
        if (on != state.getValue(LIT)) {
            level.setBlock(pos, state.setValue(LIT, on), Block.UPDATE_ALL);
            if (!transmitter) level.updateNeighborsAt(pos, this);
        }
        level.scheduleTick(pos, this, 2);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (transmitter) WirelessRedstone.silence(level.getServer(), GlobalPos.of(level.dimension(), pos));
        else level.updateNeighborsAt(pos, this);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return !transmitter;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return !transmitter && state.getValue(LIT) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    /** On air: motes in the channel's colour rise off the antenna. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) return;
        int c = channelColor(state);
        var dust = new net.minecraft.core.particles.DustParticleOptions(c & 0xFFFFFF, 1.0F);
        for (int i = 0; i < 2; i++) {
            level.addParticle(dust, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.3, pos.getY() + 0.75 + random.nextDouble() * 0.5,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.3, 0, 0.02, 0);
        }
    }

    public static int channelColor(BlockState state) {
        return 0xFF000000 | DyeColor.byId(state.getValue(CHANNEL)).getTextureDiffuseColor();
    }
}
