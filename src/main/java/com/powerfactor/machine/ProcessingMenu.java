package com.powerfactor.machine;

import com.powerfactor.upgrade.UpgradeInventory;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Machine slots, the four upgrade slots (shown only while the Upgrades tab is open), the player
 * inventory, and the shared data block. Config buttons arrive as menu button clicks.
 */
public abstract class ProcessingMenu extends MachineMenu {
    public static final int DATA_PROGRESS = 0, DATA_DURATION = 1, DATA_FE = 2, DATA_EE = 3;
    public static final int DATA_STORED_LO = 4, DATA_STORED_HI = 5, DATA_CAPACITY_LO = 6, DATA_CAPACITY_HI = 7;
    public static final int DATA_VOLTS = 8, DATA_HERTZ = 9, DATA_STATE = 10, DATA_SPEED = 11, DATA_FLAGS = 12, DATA_SIDES = 13, DATA_TANK = 14;
    public static final int DATA_COUNT = 15;

    public static final int BUTTON_REDSTONE = 0, BUTTON_AUTO_EJECT = 1, BUTTON_AUTO_INPUT = 2;
    /** Cycle a face forward: BUTTON_SIDE_NEXT + face ordinal; backward: BUTTON_SIDE_PREV + ordinal. */
    public static final int BUTTON_SIDE_NEXT = 10, BUTTON_SIDE_PREV = 20;
    public static final int BUTTON_REDSTONE_SET = 30;
    /** Where the Upgrades tab draws its 2x2 slot grid (see MachineScreen). */
    public static final int UPGRADE_X = 212, UPGRADE_Y = 24;

    private final @Nullable ProcessingBlockEntity machine;
    private final UpgradeInventory upgrades;
    private final java.util.List<UpgradeSlot> upgradeSlots = new java.util.ArrayList<>();

    protected ProcessingMenu(MenuType<?> type, int containerId, Container items, UpgradeInventory upgrades, ContainerData data,
            @Nullable ProcessingBlockEntity machine) {
        super(type, containerId, items, data);
        this.machine = machine;
        this.upgrades = upgrades;
    }

    /** Where the machine is (client side, for the GUI's 3D view); null if not sent. */
    public net.minecraft.core.@org.jspecify.annotations.Nullable BlockPos pos;
    /** Top of the player inventory in the v2 machine GUIs. */
    public static final int INVENTORY_V2 = 116;

    /** Call after adding the machine's own slots. */
    protected void addUpgradesAndInventory(Inventory inventory, int inventoryTop) {
        for (int i = 0; i < UpgradeInventory.SLOTS; i++) {
            UpgradeSlot slot = new UpgradeSlot(upgrades, i, UPGRADE_X + (i % 2) * 20, UPGRADE_Y + (i / 2) * 20);
            upgradeSlots.add(slot);
            addSlot(slot);
        }
        addPlayerInventory(inventory, inventoryTop);
    }

    /** The client screen shows upgrade slots only while their tab is open. */
    public void setUpgradeSlotsActive(boolean active) {
        upgradeSlots.forEach(s -> s.active = active);
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (machine == null) {
            return false;
        }
        if (machine.redstoneButton(buttonId)) return true;
        SideConfig.Face[] faces = SideConfig.Face.values();
        if (buttonId >= BUTTON_SIDE_NEXT && buttonId < BUTTON_SIDE_NEXT + faces.length) {
            machine.cycleSide(faces[buttonId - BUTTON_SIDE_NEXT], 1);
            return true;
        }
        if (buttonId >= BUTTON_SIDE_PREV && buttonId < BUTTON_SIDE_PREV + faces.length) {
            machine.cycleSide(faces[buttonId - BUTTON_SIDE_PREV], -1);
            return true;
        }
        switch (buttonId) {
            case BUTTON_REDSTONE -> machine.cycleRedstoneMode();
            case BUTTON_AUTO_EJECT -> machine.toggleAutoEject();
            case BUTTON_AUTO_INPUT -> machine.toggleAutoInput();
            default -> {
                return false;
            }
        }
        return true;
    }

    public float progress() {
        int d = unsigned(DATA_DURATION);
        return d == 0 ? 0 : Math.min(1, (float) unsigned(DATA_PROGRESS) / d);
    }

    public int fe() {
        return unsigned(DATA_FE);
    }

    public int ee() {
        return (short) data.get(DATA_EE);
    }

    public int stored() {
        return unsigned(DATA_STORED_LO) | unsigned(DATA_STORED_HI) << 16;
    }

    public int capacity() {
        return unsigned(DATA_CAPACITY_LO) | unsigned(DATA_CAPACITY_HI) << 16;
    }

    public double volts() {
        return unsigned(DATA_VOLTS) / 1000.0;
    }

    public double hertz() {
        return unsigned(DATA_HERTZ) / 100.0;
    }

    public Drive.State driveState() {
        return Drive.State.values()[data.get(DATA_STATE) & 3];
    }

    public boolean stalled() {
        return (data.get(DATA_STATE) & 4) != 0;
    }

    public double speed() {
        return unsigned(DATA_SPEED) / 1000.0;
    }

    public boolean autoEject() {
        return (data.get(DATA_FLAGS) & 1) != 0;
    }

    public int tank() {
        return unsigned(DATA_TANK);
    }

    public boolean autoInput() {
        return (data.get(DATA_FLAGS) & 8) != 0;
    }

    public SideConfig.Mode side(SideConfig.Face face) {
        return SideConfig.unpack(data.get(DATA_SIDES) & 0xFFFF, face);
    }

    public MachineBlockEntity.RedstoneMode redstoneMode() {
        return MachineBlockEntity.RedstoneMode.values()[Math.clamp((data.get(DATA_FLAGS) >> 1) & 3, 0, 2)];
    }

    public static final class UpgradeSlot extends Slot {
        private final UpgradeInventory upgrades;
        boolean active = true;

        UpgradeSlot(UpgradeInventory upgrades, int index, int x, int y) {
            super(upgrades, index, x, y);
            this.upgrades = upgrades;
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return upgrades.accepts(getContainerSlot(), stack);
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return stack.getMaxStackSize();
        }
    }
}
