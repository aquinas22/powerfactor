package com.powerfactor.generator;

import com.powerfactor.machine.MachineBlockEntity;
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
import org.jspecify.annotations.Nullable;

/** Shared by the generator GUIs that only need data, not slots (diesel, battery, power meter). */
public class DieselMenu extends AbstractContainerMenu {
    public static final int DATA_FE = 0, DATA_EE = 1, DATA_RPM = 2, DATA_TANK = 3, DATA_FLAGS = 4, DATA_REDSTONE = 5, DATA_STORED = 6;
    /** A second tank, for the hydrogen machines. */
    public static final int DATA_TANK2 = 7, DATA_TANK3 = 8;
    public static final int DATA_COUNT = 10;

    protected final ContainerData data;
    /** Where the machine is, for the GUI's 3D view. */
    public final BlockPos pos;
    private final ContainerLevelAccess access;
    private final @Nullable MachineBlockEntity machine;

    public DieselMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(ModMenus.DIESEL_GENERATOR.get(), id, inventory, buf.readBlockPos(), new SimpleContainerData(DATA_COUNT), null);
    }

    public DieselMenu(int id, Inventory inventory, BlockPos pos, ContainerData data, @Nullable MachineBlockEntity machine) {
        this(ModMenus.DIESEL_GENERATOR.get(), id, inventory, pos, data, machine);
    }

    protected DieselMenu(net.minecraft.world.inventory.MenuType<?> type, int id, Inventory inventory, BlockPos pos, ContainerData data,
            @Nullable MachineBlockEntity machine) {
        super(type, id);
        this.pos = pos;
        this.data = data;
        this.machine = machine;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        addDataSlots(data);
        addStandardInventorySlots(inventory, 8, 84);
    }

    public int get(int index) {
        return data.get(index);
    }

    public int unsigned(int index) {
        return data.get(index) & 0xFFFF;
    }

    public MachineBlockEntity.RedstoneMode redstoneMode() {
        return MachineBlockEntity.RedstoneMode.values()[Math.clamp(data.get(DATA_REDSTONE), 0, 2)];
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return machine != null && machine.redstoneButton(id);
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) -> player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 64, true);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /** Battery bank GUI. */
    public static class Battery extends DieselMenu {
        public Battery(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
            super(ModMenus.BATTERY_BANK.get(), id, inventory, buf.readBlockPos(), new SimpleContainerData(DATA_COUNT), null);
        }

        public Battery(int id, Inventory inventory, BlockPos pos, ContainerData data, @Nullable MachineBlockEntity machine) {
            super(ModMenus.BATTERY_BANK.get(), id, inventory, pos, data, machine);
        }
    }

    /** Power meter GUI. */
    public static class Meter extends DieselMenu {
        public Meter(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
            super(ModMenus.POWER_METER.get(), id, inventory, buf.readBlockPos(), new SimpleContainerData(DATA_COUNT), null);
        }

        public Meter(int id, Inventory inventory, BlockPos pos, ContainerData data, @Nullable MachineBlockEntity machine) {
            super(ModMenus.POWER_METER.get(), id, inventory, pos, data, machine);
        }
    }

    /** Electrolyser GUI. */
    public static class Electrolyzer extends DieselMenu {
        public Electrolyzer(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
            super(ModMenus.ELECTROLYZER.get(), id, inventory, buf.readBlockPos(), new SimpleContainerData(DATA_COUNT), null);
        }

        public Electrolyzer(int id, Inventory inventory, BlockPos pos, ContainerData data, @Nullable MachineBlockEntity machine) {
            super(ModMenus.ELECTROLYZER.get(), id, inventory, pos, data, machine);
        }
    }

    /** Fuel cell GUI. */
    public static class FuelCell extends DieselMenu {
        public FuelCell(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
            super(ModMenus.FUEL_CELL.get(), id, inventory, buf.readBlockPos(), new SimpleContainerData(DATA_COUNT), null);
        }

        public FuelCell(int id, Inventory inventory, BlockPos pos, ContainerData data, @Nullable MachineBlockEntity machine) {
            super(ModMenus.FUEL_CELL.get(), id, inventory, pos, data, machine);
        }
    }

    /** Pumpjack GUI. */
    public static class Pumpjack extends DieselMenu {
        public Pumpjack(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
            super(ModMenus.PUMPJACK.get(), id, inventory, buf.readBlockPos(), new SimpleContainerData(DATA_COUNT), null);
        }

        public Pumpjack(int id, Inventory inventory, BlockPos pos, ContainerData data, @Nullable MachineBlockEntity machine) {
            super(ModMenus.PUMPJACK.get(), id, inventory, pos, data, machine);
        }
    }

    /** Refinery GUI. */
    public static class Refinery extends DieselMenu {
        public Refinery(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
            super(ModMenus.REFINERY.get(), id, inventory, buf.readBlockPos(), new SimpleContainerData(DATA_COUNT), null);
        }

        public Refinery(int id, Inventory inventory, BlockPos pos, ContainerData data, @Nullable MachineBlockEntity machine) {
            super(ModMenus.REFINERY.get(), id, inventory, pos, data, machine);
        }
    }
}
