package com.powerfactor.registry;

import com.powerfactor.PowerFactor;
import com.powerfactor.logistics.FluidTankBlock;
import com.powerfactor.logistics.PipeBlock;
import com.powerfactor.logistics.PipeKind;
import com.powerfactor.logistics.PumpBlock;
import com.powerfactor.world.RubberLogBlock;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.UntintedParticleLeavesBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import com.powerfactor.engine.EngineBlock;
import com.powerfactor.engine.EngineKind;
import com.powerfactor.grid.WireBlock;
import com.powerfactor.machine.AutoCapacitorBankBlock;
import com.powerfactor.machine.CapacitorBankBlock;
import com.powerfactor.machine.ChargerBlock;
import com.powerfactor.machine.CoalTurbineBlock;
import com.powerfactor.machine.CrusherBlock;
import com.powerfactor.machine.ElectricFurnaceBlock;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.machine.RecipeMachine;
import com.powerfactor.machine.RecipeMachineBlock;
import com.powerfactor.scada.FiberCableBlock;
import com.powerfactor.scada.ScadaTerminalBlock;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PowerFactor.MODID);

    // ---------------------------------------------------------------- materials
    public static final DeferredBlock<DropExperienceBlock> ZINC_ORE = BLOCKS.registerBlock("zinc_ore",
            p -> new DropExperienceBlock(ConstantInt.of(0), p),
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops().strength(3.0F, 3.0F));
    public static final DeferredBlock<DropExperienceBlock> DEEPSLATE_ZINC_ORE = BLOCKS.registerBlock("deepslate_zinc_ore",
            p -> new DropExperienceBlock(ConstantInt.of(0), p),
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops().strength(4.5F, 3.0F).sound(SoundType.DEEPSLATE));
    public static final DeferredBlock<Block> RAW_ZINC_BLOCK = BLOCKS.registerSimpleBlock("raw_zinc_block",
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops().strength(5.0F, 6.0F));
    public static final DeferredBlock<Block> ZINC_BLOCK = BLOCKS.registerSimpleBlock("zinc_block", ModBlocks::metal);
    public static final DeferredBlock<Block> BRASS_BLOCK = BLOCKS.registerSimpleBlock("brass_block", ModBlocks::metal);

    // ---------------------------------------------------------------- machines
    public static final DeferredBlock<CoalTurbineBlock> COAL_TURBINE = BLOCKS.registerBlock("coal_turbine", CoalTurbineBlock::new,
            () -> machine().noOcclusion().lightLevel(s -> s.getValue(MachineBlock.RUNNING) ? 9 : 0));
    public static final DeferredBlock<CrusherBlock> CRUSHER = BLOCKS.registerBlock("crusher", CrusherBlock::new, ModBlocks::machine);
    public static final DeferredBlock<ElectricFurnaceBlock> ELECTRIC_FURNACE = BLOCKS.registerBlock("electric_furnace", ElectricFurnaceBlock::new,
            () -> machine().lightLevel(s -> s.getValue(MachineBlock.RUNNING) ? 11 : 0));
    public static final DeferredBlock<ChargerBlock> CHARGER = BLOCKS.registerBlock("charger", ChargerBlock::new,
            () -> machine().lightLevel(s -> s.getValue(MachineBlock.RUNNING) ? 6 : 0));
    public static final DeferredBlock<CapacitorBankBlock> CAPACITOR_BANK = BLOCKS.registerBlock("capacitor_bank", CapacitorBankBlock::new,
            () -> machine().noOcclusion().lightLevel(s -> s.getValue(MachineBlock.RUNNING) ? 6 : 0));
    public static final DeferredBlock<AutoCapacitorBankBlock> AUTO_CAPACITOR_BANK = BLOCKS.registerBlock("auto_capacitor_bank", AutoCapacitorBankBlock::new,
            () -> machine().noOcclusion().lightLevel(s -> s.getValue(AutoCapacitorBankBlock.STEPS) > 0 ? 3 + s.getValue(AutoCapacitorBankBlock.STEPS) : 0));

    // ---------------------------------------------------------------- BuildCraft-style engines
    public static final DeferredBlock<EngineBlock> REDSTONE_ENGINE = BLOCKS.registerBlock("redstone_engine",
            p -> new EngineBlock(EngineKind.REDSTONE, p),
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredBlock<EngineBlock> STIRLING_ENGINE = BLOCKS.registerBlock("stirling_engine",
            p -> new EngineBlock(EngineKind.STIRLING, p),
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.0F).sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion());

    // ---------------------------------------------------------------- wiring and SCADA
    public static final DeferredBlock<WireBlock> COPPER_WIRE = BLOCKS.registerBlock("copper_wire",
            p -> new WireBlock(p, 512),
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(0.5F).sound(SoundType.COPPER).noOcclusion());
    public static final DeferredBlock<FiberCableBlock> FIBER_OPTIC_CABLE = BLOCKS.registerBlock("fiber_optic_cable", FiberCableBlock::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.5F).sound(SoundType.WOOL).noOcclusion());
    public static final DeferredBlock<ScadaTerminalBlock> SCADA_TERMINAL = BLOCKS.registerBlock("scada_terminal", ScadaTerminalBlock::new,
            () -> machine().lightLevel(s -> 7));

    public static final java.util.Map<RecipeMachine, DeferredBlock<RecipeMachineBlock>> RECIPE_MACHINES = new java.util.EnumMap<>(RecipeMachine.class);

    static {
        for (RecipeMachine m : RecipeMachine.values()) {
            RECIPE_MACHINES.put(m, BLOCKS.registerBlock(m.id, p -> new RecipeMachineBlock(m, p),
                    () -> machine().lightLevel(s -> s.getValue(MachineBlock.RUNNING) && m.heater ? 11 : 0)));
        }
    }

    // ---------------------------------------------------------------- logistics
    public static final DeferredBlock<FluidTankBlock> FLUID_TANK = BLOCKS.registerBlock("fluid_tank", FluidTankBlock::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5F, 6.0F).sound(SoundType.GLASS).noOcclusion()
                    .requiresCorrectToolForDrops());
    public static final DeferredBlock<PumpBlock> ELECTRIC_PUMP = BLOCKS.registerBlock("electric_pump", PumpBlock::new, ModBlocks::machine);
    public static final DeferredBlock<PipeBlock> FLUID_PIPE = BLOCKS.registerBlock("fluid_pipe", p -> new PipeBlock(PipeKind.FLUID, p),
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(0.8F).sound(SoundType.COPPER).noOcclusion());
    public static final DeferredBlock<PipeBlock> ITEM_PIPE = BLOCKS.registerBlock("item_pipe", p -> new PipeBlock(PipeKind.ITEM, p),
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.8F).sound(SoundType.METAL).noOcclusion());

    // ---------------------------------------------------------------- rubber tree
    public static final ResourceKey<ConfiguredFeature<?, ?>> RUBBER_TREE_FEATURE = ResourceKey.create(Registries.CONFIGURED_FEATURE,
            PowerFactor.id("rubber_tree"));
    public static final DeferredBlock<RubberLogBlock> RUBBER_LOG = BLOCKS.registerBlock("rubber_log", RubberLogBlock::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_LIGHT_GRAY).instrument(NoteBlockInstrument.BASS).strength(2.0F)
                    .sound(SoundType.WOOD).ignitedByLava());
    public static final DeferredBlock<Block> RUBBER_PLANKS = BLOCKS.registerSimpleBlock("rubber_planks",
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.SAND).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F)
                    .sound(SoundType.WOOD).ignitedByLava());
    public static final DeferredBlock<UntintedParticleLeavesBlock> RUBBER_LEAVES = BLOCKS.registerBlock("rubber_leaves",
            p -> new UntintedParticleLeavesBlock(0.01F, net.minecraft.core.particles.ParticleTypes.CHERRY_LEAVES, p),
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(0.2F).randomTicks().sound(SoundType.GRASS).noOcclusion()
                    .isValidSpawn((s, l, pos, e) -> false).isSuffocating((s, l, pos) -> false).isViewBlocking((s, l, pos) -> false)
                    .ignitedByLava().pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));
    public static final DeferredBlock<SaplingBlock> RUBBER_SAPLING = BLOCKS.registerBlock("rubber_sapling",
            p -> new SaplingBlock(new TreeGrower("powerfactor_rubber", Optional.empty(), Optional.of(RUBBER_TREE_FEATURE), Optional.empty()), p),
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.GRASS)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                .requiresCorrectToolForDrops().strength(5.0F, 6.0F).sound(SoundType.METAL);
    }

    private static BlockBehaviour.Properties machine() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops();
    }

    private ModBlocks() {}
}
