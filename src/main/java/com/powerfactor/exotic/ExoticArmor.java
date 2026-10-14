package com.powerfactor.exotic;

import com.powerfactor.PowerFactor;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Holder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Exotic armor. A full set of one material grants its set bonus, and the bonuses climb with tier:
 * Adamantium <b>Bulwark</b> (resistance), Faradaenium <b>Dynamo</b> (speed; attackers are arced),
 * Cortanium <b>Crucible</b> (fire immunity, strength; attackers burn) and Quantum <b>Phase</b>
 * (all of those, night vision, no fall damage, and a phase shift that voids one hit every eight
 * seconds). Full sets shed tier-appropriate particles: glints, sparks, embers, then a portal swirl.
 */
public class ExoticArmor extends Item {
    public static final Map<ExoticMaterial, ArmorMaterial> MATERIALS = new EnumMap<>(ExoticMaterial.class);

    static {
        MATERIALS.put(ExoticMaterial.ADAMANTIUM, material(ExoticMaterial.ADAMANTIUM, 45, 4, 7, 9, 4, 15, 4F, 0.2F));
        MATERIALS.put(ExoticMaterial.FARADAENIUM, material(ExoticMaterial.FARADAENIUM, 40, 3, 7, 8, 3, 22, 3.5F, 0.1F));
        MATERIALS.put(ExoticMaterial.CORTANIUM, material(ExoticMaterial.CORTANIUM, 60, 4, 8, 10, 4, 15, 5F, 0.3F));
        MATERIALS.put(ExoticMaterial.QUANTUM, material(ExoticMaterial.QUANTUM, 70, 5, 8, 10, 5, 25, 6F, 0.3F));
    }

    private static ArmorMaterial material(ExoticMaterial m, int durability, int boots, int legs, int chest, int helm, int enchant, float toughness,
            float knockback) {
        Map<ArmorType, Integer> defense = new EnumMap<>(ArmorType.class);
        defense.put(ArmorType.BOOTS, boots);
        defense.put(ArmorType.LEGGINGS, legs);
        defense.put(ArmorType.CHESTPLATE, chest);
        defense.put(ArmorType.HELMET, helm);
        defense.put(ArmorType.BODY, chest * 2);
        ResourceKey<EquipmentAsset> asset = ResourceKey.create(EquipmentAssets.ROOT_ID, PowerFactor.id(m.id));
        TagKey<Item> repair = TagKey.create(Registries.ITEM, PowerFactor.id("repairs/" + m.id));
        return new ArmorMaterial(durability, defense, enchant, SoundEvents.ARMOR_EQUIP_NETHERITE, toughness, knockback, repair, asset);
    }

    private final ExoticMaterial material;
    private final ArmorType type;

    public ExoticArmor(ExoticMaterial material, ArmorType type, Properties properties) {
        super(props(properties.humanoidArmor(MATERIALS.get(material), type), material));
        this.material = material;
        this.type = type;
    }

    private static Properties props(Properties p, ExoticMaterial m) {
        p = p.rarity(m.rarity).fireResistant();
        if (m.unbreakable()) p = p.component(DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
        if (m == ExoticMaterial.QUANTUM) p = p.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return p;
    }

    public ExoticMaterial material() {
        return material;
    }

    public ArmorType type() {
        return type;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, out, flag);
        out.accept(Component.translatable("tooltip.powerfactor.exotic_armor.set." + material.id).withColor(material.color));
        out.accept(Component.translatable("tooltip.powerfactor.exotic_armor.bonus." + material.id).withStyle(ChatFormatting.GRAY));
    }

    // ------------------------------------------------------------------ set bonuses

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    /** Server-side cooldowns (game time a bonus is ready again). Lost on restart, which is harmless. */
    private static final Map<UUID, Long> RETALIATE = new HashMap<>(), PHASE = new HashMap<>();
    public static final int PHASE_COOLDOWN = 160, RETALIATE_COOLDOWN = 30;

    /** The material of a full matching set, or null. */
    public static ExoticMaterial fullSet(LivingEntity entity) {
        ExoticMaterial found = null;
        for (EquipmentSlot slot : SLOTS) {
            if (!(entity.getItemBySlot(slot).getItem() instanceof ExoticArmor a)) return null;
            if (found == null) found = a.material;
            else if (found != a.material) return null;
        }
        return found;
    }

    static boolean bulwark(ExoticMaterial m) {
        return m == ExoticMaterial.ADAMANTIUM || m == ExoticMaterial.QUANTUM;
    }

    static boolean dynamo(ExoticMaterial m) {
        return m == ExoticMaterial.FARADAENIUM || m == ExoticMaterial.QUANTUM;
    }

