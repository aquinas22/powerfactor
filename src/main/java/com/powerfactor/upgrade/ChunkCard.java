package com.powerfactor.upgrade;

import com.powerfactor.PowerFactor;
import com.powerfactor.machine.MachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.world.chunk.TicketController;

/** The Chunk Loader card: while fitted, the machine keeps its own chunk loaded (ticking). */
public final class ChunkCard {
    /** Tickets owned by the machine's position; on load, any machine that has lost its card gives its ticket back. */
    public static final TicketController TICKETS = new TicketController(PowerFactor.id("chunk_card"), (level, helper) -> {
        for (BlockPos owner : java.util.List.copyOf(helper.getBlockTickets().keySet())) {
            if (!(level.getBlockEntity(owner) instanceof Upgradeable u) || !u.upgrades().chunkLoading()) helper.removeAllTickets(owner);
        }
    });

    /** Called every machine tick; does its work once a second. */
    public static void tick(ServerLevel level, MachineBlockEntity machine) {
        if (!(machine instanceof Upgradeable u) || level.getGameTime() % 20 != Math.floorMod(machine.getBlockPos().asLong(), 20)) return;
        boolean want = u.upgrades().chunkLoading();
        if (want != machine.chunkForced()) set(level, machine, want);
    }

    public static void release(ServerLevel level, MachineBlockEntity machine) {
        if (machine.chunkForced()) set(level, machine, false);
    }

    private static void set(ServerLevel level, MachineBlockEntity machine, boolean on) {
        BlockPos pos = machine.getBlockPos();
        TICKETS.forceChunk(level, pos, pos.getX() >> 4, pos.getZ() >> 4, on, true);
        machine.setChunkForced(on);
    }

    private ChunkCard() {}
}
