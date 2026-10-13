package com.powerfactor.item;

import com.powerfactor.PowerFactor;
import com.powerfactor.power.ArmorPower;
import com.powerfactor.registry.ModComponents;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Server authority for shields, servo FE payment, helmet utilities and landing absorption. */
@EventBusSubscriber(modid = PowerFactor.MODID)
public final class PowerArmorServer {
    private static final Identifier SERVO = PowerFactor.id("armor_servo");
    private record Position(double x, double y, double z, boolean ground) {}
    private static final Map<UUID, Position> POSITIONS = new HashMap<>();
    private static final Map<UUID, Boolean> VISION = new HashMap<>();
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public static ItemStack piece(Player player, PowerArmorItem.Part part) {
        ItemStack stack = player.getItemBySlot(part.slot);
        return stack.getItem() instanceof PowerArmorItem armor && armor.part() == part ? stack : ItemStack.EMPTY;
    }
    private static boolean ready(Player player, ItemStack stack, int cost) {
        return !stack.isEmpty() && (player.getAbilities().instabuild || PowerArmorItem.ready(stack, cost));
    }

    @SubscribeEvent static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level() instanceof ServerLevel) tick(event.getEntity());
    }
    public static void tick(Player player) {
        ItemStack legs = piece(player, PowerArmorItem.Part.LEGS);
        int speed = ToolModules.count(legs, DrillModule.Type.SPEED), jump = ToolModules.count(legs, DrillModule.Type.JUMP);
        boolean active = ready(player, legs, ArmorPower.jumpCost(jump)) && PowerArmorItem.assists(legs) && !player.isShiftKeyDown()
                && !player.isPassenger() && !player.getAbilities().flying && !player.isFallFlying() && !player.isInWater();
        Position before = POSITIONS.put(player.getUUID(), new Position(player.getX(), player.getY(), player.getZ(), player.onGround()));
        if (active && before != null) {
            if (before.ground && !player.onGround() && player.getY() - before.y > 0.05) {
                PoweredItem.drain(legs, ToolModules.cost(legs, ArmorPower.jumpCost(jump)), player);
            } else if (player.onGround() && Math.hypot(player.getX() - before.x, player.getZ() - before.z) > 0.01) {
                PoweredItem.drain(legs, ToolModules.cost(legs, ArmorPower.walkingCost(speed)), player);
            }
            active = ready(player, legs, ArmorPower.jumpCost(jump));
        }
        modifier(player, Attributes.MOVEMENT_SPEED, active ? ArmorPower.speedBonus(speed) : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modifier(player, Attributes.JUMP_STRENGTH, active ? ArmorPower.jumpBonus(jump) : 0, AttributeModifier.Operation.ADD_VALUE);
        modifier(player, Attributes.STEP_HEIGHT, active ? 0.5 : 0, AttributeModifier.Operation.ADD_VALUE);
        helmet(player);
    }
    private static void modifier(Player player, Holder<Attribute> type, double amount, AttributeModifier.Operation operation) {
        var attribute = player.getAttribute(type); if (attribute == null) return;
        var previous = attribute.getModifier(SERVO);
        if (previous != null && previous.amount() == amount && previous.operation() == operation) return;
        attribute.removeModifier(SERVO);
        if (amount != 0) attribute.addTransientModifier(new AttributeModifier(SERVO, amount, operation));
    }
    private static void helmet(Player player) {
        ItemStack head = piece(player, PowerArmorItem.Part.HELMET);
        boolean vision = ready(player, head, 4) && PowerArmorItem.visor(head) && ToolModules.count(head, DrillModule.Type.NIGHT_VISION) > 0;
        boolean owned = VISION.getOrDefault(player.getUUID(), false);
        var effect = player.getEffect(MobEffects.NIGHT_VISION);
        boolean ours = owned && effect != null && effect.isAmbient() && !effect.isVisible() && !effect.showIcon() && effect.getDuration() <= 220;
        if (vision) {
            PoweredItem.drain(head, ToolModules.cost(head, 4), player);
            if (effect == null || ours && effect.getDuration() <= 200) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 220, 0, true, false, false));
                VISION.put(player.getUUID(), true);
            } else if (!ours) VISION.put(player.getUUID(), false);
        } else {
            if (ours) player.removeEffect(MobEffects.NIGHT_VISION);
            VISION.remove(player.getUUID());
        }
        if (ready(player, head, 2) && ToolModules.count(head, DrillModule.Type.REBREATHER) > 0
                && player.isEyeInFluid(FluidTags.WATER) && player.getAirSupply() < player.getMaxAirSupply()) {
            PoweredItem.drain(head, ToolModules.cost(head, 2), player);
            player.setAirSupply(Math.min(player.getMaxAirSupply(), player.getAirSupply() + 4));
        }
    }

    @SubscribeEvent static void onDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof Player player && player.level() instanceof ServerLevel
                && !event.getSource().is(DamageTypeTags.BYPASSES_ARMOR) && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setNewDamage(shield(player, event.getNewDamage()));
        }
    }
    public static float shield(Player player, float damage) {
        if (!Float.isFinite(damage) || damage <= 0) return damage;
        double absorbed = 0;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!(stack.getItem() instanceof PowerArmorItem armor) || armor.part().slot != slot) continue;
            double requested = Math.max(0, Math.min(damage * PowerArmorItem.shieldFraction(stack), damage * 0.5 - absorbed));
            int available = player.getAbilities().instabuild ? Integer.MAX_VALUE : Math.max(0, PoweredItem.energy(stack));
            var result = ArmorPower.absorb(requested, available, ToolModules.cost(stack, 120));
            PoweredItem.drain(stack, result.energy(), player); absorbed += result.amount();
        }
        if (absorbed > 0 && player.level() instanceof ServerLevel server) {
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1, player.getZ(),
                    8, 0.35, 0.5, 0.35, 0.025);
        }
        return (float) Math.max(0, damage - absorbed);
    }
    @SubscribeEvent static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player && player.level() instanceof ServerLevel) {
            event.setDistance(landing(player, event.getDistance()));
        }
    }
    public static double landing(Player player, double distance) {
        ItemStack boots = piece(player, PowerArmorItem.Part.BOOTS);
        if (boots.isEmpty() || !PowerArmorItem.assists(boots)) return distance;
        double requested = Math.min(Math.max(0, distance - player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE)),
                ArmorPower.landingLimit(ToolModules.count(boots, DrillModule.Type.SHOCK)));
        int available = player.getAbilities().instabuild ? Integer.MAX_VALUE : Math.max(0, PoweredItem.energy(boots));
        var result = ArmorPower.absorb(requested, available, ToolModules.cost(boots, 100));
        PoweredItem.drain(boots, result.energy(), player);
        return distance - result.amount();
    }
    public static void control(Player player, int action) {
        if (action == 0) {
            boolean on = false;
            for (var part : PowerArmorItem.Part.values()) if (!piece(player, part).isEmpty() && PowerArmorItem.assists(piece(player, part))) on = true;
            for (var part : PowerArmorItem.Part.values()) if (!piece(player, part).isEmpty()) piece(player, part).set(ModComponents.ARMOR_ASSIST.get(), !on);
        } else if (action == 1) {
            ItemStack head = piece(player, PowerArmorItem.Part.HELMET);
            if (!head.isEmpty()) head.set(ModComponents.ARMOR_VISOR.get(), !PowerArmorItem.visor(head));
        }
    }
    @SubscribeEvent static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        POSITIONS.remove(event.getEntity().getUUID()); VISION.remove(event.getEntity().getUUID());
    }
    private PowerArmorServer() {}
}
