package com.powerfactor.device;

import com.powerfactor.machine.MachineMenu;
import com.powerfactor.registry.ModQuantum;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import org.jspecify.annotations.Nullable;

public class LogicControllerMenu extends MachineMenu {
    private final @Nullable LogicControllerBlockEntity controller;

    public LogicControllerMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, null, new SimpleContainerData(LogicControllerBlockEntity.DATA_COUNT));
    }

    LogicControllerMenu(int id, Inventory inventory, @Nullable LogicControllerBlockEntity controller, ContainerData data) {
        super(ModQuantum.LOGIC_CONTROLLER_MENU.get(), id, new SimpleContainer(0), data);
        this.controller = controller;
        addPlayerInventory(inventory, 108);
    }

    @Override
    public boolean stillValid(Player player) {
        return controller == null || net.minecraft.world.Container.stillValidBlockEntity(controller, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (controller == null) return false;
        controller.button(id);
        return true;
    }

    public LogicControllerBlockEntity.Program program() {
        return LogicControllerBlockEntity.Program.values()[Math.clamp(data.get(LogicControllerBlockEntity.DATA_PROGRAM), 0, 7)];
    }

    public int n() {
        return data.get(LogicControllerBlockEntity.DATA_N);
    }

    public int inputs() {
        return data.get(LogicControllerBlockEntity.DATA_INPUTS);
    }

    public boolean output() {
        return data.get(LogicControllerBlockEntity.DATA_OUTPUT) != 0;
    }

    public int state() {
        return data.get(LogicControllerBlockEntity.DATA_STATE);
    }
}
