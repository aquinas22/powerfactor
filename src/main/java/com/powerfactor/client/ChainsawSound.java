package com.powerfactor.client;

import com.powerfactor.item.ChainsawItem;
import com.powerfactor.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/** A charged chainsaw in hand idles; while it's cutting it screams. Two loops, cross-faded. */
public final class ChainsawSound extends AbstractTickableSoundInstance {
    private static ChainsawSound idle, cut;
    private final Player player;
    private final boolean cutting;
    private float level;

    private ChainsawSound(Player player, SoundEvent sound, boolean cutting) {
        super(sound, SoundSource.PLAYERS, RandomSource.create());
        this.player = player;
        this.cutting = cutting;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01F;
    }

    private static boolean held(Player p) {
        return p.getMainHandItem().getItem() instanceof ChainsawItem && ChainsawItem.powered(p.getMainHandItem());
    }

    private static boolean sawing() {
        Minecraft mc = Minecraft.getInstance();
        return mc.gameMode != null && mc.gameMode.isDestroying() || mc.options.keyAttack.isDown();
    }

    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || !held(p)) {
            return;
        }
        if (idle == null || idle.isStopped()) {
            idle = new ChainsawSound(p, ModSounds.CHAINSAW_IDLE.get(), false);
            mc.getSoundManager().play(idle);
        }
        if (cut == null || cut.isStopped()) {
            cut = new ChainsawSound(p, ModSounds.CHAINSAW_CUT.get(), true);
            mc.getSoundManager().play(cut);
        }
    }

    @Override
    public void tick() {
        if (player.isRemoved() || !held(player)) {
            stop();
            return;
        }
        x = player.getX();
        y = player.getEyeY() - 0.4;
        z = player.getZ();
        boolean active = sawing() == cutting;
        level += ((active ? 1F : 0F) - level) * (cutting ? 0.35F : 0.2F);
        volume = Math.max(0.001F, (cutting ? 0.8F : 0.4F) * level);
    }
}
