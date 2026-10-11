package com.powerfactor.device;

import com.powerfactor.api.ReactiveLoad;
import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.scada.ScadaReading;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/**
 * Full-spectrum LED grow lamp. Lights the area (so crops grow underground) and gives every plant
 * in a 9x9 patch up to six blocks below it many extra growth ticks.
 */
public class GrowLampBlockEntity extends com.powerfactor.machine.MachineBlockEntity implements ReactiveLoad {
    public static final int FE_PER_TICK = 24, RADIUS = 4, DEPTH = 6;
    /** Chance each plant gets an extra growth tick, per 10-tick cycle (vanilla averages one per ~1365 ticks). */
    public static final float BOOST = 0.06F;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(4_000, 200, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private final List<BlockPos> plants = new ArrayList<>();
    private int boosted, lastFe;

    public GrowLampBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GROW_LAMP.get(), pos, state);
    }

    public static boolean isPlant(BlockState s) {
        return s.isRandomlyTicking() && !(s.getBlock() instanceof GrassBlock)
                && (s.getBlock() instanceof BonemealableBlock || s.is(BlockTags.CROPS)
                || s.is(Blocks.SUGAR_CANE) || s.is(Blocks.CACTUS) || s.is(Blocks.NETHER_WART) || s.is(Blocks.BAMBOO));
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        boolean on = enabled(level, pos) && energy.getAmountAsInt() >= FE_PER_TICK;
        lastFe = 0;
        if (on) {
            energy.set(energy.getAmountAsInt() - FE_PER_TICK);
            lastFe = FE_PER_TICK;
        }
        setRunning(level, pos, state, on);
        if (!on) {
            return;
        }
        long t = level.getGameTime() + pos.asLong();
        if (t % 100 == 0) {
            plants.clear();
            for (BlockPos p : BlockPos.betweenClosed(pos.offset(-RADIUS, -DEPTH, -RADIUS), pos.offset(RADIUS, -1, RADIUS))) {
                if (isPlant(level.getBlockState(p))) {
                    plants.add(p.immutable());
                }
            }
        }
        if (t % 10 != 0) {
            return;
        }
        var random = level.getRandom();
        for (BlockPos p : plants) {
            if (random.nextFloat() >= BOOST) {
                continue;
            }
            BlockState s = level.getBlockState(p);
            if (isPlant(s)) {
                s.randomTick(level, p, random);
                boosted++;
                if (random.nextInt(3) == 0) {
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5, 2, 0.25, 0.2, 0.25, 0);
                }
            }
        }
    }

    /** LED drivers are close to unity power factor. */
    @Override
    public double eeDemand() {
        return lastFe > 0 ? 6 : 0;
    }

    public int boosted() {
        return boosted;
    }

    public EnergyHandler getEnergy() {
        return energy;
    }

    @Override
    public boolean hasMenu() {
        return false;
    }

    @Override
    public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), lastFe > 0 ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF, lastFe,
                (int) eeDemand(), Component.translatable("scada.powerfactor.grow_lamp", plants.size(), boosted), true, remoteEnabled(), false,
                1F, 60F);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        boosted = input.getIntOr("boosted", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        output.putInt("boosted", boosted);
    }
}
