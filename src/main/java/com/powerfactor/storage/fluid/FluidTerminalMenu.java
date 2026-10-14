package com.powerfactor.storage.fluid;

import com.powerfactor.registry.ModStorage;
import com.powerfactor.storage.StorageNetwork;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import org.jspecify.annotations.Nullable;

/**
 * The Fluid Terminal's menu: the player's inventory plus a synced fluid list. Clicking a fluid with an
 * empty bucket or tank item fills it from the network; clicking with a full one empties it in.
 */
public class FluidTerminalMenu extends AbstractContainerMenu {
    public static final int ROWS = 4, COLS = 9;
    private final @Nullable FluidTerminalBlockEntity terminal;
    private final Player player;
    private List<FluidPayloads.Line> sent = List.of();
    private int sentStatus = -1, ticks;

    public FluidTerminalMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, (FluidTerminalBlockEntity) null);
    }

    FluidTerminalMenu(int id, Inventory inventory, @Nullable FluidTerminalBlockEntity terminal) {
        super(ModStorage.FLUID_TERMINAL_MENU.get(), id);
        this.terminal = terminal;
        this.player = inventory.player;
        addStandardInventorySlots(inventory, 8, 118);
    }

    @Override
    public boolean stillValid(Player player) {
        return terminal == null || terminal.usable(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        var slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), copy = stack.copy();
        // Shuffle between hotbar and main inventory, like a plain container.
        if (index < 27) {
            if (!moveItemStackTo(stack, 27, 36, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, 27, false)) {
            return ItemStack.EMPTY;
        }
        slot.setChanged();
        return copy;
    }

    public void click(ServerPlayer player, FluidResource fluid) {
        if (terminal == null) return;
        var cursor = ItemAccess.forPlayerCursor(player, this).oneByOne().getCapability(Capabilities.Fluid.ITEM);
        if (cursor == null) return;
        var network = terminal.handler();
        // Fill from the network first (when a fluid was clicked); if that moves nothing, pour the container in.
        var moved = fluid.isEmpty() ? null : ResourceHandlerUtil.moveFirst(network, cursor, f -> f.equals(fluid), Integer.MAX_VALUE, null);
        boolean pickup = moved != null;
        if (moved == null) moved = ResourceHandlerUtil.moveFirst(cursor, network, f -> true, Integer.MAX_VALUE, null);
        if (moved != null) FluidUtil.triggerSoundAndGameEvent(moved.resource(), player.level(), player.position(), player, pickup);
        broadcastChanges();
        sync(player, true);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (player instanceof ServerPlayer sp && ++ticks % 10 == 1) sync(sp, false);
    }

    private void sync(ServerPlayer player, boolean force) {
        if (terminal == null || !player.connection.hasChannel(FluidPayloads.Sync.TYPE)) return;
        StorageNetwork raw = terminal.network();
        int status = raw == null ? 3 : raw.conflict() ? 2 : !raw.online() ? 1 : 0;
        List<FluidPayloads.Line> lines = new ArrayList<>();
        if (status == 0) {
            for (var e : raw.fluidTotals().entrySet()) lines.add(new FluidPayloads.Line(e.getKey(), e.getValue()));
            lines.sort(Comparator.comparingLong(FluidPayloads.Line::amount).reversed());
        }
        if (!force && status == sentStatus && lines.equals(sent)) return;
        sent = lines;
        sentStatus = status;
        player.connection.send(new FluidPayloads.Sync(containerId, status, lines.size() > 512 ? lines.subList(0, 512) : lines));
    }
}
