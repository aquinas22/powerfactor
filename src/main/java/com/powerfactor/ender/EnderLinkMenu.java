package com.powerfactor.ender;

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

public class EnderLinkMenu extends AbstractContainerMenu {
    public static final int DATA_CHANNEL = 0, DATA_FLOW = 1, DATA_STORED = 2, DATA_INTERFERENCE = 3, DATA_EE = 4, DATA_REDSTONE = 5;
    public static final int DATA_COUNT = 6;
    public static final int BUTTON_CHANNEL = 100;

    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final @Nullable EnderLinkBlockEntity link;
    private final boolean transmitter;

    public EnderLinkMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos(), new SimpleContainerData(DATA_COUNT), null);
    }

    public EnderLinkMenu(int id, Inventory inventory, BlockPos pos, ContainerData data, @Nullable EnderLinkBlockEntity link) {
        super(ModMenus.ENDER_LINK.get(), id);
        this.data = data;
        this.link = link;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        this.transmitter = inventory.player.level().getBlockState(pos).getBlock() instanceof EnderLinkBlock b && b.transmitter();
        addDataSlots(data);
        addStandardInventorySlots(inventory, 8, 84);
    }

    public boolean transmitter() {
        return transmitter;
    }

    public int channel() {
        return data.get(DATA_CHANNEL);
    }

    public int flow() {
        return data.get(DATA_FLOW);
    }

    public int stored() {
        return (data.get(DATA_STORED) & 0xFFFF) * 10;
    }

    public double interference() {
        return data.get(DATA_INTERFERENCE) / 100.0;
    }

    public int ee() {
        return data.get(DATA_EE);
    }

    public MachineBlockEntity.RedstoneMode redstoneMode() {
        return MachineBlockEntity.RedstoneMode.values()[Math.clamp(data.get(DATA_REDSTONE), 0, 2)];
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (link == null) {
            return false;
        }
        if (id >= BUTTON_CHANNEL && id < BUTTON_CHANNEL + 16) {
            link.setChannel(id - BUTTON_CHANNEL);
            return true;
        }
        if (id == com.powerfactor.machine.ProcessingMenu.BUTTON_REDSTONE) {
            link.cycleRedstoneMode();
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) -> level.getBlockEntity(pos) instanceof EnderLinkBlockEntity
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 64, true);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