    static boolean crucible(ExoticMaterial m) {
        return m == ExoticMaterial.CORTANIUM || m == ExoticMaterial.QUANTUM;
    }

    @EventBusSubscriber(modid = PowerFactor.MODID)
    public static final class Events {
        @SubscribeEvent
        static void onTick(PlayerTickEvent.Post event) {
            Player player = event.getEntity();
            if (!(player.level() instanceof ServerLevel level) || player.tickCount % 10 != 0) return;
            ExoticMaterial m = fullSet(player);
            if (m == null) return;
            if (bulwark(m)) keep(player, MobEffects.RESISTANCE, 0);
            if (dynamo(m)) keep(player, MobEffects.SPEED, 0);
            if (crucible(m)) {
                keep(player, MobEffects.FIRE_RESISTANCE, 0);
                keep(player, MobEffects.STRENGTH, 0);
            }
            if (m == ExoticMaterial.QUANTUM) keep(player, MobEffects.NIGHT_VISION, 0);
            ambient(level, player, m);
        }

        /** Keeps an effect topped up quietly; night vision needs a long tail to avoid flicker. */
        private static void keep(Player player, Holder<MobEffect> effect, int amp) {
            MobEffectInstance now = player.getEffect(effect);
            if (now != null && now.getDuration() > 260) return;
            player.addEffect(new MobEffectInstance(effect, 400, amp, true, false, true));
        }

        private static void ambient(ServerLevel level, Player p, ExoticMaterial m) {
            Vec3 c = p.position().add(0, p.getBbHeight() * 0.55, 0);
            var r = level.getRandom();
            switch (m) {
                case ADAMANTIUM -> level.sendParticles(ParticleTypes.WAX_OFF, c.x, c.y, c.z, 1, 0.35, 0.5, 0.35, 0);
                case FARADAENIUM -> level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 3, 0.35, 0.55, 0.35, 0.05);
                case CORTANIUM -> {
                    level.sendParticles(ParticleTypes.SMALL_FLAME, c.x, c.y - 0.4, c.z, 2, 0.3, 0.4, 0.3, 0.01);
                    if (r.nextFloat() < 0.3F) level.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 1, 0.2, 0.3, 0.2, 0);
                }
                case QUANTUM -> {
                    // A slow ring of motes orbiting the waist, plus portal shimmer.
                    double a = p.tickCount * 0.21;
                    for (int k = 0; k < 3; k++) {
                        double t = a + k * Math.PI * 2 / 3;
                        level.sendParticles(ParticleTypes.END_ROD, p.getX() + Math.cos(t) * 0.75, c.y - 0.1, p.getZ() + Math.sin(t) * 0.75, 1, 0, 0, 0, 0);
                    }
                    level.sendParticles(ParticleTypes.PORTAL, c.x, c.y, c.z, 4, 0.3, 0.5, 0.3, 0.4);
                }
            }
        }

        @SubscribeEvent
        static void onHurt(LivingIncomingDamageEvent event) {
            LivingEntity victim = event.getEntity();
            if (!(victim.level() instanceof ServerLevel level)) return;
            ExoticMaterial m = fullSet(victim);
            if (m == null) return;
            long now = level.getGameTime();
            UUID id = victim.getUUID();
            if (m == ExoticMaterial.QUANTUM && event.getAmount() > 0 && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)
                    && PHASE.getOrDefault(id, 0L) <= now) {
                // Phase shift: the blow passes through.
                PHASE.put(id, now + PHASE_COOLDOWN);
                event.setCanceled(true);
                Vec3 at = victim.position().add(0, victim.getBbHeight() * 0.5, 0);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 40, 0.4, 0.7, 0.4, 0.15);
                level.playSound(null, victim.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 1.7F);
                return;
            }
            if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == victim) return;
            if (crucible(m)) attacker.igniteForSeconds(4);
            if (dynamo(m) && RETALIATE.getOrDefault(id, 0L) <= now) {
                RETALIATE.put(id, now + RETALIATE_COOLDOWN);
                ExoticAbilities.bolt(level, victim.position().add(0, victim.getBbHeight() * 0.6, 0),
                        attacker.position().add(0, attacker.getBbHeight() * 0.6, 0), ExoticMaterial.FARADAENIUM.color);
                attacker.hurtServer(level, victim.damageSources().lightningBolt(), 5F);
                ExoticAbilities.arc(level, victim, attacker, m == ExoticMaterial.QUANTUM ? 3 : 2, 4F, ExoticMaterial.FARADAENIUM.color);
            }
        }

        @SubscribeEvent
        static void onFall(LivingFallEvent event) {
            if (fullSet(event.getEntity()) == ExoticMaterial.QUANTUM) event.setDamageMultiplier(0);
        }

        private Events() {}
    }
}
