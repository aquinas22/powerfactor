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
import com.powerfactor.scada.ScadaDisplayBlock;
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
            p -> new WireBlock(p, com.powerfactor.grid.CableTier.COPPER),
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(0.5F).sound(SoundType.COPPER).noOcclusion());
    public static final DeferredBlock<WireBlock> INSULATED_COPPER_CABLE = BLOCKS.registerBlock("insulated_copper_cable",
            p -> new WireBlock(p, com.powerfactor.grid.CableTier.INSULATED),
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.5F).sound(SoundType.WOOL).noOcclusion());
    public static final DeferredBlock<WireBlock> ALUMINUM_CABLE = BLOCKS.registerBlock("aluminum_cable",
            p -> new WireBlock(p, com.powerfactor.grid.CableTier.ALUMINUM),
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(0.8F).sound(SoundType.METAL).noOcclusion());
    public static final DeferredBlock<com.powerfactor.grid.BreakerBlock> CIRCUIT_BREAKER = BLOCKS.registerBlock("circuit_breaker",
            com.powerfactor.grid.BreakerBlock::new, ModBlocks::machine);
    public static final DeferredBlock<com.powerfactor.grid.TransformerBlock> TRANSFORMER = BLOCKS.registerBlock("transformer",
            com.powerfactor.grid.TransformerBlock::new, ModBlocks::machine);
    public static final DeferredBlock<com.powerfactor.grid.PylonBlock> HV_PYLON = BLOCKS.registerBlock("hv_pylon",
            com.powerfactor.grid.PylonBlock::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 8.0F).sound(SoundType.METAL).noOcclusion()
                    .requiresCorrectToolForDrops());
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

    public static final DeferredBlock<ScadaDisplayBlock> SCADA_DISPLAY = BLOCKS.registerBlock("scada_display", ScadaDisplayBlock::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2.0F, 6.0F).sound(SoundType.METAL).lightLevel(s -> 6));

    public static final DeferredBlock<com.powerfactor.ender.EnderLinkBlock> ENDER_TRANSMITTER = BLOCKS.registerBlock("ender_transmitter",
            p -> new com.powerfactor.ender.EnderLinkBlock(true, p), () -> machine().lightLevel(s -> 8));
    public static final DeferredBlock<com.powerfactor.ender.EnderLinkBlock> ENDER_RECEIVER = BLOCKS.registerBlock("ender_receiver",
            p -> new com.powerfactor.ender.EnderLinkBlock(false, p), () -> machine().lightLevel(s -> 8));

    // ---------------------------------------------------------------- generators and grid devices
    public static final DeferredBlock<com.powerfactor.generator.GeneratorBlock> SOLAR_PANEL = BLOCKS.registerBlock("solar_panel",
            p -> com.powerfactor.generator.GeneratorBlock.slab(() -> ModBlockEntities.SOLAR_PANEL.get(), p), () -> machine().noOcclusion());
    public static final DeferredBlock<com.powerfactor.generator.GeneratorBlock> WIND_TURBINE = BLOCKS.registerBlock("wind_turbine",
            p -> new com.powerfactor.generator.GeneratorBlock(() -> ModBlockEntities.WIND_TURBINE.get(), Block.box(3, 0, 3, 13, 16, 13), p),
            () -> machine().noOcclusion());
    public static final DeferredBlock<com.powerfactor.generator.GeneratorBlock> HYDRO_TURBINE = BLOCKS.registerBlock("hydro_turbine",
            p -> com.powerfactor.generator.GeneratorBlock.full(() -> ModBlockEntities.HYDRO_TURBINE.get(), p), () -> machine().noOcclusion());
    public static final DeferredBlock<com.powerfactor.generator.GeneratorBlock> DIESEL_GENERATOR = BLOCKS.registerBlock("diesel_generator",
            p -> com.powerfactor.generator.GeneratorBlock.full(() -> ModBlockEntities.DIESEL_GENERATOR.get(), p), () -> machine().lightLevel(s -> s.getValue(MachineBlock.RUNNING) ? 7 : 0));
    public static final DeferredBlock<com.powerfactor.generator.GeneratorBlock> SYNCHRONOUS_CONDENSER = BLOCKS.registerBlock("synchronous_condenser",
            p -> com.powerfactor.generator.GeneratorBlock.full(() -> ModBlockEntities.SYNCHRONOUS_CONDENSER.get(), p), () -> machine().noOcclusion());
    public static final DeferredBlock<com.powerfactor.generator.GeneratorBlock> BATTERY_BANK = BLOCKS.registerBlock("battery_bank",
            p -> com.powerfactor.generator.GeneratorBlock.full(() -> ModBlockEntities.BATTERY_BANK.get(), p), ModBlocks::machine);
    public static final DeferredBlock<com.powerfactor.generator.GeneratorBlock> POWER_METER = BLOCKS.registerBlock("power_meter",
            p -> com.powerfactor.generator.GeneratorBlock.full(() -> ModBlockEntities.POWER_METER.get(), p), ModBlocks::machine);
    public static final DeferredBlock<com.powerfactor.generator.WindTowerBlock> WIND_TURBINE_TOWER = BLOCKS.registerBlock("wind_turbine_tower",
            com.powerfactor.generator.WindTowerBlock::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(3.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()
                    .noOcclusion());

    // ---------------------------------------------------------------- devices
    public static final DeferredBlock<com.powerfactor.device.TeslaCoilBlock> TESLA_COIL = BLOCKS.registerBlock("tesla_coil",
            com.powerfactor.device.TeslaCoilBlock::new, () -> machine().noOcclusion().lightLevel(s -> s.getValue(MachineBlock.RUNNING) ? 4 : 0));
    public static final DeferredBlock<com.powerfactor.generator.GeneratorBlock> GROW_LAMP = BLOCKS.registerBlock("grow_lamp",
            p -> new com.powerfactor.generator.GeneratorBlock(() -> ModBlockEntities.GROW_LAMP.get(), Block.box(1, 12, 1, 15, 16, 15), p),
            () -> machine().noOcclusion().lightLevel(s -> s.getValue(MachineBlock.RUNNING) ? 15 : 0));
    public static final DeferredBlock<com.powerfactor.generator.GeneratorBlock> CHARGING_PAD = BLOCKS.registerBlock("charging_pad",
            p -> new com.powerfactor.generator.GeneratorBlock(() -> ModBlockEntities.CHARGING_PAD.get(), Block.box(0, 0, 0, 16, 2, 16), p),
            () -> machine().noOcclusion().lightLevel(s -> s.getValue(MachineBlock.RUNNING) ? 6 : 0));
    public static final DeferredBlock<com.powerfactor.generator.GeneratorBlock> ELECTROLYZER = BLOCKS.registerBlock("electrolyzer",
            p -> com.powerfactor.generator.GeneratorBlock.full(() -> ModBlockEntities.ELECTROLYZER.get(), p),
            () -> machine().lightLevel(s -> s.getValue(MachineBlock.RUNNING) ? 5 : 0));
    public static final DeferredBlock<com.powerfactor.generator.GeneratorBlock> FUEL_CELL = BLOCKS.registerBlock("fuel_cell",
            p -> com.powerfactor.generator.GeneratorBlock.full(() -> ModBlockEntities.FUEL_CELL.get(), p),
            () -> machine().lightLevel(s -> s.getValue(MachineBlock.RUNNING) ? 5 : 0));

    public static final DeferredBlock<com.powerfactor.generator.GeneratorBlock> RADIO_TOWER = BLOCKS.registerBlock("radio_tower",
            p -> new com.powerfactor.generator.GeneratorBlock(() -> ModBlockEntities.RADIO_TOWER.get(), Block.box(5, 0, 5, 11, 16, 11), p),
            () -> machine().noOcclusion().lightLevel(s -> s.getValue(MachineBlock.RUNNING) ? 4 : 0));
    public static final DeferredBlock<com.powerfactor.upgrade.UpgradeTableBlock> UPGRADE_TABLE = BLOCKS.registerBlock("upgrade_table",
            com.powerfactor.upgrade.UpgradeTableBlock::new, () -> machine().noOcclusion().lightLevel(s -> 5));

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
