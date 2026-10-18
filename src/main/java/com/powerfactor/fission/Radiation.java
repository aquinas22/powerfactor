package com.powerfactor.fission;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.powerfactor.PowerFactor;
import com.powerfactor.registry.ModFission;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Radiation. Contaminated ground (left by meltdowns and corium) fades over time; spent fuel and nuclear
 * waste carried loose irradiate their carrier. A full Hazmat Suit keeps it all out. The dose shows as
 * poison, then wither, then real harm; the Geiger counter's clicks (client) follow the same number.
 */
public final class Radiation extends SavedData {
    public record Zone(long centre, int radius, long until) {
        static final Codec<Zone> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("centre").forGetter(Zone::centre), Codec.INT.fieldOf("radius").forGetter(Zone::radius),
                Codec.LONG.fieldOf("until").forGetter(Zone::until)).apply(i, Zone::new));
    }

    private final List<Zone> zones = new ArrayList<>();
    public static final Codec<Radiation> CODEC = Zone.CODEC.listOf().xmap(l -> {
        Radiation r = new Radiation();
        r.zones.addAll(l);
        return r;
    }, r -> r.zones);
    public static final SavedDataType<Radiation> TYPE = new SavedDataType<>(PowerFactor.id("radiation"), Radiation::new, CODEC);

    public static Radiation get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /** Marks ground around {@code centre} radioactive for {@code ticks} (refreshing an overlapping zone). */
    public static void contaminate(ServerLevel level, BlockPos centre, int radius, int ticks) {
        Radiation r = get(level);
        long until = level.getGameTime() + ticks;
        for (int i = 0; i < r.zones.size(); i++) {
            Zone z = r.zones.get(i);
            if (BlockPos.of(z.centre()).distSqr(centre) < 4 && z.radius() >= radius) {
                if (z.until() < until) r.zones.set(i, new Zone(z.centre(), z.radius(), until));
                r.setDirty();
                return;
            }
        }
        r.zones.add(new Zone(centre.asLong(), radius, until));
        r.setDirty();
    }

    /** Dose rate at a position, 0..~10 (a zone is hottest at its centre and fades toward its edge and its end). */
    public static double ambient(ServerLevel level, BlockPos pos) {
        Radiation r = get(level);
        double dose = 0;
        long now = level.getGameTime();
        r.zones.removeIf(z -> z.until() < now);
        for (Zone z : r.zones) {
            double d = Math.sqrt(BlockPos.of(z.centre()).distSqr(pos));
            if (d < z.radius()) dose += 6 * (1 - d / z.radius());
        }
        return dose + FissionCherenkov.leak(level, pos);
    }

    /** What a player carries: spent fuel and waste in the inventory. */
    public static double carried(ServerPlayer player) {
        double dose = 0;
        for (ItemStack s : player.getInventory()) {
            if (s.is(ModFission.SPENT_FUEL_ROD.get()) || s.is(ModFission.SPENT_MOX_ROD.get())) dose += 1.5 * s.getCount();
            else if (s.is(ModFission.NUCLEAR_WASTE.get())) dose += 0.25 * s.getCount();
        }
        return dose;
    }

    public static boolean shielded(ServerPlayer player) {
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!(player.getItemBySlot(slot).getItem() instanceof HazmatItem)) return false;
        }
        return true;
    }

    /** Lifetime dose (the dosimeter's reading), kept with the player. */
    public static double total(ServerPlayer player) {
        return player.getPersistentData().getDoubleOr("powerfactor_dose", 0);
    }

    /** Adds a second at this dose rate to the player's lifetime dose. */
    public static void addDose(ServerPlayer player, double rate) {
        player.getPersistentData().putDouble("powerfactor_dose", total(player) + rate);
    }

    /** Once a second per player: effects, the lifetime dose, and the reading for anyone carrying a meter. */
    public static double tick(ServerPlayer player) {
        double rate = tickEffects(player);
        boolean shielded = shielded(player);
        if (!shielded && rate > 0.05 && !player.isCreative() && !player.isSpectator()) addDose(player, rate);
        boolean meter = player.getMainHandItem().getItem() instanceof RadiationMeterItem || player.getOffhandItem().getItem() instanceof RadiationMeterItem;
        double raw = ambient((ServerLevel) player.level(), player.blockPosition()) + carried(player);
        // A meter gets every reading; anyone else hears about a strong field, which speckles their view.
        if ((meter || raw > 0.15) && player.connection.hasChannel(RadiationPayload.TYPE)) {
            player.connection.send(new RadiationPayload((float) raw, (float) total(player), shielded));
        }
        return rate;
    }

    private static double tickEffects(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        double dose = ambient(level, player.blockPosition()) + carried(player);
        if (dose <= 0.05 || player.isCreative() || player.isSpectator() || shielded(player)) return shielded(player) ? 0 : dose;
        if (dose > 0.5) player.addEffect(new MobEffectInstance(MobEffects.POISON, 60, Math.min(2, (int) (dose / 3))));
        if (dose > 3) player.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, Math.min(2, (int) (dose / 5))));
        if (dose > 6) player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0));
        return dose;
    }
}
