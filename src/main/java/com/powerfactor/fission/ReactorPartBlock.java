package com.powerfactor.fission;

import com.mojang.serialization.MapCodec;
import com.powerfactor.registry.ModFission;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** Reactor Port (coolant or fuel) and Reactor Logic Adapter (alarm out, SCRAM in). Use to change mode. */
public class ReactorPartBlock extends BaseEntityBlock implements com.powerfactor.multiblock.Connected.Part {
    /** Ports: false = coolant (blue ring), true = fuel (green ring). Unused on adapters. */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty FUEL =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("fuel");
    private final boolean adapter;
    private final com.powerfactor.multiblock.Connected.Style style;

    public ReactorPartBlock(boolean adapter, com.powerfactor.multiblock.Connected.Style style, Properties properties) {
        super(properties);
        this.adapter = adapter;
        this.style = style;
        registerDefaultState(com.powerfactor.multiblock.Connected.unconnected(stateDefinition.any().setValue(FUEL, false)
                .setValue(com.powerfactor.multiblock.CasingBlock.FORMED, false)));
    }

    @Override
    public com.powerfactor.multiblock.Connected.Style style() {
        return style;
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FUEL, com.powerfactor.multiblock.CasingBlock.FORMED);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        throw new UnsupportedOperationException("not data-driven");
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ReactorPartBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ReactorPartBlockEntity be) {
            if (adapter) {
                be.cycle(ReactorControllerBlockEntity.Alarm.values().length);
                player.sendOverlayMessage(Component.translatable("gui.powerfactor.fission.adapter",
                        Component.translatable("gui.powerfactor.fission.alarm." + ReactorControllerBlockEntity.Alarm.values()[be.mode()].name().toLowerCase(java.util.Locale.ROOT))));
            } else {
                level.setBlockAndUpdate(pos, state.cycle(FUEL));
                level.updateNeighborsAt(pos, this);
                player.sendOverlayMessage(Component.translatable("gui.powerfactor.fission.port." + (state.getValue(FUEL) ? "coolant" : "fuel")));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return adapter;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return adapter && level.getBlockEntity(pos) instanceof ReactorPartBlockEntity be ? be.signal() : 0;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean moved) {
        super.neighborChanged(state, level, pos, block, orientation, moved);
        if (adapter && level instanceof ServerLevel s && s.getBlockEntity(pos) instanceof ReactorPartBlockEntity be) {
            be.redstoneIn(s, s.hasNeighborSignal(pos));
        }
    }

    public static boolean isAdapter(BlockState s) {
        return s.is(ModFission.LOGIC_ADAPTER.get());
    }
}
