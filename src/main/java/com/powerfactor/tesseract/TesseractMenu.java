package com.powerfactor.tesseract;

import com.powerfactor.registry.ModTesseract;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Tesseract settings: frequency (0-9999), the three channel modes and private/shared. No slots. */
public class TesseractMenu extends AbstractContainerMenu {
    public static final int BUTTON_CHANNEL = 0, BUTTON_SHARED = 3, BUTTON_FREQ = 4;   // 4..9: -100 -10 -1 +1 +10 +100
    public static final int[] STEPS = {-100, -10, -1, 1, 10, 100};
    public static final int DATA_MODES = 0, DATA_FREQ = 3, DATA_SHARED = 4, DATA_LINKED = 5, DATA_COUNT = 6;
    private final @Nullable TesseractBlockEntity be;
    private final ContainerData data;
    private final BlockPos pos;

    public TesseractMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        super(ModTesseract.TESSERACT_MENU.get(), id);
        this.be = null;
        this.pos = buf.readBlockPos();
        this.data = new SimpleContainerData(DATA_COUNT);
        addDataSlots(data);
    }

    TesseractMenu(int id, Inventory inventory, TesseractBlockEntity be) {
        super(ModTesseract.TESSERACT_MENU.get(), id);
        this.be = be;
        this.pos = be.getBlockPos();
        this.data = new ContainerData() {
            @Override
            public int get(int i) {
                return switch (i) {
                    case 0, 1, 2 -> be.mode(TesseractBlockEntity.Channel.values()[i]);
                    case DATA_FREQ -> be.frequency();
                    case DATA_SHARED -> be.shared() ? 1 : 0;
                    case DATA_LINKED -> be.linked();
                    default -> 0;
                };
            }

            @Override
            public void set(int i, int v) {}

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
        addDataSlots(data);
    }

    public BlockPos pos() {
        return pos;
    }

    public int mode(int c) {
        return data.get(DATA_MODES + c);
    }

    public int frequency() {
        return data.get(DATA_FREQ);
    }

    public boolean shared() {
        return data.get(DATA_SHARED) != 0;
    }

    public int linked() {
        return data.get(DATA_LINKED);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (be == null) return false;
        if (id >= BUTTON_CHANNEL && id < BUTTON_CHANNEL + 3) be.cycle(TesseractBlockEntity.Channel.values()[id - BUTTON_CHANNEL]);
        else if (id == BUTTON_SHARED) be.toggleShared();
        else if (id >= BUTTON_FREQ && id < BUTTON_FREQ + STEPS.length) be.setFrequency(Math.floorMod(be.frequency() + STEPS[id - BUTTON_FREQ], 10_000));
        else return false;
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return be == null || !be.isRemoved() && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) < 64;
    }
}
