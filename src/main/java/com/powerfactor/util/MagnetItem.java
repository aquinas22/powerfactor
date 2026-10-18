package com.powerfactor.util;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Item Magnet: use to switch it on; while on, anywhere in your inventory, it draws loose items and
 * experience within 8 blocks to you. It leaves alone items still under a pickup delay (so what you
 * throw stays thrown) and stops while you sneak.
 */
public class MagnetItem extends Item {
    public static final double RANGE = 8;

    public MagnetItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static boolean on(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBooleanOr("magnet", false);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean now = !on(stack);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.putBoolean("magnet", now));
        if (!level.isClientSide()) {
            level.playSound(null, player.blockPosition(), now ? SoundEvents.BEACON_POWER_SELECT : SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6F, 1.8F);
            player.sendOverlayMessage(Component.translatable(now ? "gui.powerfactor.magnet.on" : "gui.powerfactor.magnet.off"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return on(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        if (!(entity instanceof Player player) || !on(stack) || player.isShiftKeyDown() || player.isSpectator()) return;
        Vec3 to = player.position().add(0, 0.6, 0);
        var box = player.getBoundingBox().inflate(RANGE);
        int pulled = 0;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && !e.hasPickUpDelay())) {
            pull(item, to);
            if (++pulled % 3 == 0) level.sendParticles(ParticleTypes.ELECTRIC_SPARK, item.getX(), item.getY() + 0.2, item.getZ(), 1, 0, 0, 0, 0);
        }
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, box, Entity::isAlive)) pull(orb, to);
    }

    private static void pull(Entity e, Vec3 to) {
        Vec3 d = to.subtract(e.position());
        double dist = d.length();
        if (dist < 0.4) return;
        Vec3 v = d.scale(Math.min(0.6, 0.12 + 0.6 / Math.max(1, dist)) / dist);
        e.setDeltaMovement(e.getDeltaMovement().scale(0.5).add(v));
        e.hurtMarked = true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable(on(stack) ? "tooltip.powerfactor.magnet.on" : "tooltip.powerfactor.magnet.off")
                .withStyle(on(stack) ? ChatFormatting.AQUA : ChatFormatting.GRAY));
    }
}
