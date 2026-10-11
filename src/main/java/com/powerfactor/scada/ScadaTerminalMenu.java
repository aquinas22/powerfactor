package com.powerfactor.scada;

import com.powerfactor.registry.ModBlocks;
import com.powerfactor.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

/** No slots; it exists so the server knows who is looking at which terminal. */
public class ScadaTerminalMenu extends AbstractContainerMenu {
    private final BlockPos terminal;
    private final ContainerLevelAccess access;

    public ScadaTerminalMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos());
    }

    public ScadaTerminalMenu(int containerId, Inventory inventory, BlockPos terminal) {
        super(ModMenus.SCADA_TERMINAL.get(), containerId);
        this.terminal = terminal;
        this.access = ContainerLevelAccess.create(inventory.player.level(), terminal);
    }

    public BlockPos terminal() {
        return terminal;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.SCADA_TERMINAL.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }
}
