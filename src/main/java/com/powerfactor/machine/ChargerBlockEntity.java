package com.powerfactor.machine;

import com.powerfactor.PowerFactorConfig;
import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.scada.ScadaReading;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import org.jspecify.annotations.Nullable;

public class ChargerBlockEntity extends MachineBlockEntity implements com.powerfactor.api.MachineAudio.Looping {
    private final SimpleContainer slot = new SimpleContainer(1) {
        @Override
        public boolean canPlaceItem(int index, ItemStack stack) {
            return isChargeable(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(ChargerBlockEntity.this, player);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            ChargerBlockEntity.this.setChanged();
        }
    };
    private final ResourceHandler<ItemResource> itemHandler = VanillaContainerWrapper.of(slot);
    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(40_000, 4_000, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private int lastFe;
    private int itemStored, itemCapacity;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case ChargerMenu.DATA_FE -> lastFe;
                case ChargerMenu.DATA_STORED -> energy.getAmountAsInt() / 10;
                case ChargerMenu.DATA_CAPACITY -> energy.getCapacityAsInt() / 10;
                case ChargerMenu.DATA_ITEM_PERMILLE -> itemCapacity == 0 ? 0 : (int) (1000L * itemStored / itemCapacity);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return ChargerMenu.DATA_COUNT;
        }
    };

    public ChargerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHARGER.get(), pos, state);
    }

    public static boolean isChargeable(ItemStack stack) {
        EnergyHandler handler = ItemAccess.forStack(stack.copy()).getCapability(Capabilities.Energy.ITEM);
        return handler != null && handler.getCapacityAsLong() > 0;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        lastFe = 0;
        itemStored = itemCapacity = 0;
        EnergyHandler target = slot.getItem(0).isEmpty() ? null
                : ItemAccess.forHandlerIndex(itemHandler, 0).getCapability(Capabilities.Energy.ITEM);
        if (target != null) {
            if (enabled(level, pos)) {
                lastFe = EnergyHandlerUtil.move(energy, target, PowerFactorConfig.CHARGER_RATE.getAsInt(), null);
            }
            // Re-read: the move replaced the stack in the slot.
            EnergyHandler after = ItemAccess.forHandlerIndex(itemHandler, 0).getCapability(Capabilities.Energy.ITEM);
            if (after != null) {
                itemStored = after.getAmountAsInt();
                itemCapacity = after.getCapacityAsInt();
            }
        }
        setRunning(level, pos, state, lastFe > 0);
    }

    @Override
    public boolean soundActive() {
        return isRunning();
    }

    @Override
    public net.minecraft.sounds.SoundEvent loopSound() {
        return com.powerfactor.registry.ModSounds.CHARGER_RUN.get();
    }

    @Override
    public float loopVolume() {
        return 0.35F;
    }

    public EnergyHandler getEnergy() {
        return energy;
    }

    public ResourceHandler<ItemResource> getItemHandler() {
        return itemHandler;
    }

    @Override
    public int comparatorSignal() {
        return itemCapacity == 0 ? 0 : (int) (15L * itemStored / itemCapacity);
    }

    @Override
    protected Container inventory() {
        return slot;
    }

    @Override
    public ScadaReading scadaReading() {
        ItemStack stack = slot.getItem(0);
        Component detail = stack.isEmpty() ? Component.translatable("scada.powerfactor.charger.empty")
                : Component.translatable("scada.powerfactor.charger.charging", stack.getHoverName(),
                        itemCapacity == 0 ? 0 : (int) (100L * itemStored / itemCapacity));
        ScadaReading.Status status = lastFe > 0 ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF;
        return new ScadaReading(worldPosition, getDisplayName(), status, lastFe, 0, detail, true, remoteEnabled(), false, 1F, 60F);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, slot.getItems());
        energy.deserialize(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, slot.getItems());
        energy.serialize(output);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ChargerMenu(containerId, inventory, slot, data);
    }
}
