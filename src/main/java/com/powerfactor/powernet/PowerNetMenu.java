package com.powerfactor.powernet;

import com.powerfactor.registry.ModPowerNet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** A power network device's screen: no slots; everything travels in {@link PowerNetPayloads}. */
public class PowerNetMenu extends AbstractContainerMenu {
    public final BlockPos pos;

    public PowerNetMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos());
    }

    public PowerNetMenu(int id, Inventory inventory, BlockPos pos) {
        super(ModPowerNet.MENU.get(), id);
        this.pos = pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof PowerNodeBlockEntity && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 64;
    }
}
