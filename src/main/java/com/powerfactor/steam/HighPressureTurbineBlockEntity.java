package com.powerfactor.steam;

import com.powerfactor.multiblock.Structure;
import com.powerfactor.registry.ModBlocks;
import com.powerfactor.registry.ModFission;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * High-Pressure Turbine: the steam turbine at power-station scale, 5 wide, 3 tall and 9 long, with fifteen
 * rotor stages and six alternator magnets. Rated 50,000 FE/t on 2,500 mB/t of steam: sized for a fission
 * reactor's output, which would otherwise need dozens of ordinary turbines.
 */
public class HighPressureTurbineBlockEntity extends SteamTurbineBlockEntity {
    public static final Structure STRUCTURE = new Structure.Builder()
            .key('B', ModBlocks.TURBINE_CASING).key('R', ModBlocks.STEAM_ROTOR).key('M', ModBlocks.GENERATOR_MAGNET)
            .layer(0, "BBCBB", "BBBBB", "BBBBB", "BBBBB", "BBBBB", "BBBBB", "BBBBB", "BBBBB", "BBBBB")
            .layer(1, "BBBBB", "BRRRB", "BRRRB", "BRRRB", "BRRRB", "BRRRB", "BMMMB", "BMMMB", "BBBBB")
            .layer(2, "BBBBB", "BBBBB", "BBBBB", "BBBBB", "BBBBB", "BBBBB", "BBBBB", "BBBBB", "BBBBB").build();
    /** Tank and buffer stay under 65,536 (and the buffer under 655,360) so the console's 16-bit gauges read true. */
    public static final Tier HP = new Tier(50_000, 62_500, 64_000, 600_000, 200_000, STRUCTURE);

    public HighPressureTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(ModFission.HP_TURBINE_BE.get(), pos, state, HP);
    }
}
