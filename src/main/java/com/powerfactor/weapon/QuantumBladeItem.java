package com.powerfactor.weapon;

import com.powerfactor.item.PoweredItem;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A blade of phase-locked qubits. Melee strikes cost a little FE; right-click phase-dashes up to ten
 * blocks along your line of sight (stopping short of walls), cutting through everything in between.
 */
public class QuantumBladeItem extends PoweredItem {
    public static final int DASH_FE = 4000, HIT_FE = 150;
    public static final double DASH = 10;

    public QuantumBladeItem(Properties properties) {
        super(properties.sword(ToolMaterial.NETHERITE, 9F, -2.2F).component(DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE)
                .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true).fireResistant());
    }

    @Override
    public int capacity() {
        return 800_000;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        super.postHurtEnemy(stack, target, attacker);
        if (attacker instanceof Player p && target.level() instanceof ServerLevel level && drain(stack, HIT_FE, p)) {
            Vec3 at = target.position().add(0, target.getBbHeight() / 2, 0);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 16, .3, .4, .3, .1);
            target.hurtServer(level, p.damageSources().magic(), 4F);
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.getAbilities().instabuild && energy(stack) < DASH_FE) return InteractionResult.FAIL;
        if (level instanceof ServerLevel server) dash(server, player, stack);
        player.getCooldowns().addCooldown(stack, 30);
        return InteractionResult.SUCCESS;
    }

    private static void dash(ServerLevel level, Player player, ItemStack stack) {
        Vec3 start = player.position();
        Vec3 dir = player.getViewVector(1);
        Vec3 flat = new Vec3(dir.x, Math.max(-0.4, Math.min(0.4, dir.y)), dir.z).normalize();
        Vec3 eye = player.getEyePosition();
        HitResult wall = level.clip(new ClipContext(eye, eye.add(flat.scale(DASH)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double reach = wall.getType() == HitResult.Type.MISS ? DASH : Math.max(0, wall.getLocation().distanceTo(eye) - 0.8);
        Vec3 end = start.add(flat.scale(reach));
        if (!level.noCollision(player, player.getBoundingBox().move(end.subtract(start)))) return;
        drain(stack, DASH_FE, player);
        // Everything the body passes through is cut.
        AABB path = player.getBoundingBox().expandTowards(end.subtract(start)).inflate(0.6);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, path, e -> e != player && e.isAlive())) {
            e.hurtServer(level, player.damageSources().playerAttack(player), 14F);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 1, 0, 0, 0, 0);
        }
        player.teleportTo(end.x, end.y, end.z);
        player.setDeltaMovement(flat.scale(0.6));
        player.hurtMarked = true;
        player.resetFallDistance();
        level.playSound(null, BlockPos.containing(start), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.4F);
        level.playSound(null, BlockPos.containing(end), SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.PLAYERS, 0.8F, 1.6F);
        FxPayload.send(level, new FxPayload(FxPayload.DASH, start.add(0, 1, 0), end.add(0, 1, 0), 0xFFB18CFF, 1F, player.getId()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        builder.accept(Component.translatable("tooltip.powerfactor.quantum_blade").withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
