package com.powerfactor.pocket;

import com.powerfactor.PowerFactor;
import com.powerfactor.registry.ModPocket;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import org.jspecify.annotations.Nullable;

/**
 * The pocket dimension's layout. Room n is a cube centred on x = n * 1024, z = 0, floor at y = 64; tier 1,
 * 2 and 3 rooms are 7, 13 and 21 blocks across inside. Growing a room replaces its shell one size out and
 * leaves the contents where they are.
 */
public final class PocketRooms {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, PowerFactor.id("pocket"));
    public static final int SPACING = 1024, FLOOR = 64, MAX_TIER = 3;
    /** Room chunks stay loaded while the door's chunk is, so machines inside keep running. */
    public static final TicketController TICKETS = new TicketController(PowerFactor.id("pocket_room"), (level, helper) -> {
        // Doors re-add their rooms when they load; drop whatever was left from the last session.
        for (BlockPos owner : java.util.List.copyOf(helper.getBlockTickets().keySet())) helper.removeAllTickets(owner);
    });

    public static int half(int tier) {
        return switch (tier) {
            case 1 -> 3;
            case 2 -> 6;
            default -> 10;
        };
    }

    public static int size(int tier) {
        return half(tier) * 2 + 1;
    }

    public static BlockPos origin(int id) {
        return new BlockPos(id * SPACING, FLOOR, 0);
    }

    /** The room a pocket-dimension position belongs to. */
    public static int roomAt(BlockPos pos) {
        return Math.round(pos.getX() / (float) SPACING);
    }

    /**
     * GameTest servers bake only the flat preset and leave datapack dimensions out, so tests set this to
     * build rooms in the test level instead. Always null in play.
     */
    public static @Nullable ServerLevel testLevel;

    public static boolean isPocket(Level level) {
        return level.dimension() == DIMENSION || level == testLevel;
    }

    public static @Nullable ServerLevel level(net.minecraft.server.MinecraftServer server) {
        ServerLevel l = server.getLevel(DIMENSION);
        return l != null ? l : testLevel;
    }

    /** Builds (or rebuilds one size out) the shell of room {@code id}. */
    public static void build(ServerLevel pocket, int id, int tier, int oldTier) {
        BlockPos o = origin(id);
        if (oldTier > 0) shell(pocket, o, half(oldTier), Blocks.AIR.defaultBlockState());
        shell(pocket, o, half(tier), ModPocket.POCKET_WALL.get().defaultBlockState());
    }

    private static void shell(ServerLevel level, BlockPos o, int h, BlockState state) {
        int r = h + 1;
        for (int x = -r; x <= r; x++) {
            for (int y = -1; y <= 2 * h + 1; y++) {
                for (int z = -r; z <= r; z++) {
                    boolean edge = Math.abs(x) == r || Math.abs(z) == r || y == -1 || y == 2 * h + 1;
                    if (!edge) continue;
                    BlockPos p = o.offset(x, y, z);
                    BlockState now = level.getBlockState(p);
                    // When clearing an old shell, only lift wall blocks (and keep the port if it was set into the wall).
                    if (state.isAir() && !now.is(ModPocket.POCKET_WALL.get())) continue;
                    level.setBlock(p, state, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    public static void forceRoom(ServerLevel pocket, int id, int tier, boolean add) {
        if (pocket == testLevel) return;   // the shared GameTest level's forced chunks belong to the chunk loader's test
        BlockPos o = origin(id);
        int r = half(tier) + 1;
        ChunkPos a = ChunkPos.containing(o.offset(-r, 0, -r)), b = ChunkPos.containing(o.offset(r, 0, r));
        for (int x = a.x(); x <= b.x(); x++) {
            for (int z = a.z(); z <= b.z(); z++) TICKETS.forceChunk(pocket, o, x, z, add, true);
        }
    }

    /** Takes a player into room {@code room}, standing on the floor facing the middle. */
    public static void enter(ServerPlayer player, PocketData.Room room) {
        ServerLevel pocket = level(player.level().getServer());
        if (pocket == null) return;
        BlockPos o = origin(room.id);
        int h = half(room.tier);
        flash(player.level(), player.blockPosition());
        player.teleportTo(pocket, o.getX() + 0.5, o.getY(), o.getZ() + h - 0.5, Set.<Relative>of(), 180, 0, false);
        flash(pocket, player.blockPosition());
    }

    /** Takes a player out of the room they're in, to stand beside its door. */
    public static boolean leave(ServerPlayer player) {
        var server = player.level().getServer();
        int id = roomAt(player.blockPosition());
        var room = PocketData.get(server).room(id).orElse(null);
        ServerLevel to = room == null || room.doorDim.isEmpty() ? server.overworld()
                : server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(room.doorDim)));
        if (to == null) to = server.overworld();
        BlockPos door = room == null || room.doorDim.isEmpty() ? to.getRespawnData().pos() : BlockPos.of(room.doorPos);
        BlockPos stand = door.above();
        for (var d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos p = door.relative(d);
            if (to.getBlockState(p).getCollisionShape(to, p).isEmpty() && to.getBlockState(p.above()).getCollisionShape(to, p.above()).isEmpty()) {
                stand = p;
                break;
            }
        }
        flash(player.level(), player.blockPosition());
        player.teleportTo(to, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, Set.<Relative>of(), player.getYRot(), player.getXRot(), false);
        flash(to, stand);
        return true;
    }

    private static void flash(Level level, BlockPos at) {
        if (level instanceof ServerLevel s) {
            s.sendParticles(ParticleTypes.REVERSE_PORTAL, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 40, 0.4, 0.8, 0.4, 0.05);
            s.playSound(null, at, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 0.6F);
        }
    }

    private PocketRooms() {}
}
