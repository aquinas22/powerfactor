package com.powerfactor.item;

import com.powerfactor.registry.ModComponents;
import com.powerfactor.registry.ModSounds;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The Arc Saber: an energy blade that runs on grid power. Use to light or douse it; sneak-use to
 * pick its color, blade style and hit effect. Lit, it hits for 10 and drains FE; unlit it's a club.
 */
public class ArcSaberItem extends Item {
    public static final int CAPACITY = 80_000, PER_HIT = 150, IDLE = 2;
    public static final float LIT_DAMAGE = 10F;
    private static final Identifier DAMAGE_ID = Identifier.withDefaultNamespace("base_attack_damage");
    private static final Identifier SPEED_ID = Identifier.withDefaultNamespace("base_attack_speed");

    /** Client hook to open the customization screen. */
    public static java.util.function.Consumer<InteractionHand> openScreen = h -> {};

    public ArcSaberItem(Properties properties) {
        super(properties);
    }

    public static Properties properties(Properties p) {
        return p.stacksTo(1).fireResistant()
                .component(ModComponents.ENERGY.get(), 0)
                .component(ModComponents.SABER.get(), SaberSettings.DEFAULT)
                .attributes(ItemAttributeModifiers.builder()
                        .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(DAMAGE_ID, LIT_DAMAGE - 1, AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND)
                        .add(Attributes.ATTACK_SPEED, new AttributeModifier(SPEED_ID, -2.2, AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND)
                        .build());
    }

    public static int energy(ItemStack stack) {
        return stack.getOrDefault(ModComponents.ENERGY.get(), 0);
    }

    public static boolean lit(ItemStack stack) {
        return stack.has(ModComponents.SABER_LIT.get());
    }

    public static SaberSettings settings(ItemStack stack) {
        return stack.getOrDefault(ModComponents.SABER.get(), SaberSettings.DEFAULT);
    }

    public static void setLit(ItemStack stack, boolean lit) {
        if (lit) {
            stack.set(ModComponents.SABER_LIT.get(), net.minecraft.util.Unit.INSTANCE);
        } else {
            stack.remove(ModComponents.SABER_LIT.get());
        }
        refreshModel(stack);
    }

    /** The item model reads lit/style as strings and the blade color as a tint. */
    public static void refreshModel(ItemStack stack) {
        SaberSettings s = settings(stack);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(),
                List.of(lit(stack) ? "lit" : "off", s.style().name().toLowerCase(java.util.Locale.ROOT)), List.of(s.color())));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (level.isClientSide()) {
                openScreen.accept(hand);
            }
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide()) {
            boolean lighting = !lit(stack);
            if (lighting && energy(stack) < PER_HIT && !player.getAbilities().instabuild) {
                level.playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6F, 1.4F);
                return InteractionResult.FAIL;
            }
            setLit(stack, lighting);
            level.playSound(null, player.blockPosition(), lighting ? ModSounds.SWORD_IGNITE.get() : ModSounds.SWORD_RETRACT.get(),
                    SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        if (!lit(stack)) {
            return;
        }
        boolean held = owner instanceof LivingEntity living && (living.getMainHandItem() == stack || living.getOffhandItem() == stack);
        if (!held || owner instanceof Player p && !p.getAbilities().instabuild && energy(stack) <= IDLE) {
            setLit(stack, false);
            level.playSound(null, owner.blockPosition(), ModSounds.SWORD_RETRACT.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
            return;
        }
        if (!(owner instanceof Player p && p.getAbilities().instabuild)) {
            stack.set(ModComponents.ENERGY.get(), energy(stack) - IDLE);
        }
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity, InteractionHand hand) {
        if (lit(stack) && !entity.level().isClientSide()) {
            entity.level().playSound(null, entity.getX(), entity.getEyeY(), entity.getZ(), ModSounds.SWORD_SWING.get(), SoundSource.PLAYERS,
                    0.9F, 0.9F + entity.getRandom().nextFloat() * 0.25F);
        }
        return false;
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!lit(stack) || !(target.level() instanceof ServerLevel level)) {
            return;
        }
        if (!(attacker instanceof Player p && p.getAbilities().instabuild)) {
            stack.set(ModComponents.ENERGY.get(), Math.max(0, energy(stack) - PER_HIT));
        }
        SaberSettings s = settings(stack);
        int rgb = s.style() == SaberSettings.Style.RAINBOW ? Mth.hsvToRgb((level.getGameTime() % 60) / 60F, 0.8F, 1F) : s.color();
        level.playSound(null, target.getX(), target.getY(0.5), target.getZ(), ModSounds.SWORD_HIT.get(), SoundSource.PLAYERS, 1.0F,
                0.9F + level.getRandom().nextFloat() * 0.2F);
        level.sendParticles(new DustParticleOptions(rgb, 1.4F), target.getX(), target.getY(0.6), target.getZ(), 18, 0.25, 0.35, 0.25, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY(0.6), target.getZ(), 10, 0.2, 0.3, 0.2, 0.25);
        switch (s.effect()) {
            case STUN -> target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 3));
            case IGNITE -> target.igniteForSeconds(4);
            case CHAIN -> {
                for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(4),
                        e -> e != target && e != attacker && e.isAlive())) {
                    other.hurtServer(level, attacker.damageSources().mobAttack(attacker), LIT_DAMAGE * 0.4F);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, other.getX(), other.getY(0.6), other.getZ(), 12, 0.2, 0.3, 0.2, 0.3);
                    break;
                }
            }
            case DRAIN -> attacker.heal(LIT_DAMAGE * 0.15F);
            default -> {
            }
        }
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !newStack.is(oldStack.getItem());
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13F * Mth.clamp((float) energy(stack) / CAPACITY, 0, 1));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return settings(stack).color();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        SaberSettings s = settings(stack);
        builder.accept(Component.translatable("tooltip.powerfactor.energy", String.format("%,d", energy(stack)), String.format("%,d", CAPACITY))
                .withColor(0xFF5A4A));
        builder.accept(Component.translatable("tooltip.powerfactor.saber.style",
                Component.translatable("gui.powerfactor.saber.style." + s.style().name().toLowerCase(java.util.Locale.ROOT))).withColor(s.color()));
        builder.accept(Component.translatable("tooltip.powerfactor.saber.effect",
                Component.translatable("gui.powerfactor.saber.effect." + s.effect().name().toLowerCase(java.util.Locale.ROOT)))
                .withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.powerfactor.saber.help").withStyle(ChatFormatting.DARK_GRAY));
    }
}
