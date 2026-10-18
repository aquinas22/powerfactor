package com.powerfactor.drone;

import com.powerfactor.PowerFactor;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** The Drone Hub console's live feed: drones, veins, the AI log, survey progress. */
public final class DronePayloads {
    public record Snapshot(BlockPos pos, CompoundTag data) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(PowerFactor.id("drone_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> STREAM_CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, Snapshot::pos,
                ByteBufCodecs.COMPOUND_TAG, Snapshot::data, Snapshot::new);

        @Override
        public Type<Snapshot> type() {
            return TYPE;
        }
    }

    public static java.util.function.Consumer<Snapshot> clientHandler = s -> {};

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(Snapshot.TYPE, Snapshot.STREAM_CODEC, (p, ctx) -> clientHandler.accept(p));
    }

    static Snapshot snapshot(ServerLevel level, DroneHubBlockEntity hub) {
        BlockPos o = hub.getBlockPos();
        CompoundTag t = new CompoundTag();
        ListTag drones = new ListTag();
        for (var id : hub.droneIds()) {
            if (!(level.getEntity(id) instanceof MiningDroneEntity d)) continue;
            CompoundTag e = new CompoundTag();
            e.putFloat("x", (float) (d.getX() - o.getX() - 0.5));
            e.putFloat("y", (float) (d.getY() - o.getY()));
            e.putFloat("z", (float) (d.getZ() - o.getZ() - 0.5));
            e.putInt("s", d.state());
            e.putInt("i", d.index());
            e.putInt("b", d.battery());
            e.putInt("m", d.mined());
            drones.add(e);
        }
        t.put("drones", drones);
        ListTag veins = new ListTag();
        for (var v : hub.veins()) {
            CompoundTag e = new CompoundTag();
            BlockPos p = v.blocks().getFirst();
            e.putInt("x", p.getX() - o.getX());
            e.putInt("y", p.getY() - o.getY());
            e.putInt("z", p.getZ() - o.getZ());
            e.putInt("n", v.blocks().size());
            e.putInt("v", v.value());
            e.putInt("c", v.ore().getMapColor(level, p).col);
            e.putBoolean("a", hub.assigned().containsKey(v.id()));
            e.putBoolean("h", v.hazard());
            veins.add(e);
            if (veins.size() >= 120) break;
        }
        t.put("veins", veins);
        ListTag log = new ListTag();
        log.addAll(hub.logLines());
        t.put("log", log);
        t.putFloat("survey", hub.surveyProgress());
        t.putInt("energy", hub.getEnergy().getAmountAsInt());
        t.putInt("mined", hub.totalMined());
        return new Snapshot(o, t);
    }

    static void broadcast(ServerLevel level, DroneHubBlockEntity hub) {
        Snapshot s = null;
        for (ServerPlayer p : level.players()) {
            if (p.containerMenu instanceof DroneHubMenu m && m.pos.equals(hub.getBlockPos())) {
                if (s == null) s = snapshot(level, hub);
                p.connection.send(s);
            }
        }
    }

    private DronePayloads() {}
}
