package com.powerfactor.redstone;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;

/** Illumar Lamp: a coloured lamp with a soft halo, lit by redstone (or, inverted, lit until powered). */
public class IllumarLampBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private final int color;
    private final boolean inverted;

    public IllumarLampBlock(int color, boolean inverted, Properties properties) {
        super(properties);
        this.color = color;
        this.inverted = inverted;
        registerDefaultState(stateDefinition.any().setValue(LIT, inverted));
    }

    public int color() {
        return color;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        throw new UnsupportedOperationException("not data-driven");
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    private boolean wanted(Level level, BlockPos pos) {
        return level.hasNeighborSignal(pos) != inverted;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(LIT, wanted(ctx.getLevel(), ctx.getClickedPos()));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean moved) {
        if (level.isClientSide()) return;
        boolean want = wanted(level, pos);
        if (want != state.getValue(LIT)) {
            // Like vanilla lamps: on at once, off a moment later (so a flickering line doesn't strobe).
            if (want) level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_CLIENTS);
            else level.scheduleTick(pos, this, 4);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT) && !wanted(level, pos)) level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_CLIENTS);
    }
}
