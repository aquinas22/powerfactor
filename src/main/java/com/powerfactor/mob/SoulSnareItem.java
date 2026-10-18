package com.powerfactor.mob;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;

/** Soul Snare: use it on a mob to take an imprint of its kind (the mob is unharmed). A Spawner Cage reproduces the imprint. */
public class SoulSnareItem extends Item {
    public SoulSnareItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static @Nullable EntityType<?> soul(ItemStack stack) {
        String id = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getStringOr("soul", "");
        if (id.isEmpty()) return null;
        return BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(id)).orElse(null);
    }

    public static boolean capturable(LivingEntity e) {
        return e instanceof Mob && e.canUsePortal(false) && !(e.getType() == net.minecraft.world.entity.EntityTypes.WITHER || e.getType() == net.minecraft.world.entity.EntityTypes.ENDER_DRAGON
                || e.getType() == net.minecraft.world.entity.EntityTypes.ELDER_GUARDIAN || e.getType() == net.minecraft.world.entity.EntityTypes.WARDEN);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!capturable(target)) return InteractionResult.PASS;
        if (player.level() instanceof ServerLevel level) {
            ItemStack held = player.getItemInHand(hand);
            CustomData.update(DataComponents.CUSTOM_DATA, held, t -> t.putString("soul", BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString()));
            level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 20, 0.3, 0.4, 0.3, 0.02);
            level.playSound(null, target.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1F, 1F);
            player.sendOverlayMessage(Component.translatable("gui.powerfactor.snare.captured", target.getType().getDescription()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return soul(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        EntityType<?> soul = soul(stack);
        out.accept(soul == null ? Component.translatable("tooltip.powerfactor.snare.empty").withStyle(ChatFormatting.GRAY)
                : Component.translatable("tooltip.powerfactor.snare.holds", soul.getDescription()).withStyle(ChatFormatting.AQUA));
    }
}
