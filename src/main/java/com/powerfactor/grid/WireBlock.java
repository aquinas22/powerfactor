package com.powerfactor.grid;

import com.powerfactor.api.ReactiveLoad;
import com.powerfactor.registry.ModItems;
import com.powerfactor.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * Power wire. Bare conductor bites: touch an energized bare wire and you're shocked, harder the
 * higher its voltage. Right-click one with rubber to coat it (a wrench strips the coat off again).
 * Insulated cable comes coated.
 *
 * <p>Power wire. Connects to wire of the same rating, to anything with an FE handler on that side, and
 * to EE loads/sources such as capacitor banks.
 * Wires of different ratings don't join; that's what transformers will be for.
 */
public class WireBlock extends ConnectorBlock {
    public static final BooleanProperty COATED = BooleanProperty.create("coated");
    private static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> RUBBERS = net.minecraft.tags.ItemTags.create(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("c", "rubbers"));
    public static final ResourceKey<DamageType> ELECTROCUTION = ResourceKey.create(Registries.DAMAGE_TYPE,
            com.powerfactor.PowerFactor.id("electrocution"));
    private final CableTier tier;

    public WireBlock(Properties properties, CableTier tier) {
        super(properties, tier == CableTier.ALUMINUM ? 6 : tier == CableTier.INSULATED ? 5 : 4);
        this.tier = tier;
        registerDefaultState(defaultBlockState().setValue(COATED, tier == CableTier.INSULATED));
    }

    /** Insulated cable is made coated; the bare conductors can be coated by hand. */
    public boolean coatable() {
        return tier == CableTier.COPPER || tier == CableTier.ALUMINUM;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(COATED);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (!stack.is(RUBBERS) || !coatable() || state.getValue(COATED)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(COATED, true), Block.UPDATE_ALL);
            stack.consume(1, player);
            level.playSound(null, pos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 0.7F, 1.3F);
        }
        return InteractionResult.SUCCESS;
    }

    /** Strip the coat (the wrench does this); returns false if there's nothing to strip. */
    public boolean strip(Level level, BlockPos pos, BlockState state, Player player) {
        if (!coatable() || !state.getValue(COATED)) {
            return false;
        }
        level.setBlock(pos, state.setValue(COATED, false), Block.UPDATE_ALL);
        if (!player.getAbilities().instabuild) {
            Block.popResource(level, pos, new ItemStack(ModItems.RUBBER.get()));
        }
        return true;
    }

    // ------------------------------------------------------------------ shocks

    @Override
    protected VoxelShape getEntityInsideCollisionShape(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return getShape(state, level, pos, CollisionContext.empty()).move(0, 0, 0);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean precise) {
        shock(state, level, pos, entity);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        shock(state, level, pos, entity);
    }

    /** Shock damage for a bare conductor at this tier's voltage. */
    public static float shockDamage(CableTier tier) {
        return tier.volts >= 10_000 ? 10F : 3F;
    }

    /**
     * Players move client-side, so block contact callbacks don't fire reliably for them; the server
     * checks each player's feet and body against nearby wire shapes every tick instead.
     */
    public static void checkPlayer(ServerLevel level, Player player) {
        var box = player.getBoundingBox().inflate(0.06);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof WireBlock wire && !state.getValue(COATED)) {
                for (var aabb : state.getShape(level, pos).toAabbs()) {
                    if (aabb.move(pos).intersects(box)) {
                        wire.shock(state, level, pos.immutable(), player);
                        return;
                    }
                }
            }
        }
    }

    private void shock(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(level instanceof ServerLevel server) || state.getValue(COATED) || !(entity instanceof LivingEntity living)
                || living.invulnerableTime > 0 || entity instanceof Player p && (p.isCreative() || p.isSpectator())) {
            return;
        }
        PowerGrid grid = GridManager.get(server).gridAt(pos);
        if (grid == null || !grid.energized() || grid.state().volts() < 0.3) {
            return;
        }
        DamageSource source = new DamageSource(server.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ELECTROCUTION));
        if (living.hurtServer(server, source, shockDamage(tier) * (float) Math.min(1.2, grid.state().volts()))) {
            Vec3 away = living.position().subtract(Vec3.atCenterOf(pos)).multiply(1, 0, 1);
            if (away.lengthSqr() < 1e-4) {
                away = new Vec3(server.getRandom().nextDouble() - 0.5, 0, server.getRandom().nextDouble() - 0.5);
            }
            away = away.normalize().scale(tier.volts >= 10_000 ? 0.9 : 0.4);
            living.push(away.x, tier.volts >= 10_000 ? 0.45 : 0.2, away.z);
            living.hurtMarked = true;
            server.playSound(null, pos, ModSounds.WIRE_SHOCK.get(), SoundSource.BLOCKS, tier.volts >= 10_000 ? 1.4F : 0.8F,
                    0.9F + server.getRandom().nextFloat() * 0.2F);
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, living.getX(), living.getY() + 0.3, living.getZ(), 18, 0.3, 0.3, 0.3, 0.4);
        }
    }

    public CableTier tier() {
        return tier;
    }

    /** Apparent power (CE/t) the whole network can carry. */
    public int ratingCe() {
        return tier.ratingCe;
    }

    @Override
    protected boolean connectsTo(BlockGetter level, BlockPos pos, Direction side) {
        BlockPos other = pos.relative(side);
        if (level.getBlockState(other).getBlock() instanceof ConnectorBlock connector) {
            return connector == this;
        }
        if (level.getBlockState(other).getBlock() instanceof BreakerBlock) {
            return true;
        }
        if (level.getBlockEntity(other) instanceof ReactiveLoad) {
            return true;
        }
        return level instanceof Level l && l.getCapability(Capabilities.Energy.BLOCK, other, side.getOpposite()) != null;
    }

    @Override
    protected void onTopologyChanged(Level level, BlockPos pos) {
        if (level instanceof ServerLevel server) {
            GridManager.get(server).invalidate(pos);
        }
    }
}
