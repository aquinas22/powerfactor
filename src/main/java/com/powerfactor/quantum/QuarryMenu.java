package com.powerfactor.quantum;

import com.powerfactor.machine.MachineMenu;
import com.powerfactor.registry.ModQuantum;
import com.powerfactor.upgrade.UpgradeItem;
import com.powerfactor.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public class QuarryMenu extends MachineMenu {
    public static final int UPGRADE_X = 152, UPGRADE_Y = 22, INVENTORY_Y = 108;
    private final @Nullable QuarryBlockEntity quarry;
    private final BlockPos pos;

    public QuarryMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos(), null, new SimpleContainer(QuarryBlockEntity.UPGRADES), new SimpleContainerData(QuarryBlockEntity.DATA_COUNT));
    }

    QuarryMenu(int id, Inventory inventory, QuarryBlockEntity quarry, Container upgrades, ContainerData data) {
        this(id, inventory, quarry.getBlockPos(), quarry, upgrades, data);
    }

    private QuarryMenu(int id, Inventory inventory, BlockPos pos, @Nullable QuarryBlockEntity quarry, Container upgrades, ContainerData data) {
        super(ModQuantum.QUARRY_MENU.get(), id, upgrades, data);
        this.quarry = quarry;
        this.pos = pos;
        for (int i = 0; i < QuarryBlockEntity.UPGRADES; i++) {
            addSlot(new Slot(upgrades, i, UPGRADE_X, UPGRADE_Y + i * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof UpgradeItem u && u.type() == UpgradeType.SPEED;
                }
            });
        }
        addPlayerInventory(inventory, INVENTORY_Y);
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    public boolean stillValid(Player player) {
        return quarry == null || !quarry.isRemoved() && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) < 64;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return quarry != null && quarry.button(id);
    }

    public QuarryBlockEntity.Status status() {
        return QuarryBlockEntity.Status.values()[Math.clamp(data.get(QuarryBlockEntity.DATA_STATUS), 0, QuarryBlockEntity.Status.values().length - 1)];
    }

    public int size() {
        return unsigned(QuarryBlockEntity.DATA_SIZE);
    }

    public int depth() {
        return unsigned(QuarryBlockEntity.DATA_DEPTH);
    }

    public int totalDepth() {
        return Math.max(1, unsigned(QuarryBlockEntity.DATA_TOTAL_DEPTH));
    }

    public boolean silk() {
        return (data.get(QuarryBlockEntity.DATA_FLAGS) & QuarryBlockEntity.FLAG_SILK) != 0;
    }

    public boolean voidStone() {
        return (data.get(QuarryBlockEntity.DATA_FLAGS) & QuarryBlockEntity.FLAG_VOID) != 0;
    }

    public int fortune() {
        return data.get(QuarryBlockEntity.DATA_FORTUNE);
    }

    public long energy() {
        return ((long) unsigned(QuarryBlockEntity.DATA_ENERGY_HI) << 16) | unsigned(QuarryBlockEntity.DATA_ENERGY_LO);
    }

    public int fe() {
        return unsigned(QuarryBlockEntity.DATA_FE);
    }

    public long mined() {
        return ((long) unsigned(QuarryBlockEntity.DATA_MINED_HI) << 16) | unsigned(QuarryBlockEntity.DATA_MINED_LO);
    }

    public boolean quantum() {
        return data.get(QuarryBlockEntity.DATA_TIER) == 1;
    }
}
