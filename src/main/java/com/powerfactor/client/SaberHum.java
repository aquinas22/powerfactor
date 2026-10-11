package com.powerfactor.client;

import com.powerfactor.item.ArcSaberItem;
import com.powerfactor.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/** The lit saber's hum, following the player and swelling as they move. */
public final class SaberHum extends AbstractTickableSoundInstance {
    private static SaberHum playing;
    private final Player player;

    private SaberHum(Player player) {
        super(ModSounds.SWORD_HUM.get(), SoundSource.PLAYERS, RandomSource.create());
        this.player = player;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.35F;
    }

    public static void clientTick() {
        Player p = Minecraft.getInstance().player;
        boolean lit = p != null && (ArcSaberItem.lit(p.getMainHandItem()) && p.getMainHandItem().getItem() instanceof ArcSaberItem
                || ArcSaberItem.lit(p.getOffhandItem()) && p.getOffhandItem().getItem() instanceof ArcSaberItem);
        if (lit && (playing == null || playing.isStopped())) {
            playing = new SaberHum(p);
            Minecraft.getInstance().getSoundManager().play(playing);
        }
    }

    @Override
    public void tick() {
        boolean lit = ArcSaberItem.lit(player.getMainHandItem()) || ArcSaberItem.lit(player.getOffhandItem());
        if (!lit || player.isRemoved()) {
            stop();
            return;
        }
        x = player.getX();
        y = player.getEyeY();
        z = player.getZ();
        double speed = player.getDeltaMovement().length();
        volume = 0.3F + (float) Math.min(0.3, speed);
        pitch = 1.0F + (float) Math.min(0.15, speed * 0.4);
    }
}
