package com.powerfactor.device;

import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.scada.ScadaReading;
import com.powerfactor.steam.ThermalMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** An open catchment basin. Water is generated only by actual rain reaching its top. */
public final class RainCollectorBlockEntity extends MachineBlockEntity {
    public static final int TANK = 16000;
    private final HydrogenTanks tank = new HydrogenTanks(TANK, new net.minecraft.world.level.material.Fluid[] {Fluids.WATER}, new boolean[] {true}, this::setChanged);
    private int lastWater;
    private int syncedWater = -1;
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case ThermalMenu.DATA_TANK -> tank.amount(0);
                case ThermalMenu.DATA_FLAGS -> lastWater > 0 ? 1 : 0;
                case ThermalMenu.DATA_REDSTONE -> redstoneMode().ordinal();
                case ThermalMenu.DATA_RATE -> lastWater;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {}
        @Override public int getCount() { return ThermalMenu.DATA_COUNT; }
    };
    public RainCollectorBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.RAIN_COLLECTOR.get(), pos, state); }
    public HydrogenTanks getTank() { return tank; }
    @Override public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        lastWater = enabled(level, pos) && level.isRainingAt(pos.above()) ? Math.min(TANK - tank.amount(0), level.isThundering() ? 6 : 2) : 0;
        if (lastWater > 0) tank.add(0, lastWater);
        setRunning(level, pos, state, lastWater > 0);
        if (syncedWater < 0 || Math.abs(tank.amount(0) - syncedWater) >= 40 || (tank.amount(0) == 0) != (syncedWater == 0)
                || (tank.amount(0) == TANK) != (syncedWater == TANK)) {
            syncedWater = tank.amount(0);
            level.sendBlockUpdated(pos, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }
    @Override public int comparatorSignal() { return 15 * tank.amount(0) / TANK; }
    @Override public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), lastWater > 0 ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF,
                0, 0, Component.translatable("scada.powerfactor.rain_collector", lastWater, tank.amount(0)), true, remoteEnabled(), false, 0, 0);
    }
    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input); tank.deserialize(input.childOrEmpty("tank"));
        int water = Math.clamp(input.getIntOr("water", tank.amount(0)), 0, TANK);
        if (water != tank.amount(0)) tank.add(0, water - tank.amount(0));
    }
    @Override protected void saveAdditional(ValueOutput output) { super.saveAdditional(output); tank.serialize(output.child("tank")); }
    @Override public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        var tag = new net.minecraft.nbt.CompoundTag(); tag.putInt("water", tank.amount(0)); return tag;
    }
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
    @Override public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ThermalMenu(ThermalMenu.Device.RAIN_COLLECTOR, id, inventory, worldPosition, data, this);
    }
}
