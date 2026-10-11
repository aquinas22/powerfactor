package com.powerfactor.scada;

import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.registry.ModBlockEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * Polls its network twice a second while someone is looking at it. The terminal itself isn't a
 * device, so the base class's SCADA hooks just report it as a screen.
 */
public class ScadaTerminalBlockEntity extends MachineBlockEntity {
    private static final int POLL_TICKS = 10;

    public ScadaTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SCADA_TERMINAL.get(), pos, state);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        boolean watched = false;
        if (level.getGameTime() % POLL_TICKS == 0) {
            List<ScadaReading> readings = null;
            for (ServerPlayer player : level.players()) {
                if (player.containerMenu instanceof ScadaTerminalMenu menu && menu.terminal().equals(pos)) {
                    if (readings == null) {
                        readings = ScadaNetwork.poll(level, pos);
                    }
                    PacketDistributor.sendToPlayer(player, new ScadaPayloads.Snapshot(pos, readings));
                    watched = true;
                }
            }
            setRunning(level, pos, state, watched);
        }
    }

    @Override
    public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), ScadaReading.Status.RUNNING, 0, 0,
                net.minecraft.network.chat.Component.empty(), false, true, false, 1F, 60F);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ScadaTerminalMenu(containerId, inventory, worldPosition);
    }
}
