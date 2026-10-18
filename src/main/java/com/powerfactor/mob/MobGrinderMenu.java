package com.powerfactor.mob;

import com.powerfactor.machine.MachineMenu;
import com.powerfactor.registry.ModMob;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

public class MobGrinderMenu extends MachineMenu {
    public static final int OUT_X = 98, OUT_Y = 24, BUTTON_XP = 0;
    private final MobGrinderBlockEntity be;

    public MobGrinderMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, null, new SimpleContainer(MobGrinderBlockEntity.OUT), new SimpleContainerData(MobGrinderBlockEntity.DATA_COUNT));
    }

    MobGrinderMenu(int id, Inventory inventory, MobGrinderBlockEntity be, Container slots, ContainerData data) {
        super(ModMob.MOB_GRINDER_MENU.get(), id, slots, data);
        this.be = be;
        for (int i = 0; i < MobGrinderBlockEntity.OUT; i++) addSlot(new OutputSlot(slots, i, OUT_X + (i % 3) * 18, OUT_Y + (i / 3) * 18));
        addPlayerInventory(inventory, 108);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (be != null && id == BUTTON_XP) {
            be.drawXp(player);
            return true;
        }
        return false;
    }

    public int energy() {
        return unsigned(MobGrinderBlockEntity.DATA_ENERGY) * 100;
    }

    public int xp() {
        return unsigned(MobGrinderBlockEntity.DATA_XP_HI) << 16 | unsigned(MobGrinderBlockEntity.DATA_XP_LO);
    }

    public int kills() {
        return data.get(MobGrinderBlockEntity.DATA_KILLS);
    }

    public boolean on() {
        return data.get(MobGrinderBlockEntity.DATA_ON) != 0;
    }
}
