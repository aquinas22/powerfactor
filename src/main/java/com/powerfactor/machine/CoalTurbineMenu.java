package com.powerfactor.machine;


import com.powerfactor.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class CoalTurbineMenu extends AbstractContainerMenu {
    // Data slots sync as shorts, so 32-bit values are split in two.
    public static final int DATA_RPM = 0;
    public static final int DATA_FLAGS = 1;
    public static final int DATA_FUEL_PERMILLE = 2;
    public static final int DATA_FE = 3;
    public static final int DATA_EE = 4;
    public static final int DATA_STORED_LO = 5;
    public static final int DATA_STORED_HI = 6;
    public static final int DATA_CAPACITY_LO = 7;
    public static final int DATA_CAPACITY_HI = 8;
    public static final int DATA_VOLTS = 9;
    public static final int DATA_FLAG_OVERLOAD = 10;
    public static final int DATA_REDSTONE = 11;
    public static final int DATA_COUNT = 12;

    public static final int FLAG_BREAKER = 1;
    public static final int FLAG_STEAM = 2;

    public static final int FUEL_SLOT_X = 17;
    public static final int FUEL_SLOT_Y = 58;
    public static final int INVENTORY_Y = 94;

    private final Container fuel;
    private final ContainerData data;
    private final @org.jspecify.annotations.Nullable MachineBlockEntity machine;

    /** Client side. */
    public CoalTurbineMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, new SimpleContainer(1), new SimpleContainerData(DATA_COUNT), null);
    }

    public CoalTurbineMenu(int containerId, Inventory inventory, Container fuel, ContainerData data,
            @org.jspecify.annotations.Nullable MachineBlockEntity machine) {
        super(ModMenus.COAL_TURBINE.get(), containerId);
        this.machine = machine;
        checkContainerSize(fuel, 1);
        checkContainerDataCount(data, DATA_COUNT);
        this.fuel = fuel;
        this.data = data;
        addSlot(new Slot(fuel, 0, FUEL_SLOT_X, FUEL_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return CoalTurbineBlockEntity.isFuel(stack);
            }
        });
        addStandardInventorySlots(inventory, 8, INVENTORY_Y);
        addDataSlots(data);
    }

    private int unsigned(int index) {
        return data.get(index) & 0xFFFF;
    }

    public int rpm() {
        return unsigned(DATA_RPM);
    }

    public double hertz() {
        return rpm() / 60.0;
    }

    public boolean breakerClosed() {
        return (data.get(DATA_FLAGS) & FLAG_BREAKER) != 0;
    }

    public boolean steam() {
        return (data.get(DATA_FLAGS) & FLAG_STEAM) != 0;
    }

    public float fuelProgress() {
        return unsigned(DATA_FUEL_PERMILLE) / 1000F;
    }

    public int fe() {
        return unsigned(DATA_FE);
    }

    /** Signed: negative while absorbing EE from an over-corrected grid. */
    public int ee() {
        return (short) data.get(DATA_EE);
    }

    public double volts() {
        return unsigned(DATA_VOLTS) / 1000.0;
    }

    public boolean overloaded() {
        return data.get(DATA_FLAG_OVERLOAD) != 0;
    }

    public int stored() {
        return unsigned(DATA_STORED_LO) | unsigned(DATA_STORED_HI) << 16;
    }

    public int capacity() {
        return unsigned(DATA_CAPACITY_LO) | unsigned(DATA_CAPACITY_HI) << 16;
    }

    public MachineBlockEntity.RedstoneMode redstoneMode() {
        return MachineBlockEntity.RedstoneMode.values()[Math.clamp(data.get(DATA_REDSTONE), 0, 2)];
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (machine != null && buttonId == ProcessingMenu.BUTTON_REDSTONE) {
            machine.cycleRedstoneMode();
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return fuel.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int invStart = 1;
        int hotbarStart = invStart + 27;
        int end = hotbarStart + 9;
        if (slotIndex == 0) {
            if (!moveItemStackTo(stack, invStart, end, true)) {
                return ItemStack.EMPTY;
            }
        } else if (CoalTurbineBlockEntity.isFuel(stack)) {
            if (!moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (slotIndex < hotbarStart) {
            if (!moveItemStackTo(stack, hotbarStart, end, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, invStart, hotbarStart, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }
}
