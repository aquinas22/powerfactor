package com.powerfactor.steam;

import com.powerfactor.machine.CoalTurbineBlockEntity;
import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.machine.MachineMenu;
import com.powerfactor.machine.ProcessingMenu;
import com.powerfactor.registry.ModMenus;
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

public final class SteamBoilerMenu extends MachineMenu {
    public static final int WATER = 0, STEAM = 1, HEAT = 2, PRESSURE = 3, FUEL = 4, FLAGS = 5, REDSTONE = 6, RATE = 7, COUNT = 8;
    private final @Nullable SteamBoilerBlockEntity boiler;
    public final BlockPos pos;

    public SteamBoilerMenu(int id, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(id, inventory, data.readBlockPos(), new SimpleContainer(1), new SimpleContainerData(COUNT), null);
    }
    public SteamBoilerMenu(int id, Inventory inventory, BlockPos pos, Container fuel, ContainerData data, @Nullable SteamBoilerBlockEntity boiler) {
        super(ModMenus.STEAM_BOILER.get(), id, fuel, data);
        this.pos = pos;
        this.boiler = boiler;
        addSlot(new Slot(fuel, 0, 16, 52) {
            @Override public boolean mayPlace(ItemStack stack) { return CoalTurbineBlockEntity.isFuel(stack); }
        });
        addPlayerInventory(inventory, 116);
    }
    public int get(int index) { return data.get(index) & 0xFFFF; }
    public MachineBlockEntity.RedstoneMode redstoneMode() { return MachineBlockEntity.RedstoneMode.values()[Math.clamp(get(REDSTONE), 0, 2)]; }
    @Override public boolean clickMenuButton(Player player, int id) {
        return boiler != null && boiler.redstoneButton(id);
    }
}
