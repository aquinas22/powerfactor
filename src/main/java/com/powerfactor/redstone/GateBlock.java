package com.powerfactor.redstone;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A logic gate on a stone circuit plate. It outputs from its front; its inputs are the left, back and right
 * sides. Lamps on the plate show each input and the output. Use a Screwdriver to turn it (sneak: change which
 * inputs it uses); use it bare-handed to set a repeater's delay, a timer's interval or a counter's target.
 */
public class GateBlock extends BaseEntityBlock {
    public enum Type {
        AND, OR, NOT, NAND, NOR, XOR, XNOR, BUFFER, MULTIPLEXER, PULSE, REPEATER, RANDOMIZER, RS_LATCH, TOGGLE_LATCH, TIMER, COUNTER;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        /** Gates whose inputs can be switched off with the screwdriver. */
        public boolean maskable() {
            return this == AND || this == OR || this == NAND || this == NOR;
        }

        public boolean timed() {
            return this == REPEATER || this == TIMER;
        }
    }

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OUT = BooleanProperty.create("out"), IN_LEFT = BooleanProperty.create("in_left"),
            IN_BACK = BooleanProperty.create("in_back"), IN_RIGHT = BooleanProperty.create("in_right");
    private static final VoxelShape PLATE = Block.box(0, 0, 0, 16, 2, 16);
    public static final int[] DELAYS = {2, 4, 6, 10, 20, 40, 60, 100, 200, 400, 1200};

    private final Type type;

    public GateBlock(Type type, Properties properties) {
        super(properties);
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OUT, false).setValue(IN_LEFT, false)
                .setValue(IN_BACK, false).setValue(IN_RIGHT, false));
    }

    public Type type() {
        return type;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        throw new UnsupportedOperationException("not data-driven");
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OUT, IN_LEFT, IN_BACK, IN_RIGHT);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GateBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // Output away from the player, like a repeater.
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return PLATE;
    }

    // ---------------------------------------------------------------- inputs and outputs

    public static Direction left(Direction front) {
        return front.getCounterClockWise();
    }

    public static Direction right(Direction front) {
        return front.getClockWise();
    }

    static boolean input(Level level, BlockPos pos, Direction side) {
        return level.getSignal(pos.relative(side), side) > 0;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return direction != null && direction.getAxis() == Direction.Axis.Y ? false : true;
    }

    /** Powers only the block in front (and through it, strongly). */
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(OUT) && direction == state.getValue(FACING).getOpposite() ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean moved) {
        if (level.isClientSide()) return;
        if (!canSurvive(state, level, pos)) {
            level.destroyBlock(pos, true);
            return;
        }
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
            int delay = type == Type.REPEATER && level.getBlockEntity(pos) instanceof GateBlockEntity g ? g.delay : 2;
            level.scheduleTick(pos, this, delay);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!level.isClientSide() && !old.is(this)) level.scheduleTick(pos, this, 2);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof GateBlockEntity g) g.evaluate(level, pos, state, random);
    }

    /** Sets the lamps and output; tells the block in front if the output changed. */
    void show(ServerLevel level, BlockPos pos, BlockState state, boolean l, boolean b, boolean r, boolean out) {
        BlockState now = state.setValue(IN_LEFT, l).setValue(IN_BACK, b).setValue(IN_RIGHT, r).setValue(OUT, out);
        if (now == state) return;
        level.setBlock(pos, now, Block.UPDATE_CLIENTS);
        if (out != state.getValue(OUT)) {
            Direction front = state.getValue(FACING);
            BlockPos at = pos.relative(front);
            level.neighborChanged(at, this, null);
            level.updateNeighborsAtExceptFromFacing(at, this, front.getOpposite(), null);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        if (!moved && state.getValue(OUT)) {
            Direction front = state.getValue(FACING);
            level.neighborChanged(pos.relative(front), this, null);
            level.updateNeighborsAtExceptFromFacing(pos.relative(front), this, front.getOpposite(), null);
        }
    }

    // ---------------------------------------------------------------- configuration

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof ScrewdriverItem)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level instanceof ServerLevel s && level.getBlockEntity(pos) instanceof GateBlockEntity g) {
            if (player.isShiftKeyDown() && type.maskable()) {
                g.mask = g.mask == 7 ? 6 : g.mask == 6 ? 5 : g.mask == 5 ? 3 : 7;
                g.setChanged();
                player.sendOverlayMessage(Component.translatable("gui.powerfactor.gate.inputs", inputs(g.mask)).withStyle(ChatFormatting.GOLD));
            } else {
                level.setBlock(pos, state.setValue(FACING, state.getValue(FACING).getClockWise()), Block.UPDATE_ALL);
            }
            level.scheduleTick(pos, this, 2);
            s.playSound(null, pos, SoundEvents.CROSSBOW_LOADING_START.value(), SoundSource.BLOCKS, 0.4F, 1.6F);
        }
        return InteractionResult.SUCCESS;
    }

    private static String inputs(int mask) {
        return ((mask & 1) != 0 ? "L" : "·") + ((mask & 2) != 0 ? "B" : "·") + ((mask & 4) != 0 ? "R" : "·");
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof GateBlockEntity g)) return InteractionResult.PASS;
        if (type.timed()) {
            if (!level.isClientSide()) {
                int i = 0;
                while (i < DELAYS.length && DELAYS[i] != g.delay) i++;
                i = Math.floorMod((i >= DELAYS.length ? 0 : i) + (player.isShiftKeyDown() ? -1 : 1), DELAYS.length);
                g.delay = DELAYS[i];
                g.setChanged();
                player.sendOverlayMessage(Component.translatable(type == Type.TIMER ? "gui.powerfactor.gate.interval" : "gui.powerfactor.gate.delay",
                        String.format("%.1f", g.delay / 20.0)).withStyle(ChatFormatting.GOLD));
                level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.3F, 1.4F);
            }
            return InteractionResult.SUCCESS;
        }
        if (type == Type.COUNTER) {
            if (!level.isClientSide()) {
                g.max = Math.floorMod(g.max - 1 + (player.isShiftKeyDown() ? -1 : 1), 64) + 1;
                g.count = Math.min(g.count, g.max);
                g.setChanged();
                player.sendOverlayMessage(Component.translatable("gui.powerfactor.gate.counter", g.count, g.max).withStyle(ChatFormatting.GOLD));
                level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.3F, 1.4F);
                level.scheduleTick(pos, this, 2);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
