package com.powerfactor.radio;

import com.powerfactor.registry.ModSounds;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Game-thread side of the radio: who is talking and who is hearing whom, for the key-up click,
 * the squelch tail when a transmission ends, and the HUD.
 */
public final class RadioNet {
    private static final int GAP = 6;
    private record Air(int channel, String name, long last) {}

    private static final Map<UUID, Air> TALKING = new HashMap<>();
    private static final Map<UUID, Air> HEARING = new HashMap<>();
    private static long now;

    public static void transmitting(ServerPlayer p, int channel) {
        Air before = TALKING.put(p.getUUID(), new Air(channel, p.getPlainTextName(), now));
        if (before == null) {
            p.level().playSound(null, p.blockPosition(), ModSounds.RADIO_KEY.get(), SoundSource.PLAYERS, 0.4F, 1.0F);
            PacketDistributor.sendToPlayer(p, new RadioPayload(channel, "", true));
        }
    }

    public static void receiving(ServerPlayer listener, ServerPlayer speaker, int channel) {
        Air before = HEARING.put(listener.getUUID(), new Air(channel, speaker.getPlainTextName(), now));
        if (before == null) {
            privateSound(listener, ModSounds.RADIO_KEY.get(), 0.5F);
            PacketDistributor.sendToPlayer(listener, new RadioPayload(channel, speaker.getPlainTextName(), false));
        }
    }

    public static void tick(MinecraftServer server) {
        now++;
        TALKING.entrySet().removeIf(e -> {
            if (now - e.getValue().last() <= GAP) {
                return false;
            }
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) {
                PacketDistributor.sendToPlayer(p, new RadioPayload(0, "", true));
            }
            return true;
        });
        HEARING.entrySet().removeIf(e -> {
            if (now - e.getValue().last() <= GAP) {
                return false;
            }
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) {
                privateSound(p, ModSounds.RADIO_SQUELCH.get(), 0.45F);
                PacketDistributor.sendToPlayer(p, new RadioPayload(0, "", false));
            }
            return true;
        });
    }

    /** A sound only this player hears (their own radio's speaker). */
    private static void privateSound(ServerPlayer p, net.minecraft.sounds.SoundEvent sound, float volume) {
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.PLAYERS,
                p.getX(), p.getEyeY(), p.getZ(), volume, 1.0F, p.getRandom().nextLong()));
    }

    private RadioNet() {}
}
