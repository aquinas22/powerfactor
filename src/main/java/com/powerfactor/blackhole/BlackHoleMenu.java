package com.powerfactor.blackhole;

import com.powerfactor.machine.MachineMenu;
import com.powerfactor.registry.ModBlackHole;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

/** The Black Hole console: a 9x3 matter hopper (the injectors' store) and the live feed from {@link BlackHolePayloads}. */
public class BlackHoleMenu extends MachineMenu {
    public static final int HOPPER_X = 8, HOPPER_Y = 115;
    public final BlockPos pos;
    private final @Nullable BlackHoleBlockEntity be;

    public BlackHoleMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, null, new SimpleContainer(BlackHoleBlockEntity.FEED), buf.readBlockPos());
    }

    BlackHoleMenu(int id, Inventory inventory, BlackHoleBlockEntity be, Container feed) {
        this(id, inventory, be, feed, be.getBlockPos());
    }

    private BlackHoleMenu(int id, Inventory inventory, @Nullable BlackHoleBlockEntity be, Container feed, BlockPos pos) {
        super(ModBlackHole.MENU.get(), id, feed, new SimpleContainerData(1));
        this.pos = pos;
        this.be = be;
        for (int i = 0; i < BlackHoleBlockEntity.FEED; i++) addSlot(new Slot(feed, i, HOPPER_X + (i % 9) * 18, HOPPER_Y + (i / 9) * 18));
        addPlayerInventory(inventory, 154);
    }

    public @Nullable BlackHoleBlockEntity blackHole() {
        return be;
    }
}
