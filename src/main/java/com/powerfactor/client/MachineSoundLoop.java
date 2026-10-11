package com.powerfactor.client;

import com.powerfactor.api.MachineAudio;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;

/** One looping machine sound that follows its block entity's pitch and fades in and out. */
public final class MachineSoundLoop extends AbstractTickableSoundInstance {
    private static final Map<BlockPos, MachineSoundLoop> PLAYING = new HashMap<>();

    private final BlockEntity be;
    private final MachineAudio.Looping source;
    private final SoundEvent event;
    private float fade;

    private MachineSoundLoop(BlockEntity be, MachineAudio.Looping source, SoundEvent event) {
        super(event, SoundSource.BLOCKS, RandomSource.create());
        this.be = be;
        this.source = source;
        this.event = event;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01F;
        this.pitch = source.loopPitch();
        BlockPos p = be.getBlockPos();
        this.x = p.getX() + 0.5;
        this.y = p.getY() + 0.5;
        this.z = p.getZ() + 0.5;
    }

    /** Called every client tick for every looping machine. */
    public static void update(BlockEntity be) {
        if (!(be instanceof MachineAudio.Looping source)) {
            return;
        }
        MachineSoundLoop current = PLAYING.get(be.getBlockPos());
        boolean active = source.soundActive();
        if (current != null && (current.isStopped() || current.be != be)) {
            PLAYING.remove(be.getBlockPos());
            current = null;
        }
        if (active && (current == null || current.event != source.loopSound())) {
            if (current != null) {
                current.stopNow();
            }
            MachineSoundLoop loop = new MachineSoundLoop(be, source, source.loopSound());
            PLAYING.put(be.getBlockPos(), loop);
            Minecraft.getInstance().getSoundManager().play(loop);
        }
    }

    @Override
    public void tick() {
        boolean active = !be.isRemoved() && source.soundActive() && source.loopSound() == event;
        fade = Math.clamp(fade + (active ? 0.1F : -0.08F), 0, 1);
        if (fade <= 0 && !active) {
            stopNow();
            return;
        }
        volume = Math.max(0.01F, fade * source.loopVolume());
        pitch = source.loopPitch();
    }

    private void stopNow() {
        PLAYING.remove(be.getBlockPos(), this);
        stop();
    }
}
