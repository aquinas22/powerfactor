package com.powerfactor.machine;

import com.powerfactor.registry.ModBlocks;
import com.powerfactor.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/** No slots: a controller display for the grid the bank sits on. */
public class AutoCapacitorMenu extends AbstractContainerMenu {
    public static final int DATA_STEPS = 0, DATA_VOLTS = 1, DATA_HERTZ = 2, DATA_FE = 3, DATA_EE = 4, DATA_FLAGS = 5, DATA_TIMER = 6;
    public static final int DATA_COUNT = 7;

    private final ContainerData data;
    private final ContainerLevelAccess access;

    public AutoCapacitorMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), new SimpleContainerData(DATA_COUNT));
    }

    public AutoCapacitorMenu(int containerId, Inventory inventory, BlockPos pos, ContainerData data) {
        super(ModMenus.AUTO_CAPACITOR_BANK.get(), containerId);
        checkContainerDataCount(data, DATA_COUNT);
        this.data = data;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        addDataSlots(data);
    }

    public int steps() {
        return data.get(DATA_STEPS);
    }

    public double volts() {
        return (data.get(DATA_VOLTS) & 0xFFFF) / 1000.0;
    }

    public double hertz() {
        return (data.get(DATA_HERTZ) & 0xFFFF) / 100.0;
    }

    public int fe() {
        return (short) data.get(DATA_FE);
    }

    /** Net grid EE after correction; negative is leading. */
    public int ee() {
        return (short) data.get(DATA_EE);
    }

    public boolean onGrid() {
        return (data.get(DATA_FLAGS) & 1) != 0;
    }

    public boolean tripped() {
        return (data.get(DATA_FLAGS) & 2) != 0;
    }

    public boolean enabled() {
        return (data.get(DATA_FLAGS) & 4) != 0;
    }

    public float nextSwitch() {
        return Math.clamp(data.get(DATA_TIMER) / 40F, 0, 1);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.AUTO_CAPACITOR_BANK.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }
}
