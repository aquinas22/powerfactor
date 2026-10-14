package com.powerfactor.quantum;

import com.powerfactor.machine.MachineMenu;
import com.powerfactor.registry.ModQuantum;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import org.jspecify.annotations.Nullable;

public class FusionMenu extends MachineMenu {
    private final @Nullable FusionReactorBlockEntity reactor;

    public FusionMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, null, new SimpleContainerData(FusionReactorBlockEntity.DATA_COUNT));
    }

    FusionMenu(int id, Inventory inventory, @Nullable FusionReactorBlockEntity reactor, ContainerData data) {
        super(ModQuantum.FUSION_MENU.get(), id, new SimpleContainer(0), data);
        this.reactor = reactor;
        addPlayerInventory(inventory, 108);
    }

    @Override
    public boolean stillValid(Player player) {
        return reactor == null || net.minecraft.world.Container.stillValidBlockEntity(reactor, player);
    }

    public int state() {
        return data.get(FusionReactorBlockEntity.DATA_STATE);
    }

    public float temperature() {
        return data.get(FusionReactorBlockEntity.DATA_TEMP) / 1000F;
    }

    public int output() {
        return unsigned(FusionReactorBlockEntity.DATA_OUT_HI) << 16 | unsigned(FusionReactorBlockEntity.DATA_OUT_LO);
    }

    public int fuel() {
        return data.get(FusionReactorBlockEntity.DATA_FUEL);
    }

    public long energy() {
        return (data.get(FusionReactorBlockEntity.DATA_ENERGY) & 0xFFFFFFFFL) * 10_000L;
    }

    public boolean formed() {
        return data.get(FusionReactorBlockEntity.DATA_FORMED) != 0;
    }
}
