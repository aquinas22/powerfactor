package com.powerfactor.world;

import com.powerfactor.PowerFactor;
import com.powerfactor.PowerFactorConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Rolls for meteors at night in the Overworld and launches them on a steep line toward a spot near a player. */
@EventBusSubscriber(modid = PowerFactor.MODID)
public final class MeteorShowers {
    private static final int CHECK = 200, NIGHT_TICKS = 10_000;

    @SubscribeEvent
    static void onTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (level.getGameTime() % CHECK != 0 || !PowerFactorConfig.meteors()) return;
        if (!level.isDarkOutside()) return;
        double perCheck = PowerFactorConfig.meteorChance() * CHECK / NIGHT_TICKS;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || level.getRandom().nextDouble() >= perCheck) continue;
            launch(level, player.position(), level.getRandom().nextInt(3) + 1, true);
        }
    }

    /** Launches a meteor at a spot 30-70 blocks from {@code near}; returns the entity. */
    public static MeteorEntity launch(ServerLevel level, Vec3 near, int size, boolean announce) {
        var rnd = level.getRandom();
        double a = rnd.nextDouble() * Math.PI * 2, d = 30 + rnd.nextDouble() * 40;
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(near.x + Math.cos(a) * d, 0, near.z + Math.sin(a) * d));
        // Come in from the far side of the target, steeply, so the streak crosses the sky above the player.
        double b = a + Math.PI + (rnd.nextDouble() - 0.5);
        Vec3 target = Vec3.atBottomCenterOf(ground);
        Vec3 from = target.add(Math.cos(b) * 60, Math.min(140, level.getMaxY() - target.y - 4), Math.sin(b) * 60);
        Vec3 velocity = target.subtract(from).normalize().scale(1.4);
        MeteorEntity meteor = new MeteorEntity(level, from, velocity, size);
        level.addFreshEntity(meteor);
        if (announce) {
            for (ServerPlayer p : level.players()) {
                if (p.position().distanceToSqr(target) > 300 * 300) continue;
                p.sendSystemMessage(Component.translatable("message.powerfactor.meteor", direction(p.position(), target)).withStyle(ChatFormatting.GOLD));
                level.playSound(null, p.blockPosition(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, SoundSource.AMBIENT, 1F, 0.5F);
            }
        }
        return meteor;
    }

    private static Component direction(Vec3 from, Vec3 to) {
        double ang = Math.toDegrees(Math.atan2(to.x - from.x, -(to.z - from.z)));
        String[] dirs = {"north", "north_east", "east", "south_east", "south", "south_west", "west", "north_west"};
        return Component.translatable("direction.powerfactor.compass." + dirs[(int) Math.floorMod(Math.round(ang / 45), 8)]);
    }

    private MeteorShowers() {}
}
