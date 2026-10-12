package com.powerfactor.compat.voicechat;

import com.powerfactor.PowerFactor;
import com.powerfactor.item.PoweredItem;
import com.powerfactor.radio.RadioFilter;
import com.powerfactor.radio.RadioLink;
import com.powerfactor.radio.RadioNet;
import com.powerfactor.radio.RadioTowerBlockEntity;
import com.powerfactor.radio.WalkieTalkieItem;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Simple Voice Chat integration: a player holding a switched-on walkie-talkie is heard, through a
 * radio filter, by every other player carrying one on the same channel and in range. Loaded by
 * Simple Voice Chat only when it is installed; PowerFactor runs fine without it.
 */
@ForgeVoicechatPlugin
public class WalkieTalkiePlugin implements VoicechatPlugin {
    public static final String CATEGORY = "pf_walkie";  // Simple Voice Chat allows a-z and _, at most 16 chars

    /** Per sender: their decoder. Per sender-receiver link: filter and encoder. */
    private final Map<UUID, OpusDecoder> decoders = new ConcurrentHashMap<>();
    private final Map<String, Link> links = new ConcurrentHashMap<>();

    private record Link(RadioFilter filter, OpusEncoder encoder) {}

    @Override
    public String getPluginId() {
        return PowerFactor.MODID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, e -> e.getVoicechat().registerVolumeCategory(
                e.getVoicechat().volumeCategoryBuilder().setId(CATEGORY).setName("Walkie-talkies")
                        .setDescription("PowerFactor walkie-talkie radio").build()));
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophone);
    }

    /** The walkie-talkie the sender is talking into: switched on, charged, in a hand. */
    private static ItemStack talkingInto(ServerPlayer p) {
        for (ItemStack s : new ItemStack[] {p.getMainHandItem(), p.getOffhandItem()}) {
            if (s.getItem() instanceof WalkieTalkieItem && WalkieTalkieItem.on(s)
                    && (p.getAbilities().instabuild || PoweredItem.energy(s) >= WalkieTalkieItem.PER_PACKET)) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Whether this player carries a switched-on walkie-talkie tuned to {@code channel}. */
    private static boolean listening(ServerPlayer p, int channel) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof WalkieTalkieItem && WalkieTalkieItem.on(s) && WalkieTalkieItem.channel(s) == channel) {
                return true;
            }
        }
        return false;
    }

    private void onMicrophone(MicrophonePacketEvent event) {
        VoicechatConnection senderConn = event.getSenderConnection();
        if (senderConn == null || !(senderConn.getPlayer().getPlayer() instanceof ServerPlayer sender)) {
            return;
        }
        ItemStack radio = talkingInto(sender);
        if (radio.isEmpty()) {
            return;
        }
        VoicechatServerApi api = event.getVoicechat();
        int channel = WalkieTalkieItem.channel(radio);
        var server = sender.level().getServer();
        // Charge and HUD/sounds belong on the game thread.
        server.execute(() -> {
            PoweredItem.drain(talkingInto(sender), WalkieTalkieItem.PER_PACKET, sender);
            RadioNet.transmitting(sender, channel);
        });
        byte[] opus = event.getPacket().getOpusEncodedData();
        short[] pcm = decoders.computeIfAbsent(sender.getUUID(), k -> api.createDecoder()).decode(opus);
        boolean senderTower = RadioTowerBlockEntity.nearTower(sender.level().dimension(), sender.position());
        for (ServerPlayer receiver : server.getPlayerList().getPlayers()) {
            if (receiver == sender || receiver.level() != sender.level() || !listening(receiver, channel)) {
                continue;
            }
            double noise = RadioLink.noise(receiver.distanceTo(sender), senderTower,
                    RadioTowerBlockEntity.nearTower(receiver.level().dimension(), receiver.position()));
            if (noise < 0) {
                continue;
            }
            VoicechatConnection conn = api.getConnectionOf(receiver.getUUID());
            if (conn == null || !conn.isConnected()) {
                continue;
            }
            Link link = links.computeIfAbsent(sender.getUUID() + ">" + receiver.getUUID(),
                    k -> new Link(new RadioFilter(k.hashCode()), api.createEncoder()));
            byte[] out = link.encoder().encode(link.filter().process(pcm, noise));
            api.sendStaticSoundPacketTo(conn, event.getPacket().staticSoundPacketBuilder().opusEncodedData(out).category(CATEGORY).build());
            server.execute(() -> RadioNet.receiving(receiver, sender, channel));
        }
    }
}
