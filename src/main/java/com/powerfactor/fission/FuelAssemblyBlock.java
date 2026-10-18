package com.powerfactor.fission;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A Fuel Assembly. Its glow (0 to 3) is set by the reactor controller from the core's power, and gives real light:
 * a running reactor lights its pool, and through Reactor Glass the room round it.
 */
public class FuelAssemblyBlock extends Block {
    public static final IntegerProperty GLOW = IntegerProperty.create("glow", 0, 3);
    private static final int[] LIGHT = {3, 8, 12, 15};

    public FuelAssemblyBlock(Properties properties) {
        super(properties.lightLevel(s -> LIGHT[s.getValue(GLOW)]));
        registerDefaultState(stateDefinition.any().setValue(GLOW, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GLOW);
    }

    /** The pool's faces against the fuel aren't drawn: the reactor draws the water's light round its fuel itself. */
    @Override
    public boolean shouldHideAdjacentFluidFace(BlockState state, net.minecraft.core.Direction selfFace, net.minecraft.world.level.material.FluidState adjacentFluid) {
        return true;
    }

    /** The glow for a core at {@code power} (1 = rated). */
    public static int band(double power) {
        return power < 0.02 ? 0 : power < 0.25 ? 1 : power < 0.7 ? 2 : 3;
    }
}
