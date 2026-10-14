package com.powerfactor.weapon;

import com.powerfactor.item.PoweredItem;
import com.powerfactor.registry.ModComponents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Plasma rifle: hold to fire a stream of plasma bolts. Each shot adds heat; past 100 the coils lock
 * out and vent for three seconds. Heat bleeds off whenever the trigger is released.
 */
public class PlasmaRifleItem extends PoweredItem {
    public static final int SHOT_FE = 450, HEAT_PER_SHOT = 7, MAX_HEAT = 100, FIRE_INTERVAL = 3;

    public PlasmaRifleItem(Properties properties) {
        super(properties.component(ModComponents.WEAPON_HEAT.get(), 0));
    }

    @Override
    public int capacity() {
        return 600_000;
    }

    public static int heat(ItemStack stack) {
        return stack.getOrDefault(ModComponents.WEAPON_HEAT.get(), 0);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72_000;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel server) || !(user instanceof Player player)) return;
        if ((getUseDuration(stack, user) - remaining) % FIRE_INTERVAL != 0) return;
        if (!drain(stack, SHOT_FE, player)) {
            player.stopUsingItem();
            server.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6F, 1.4F);
            return;
        }
        Vec3 dir = player.getViewVector(1);
        var r = server.getRandom();
        float spread = 0.012F + heat(stack) * 0.0004F;
        Vec3 v = dir.add(r.nextGaussian() * spread, r.nextGaussian() * spread, r.nextGaussian() * spread).normalize().scale(2.6);
        server.addFreshEntity(new PlasmaBoltEntity(server, player, v));
        server.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.35F, 1.7F + r.nextFloat() * 0.2F);
        int heat = heat(stack) + HEAT_PER_SHOT;
        if (heat >= MAX_HEAT) {
            // Overheat: lock out and vent.
            player.stopUsingItem();
            player.getCooldowns().addCooldown(stack, 60);
            Vec3 at = player.getEyePosition().add(dir.scale(0.6));
            server.sendParticles(ParticleTypes.CLOUD, at.x, at.y - 0.2, at.z, 14, .15, .15, .15, .05);
            server.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.9F, 0.8F);
        }
        stack.set(ModComponents.WEAPON_HEAT.get(), Math.min(MAX_HEAT, heat));
        FxPayload.send(server, new FxPayload(FxPayload.BOLT, player.getEyePosition(), player.getEyePosition(), 0, 0.25F, player.getId()));
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        int heat = heat(stack);
        boolean firing = owner instanceof LivingEntity l && l.isUsingItem() && l.getUseItem() == stack;
        if (heat > 0 && !firing && level.getGameTime() % 2 == 0) stack.set(ModComponents.WEAPON_HEAT.get(), Math.max(0, heat - 3));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int heat = heat(stack);
        return heat > 0 ? Math.round(13F * heat / MAX_HEAT) : super.getBarWidth(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        int heat = heat(stack);
        return heat > 70 ? 0xFF4020 : heat > 0 ? 0xFFB040 : 0x7CFF9A;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        builder.accept(Component.translatable("tooltip.powerfactor.plasma_rifle").withStyle(ChatFormatting.GREEN));
        builder.accept(Component.translatable("tooltip.powerfactor.plasma_rifle.heat", heat(stack), MAX_HEAT).withStyle(ChatFormatting.DARK_GRAY));
    }
}
