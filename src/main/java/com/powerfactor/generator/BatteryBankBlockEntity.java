package com.powerfactor.generator;

import com.powerfactor.api.Bus;
import com.powerfactor.grid.BusLocator;
import com.powerfactor.grid.PowerGrid;
import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.scada.ScadaReading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Grid battery. Soaks up surplus FE like any load; when its grid sags (frequency under 59.7 Hz or
 * voltage under 0.95 pu) it pushes power back in to hold it up. Inverter-fed: FE only.
 */
public class BatteryBankBlockEntity extends MachineBlockEntity implements com.powerfactor.api.GridStorage {
    public static final int CAPACITY = 2_000_000, RATE = 1_024;
    public static final int FLAG_DISABLED = 2;
    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CAPACITY, RATE, RATE) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private int lastIn, lastOut;
    private int previous;
    private boolean supporting;
    private float shownCharge, previousCharge;
    private boolean visualsInitialized;
    private int visualFlow, syncedCharge = -1, syncedFlow;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case DieselMenu.DATA_FE -> lastOut;
                case DieselMenu.DATA_EE -> lastIn;
                case DieselMenu.Battery.DATA_STORED_LO -> energy.getAmountAsInt() & 0xFFFF;
                case DieselMenu.Battery.DATA_STORED_HI -> energy.getAmountAsInt() >>> 16;
                case DieselMenu.DATA_FLAGS -> (supporting ? 1 : 0) | (!controlEnabled() ? FLAG_DISABLED : 0);
                case DieselMenu.DATA_REDSTONE -> redstoneMode().ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return DieselMenu.DATA_COUNT;
        }
    };

    public BatteryBankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BATTERY_BANK.get(), pos, state);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        int now = energy.getAmountAsInt();
        lastIn = Math.max(0, now - previous);
        lastOut = Math.max(0, previous - now);
        Bus bus = BusLocator.find(level, pos);
        supporting = now > 0 && enabled(level, pos) && bus instanceof PowerGrid grid && grid.energized()
                && (grid.hertz() < 59.7 || grid.volts() < 0.95);
        if (supporting && bus instanceof PowerGrid grid) {
            try (Transaction tx = Transaction.openRoot()) {
                int moved = grid.handler().insert(Math.min(RATE, now), tx);
                tx.commit();
                energy.set(now - moved);
                lastOut += moved;
            }
        }
        previous = energy.getAmountAsInt();
        setRunning(level, pos, state, energy.getAmountAsInt() > CAPACITY / 5);
        int charge = (int) Math.ceil(1000.0 * energy.getAmountAsInt() / CAPACITY);
        int flow = lastOut > 0 ? -1 : lastIn > 0 ? 1 : 0;
        if ((flow == 0) != (syncedFlow == 0) || flow * syncedFlow < 0
                || level.getGameTime() % 4 == 0 && (charge != syncedCharge || flow != syncedFlow)) {
            syncedCharge = charge; syncedFlow = flow;
            level.sendBlockUpdated(pos, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override public void clientTick() {
        super.clientTick(); previousCharge = shownCharge;
        shownCharge += ((float) energy.getAmountAsInt() / CAPACITY - shownCharge) * 0.14F;
    }

    public float shownCharge(float partial) { return previousCharge + (shownCharge - previousCharge) * Math.clamp(partial, 0, 1); }
    public int visualFlow() { return visualFlow; }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup) {
        var tag = saveCustomOnly(lookup);
        tag.putInt("battery_flow", lastOut > 0 ? -1 : lastIn > 0 ? 1 : 0);
        return tag;
    }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    public EnergyHandler getEnergy() {
        return energy;
    }

    @Override public Role role(net.minecraft.core.Direction face) { return supporting ? Role.SOURCE : Role.LOAD; }

    private boolean controlEnabled() { return !(level instanceof ServerLevel server) || enabled(server, worldPosition); }

    @Override
    public int comparatorSignal() {
        return (int) (15L * energy.getAmountAsInt() / CAPACITY);
    }

    @Override
    public ScadaReading scadaReading() {
        boolean enabled = controlEnabled(), empty = energy.getAmountAsInt() == 0;
        return new ScadaReading(worldPosition, getDisplayName(), !enabled ? ScadaReading.Status.OFF : empty ? ScadaReading.Status.WARNING
                : supporting && lastOut > 0 ? ScadaReading.Status.WARNING : ScadaReading.Status.RUNNING,
                lastOut, 0, Component.translatable(!enabled ? "scada.powerfactor.battery.disabled" : empty ? "scada.powerfactor.battery.empty"
                        : supporting && lastOut > 0 ? "scada.powerfactor.battery.support" : "scada.powerfactor.battery",
                        (int) (100L * energy.getAmountAsInt() / CAPACITY)), true, remoteEnabled(), lastOut > 0, 1F, 0F);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        previous = energy.getAmountAsInt();
        visualFlow = Math.clamp(input.getIntOr("battery_flow", 0), -1, 1);
        if (!visualsInitialized) {
            previousCharge = shownCharge = (float) previous / CAPACITY;
            visualsInitialized = true;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new DieselMenu.Battery(id, inventory, worldPosition, data, this);
    }
}
