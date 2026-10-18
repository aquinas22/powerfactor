package com.powerfactor.pocket;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.powerfactor.PowerFactor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Every pocket room in the world: its size tier, where its door (the Dimensional Pocket block) last stood,
 * and its Pocket Port, if one has been placed inside.
 */
public final class PocketData extends SavedData {
    public static final class Room {
        public final int id;
        public int tier;
        public String doorDim = "";
        public long doorPos;
        public long portPos;
        public int portFace = -1;

        Room(int id, int tier) {
            this.id = id;
            this.tier = tier;
        }

        static final Codec<Room> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("id").forGetter(r -> r.id),
                Codec.INT.fieldOf("tier").forGetter(r -> r.tier),
                Codec.STRING.optionalFieldOf("door_dim", "").forGetter(r -> r.doorDim),
                Codec.LONG.optionalFieldOf("door_pos", 0L).forGetter(r -> r.doorPos),
                Codec.LONG.optionalFieldOf("port_pos", 0L).forGetter(r -> r.portPos),
                Codec.INT.optionalFieldOf("port_face", -1).forGetter(r -> r.portFace)
        ).apply(i, (id, tier, dim, door, port, face) -> {
            Room r = new Room(id, tier);
            r.doorDim = dim;
            r.doorPos = door;
            r.portPos = port;
            r.portFace = face;
            return r;
        }));

        public Optional<BlockPos> port() {
            return portFace < 0 ? Optional.empty() : Optional.of(BlockPos.of(portPos));
        }

        public Direction portFacing() {
            return Direction.from3DDataValue(Math.max(0, portFace));
        }
    }

    private int next;
    private final Map<Integer, Room> rooms = new HashMap<>();

    public static final Codec<PocketData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("next").forGetter(d -> d.next),
            Room.CODEC.listOf().fieldOf("rooms").forGetter(d -> new ArrayList<>(d.rooms.values()))
    ).apply(i, (next, list) -> {
        PocketData d = new PocketData();
        d.next = next;
        for (Room r : list) d.rooms.put(r.id, r);
        return d;
    }));
    public static final SavedDataType<PocketData> TYPE = new SavedDataType<>(PowerFactor.id("pocket_rooms"), PocketData::new, CODEC);

    public static PocketData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /** A new room of the given tier; ids start at 1 (0 means "no room yet"). */
    public Room create(int tier) {
        Room r = new Room(++next, tier);
        rooms.put(r.id, r);
        setDirty();
        return r;
    }

    public Optional<Room> room(int id) {
        return Optional.ofNullable(rooms.get(id));
    }

    public List<Room> all() {
        return List.copyOf(rooms.values());
    }

    public void changed() {
        setDirty();
    }
}
