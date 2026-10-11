package com.powerfactor.item;

import com.powerfactor.api.Bus;
import com.powerfactor.api.ScadaDevice;
import com.powerfactor.grid.BusLocator;
import com.powerfactor.grid.GridManager;
import com.powerfactor.grid.PowerGrid;
import com.powerfactor.power.BusSolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;

/** Builds the clamp meter's reading of whatever block it's pointed at. Server side. */
public final class MeterService {
    public static ToolPayloads.MeterReading read(ServerLevel level, BlockPos pos, boolean flat) {
        BlockState state = level.getBlockState(pos);
        // The top half of a two-block machine reads as the machine.
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
            pos = pos.below();
            state = level.getBlockState(pos);
        }
        Component title = state.getBlock().getName();
        List<Component> lines = new ArrayList<>();
        if (flat) {
            lines.add(Component.translatable("meter.powerfactor.flat"));
            return new ToolPayloads.MeterReading(pos, title, lines, -1, 0, 0, 0);
        }
        float volts = -1, fe = 0, ee = 0;
        int status = 0;
        PowerGrid grid = GridManager.get(level).gridAt(pos);
        BlockEntity be = level.getBlockEntity(pos);
        if (grid != null) {
            BusSolver.Result r = grid.state();
            volts = (float) r.volts();
            fe = (float) r.fe();
            ee = (float) r.netEe();
            lines.add(Component.translatable("meter.powerfactor.grid", Component.translatable("gui.powerfactor.meter.tier."
                    + grid.tier().name().toLowerCase(Locale.ROOT)), Math.round(r.volts() * grid.tier().volts), String.format("%.2f", grid.hertz())));
            lines.add(Component.translatable("meter.powerfactor.loading", Math.round(r.current() * 100), String.format("%.1f", r.lossFe())));
            if (grid.interference() > 0.01) {
                lines.add(Component.translatable("meter.powerfactor.interference", Math.round(grid.interference() * 100)));
            }
            status = r.current() > 1 || r.volts() < 0.9 ? 2 : grid.energized() ? 1 : 3;
        } else if (be instanceof ScadaDevice device) {
            var reading = device.scadaReading();
            fe = reading.fe();
            ee = reading.ee();
            lines.add(reading.detail());
            Bus bus = BusLocator.find(level, pos);
            if (bus instanceof PowerGrid supply) {
                volts = (float) supply.state().volts();
                lines.add(Component.translatable("meter.powerfactor.supply", Math.round(supply.state().volts() * supply.tier().volts),
                        String.format("%.2f", supply.hertz())));
            }
            status = switch (reading.status()) {
                case RUNNING -> 1;
                case WARNING -> 2;
                case ALARM -> 3;
                case OFF -> 0;
            };
        }
        EnergyHandler energy = level.getCapability(Capabilities.Energy.BLOCK, pos, null);
        if (energy != null && grid == null && energy.getCapacityAsLong() > 0) {
            lines.add(Component.translatable("meter.powerfactor.stored", String.format("%,d", energy.getAmountAsLong()),
                    String.format("%,d", energy.getCapacityAsLong())));
        }
        var fluids = level.getCapability(Capabilities.Fluid.BLOCK, pos, null);
        if (fluids != null) {
            for (int i = 0; i < Math.min(3, fluids.size()); i++) {
                if (!fluids.getResource(i).isEmpty()) {
                    lines.add(Component.translatable("meter.powerfactor.fluid", fluids.getResource(i).toStack(1).getHoverName(),
                            String.format("%,d", fluids.getAmountAsLong(i)), String.format("%,d", fluids.getCapacityAsLong(i, fluids.getResource(i)))));
                }
            }
        }
        if (lines.isEmpty() && volts < 0 && fe == 0) {
            lines.add(Component.translatable("meter.powerfactor.nothing"));
        }
        return new ToolPayloads.MeterReading(pos, title, lines, volts, fe, ee, status);
    }

    private MeterService() {}
}
