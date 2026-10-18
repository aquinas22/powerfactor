package com.powerfactor.fission;

import com.powerfactor.machine.MachineMenu;
import com.powerfactor.registry.ModFission;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SimpleContainerData;
import org.jspecify.annotations.Nullable;

/** The reactor console's menu: no slots of its own; the screen is fed by {@link ReactorPayloads.Snapshot}. */
public class ReactorMenu extends MachineMenu {
    public final BlockPos pos;
    private final @Nullable ReactorControllerBlockEntity be;

    public ReactorMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        super(ModFission.REACTOR_MENU.get(), id, new SimpleContainer(0), new SimpleContainerData(1));
        this.pos = buf.readBlockPos();
        this.be = null;

    }

    ReactorMenu(int id, Inventory inventory, ReactorControllerBlockEntity be) {
        super(ModFission.REACTOR_MENU.get(), id, new SimpleContainer(0), new SimpleContainerData(1));
        this.pos = be.getBlockPos();
        this.be = be;

    }

    public @Nullable ReactorControllerBlockEntity reactor() {
        return be;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 64 * 64 / 4.0;
    }
}
