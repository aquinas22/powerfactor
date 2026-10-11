package com.powerfactor.grid;

import com.powerfactor.PowerFactor;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = PowerFactor.MODID)
public final class GridEvents {
    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            GridManager.get(level).tick();
        }
    }

    @SubscribeEvent
    static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            GridManager.get(level).onChunkUnloaded(event.getChunk().getPos());
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        GridManager.clearAll();
        com.powerfactor.ender.EnderNetwork.clear();
    }

    private GridEvents() {}
}
