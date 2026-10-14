package com.powerfactor.defense;

import com.powerfactor.machine.MachineMenu;
import com.powerfactor.registry.ModDefense;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import org.jspecify.annotations.Nullable;

public class TurretMenu extends MachineMenu {
    private final TurretBlockEntity.Tier tier;
    private final @Nullable TurretBlockEntity turret;

    private final BlockPos pos;

    public TurretMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos(), null, new SimpleContainerData(TurretBlockEntity.DATA_COUNT));
    }

    TurretMenu(int id, Inventory inventory, TurretBlockEntity turret, ContainerData data) {
        this(id, inventory, turret.getBlockPos(), turret, data);
    }

    private TurretMenu(int id, Inventory inventory, BlockPos pos, @Nullable TurretBlockEntity turret, ContainerData data) {
        super(ModDefense.TURRET_MENU.get(), id, new SimpleContainer(0), data);
        this.pos = pos;
        this.tier = turret != null ? turret.tier()
                : inventory.player.level().getBlockEntity(pos) instanceof TurretBlockEntity t ? t.tier() : TurretBlockEntity.Tier.PLASMA;
        this.turret = turret;
        addPlayerInventory(inventory, 108);
    }

    /** The turret's position (the client screen plots nearby creatures around it). */
    public BlockPos pos() {
        return pos;
    }

    public TurretBlockEntity.Tier tier() {
        return tier;
    }

    @Override
    public boolean stillValid(Player player) {
        return turret == null || net.minecraft.world.Container.stillValidBlockEntity(turret, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (turret == null) return false;
        switch (id) {
            case TurretBlockEntity.BUTTON_ANIMALS -> turret.toggle(TurretBlockEntity.MODE_ANIMALS);
            case TurretBlockEntity.BUTTON_PLAYERS -> turret.toggle(TurretBlockEntity.MODE_PLAYERS);
            default -> {
                return false;
            }
        }
        return true;
    }

    public long energy() {
        return (long) unsigned(TurretBlockEntity.DATA_ENERGY_HI) << 16 | unsigned(TurretBlockEntity.DATA_ENERGY_LO);
    }

    public int mode() {
        return data.get(TurretBlockEntity.DATA_MODE);
    }

    public boolean tracking() {
        return data.get(TurretBlockEntity.DATA_TARGET) != 0;
    }

    public int kills() {
        return data.get(TurretBlockEntity.DATA_KILLS);
    }

    public float charge() {
        return data.get(TurretBlockEntity.DATA_CHARGE) / 1000F;
    }
}
