package com.powerfactor.world;

import com.mojang.serialization.Codec;
import com.powerfactor.PowerFactor;
import com.powerfactor.registry.ModBlocks;
import com.powerfactor.registry.ModQuantum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * An abandoned quantum lab, sealed in deepslate: a ruined steel room around a dead Particle Collider,
 * with hazard-striped flooring, shattered lights, qubit crystal growing through the cracks, a rift
 * leaking endermites, and the last researcher's chest. Only placed where the rock is solid and dry.
 */
public class QuantumLabFeature extends Feature<NoneFeatureConfiguration> {
    public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, PowerFactor.id("chests/quantum_lab"));
    private static final int R = 4, H = 5;

    public QuantumLabFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource rnd = ctx.random();
        BlockPos o = ctx.origin();
        // Solid, dry rock all round, or no lab: never cut into caves, aquifers or lava.
        int air = 0;
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-R - 1, -1, -R - 1), o.offset(R + 1, H, R + 1))) {
            BlockState s = level.getBlockState(p);
            if (!s.getFluidState().isEmpty()) return false;
            if (s.isAir() && ++air > 12) return false;
        }
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-R, 0, -R), o.offset(R, H - 1, R))) {
            int dx = p.getX() - o.getX(), dz = p.getZ() - o.getZ(), dy = p.getY() - o.getY();
            boolean wall = Math.abs(dx) == R || Math.abs(dz) == R, floor = dy == 0, ceiling = dy == H - 1;
            BlockState put;
            if (floor) {
                put = (Math.abs(dx) == R - 1 || Math.abs(dz) == R - 1) && !wall ? ModBlocks.HAZARD_BLOCK.get().defaultBlockState()
                        : ModBlocks.RIVETED_STEEL.get().defaultBlockState();
            } else if (ceiling || wall) {
                // Decay: some panels have fallen away to bare deepslate, a few bloom with qubit crystal.
                float roll = rnd.nextFloat();
                put = roll < 0.18F ? Blocks.COBBLED_DEEPSLATE.defaultBlockState()
                        : roll < 0.23F ? ModQuantum.QUBIT_ORE.get().defaultBlockState()
                        : ceiling && (Math.abs(dx) + Math.abs(dz)) % 4 == 0 && rnd.nextFloat() < 0.5F
                                ? (rnd.nextBoolean() ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.REDSTONE_LAMP.defaultBlockState())
                                : wall && dy == 2 && (dx + dz) % 3 == 0 ? ModBlocks.WARNING_BLOCK.get().defaultBlockState()
                                : ModBlocks.RIVETED_STEEL.get().defaultBlockState();
            } else {
                put = Blocks.CAVE_AIR.defaultBlockState();
            }
            level.setBlock(p, put, 2);
        }
        // Rubble and cobwebs scattered on the floor and in the corners.
        for (int i = 0; i < 10; i++) {
            BlockPos p = o.offset(rnd.nextInt(2 * R - 1) - R + 1, 1 + (rnd.nextFloat() < 0.4F ? H - 3 : 0), rnd.nextInt(2 * R - 1) - R + 1);
            if (!level.getBlockState(p).isAir() || p.equals(o.above())) continue;
            level.setBlock(p, rnd.nextFloat() < 0.6F ? Blocks.COBWEB.defaultBlockState() : Blocks.COBBLED_DEEPSLATE_SLAB.defaultBlockState(), 2);
        }
        // The dead collider at the heart of the room, on a steel plinth.
        level.setBlock(o.above(), ModQuantum.PARTICLE_COLLIDER.get().defaultBlockState(), 2);
        // The rift that killed the lab: a spawner still leaking endermites, in a corner.
        BlockPos rift = o.offset(R - 1, 1, R - 1);
        level.setBlock(rift, Blocks.SPAWNER.defaultBlockState(), 2);
        if (level.getBlockEntity(rift) instanceof SpawnerBlockEntity spawner) spawner.setEntityId(net.minecraft.world.entity.EntityTypes.ENDERMITE, rnd);
        // The researcher's chest, against the opposite wall.
        BlockPos chest = o.offset(-R + 1, 1, -R + 1);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH), 2);
        if (level.getBlockEntity(chest) instanceof ChestBlockEntity c) c.setLootTable(LOOT, rnd.nextLong());
        return true;
    }
}
