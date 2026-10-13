package com.powerfactor.generator;

import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.scada.ScadaReading;
import com.powerfactor.steam.ThermalMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/** Passive heat harvesters: geothermal taps a hot neighbour; thermoelectric requires hot/cold opposite faces. */
public final class AmbientGeneratorBlockEntity extends PushingGenerator {
    public static final int BUFFER = 8000;
    private final boolean thermoelectric;
    private int available;
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case ThermalMenu.DATA_FE -> lastFe;
                case ThermalMenu.DATA_STORED -> buffer.getAmountAsInt() / 10;
                case ThermalMenu.DATA_FLAGS -> lastFe > 0 ? 1 : 0;
                case ThermalMenu.DATA_REDSTONE -> redstoneMode().ordinal();
                case ThermalMenu.DATA_RATE -> available;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {}
        @Override public int getCount() { return ThermalMenu.DATA_COUNT; }
    };
    public AmbientGeneratorBlockEntity(boolean thermoelectric, BlockPos pos, BlockState state) {
        super(thermoelectric ? ModBlockEntities.THERMOELECTRIC_GENERATOR.get() : ModBlockEntities.GEOTHERMAL_GENERATOR.get(), pos, state, BUFFER);
        this.thermoelectric = thermoelectric;
    }
    private static int hot(ServerLevel level, BlockPos pos) {
        return level.getFluidState(pos).is(Fluids.LAVA) ? 64 : level.getBlockState(pos).is(Blocks.MAGMA_BLOCK) ? 24 : 0;
    }
    private static boolean cold(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return level.getFluidState(pos).is(Fluids.WATER) || state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE)
                || state.is(Blocks.BLUE_ICE) || state.is(Blocks.SNOW_BLOCK);
    }
    @Override public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        available = 0;
        for (Direction direction : Direction.values()) {
            BlockPos source = pos.relative(direction);
            if (!level.hasChunkAt(source)) continue;
            int heat = hot(level, source);
            if (!thermoelectric || level.hasChunkAt(pos.relative(direction.getOpposite())) && cold(level, pos.relative(direction.getOpposite()))) {
                available = Math.max(available, thermoelectric ? heat * 3 / 4 : heat);
            }
        }
        generate(enabled(level, pos) ? available : 0);
        push(level, pos);
        setRunning(level, pos, state, lastFe > 0);
    }
    @Override public boolean hasMenu() { return true; }
    @Override public int comparatorSignal() { return available * 15 / 64; }
    @Override public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), lastFe > 0 ? ScadaReading.Status.RUNNING : available == 0 ? ScadaReading.Status.WARNING : ScadaReading.Status.OFF,
                lastFe, 0, Component.translatable(thermoelectric ? "scada.powerfactor.thermoelectric" : "scada.powerfactor.geothermal", available),
                true, remoteEnabled(), true, 1, 0);
    }
    @Override public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ThermalMenu(thermoelectric ? ThermalMenu.Device.THERMOELECTRIC : ThermalMenu.Device.GEOTHERMAL,
                id, inventory, worldPosition, data, this);
    }
}
