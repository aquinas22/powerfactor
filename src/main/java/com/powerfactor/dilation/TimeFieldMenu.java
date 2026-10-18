package com.powerfactor.dilation;

import com.powerfactor.machine.MachineMenu;
import com.powerfactor.registry.ModTime;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class TimeFieldMenu extends MachineMenu {
    public static final int CARD_X = 9, CARD_Y = 75;
    private final TimeFieldBlockEntity be;

    public TimeFieldMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, null, new SimpleContainer(1), new SimpleContainerData(TimeFieldBlockEntity.DATA_COUNT));
    }

    TimeFieldMenu(int id, Inventory inventory, TimeFieldBlockEntity be, Container cards, ContainerData data) {
        super(ModTime.TIME_FIELD_MENU.get(), id, cards, data);
        this.be = be;
        addSlot(new Slot(cards, 0, CARD_X, CARD_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(com.powerfactor.registry.ModUtility.RANGE_UPGRADE.get());
            }

            @Override
            public int getMaxStackSize() {
                return TimeFieldBlockEntity.MAX_CARDS;
            }
        });
        addPlayerInventory(inventory, 108);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (be != null && id >= TimeFieldBlockEntity.BUTTON_SPEED && id < TimeFieldBlockEntity.BUTTON_SPEED + TimeFieldBlockEntity.SPEEDS.length) {
            be.setSpeed(id - TimeFieldBlockEntity.BUTTON_SPEED);
            return true;
        }
        return false;
    }

    public int speedIndex() {
        return data.get(TimeFieldBlockEntity.DATA_SPEED);
    }

    public int energy() {
        return unsigned(TimeFieldBlockEntity.DATA_ENERGY_HI) << 16 | unsigned(TimeFieldBlockEntity.DATA_ENERGY_LO);
    }

    public int cost() {
        return unsigned(TimeFieldBlockEntity.DATA_COST_HI) << 16 | unsigned(TimeFieldBlockEntity.DATA_COST_LO);
    }

    public int targets() {
        return data.get(TimeFieldBlockEntity.DATA_TARGETS);
    }

    public int radius() {
        return data.get(TimeFieldBlockEntity.DATA_RADIUS);
    }

    public boolean on() {
        return data.get(TimeFieldBlockEntity.DATA_ON) != 0;
    }
}
