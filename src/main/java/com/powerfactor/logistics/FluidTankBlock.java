package com.powerfactor.logistics;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import org.jspecify.annotations.Nullable;

/** A glass-and-steel fluid tank. Use a bucket on it; it keeps its fluid when broken. */
public class FluidTankBlock extends BaseEntityBlock {
    public static final MapCodec<FluidTankBlock> CODEC = simpleCodec(FluidTankBlock::new);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);
    public static final java.util.Map<net.minecraft.core.Direction, net.minecraft.world.level.block.state.properties.BooleanProperty> JOINED =
            new java.util.EnumMap<>(net.minecraft.core.Direction.class);
    static { for (var direction : net.minecraft.core.Direction.values()) JOINED.put(direction,
            net.minecraft.world.level.block.state.properties.BooleanProperty.create(direction.getSerializedName())); }

    public FluidTankBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (var property : JOINED.values()) state = state.setValue(property, false);
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluidTankBlockEntity(pos, state);
    }

    @Override protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(JOINED.values().toArray(net.minecraft.world.level.block.state.properties.Property[]::new));
    }
    @Override public <T extends BlockEntity> net.minecraft.world.level.block.entity.@Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, com.powerfactor.registry.ModBlockEntities.FLUID_TANK.get(), (l,p,s,t) -> t.serverTick());
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
            net.minecraft.world.level.redstone.@Nullable Orientation orientation, boolean moved) {
        if (level.getBlockEntity(pos) instanceof FluidTankBlockEntity tank) tank.markStructureDirty();
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.@Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FluidTankBlockEntity tank) tank.balance();
    }

    @Override protected ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        ItemStack stack = new ItemStack(asItem());
        if (includeData && level.getBlockEntity(pos) instanceof FluidTankBlockEntity tank && !tank.fluid().isEmpty())
            stack.set(com.powerfactor.registry.ModComponents.FLUID.get(), net.neoforged.neoforge.fluids.SimpleFluidContent.copyOf(tank.fluid()));
        return stack;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FluidTankBlockEntity tank) {
            tank.balance();
            player.sendOverlayMessage(tank.describe());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, net.minecraft.core.Direction direction) {
        return level.getBlockEntity(pos) instanceof FluidTankBlockEntity tank ? tank.comparator() : 0;
    }
}
