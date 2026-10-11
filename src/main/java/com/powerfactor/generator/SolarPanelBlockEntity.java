package com.powerfactor.generator;

import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.scada.ScadaReading;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

/** Photovoltaic panel: FE follows the sun's height, cut by rain and anything overhead. Inverter-fed: no EE. */
public class SolarPanelBlockEntity extends PushingGenerator {
    public static final int PEAK = 24;
    private float output;

    public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOLAR_PANEL.get(), pos, state, 2_000);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getGameTime() % 20 == 0) {
            // Sky darkening runs 0 (noon) to 11 (night); weather darkens it too, so it covers clouds as well.
            float sun = 1F - level.getSkyDarken() / 11F;
            float sky = level.getBrightness(LightLayer.SKY, pos.above()) / 15F;
            float weather = level.isThundering() ? 0.2F : level.isRaining() ? 0.45F : 1F;
            output = level.dimensionType().hasSkyLight() ? Math.max(0, sun) * sky * weather : 0;
        }
        generate(enabled(level, pos) ? Math.round(PEAK * output) : 0);
        push(level, pos);
        setRunning(level, pos, state, lastFe > 0);
    }

    @Override
    public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), lastFe > 0 ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF, lastFe, 0,
                Component.translatable("scada.powerfactor.solar", Math.round(output * 100)), true, remoteEnabled(), true, 1F, 0F);
    }
}
