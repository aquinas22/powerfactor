package com.powerfactor.defense;

import com.powerfactor.machine.MachineMenu;
import com.powerfactor.registry.ModDefense;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import org.jspecify.annotations.Nullable;

public class ShieldGeneratorMenu extends MachineMenu {
    private final @Nullable ShieldGeneratorBlockEntity shield;

    public ShieldGeneratorMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, null, new SimpleContainerData(ShieldGeneratorBlockEntity.DATA_COUNT));
    }

    ShieldGeneratorMenu(int id, Inventory inventory, @Nullable ShieldGeneratorBlockEntity shield, ContainerData data) {
        super(ModDefense.SHIELD_MENU.get(), id, new SimpleContainer(0), data);
        this.shield = shield;
        addPlayerInventory(inventory, 108);
    }

    @Override
    public boolean stillValid(Player player) {
        return shield == null || net.minecraft.world.Container.stillValidBlockEntity(shield, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (shield == null) return false;
        shield.button(id);
        return true;
    }

    public int radius() {
        return data.get(ShieldGeneratorBlockEntity.DATA_RADIUS);
    }

    public long energy() {
        return data.get(ShieldGeneratorBlockEntity.DATA_ENERGY) * 1000L;
    }

    public boolean on() {
        return data.get(ShieldGeneratorBlockEntity.DATA_ON) != 0;
    }

    public int hits() {
        return data.get(ShieldGeneratorBlockEntity.DATA_HITS);
    }

    public int upkeep() {
        return data.get(ShieldGeneratorBlockEntity.DATA_UPKEEP);
    }
}
