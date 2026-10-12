package com.powerfactor.gametest;

import com.powerfactor.PowerFactor;
import com.powerfactor.engine.EngineBlock;
import com.powerfactor.engine.EngineBlockEntity;
import com.powerfactor.grid.WireBlock;
import com.powerfactor.machine.CoalTurbineBlockEntity;
import com.powerfactor.machine.CrusherBlockEntity;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.power.TurbineModel;
import com.powerfactor.registry.ModBlocks;
import com.powerfactor.registry.ModItems;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** In-world tests, registered only in dev runs (see neoforge.enabledGameTestNamespaces in build.gradle). */
public final class PowerFactorGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, PowerFactor.MODID);
    private static final BlockPos CENTER = new BlockPos(1, 1, 1);
    /** Enough to spin a turbine up (190 ticks) and run for a while. */
    private static final int RUN_TICKS = 260;
    private static final int MAX_TICKS = 800;

    static {
        FUNCTIONS.register("turbine_burns_coal_and_exports_fe", () -> PowerFactorGameTests::burnsCoalAndExportsFe);
        FUNCTIONS.register("turbine_redstone_shutdown", () -> PowerFactorGameTests::redstoneShutdown);
        FUNCTIONS.register("crusher_doubles_raw_iron", () -> PowerFactorGameTests::crusherDoublesRawIron);
        FUNCTIONS.register("wire_grid_powers_crusher_and_carries_ee", () -> PowerFactorGameTests::gridPowersCrusher);
        FUNCTIONS.register("fixed_capacitor_over_corrects", () -> PowerFactorGameTests::fixedCapacitorOverCorrects);
        FUNCTIONS.register("auto_capacitor_bank_holds_unity_pf", () -> PowerFactorGameTests::autoCapacitorHoldsUnity);
        FUNCTIONS.register("stirling_engine_powers_crusher", () -> PowerFactorGameTests::stirlingEnginePowersCrusher);
        FUNCTIONS.register("electric_furnace_smelts", () -> PowerFactorGameTests::electricFurnaceSmelts);
        FUNCTIONS.register("capacitor_upgrades_correct_motor_pf", () -> PowerFactorGameTests::capacitorUpgradesCorrectPf);
        FUNCTIONS.register("side_config_controls_faces", () -> PowerFactorGameTests::sideConfigControlsFaces);
        FUNCTIONS.register("alloy_furnace_makes_brass", () -> PowerFactorGameTests::alloyFurnaceMakesBrass);
        FUNCTIONS.register("arc_furnace_makes_steel", () -> PowerFactorGameTests::arcFurnaceMakesSteel);
        FUNCTIONS.register("ore_washer_triples", () -> PowerFactorGameTests::oreWasherTriples);
        FUNCTIONS.register("item_pipe_moves_items", () -> PowerFactorGameTests::itemPipeMovesItems);
        FUNCTIONS.register("pump_fills_tank_through_pipe", () -> PowerFactorGameTests::pumpFillsTank);
        FUNCTIONS.register("ender_link_beams_power", () -> PowerFactorGameTests::enderLinkBeamsPower);
        FUNCTIONS.register("long_transformer_chain", () -> PowerFactorGameTests::transformerChain);
        FUNCTIONS.register("breaker_splits_grid", () -> PowerFactorGameTests::breakerSplitsGrid);
        FUNCTIONS.register("tesla_coil_zaps_zombie", () -> PowerFactorGameTests::teslaCoilZapsZombie);
        FUNCTIONS.register("electrolyzer_makes_hydrogen", () -> PowerFactorGameTests::electrolyzerMakesHydrogen);
        FUNCTIONS.register("fuel_cell_powers_crusher", () -> PowerFactorGameTests::fuelCellPowersCrusher);
        FUNCTIONS.register("grow_lamp_boosts_crops", () -> PowerFactorGameTests::growLampBoostsCrops);
        FUNCTIONS.register("charging_pad_charges_dropped_tool", () -> PowerFactorGameTests::chargingPadChargesDroppedTool);
        FUNCTIONS.register("chainsaw_fells_tree", () -> PowerFactorGameTests::chainsawFellsTree);
        FUNCTIONS.register("charger_charges_drill", () -> PowerFactorGameTests::chargerChargesDrill);
        FUNCTIONS.register("bare_wire_shocks_coated_does_not", () -> PowerFactorGameTests::bareWireShocks);
        FUNCTIONS.register("upgrade_table_installs_modules", () -> PowerFactorGameTests::upgradeTableInstalls);
        FUNCTIONS.register("laser_engraver_needs_right_mask", () -> PowerFactorGameTests::laserEngraverMakesChips);
        FUNCTIONS.register("mining_drill_instamines_dirt", () -> PowerFactorGameTests::drillInstaminesDirt);
        FUNCTIONS.register("woodcutter_drill_and_wrench", () -> PowerFactorGameTests::woodcutterAndWrench);
        FUNCTIONS.register("pumpjack_and_refinery", () -> PowerFactorGameTests::pumpjackAndRefinery);
    }

    public static void register(IEventBus modBus) {
        String enabled = System.getProperty("neoforge.enabledGameTestNamespaces", "");
        if (!enabled.contains(PowerFactor.MODID)) {
            return;
        }
        FUNCTIONS.register(modBus);
        modBus.addListener(PowerFactorGameTests::onRegisterTests);
    }

    private static void onRegisterTests(RegisterGameTestsEvent event) {
        var env = event.registerEnvironment(PowerFactor.id("default"));
        java.util.Set<String> wide = java.util.Set.of("long_transformer_chain", "breaker_splits_grid", "tesla_coil_zaps_zombie",
                "bare_wire_shocks_coated_does_not");
        java.util.Set<String> tall = java.util.Set.of("chainsaw_fells_tree", "pumpjack_and_refinery");
        for (var fn : FUNCTIONS.getEntries()) {
            String structure = wide.contains(fn.getId().getPath()) ? "empty_9x3x3" : tall.contains(fn.getId().getPath()) ? "empty_7x8x7"
                    : "empty_3x3x3";
            event.registerTest(fn.getId(), new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, fn.getId()),
                    new TestData<>(env, PowerFactor.id(structure), MAX_TICKS, 0, true)));
        }
    }

    // ------------------------------------------------------------------ helpers

    private static CoalTurbineBlockEntity placeTurbine(GameTestHelper helper, BlockPos pos, Direction front) {
        helper.setBlock(pos, ModBlocks.COAL_TURBINE.get().defaultBlockState().setValue(MachineBlock.FACING, front));
        return helper.getBlockEntity(pos, CoalTurbineBlockEntity.class);
    }

    private static CrusherBlockEntity placeCrusher(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.CRUSHER.get().defaultBlockState());
        return helper.getBlockEntity(pos, CrusherBlockEntity.class);
    }

    /** Places wire with its connections worked out, as a player placing it would. */
    private static void placeWire(GameTestHelper helper, BlockPos pos) {
        WireBlock wire = ModBlocks.COPPER_WIRE.get();
        helper.setBlock(pos, wire.withConnections(helper.getLevel(), helper.absolutePos(pos), wire.defaultBlockState()));
    }

    private static void insert(GameTestHelper helper, BlockPos pos, Item item, int count) {
        var items = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(pos), Direction.UP);
        helper.assertTrue(items != null, "no item handler at " + pos);
        try (var tx = Transaction.openRoot()) {
            helper.assertValueEqual(items.insert(ItemResource.of(item), count, tx), count, item + " inserted");
            tx.commit();
        }
    }

    // ------------------------------------------------------------------ turbine

    private static void burnsCoalAndExportsFe(GameTestHelper helper) {
        CoalTurbineBlockEntity turbine = placeTurbine(helper, CENTER, Direction.NORTH);
        var items = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(CENTER), Direction.UP);
        try (var tx = Transaction.openRoot()) {
            helper.assertValueEqual(items.insert(ItemResource.of(Items.DIRT), 1, tx), 0, "dirt accepted as fuel");
        }
        insert(helper, CENTER, Items.COAL, 2);
        helper.runAfterDelay(RUN_TICKS, () -> {
            helper.assertBlockProperty(CENTER, MachineBlock.RUNNING, true);
            // Loaded while filling its buffer, so the governor droops a little below 3600.
            helper.assertTrue(turbine.rpm() >= 3450 && turbine.rpm() <= TurbineModel.SYNC_RPM, "rpm " + turbine.rpm());
            EnergyHandler energy = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(CENTER), Direction.EAST);
            helper.assertTrue(energy != null, "turbine exposes no energy handler");
            helper.assertTrue(energy.getAmountAsInt() >= 5_000, "only " + energy.getAmountAsInt() + " FE generated");
            try (var tx = Transaction.openRoot()) {
                helper.assertValueEqual(energy.insert(100, tx), 0, "turbine accepted FE");
                helper.assertValueEqual(energy.extract(5_000, tx), 5_000, "FE extracted");
                tx.commit();
            }
            helper.succeed();
        });
    }

    private static void redstoneShutdown(GameTestHelper helper) {
        CoalTurbineBlockEntity turbine = placeTurbine(helper, CENTER, Direction.NORTH);
        helper.setBlock(CENTER.above(), Blocks.REDSTONE_BLOCK);
        insert(helper, CENTER, Items.COAL, 2);
        helper.runAfterDelay(RUN_TICKS, () -> {
            helper.assertBlockProperty(CENTER, MachineBlock.RUNNING, false);
            helper.assertValueEqual(turbine.rpm(), 0, "rpm");
            helper.assertValueEqual(turbine.getEnergy().getAmountAsInt(), 0, "stored FE");
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ crusher and grid

    private static void crusherDoublesRawIron(GameTestHelper helper) {
        CrusherBlockEntity crusher = placeCrusher(helper, CENTER);
        // The crusher takes at most 400 FE per insert, like a cable feeding it.
        for (int i = 0; i < 20; i++) {
            try (var tx = Transaction.openRoot()) {
                crusher.getEnergy().insert(400, tx);
                tx.commit();
            }
        }
        insert(helper, CENTER, Items.RAW_IRON, 1);
        helper.runAfterDelay(110, () -> {
            helper.assertTrue(crusher.slot(CrusherBlockEntity.OUTPUT).is(ModItems.IRON_DUST.get()), "output is " + crusher.slot(CrusherBlockEntity.OUTPUT));
            helper.assertValueEqual(crusher.slot(CrusherBlockEntity.OUTPUT).getCount(), 2, "iron dust count");
            helper.assertTrue(crusher.slot(CrusherBlockEntity.INPUT).isEmpty(), "raw iron not consumed");
            helper.succeed();
        });
    }

    /** Turbine (west) -> wire -> crusher (east). The crusher's motor EE should show up at the turbine. */
    private static void gridPowersCrusher(GameTestHelper helper) {
        BlockPos turbinePos = new BlockPos(0, 1, 1), wirePos = new BlockPos(1, 1, 1), crusherPos = new BlockPos(2, 1, 1);
        CoalTurbineBlockEntity turbine = placeTurbine(helper, turbinePos, Direction.NORTH);
        CrusherBlockEntity crusher = placeCrusher(helper, crusherPos);
        placeWire(helper, wirePos);
        insert(helper, turbinePos, Items.COAL, 4);
        insert(helper, crusherPos, Items.RAW_IRON, 8);
        helper.runAfterDelay(RUN_TICKS + 40, () -> {
            var r = crusher.scadaReading();
            helper.assertTrue(crusher.motor() == com.powerfactor.machine.Drive.State.RUNNING,
                    "crusher " + crusher.motor() + ": " + r.detail().getString() + " at " + r.volts() + " pu, turbine " + turbine.lastOutput());
            helper.assertValueEqual((int) Math.round(turbine.lastOutput().ee()), 15, "EE supplied by turbine");
            helper.assertTrue(turbine.lastOutput().powerFactor() < 1.0, "turbine PF should drop below 1 with a motor load");
            helper.succeed();
        });
    }

    /** A 60 EE bank on a 15 EE motor leaves the grid leading; the turbine has to absorb the excess. */
    private static void fixedCapacitorOverCorrects(GameTestHelper helper) {
        BlockPos turbinePos = new BlockPos(0, 1, 1), wirePos = new BlockPos(1, 1, 1), crusherPos = new BlockPos(2, 1, 1);
        BlockPos capPos = new BlockPos(1, 1, 2);
        CoalTurbineBlockEntity turbine = placeTurbine(helper, turbinePos, Direction.NORTH);
        placeCrusher(helper, crusherPos);
        helper.setBlock(capPos, ModBlocks.CAPACITOR_BANK.get().defaultBlockState());
        placeWire(helper, wirePos);
        insert(helper, turbinePos, Items.COAL, 4);
        insert(helper, crusherPos, Items.RAW_IRON, 8);
        helper.runAfterDelay(RUN_TICKS + 40, () -> {
            helper.assertBlockProperty(crusherPos, MachineBlock.RUNNING, true);
            helper.assertTrue(turbine.lastOutput().ee() < -30, "turbine should absorb leading EE, got " + turbine.lastOutput());
            helper.succeed();
        });
    }

    /** The automatic bank should switch in one 20 EE step for one 15 EE motor and stop there. */
    private static void autoCapacitorHoldsUnity(GameTestHelper helper) {
        BlockPos turbinePos = new BlockPos(0, 1, 1), wirePos = new BlockPos(1, 1, 1), crusherPos = new BlockPos(2, 1, 1);
        BlockPos capPos = new BlockPos(1, 1, 2);
        CoalTurbineBlockEntity turbine = placeTurbine(helper, turbinePos, Direction.NORTH);
        placeCrusher(helper, crusherPos);
        helper.setBlock(capPos, ModBlocks.AUTO_CAPACITOR_BANK.get().defaultBlockState());
        placeWire(helper, wirePos);
        insert(helper, turbinePos, Items.COAL, 4);
        insert(helper, crusherPos, Items.RAW_IRON, 16);
        helper.runAfterDelay(RUN_TICKS + 200, () -> {
            var bank = helper.getBlockEntity(capPos, com.powerfactor.machine.AutoCapacitorBankBlockEntity.class);
            helper.assertValueEqual((int) -bank.eeDemand(), 20, "EE switched in");
            helper.assertTrue(Math.abs(turbine.lastOutput().ee()) <= 6, "grid should sit near unity, turbine EE " + turbine.lastOutput());
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ engines

    private static void stirlingEnginePowersCrusher(GameTestHelper helper) {
        BlockPos enginePos = new BlockPos(0, 1, 1), crusherPos = new BlockPos(1, 1, 1);
        helper.setBlock(enginePos, ModBlocks.STIRLING_ENGINE.get().defaultBlockState().setValue(EngineBlock.FACING, Direction.EAST));
        helper.setBlock(enginePos.below(), Blocks.REDSTONE_BLOCK);
        CrusherBlockEntity crusher = placeCrusher(helper, crusherPos);
        EngineBlockEntity engine = helper.getBlockEntity(enginePos, EngineBlockEntity.class);
        insert(helper, enginePos, Items.COAL, 1);
        insert(helper, crusherPos, Items.COBBLESTONE, 2);
        helper.runAfterDelay(200, () -> {
            helper.assertTrue(crusher.slot(CrusherBlockEntity.OUTPUT).is(Items.GRAVEL), "engine-powered crusher made " + crusher.slot(CrusherBlockEntity.OUTPUT));
            helper.assertTrue(engine.scadaReading().fe() > 0, "engine not producing");
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ phase A: furnace, upgrades, sides

    private static void fillEnergy(com.powerfactor.machine.ProcessingBlockEntity machine) {
        for (int i = 0; i < 40; i++) {
            try (var tx = Transaction.openRoot()) {
                machine.getEnergy().insert(400, tx);
                tx.commit();
            }
        }
    }

    private static void electricFurnaceSmelts(GameTestHelper helper) {
        helper.setBlock(CENTER, ModBlocks.ELECTRIC_FURNACE.get().defaultBlockState());
        var furnace = helper.getBlockEntity(CENTER, com.powerfactor.machine.ElectricFurnaceBlockEntity.class);
        fillEnergy(furnace);
        insert(helper, CENTER, Items.RAW_IRON, 1);
        helper.runAfterDelay(170, () -> {
            helper.assertTrue(furnace.slot(com.powerfactor.machine.ElectricFurnaceBlockEntity.OUTPUT).is(Items.IRON_INGOT),
                    "furnace made " + furnace.slot(com.powerfactor.machine.ElectricFurnaceBlockEntity.OUTPUT));
            helper.assertValueEqual((int) furnace.eeDemand(), 0, "resistive heater EE");
            helper.succeed();
        });
    }

    /** Three Built-in Capacitor cards should cancel a stock crusher's 15 EE, so the turbine sees about none. */
    private static void capacitorUpgradesCorrectPf(GameTestHelper helper) {
        BlockPos turbinePos = new BlockPos(0, 1, 1), wirePos = new BlockPos(1, 1, 1), crusherPos = new BlockPos(2, 1, 1);
        CoalTurbineBlockEntity turbine = placeTurbine(helper, turbinePos, Direction.NORTH);
        CrusherBlockEntity crusher = placeCrusher(helper, crusherPos);
        crusher.upgrades().setItem(0, new net.minecraft.world.item.ItemStack(ModItems.CAPACITOR_UPGRADE.get(), 3));
        placeWire(helper, wirePos);
        insert(helper, turbinePos, Items.COAL, 4);
        insert(helper, crusherPos, Items.RAW_IRON, 16);
        helper.runAfterDelay(RUN_TICKS + 40, () -> {
            helper.assertTrue(crusher.motor() == com.powerfactor.machine.Drive.State.RUNNING, "crusher " + crusher.motor());
            helper.assertTrue(Math.abs(turbine.lastOutput().ee()) <= 2, "turbine EE with corrected motor " + turbine.lastOutput());
            helper.succeed();
        });
    }

    /** Defaults: front closed, bottom output-only, other faces both. Hoppers must obey. */
    private static void sideConfigControlsFaces(GameTestHelper helper) {
        helper.setBlock(CENTER, ModBlocks.CRUSHER.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        var level = helper.getLevel();
        BlockPos abs = helper.absolutePos(CENTER);
        java.util.function.BiFunction<Direction, Item, Integer> tryInsert = (side, item) -> {
            var handler = level.getCapability(Capabilities.Item.BLOCK, abs, side);
            try (var tx = Transaction.openRoot()) {
                return handler == null ? 0 : handler.insert(ItemResource.of(item), 1, tx);
            }
        };
        helper.assertValueEqual(tryInsert.apply(Direction.UP, Items.RAW_IRON), 1, "insert from top");
        helper.assertValueEqual(tryInsert.apply(Direction.NORTH, Items.RAW_IRON), 0, "insert through closed front");
        helper.assertValueEqual(tryInsert.apply(Direction.DOWN, Items.RAW_IRON), 0, "insert through output-only bottom");
        helper.getBlockEntity(CENTER, CrusherBlockEntity.class).cycleSide(com.powerfactor.machine.SideConfig.Face.FRONT, 1);
        helper.assertValueEqual(tryInsert.apply(Direction.NORTH, Items.RAW_IRON), 1, "front set to input");
        helper.succeed();
    }

    // ------------------------------------------------------------------ phase B

    private static com.powerfactor.machine.RecipeMachineBlockEntity placeRecipeMachine(GameTestHelper helper, BlockPos pos,
            com.powerfactor.machine.RecipeMachine machine) {
        helper.setBlock(pos, ModBlocks.RECIPE_MACHINES.get(machine).get().defaultBlockState());
        var be = helper.getBlockEntity(pos, com.powerfactor.machine.RecipeMachineBlockEntity.class);
        // Stand in for a grid: keep it topped up.
        helper.onEachTick(() -> fillEnergy(be));
        return be;
    }

    private static void alloyFurnaceMakesBrass(GameTestHelper helper) {
        var be = placeRecipeMachine(helper, CENTER, com.powerfactor.machine.RecipeMachine.ALLOY_FURNACE);
        insert(helper, CENTER, Items.COPPER_INGOT, 3);
        insert(helper, CENTER, ModItems.ZINC_INGOT.get(), 1);
        helper.runAfterDelay(230, () -> {
            var out = be.slot(2);
            helper.assertTrue(out.is(ModItems.BRASS_INGOT.get()) && out.getCount() == 4, "alloy furnace made " + out);
            helper.succeed();
        });
    }

    private static void arcFurnaceMakesSteel(GameTestHelper helper) {
        var be = placeRecipeMachine(helper, CENTER, com.powerfactor.machine.RecipeMachine.ARC_FURNACE);
        insert(helper, CENTER, Items.IRON_INGOT, 1);
        insert(helper, CENTER, ModItems.COAL_DUST.get(), 1);
        insert(helper, CENTER, ModItems.GRAPHITE_ELECTRODE.get(), 1);
        helper.runAfterDelay(330, () -> {
            var out = be.slot(2);
            helper.assertTrue(out.is(com.powerfactor.registry.ModMaterials.INGOTS.get("steel").get()), "arc furnace made " + out);
            helper.assertValueEqual(be.slot(3).getDamageValue(), 1, "electrode wear");
            helper.succeed();
        });
    }

    private static void oreWasherTriples(GameTestHelper helper) {
        var be = placeRecipeMachine(helper, CENTER, com.powerfactor.machine.RecipeMachine.ORE_WASHER);
        try (var tx = Transaction.openRoot()) {
            be.getTank().insert(net.neoforged.neoforge.transfer.fluid.FluidResource.of(net.minecraft.world.level.material.Fluids.WATER), 1000, tx);
            tx.commit();
        }
        insert(helper, CENTER, Items.RAW_IRON, 1);
        helper.runAfterDelay(180, () -> {
            var out = be.slot(1);
            helper.assertTrue(out.is(ModItems.IRON_DUST.get()) && out.getCount() == 3, "washer made " + out);
            helper.assertValueEqual(be.getTank().getAmountAsInt(0), 750, "water left");
            helper.succeed();
        });
    }

    /** Chest -> extract arm -> pipe -> insert arm -> crusher input. */
    private static void itemPipeMovesItems(GameTestHelper helper) {
        BlockPos chestPos = new BlockPos(0, 1, 1), pipePos = new BlockPos(1, 1, 1), crusherPos = new BlockPos(2, 1, 1);
        helper.setBlock(chestPos, Blocks.CHEST);
        helper.setBlock(crusherPos, ModBlocks.CRUSHER.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(chestPos));
        chest.setItem(0, new net.minecraft.world.item.ItemStack(Items.RAW_IRON, 10));
        var pipe = ModBlocks.ITEM_PIPE.get();
        helper.setBlock(pipePos, pipe.defaultBlockState()
                .setValue(com.powerfactor.logistics.PipeBlock.SIDES.get(Direction.WEST), com.powerfactor.logistics.PipeSide.EXTRACT)
                .setValue(com.powerfactor.logistics.PipeBlock.SIDES.get(Direction.EAST), com.powerfactor.logistics.PipeSide.INSERT));
        helper.runAfterDelay(30, () -> {
            var crusher = helper.getBlockEntity(crusherPos, CrusherBlockEntity.class);
            helper.assertTrue(crusher.slot(CrusherBlockEntity.INPUT).is(Items.RAW_IRON), "crusher got " + crusher.slot(CrusherBlockEntity.INPUT));
            helper.assertTrue(chest.getItem(0).getCount() < 10, "chest still has " + chest.getItem(0));
            helper.succeed();
        });
    }

    /** Pump on water -> fluid pipe -> tank. */
    private static void pumpFillsTank(GameTestHelper helper) {
        BlockPos waterPos = new BlockPos(0, 0, 1), pumpPos = new BlockPos(0, 1, 1), pipePos = new BlockPos(1, 1, 1), tankPos = new BlockPos(2, 1, 1);
        helper.setBlock(waterPos, Blocks.WATER);
        helper.setBlock(pumpPos, ModBlocks.ELECTRIC_PUMP.get().defaultBlockState());
        var pump = helper.getBlockEntity(pumpPos, com.powerfactor.logistics.PumpBlockEntity.class);
        fillEnergy(pump);
        helper.setBlock(tankPos, ModBlocks.FLUID_TANK.get().defaultBlockState());
        helper.setBlock(pipePos, ModBlocks.FLUID_PIPE.get().defaultBlockState()
                .setValue(com.powerfactor.logistics.PipeBlock.SIDES.get(Direction.WEST), com.powerfactor.logistics.PipeSide.EXTRACT)
                .setValue(com.powerfactor.logistics.PipeBlock.SIDES.get(Direction.EAST), com.powerfactor.logistics.PipeSide.INSERT));
        helper.runAfterDelay(120, () -> {
            var tank = helper.getBlockEntity(tankPos, com.powerfactor.logistics.FluidTankBlockEntity.class);
            helper.assertTrue(tank.fluid().is(net.minecraft.world.level.material.Fluids.WATER) && tank.fluid().getAmount() >= 500,
                    "tank has " + tank.fluid().getAmount() + " " + tank.fluid().getFluid());
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ phases C-F

    private static void enderLinkBeamsPower(GameTestHelper helper) {
        BlockPos tx = new BlockPos(0, 1, 0), rx = new BlockPos(2, 1, 2), crusherPos = new BlockPos(2, 1, 1);
        helper.setBlock(tx, ModBlocks.ENDER_TRANSMITTER.get().defaultBlockState());
        helper.setBlock(rx, ModBlocks.ENDER_RECEIVER.get().defaultBlockState());
        CrusherBlockEntity crusher = placeCrusher(helper, crusherPos);
        var transmitter = helper.getBlockEntity(tx, com.powerfactor.ender.EnderLinkBlockEntity.class);
        var receiver = helper.getBlockEntity(rx, com.powerfactor.ender.EnderLinkBlockEntity.class);
        transmitter.setChannel(7);
        receiver.setChannel(7);
        helper.onEachTick(() -> {
            try (var t = Transaction.openRoot()) {
                transmitter.getEnergy().insert(1024, t);
                t.commit();
            }
        });
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(crusher.getEnergy().getAmountAsInt() > 1000, "crusher got " + crusher.getEnergy().getAmountAsInt() + " FE over the air");
            helper.assertTrue(transmitter.eeDemand() > 100, "transmitter should draw EE while sending, got " + transmitter.eeDemand());
            helper.assertTrue(com.powerfactor.ender.EnderNetwork.interference(helper.getLevel(), helper.absolutePos(crusherPos)) > 0.5,
                    "busy links should radiate interference");
            helper.succeed();
        });
    }

    /** Turbine -> copper -> step-up -> MV aluminum -> step-down -> copper -> crusher. */
    private static void transformerChain(GameTestHelper helper) {
        CoalTurbineBlockEntity turbine = placeTurbine(helper, new BlockPos(0, 1, 1), Direction.NORTH);
        placeWire(helper, new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(2, 1, 1), ModBlocks.TRANSFORMER.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.EAST));
        for (int x = 3; x <= 5; x++) {
            var cable = ModBlocks.ALUMINUM_CABLE.get();
            helper.setBlock(new BlockPos(x, 1, 1), cable.defaultBlockState());
        }
        helper.setBlock(new BlockPos(6, 1, 1), ModBlocks.TRANSFORMER.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.EAST));
        CrusherBlockEntity crusher = placeCrusher(helper, new BlockPos(8, 1, 1));
        placeWire(helper, new BlockPos(7, 1, 1));
        // Cables were placed before their neighbours existed; settle their connections.
        for (int x = 1; x <= 7; x++) {
            BlockPos abs = helper.absolutePos(new BlockPos(x, 1, 1));
            var state = helper.getLevel().getBlockState(abs);
            if (state.getBlock() instanceof WireBlock w) {
                helper.getLevel().setBlock(abs, w.withConnections(helper.getLevel(), abs, state), 3);
            }
        }
        insert(helper, new BlockPos(0, 1, 1), Items.COAL, 4);
        insert(helper, new BlockPos(8, 1, 1), Items.RAW_IRON, 8);
        helper.runAfterDelay(RUN_TICKS + 60, () -> {
            helper.assertTrue(crusher.motor() == com.powerfactor.machine.Drive.State.RUNNING,
                    "crusher at the far end is " + crusher.motor() + " with " + crusher.getEnergy().getAmountAsInt() + " FE");
            // Its EE is reflected back through both transformers (plus their magnetizing EE).
            helper.assertTrue(turbine.lastOutput().ee() > 15, "turbine EE " + turbine.lastOutput());
            helper.succeed();
        });
    }

    private static void breakerSplitsGrid(GameTestHelper helper) {
        placeTurbine(helper, new BlockPos(0, 1, 1), Direction.NORTH);
        placeWire(helper, new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(2, 1, 1), ModBlocks.CIRCUIT_BREAKER.get().defaultBlockState()
                .setValue(com.powerfactor.grid.BreakerBlock.CLOSED, false));
        CrusherBlockEntity crusher = placeCrusher(helper, new BlockPos(4, 1, 1));
        placeWire(helper, new BlockPos(3, 1, 1));
        insert(helper, new BlockPos(0, 1, 1), Items.COAL, 4);
        helper.runAfterDelay(RUN_TICKS, () -> {
            helper.assertValueEqual(crusher.getEnergy().getAmountAsInt(), 0, "FE through an open breaker");
            var breaker = helper.getBlockEntity(new BlockPos(2, 1, 1), com.powerfactor.grid.BreakerBlockEntity.class);
            breaker.operate(helper.getLevel(), true, false);
        });
        helper.runAfterDelay(RUN_TICKS + 40, () -> {
            helper.assertTrue(crusher.getEnergy().getAmountAsInt() > 0, "no FE after closing the breaker");
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ power tools and devices

    private static void feed(GameTestHelper helper, net.neoforged.neoforge.transfer.energy.EnergyHandler energy, int perTick) {
        helper.onEachTick(() -> {
            try (var t = Transaction.openRoot()) {
                energy.insert(perTick, t);
                t.commit();
            }
        });
    }

    private static void teslaCoilZapsZombie(GameTestHelper helper) {
        BlockPos coil = new BlockPos(1, 1, 1);
        var state = ModBlocks.TESLA_COIL.get().defaultBlockState();
        helper.setBlock(coil, state);
        helper.setBlock(coil.above(), state.setValue(com.powerfactor.device.TeslaCoilBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        var be = helper.getBlockEntity(coil, com.powerfactor.device.TeslaCoilBlockEntity.class);
        feed(helper, be.getEnergy(), 600);
        var zombie = helper.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(7, 1, 1));
        zombie.setNoAi(true);
        float full = zombie.getHealth();
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(zombie.getHealth() < full || !zombie.isAlive(), "zombie took no lightning (health " + zombie.getHealth() + ")");
            helper.succeed();
        });
    }

    private static void electrolyzerMakesHydrogen(GameTestHelper helper) {
        helper.setBlock(CENTER, ModBlocks.ELECTROLYZER.get().defaultBlockState());
        var be = helper.getBlockEntity(CENTER, com.powerfactor.device.ElectrolyzerBlockEntity.class);
        feed(helper, be.getEnergy(), 400);
        try (var t = Transaction.openRoot()) {
            int in = be.getTanks().insert(net.neoforged.neoforge.transfer.fluid.FluidResource.of(net.minecraft.world.level.material.Fluids.WATER),
                    4000, t);
            t.commit();
            helper.assertValueEqual(in, 4000, "water accepted");
        }
        try (var t = Transaction.openRoot()) {
            int sneaky = be.getTanks().extract(net.neoforged.neoforge.transfer.fluid.FluidResource.of(net.minecraft.world.level.material.Fluids.WATER),
                    100, t);
            helper.assertValueEqual(sneaky, 0, "water can't be pulled back out");
        }
        helper.runAfterDelay(80, () -> {
            int h2 = be.getTanks().amount(com.powerfactor.device.ElectrolyzerBlockEntity.HYDROGEN);
            helper.assertTrue(h2 > 100, "only " + h2 + " mB hydrogen");
            helper.assertTrue(be.getTanks().amount(com.powerfactor.device.ElectrolyzerBlockEntity.WATER) < 4000, "no water used");
            helper.succeed();
        });
    }

    private static void fuelCellPowersCrusher(GameTestHelper helper) {
        helper.setBlock(CENTER, ModBlocks.FUEL_CELL.get().defaultBlockState());
        var cell = helper.getBlockEntity(CENTER, com.powerfactor.generator.FuelCellBlockEntity.class);
        cell.getTank().add(0, 2000);
        CrusherBlockEntity crusher = placeCrusher(helper, CENTER.east());
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(crusher.getEnergy().getAmountAsInt() > 500, "crusher got " + crusher.getEnergy().getAmountAsInt() + " FE");
            helper.assertTrue(cell.getTank().amount(0) < 2000, "no hydrogen used");
            helper.assertValueEqual(cell.hertz(), 60.0, "grid-forming at 60 Hz");
            helper.succeed();
        });
    }

    private static void growLampBoostsCrops(GameTestHelper helper) {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(new BlockPos(x, 0, z), net.minecraft.world.level.block.Blocks.FARMLAND.defaultBlockState());
                helper.setBlock(new BlockPos(x, 1, z), net.minecraft.world.level.block.Blocks.WHEAT.defaultBlockState());
            }
        }
        BlockPos lamp = new BlockPos(1, 2, 1);
        helper.setBlock(lamp, ModBlocks.GROW_LAMP.get().defaultBlockState());
        var be = helper.getBlockEntity(lamp, com.powerfactor.device.GrowLampBlockEntity.class);
        feed(helper, be.getEnergy(), 200);
        helper.runAfterDelay(400, () -> {
            helper.assertTrue(be.boosted() > 0, "lamp gave no growth ticks");
            helper.assertTrue(helper.getBlockState(lamp).getValue(com.powerfactor.machine.MachineBlock.RUNNING), "lamp not lit");
            helper.succeed();
        });
    }

    private static void chargingPadChargesDroppedTool(GameTestHelper helper) {
        helper.setBlock(CENTER, ModBlocks.CHARGING_PAD.get().defaultBlockState());
        var pad = helper.getBlockEntity(CENTER, com.powerfactor.device.ChargingPadBlockEntity.class);
        feed(helper, pad.getEnergy(), 4000);
        var drop = helper.spawnItem(ModItems.ELECTRIC_DRILL.get(), new net.minecraft.world.phys.Vec3(1.5, 1.2, 1.5));
        helper.runAfterDelay(40, () -> {
            int e = drop.getItem().getOrDefault(com.powerfactor.registry.ModComponents.ENERGY.get(), 0);
            helper.assertTrue(e > 0, "dropped drill not charged: pad " + pad.getEnergy().getAmountAsInt() + " FE, drop alive " + drop.isAlive()
                    + " at " + drop.position() + ", pad at " + helper.absolutePos(CENTER) + ", item " + drop.getItem());
            helper.succeed();
        });
    }

    private static void chainsawFellsTree(GameTestHelper helper) {
        BlockPos base = new BlockPos(3, 1, 3);
        var log = net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState();
        var leaves = net.minecraft.world.level.block.Blocks.OAK_LEAVES.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE, 1);
        for (int y = 0; y < 5; y++) {
            helper.setBlock(base.above(y), log);
        }
        for (BlockPos p : BlockPos.betweenClosed(base.offset(-2, 3, -2), base.offset(2, 5, 2))) {
            if (helper.getBlockState(p).isAir()) {
                helper.setBlock(p, leaves);
            }
        }
        // A log pillar with no leaves is not a tree.
        BlockPos post = new BlockPos(0, 1, 0);
        helper.setBlock(post, log);
        helper.setBlock(post.above(), log);
        var level = helper.getLevel();
        helper.assertTrue(com.powerfactor.item.TreeFeller.findTree(level, helper.absolutePos(post)).isEmpty(), "a bare post counted as a tree");
        var logs = com.powerfactor.item.TreeFeller.findTree(level, helper.absolutePos(base));
        helper.assertValueEqual(logs.size(), 5, "logs found");
        var player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var saw = new net.minecraft.world.item.ItemStack(ModItems.ELECTRIC_CHAINSAW.get());
        saw.set(com.powerfactor.registry.ModComponents.ENERGY.get(), com.powerfactor.item.ChainsawItem.CAPACITY);
        level.destroyBlock(helper.absolutePos(base), true, player);
        com.powerfactor.item.TreeFeller.fell(level, player, saw, helper.absolutePos(base), logs);
        helper.runAfterDelay(10, () -> {
            for (int y = 0; y < 5; y++) {
                helper.assertBlockNotPresent(net.minecraft.world.level.block.Blocks.OAK_LOG, base.above(y));
            }
            helper.assertTrue(com.powerfactor.item.PoweredItem.energy(saw) < com.powerfactor.item.ChainsawItem.CAPACITY, "felling was free");
            helper.assertBlockPresent(net.minecraft.world.level.block.Blocks.OAK_LOG, post);
        });
        helper.runAfterDelay(160, () -> {
            for (BlockPos p : BlockPos.betweenClosed(base.offset(-2, 3, -2), base.offset(2, 5, 2))) {
                helper.assertBlockNotPresent(net.minecraft.world.level.block.Blocks.OAK_LEAVES, p.immutable());
            }
            helper.succeed();
        });
    }

    private static void chargerChargesDrill(GameTestHelper helper) {
        helper.setBlock(CENTER, ModBlocks.CHARGER.get().defaultBlockState());
        var charger = helper.getBlockEntity(CENTER, com.powerfactor.machine.ChargerBlockEntity.class);
        feed(helper, charger.getEnergy(), 4000);
        try (var t = Transaction.openRoot()) {
            charger.getItemHandler().insert(net.neoforged.neoforge.transfer.item.ItemResource.of(ModItems.ELECTRIC_DRILL.get()), 1, t);
            t.commit();
        }
        helper.runAfterDelay(40, () -> {
            var stack = charger.getItemHandler().getResource(0).toStack(1);
            int e = stack.getOrDefault(com.powerfactor.registry.ModComponents.ENERGY.get(), 0);
            helper.assertTrue(e > 1000, "drill in the charger has " + e + " FE");
            helper.succeed();
        });
    }

    private static void bareWireShocks(GameTestHelper helper) {
        placeTurbine(helper, new BlockPos(0, 1, 1), Direction.NORTH);
        insert(helper, new BlockPos(0, 1, 1), Items.COAL, 8);
        for (int x = 1; x <= 8; x++) {
            helper.setBlock(new BlockPos(x, 1, 1), ModBlocks.COPPER_WIRE.get().defaultBlockState());
        }
        // Coat the far half with rubber, as a player would.
        for (int x = 5; x <= 8; x++) {
            BlockPos abs = helper.absolutePos(new BlockPos(x, 1, 1));
            var st = helper.getLevel().getBlockState(abs);
            helper.getLevel().setBlock(abs, st.setValue(WireBlock.COATED, true), 3);
        }
        helper.runAfterDelay(RUN_TICKS, () -> {
            // Husks: zombies would burn in the test world's daylight and spoil the comparison.
            var bare = helper.spawn(net.minecraft.world.entity.EntityTypes.HUSK, new BlockPos(3, 1, 1));
            var safe = helper.spawn(net.minecraft.world.entity.EntityTypes.HUSK, new BlockPos(7, 1, 1));
            bare.setNoAi(true);
            safe.setNoAi(true);
            float full = bare.getHealth();
            helper.runAfterDelay(30, () -> {
                helper.assertTrue(bare.getHealth() < full || !bare.isAlive(), "zombie on bare live wire wasn't shocked");
                helper.assertValueEqual(safe.getHealth(), full, "zombie on coated wire health");
                helper.succeed();
            });
        });
    }

    private static void upgradeTableInstalls(GameTestHelper helper) {
        var saw = new net.minecraft.world.item.ItemStack(ModItems.ELECTRIC_CHAINSAW.get());
        int base = ModItems.ELECTRIC_CHAINSAW.get().capacity(saw);
        var modules = net.minecraft.core.NonNullList.withSize(com.powerfactor.item.ToolModules.SLOTS, net.minecraft.world.item.ItemStack.EMPTY);
        modules.set(0, new net.minecraft.world.item.ItemStack(ModItems.DRILL_MODULES.get(com.powerfactor.item.DrillModule.Type.BATTERY).get(), 2));
        modules.set(1, new net.minecraft.world.item.ItemStack(ModItems.DRILL_MODULES.get(com.powerfactor.item.DrillModule.Type.EFFICIENCY).get(), 1));
        com.powerfactor.item.ToolModules.install(saw, modules, helper.getLevel().registryAccess());
        helper.assertValueEqual(ModItems.ELECTRIC_CHAINSAW.get().capacity(saw), base * 3, "capacity with two batteries");
        helper.assertValueEqual(com.powerfactor.item.ChainsawItem.perLog(saw), 48, "cost per log with one efficiency module");
        // A jetpack gains armour from plating.
        var pack = new net.minecraft.world.item.ItemStack(ModItems.ELECTRIC_JETPACK.get());
        var plating = net.minecraft.core.NonNullList.withSize(com.powerfactor.item.ToolModules.SLOTS, net.minecraft.world.item.ItemStack.EMPTY);
        plating.set(0, new net.minecraft.world.item.ItemStack(ModItems.DRILL_MODULES.get(com.powerfactor.item.DrillModule.Type.PLATING).get(), 3));
        com.powerfactor.item.ToolModules.install(pack, plating, helper.getLevel().registryAccess());
        double armor = pack.get(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream()
                .filter(m -> m.attribute().is(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR)).mapToDouble(m -> m.modifier().amount()).sum();
        helper.assertValueEqual(armor, 9.0, "jetpack armour with three plates");
        // And the menu refuses a module that doesn't fit the tool.
        helper.setBlock(CENTER, ModBlocks.UPGRADE_TABLE.get().defaultBlockState());
        var table = helper.getBlockEntity(CENTER, com.powerfactor.upgrade.UpgradeTableBlockEntity.class);
        table.tool().setItem(0, saw);
        var menu = new com.powerfactor.upgrade.UpgradeTableMenu(0, helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL).getInventory(), table);
        helper.assertFalse(menu.accepts(new net.minecraft.world.item.ItemStack(ModItems.DRILL_MODULES.get(com.powerfactor.item.DrillModule.Type.THRUSTER).get()), 2),
                "a thruster fitted a chainsaw");
        helper.assertTrue(menu.accepts(new net.minecraft.world.item.ItemStack(ModItems.DRILL_MODULES.get(com.powerfactor.item.DrillModule.Type.SPEED).get()), 2),
                "a speed module didn't fit a chainsaw");
        helper.assertFalse(menu.accepts(new net.minecraft.world.item.ItemStack(ModItems.DRILL_MODULES.get(com.powerfactor.item.DrillModule.Type.BATTERY).get()), 2),
                "a second battery stack was allowed alongside the first");
        helper.succeed();
    }

    private static void laserEngraverMakesChips(GameTestHelper helper) {
        var be = placeRecipeMachine(helper, CENTER, com.powerfactor.machine.RecipeMachine.LASER_ENGRAVER);
        insert(helper, CENTER, ModItems.SILICON_WAFER.get(), 2);
        insert(helper, CENTER, Items.GOLD_NUGGET, 4);
        // The wrong mask first: nothing should happen.
        insert(helper, CENTER, ModItems.PHOTOMASKS.get("memory").get(), 1);
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(be.slot(2).isEmpty(), "engraved with the wrong mask: " + be.slot(2));
            be.setSlot(3, new net.minecraft.world.item.ItemStack(ModItems.PHOTOMASKS.get("logic").get()));
        });
        helper.runAfterDelay(120 + 330, () -> {
            helper.assertTrue(be.slot(2).is(ModItems.MICROPROCESSOR.get()), "engraver made " + be.slot(2));
            helper.assertValueEqual(be.slot(3).getDamageValue(), 1, "mask wear");
            helper.succeed();
        });
    }

    private static void drillInstaminesDirt(GameTestHelper helper) {
        helper.setBlock(CENTER, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState());
        helper.setBlock(CENTER.east(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var drill = new net.minecraft.world.item.ItemStack(ModItems.ELECTRIC_DRILL.get());
        drill.set(com.powerfactor.registry.ModComponents.ENERGY.get(), 50_000);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, drill);
        player.setOnGround(true);   // miners in mid-air dig five times slower
        BlockPos dirt = helper.absolutePos(CENTER), stone = helper.absolutePos(CENTER.east());
        float dirtProgress = helper.getLevel().getBlockState(dirt).getDestroyProgress(player, helper.getLevel(), dirt);
        float stoneProgress = helper.getLevel().getBlockState(stone).getDestroyProgress(player, helper.getLevel(), stone);
        helper.assertTrue(dirtProgress >= 1F, "dirt should break instantly, progress/tick " + dirtProgress);
        helper.assertTrue(stoneProgress > 0.1F, "stone should still be fast, progress/tick " + stoneProgress);
        helper.succeed();
    }

    private static void woodcutterAndWrench(GameTestHelper helper) {
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var drill = new net.minecraft.world.item.ItemStack(ModItems.ELECTRIC_DRILL.get());
        drill.set(com.powerfactor.registry.ModComponents.ENERGY.get(), 50_000);
        helper.assertFalse(com.powerfactor.item.TreeFeller.canFell(drill, player), "a drill without the module felled trees");
        var modules = net.minecraft.core.NonNullList.withSize(com.powerfactor.item.ToolModules.SLOTS, net.minecraft.world.item.ItemStack.EMPTY);
        modules.set(0, new net.minecraft.world.item.ItemStack(ModItems.DRILL_MODULES.get(com.powerfactor.item.DrillModule.Type.WOODCUTTER).get()));
        com.powerfactor.item.ToolModules.install(drill, modules, helper.getLevel().registryAccess());
        helper.assertTrue(com.powerfactor.item.TreeFeller.canFell(drill, player), "the woodcutter module didn't enable felling");
        // The wrench turns a machine, no power needed.
        helper.setBlock(CENTER, ModBlocks.CRUSHER.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        var wrench = new net.minecraft.world.item.ItemStack(ModItems.WRENCH.get());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, wrench);
        BlockPos abs = helper.absolutePos(CENTER);
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(abs), Direction.UP, abs, false);
        wrench.getItem().onItemUseFirst(wrench, new net.minecraft.world.item.context.UseOnContext(player,
                net.minecraft.world.InteractionHand.MAIN_HAND, hit));
        helper.assertValueEqual(helper.getBlockState(CENTER).getValue(MachineBlock.FACING), Direction.EAST, "facing after one turn");
        helper.succeed();
    }

    private static void pumpjackAndRefinery(GameTestHelper helper) {
        // A small reservoir: crude oil sealed in stone under the pumpjack.
        var oil = com.powerfactor.registry.ModFluids.CRUDE_OIL.block().get().defaultBlockState();
        for (BlockPos p : BlockPos.betweenClosed(new BlockPos(0, 0, 0), new BlockPos(6, 2, 6))) {
            helper.setBlock(p, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        }
        for (BlockPos p : BlockPos.betweenClosed(new BlockPos(2, 0, 2), new BlockPos(4, 1, 4))) {
            helper.setBlock(p, oil);
        }
        BlockPos jack = new BlockPos(3, 3, 3);
        helper.setBlock(jack, ModBlocks.PUMPJACK.get().defaultBlockState());
        helper.setBlock(jack.above(), ModBlocks.PUMPJACK.get().defaultBlockState().setValue(com.powerfactor.machine.TallMachineBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        var pump = helper.getBlockEntity(jack, com.powerfactor.oil.PumpjackBlockEntity.class);
        feed(helper, pump.getEnergy(), 400);
        BlockPos col = new BlockPos(0, 3, 0);
        helper.setBlock(col, ModBlocks.REFINERY.get().defaultBlockState());
        helper.setBlock(col.above(), ModBlocks.REFINERY.get().defaultBlockState().setValue(com.powerfactor.machine.TallMachineBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        var refinery = helper.getBlockEntity(col, com.powerfactor.oil.RefineryBlockEntity.class);
        feed(helper, refinery.getEnergy(), 500);
        helper.runAfterDelay(120, () -> {
            int crude = pump.getTank().amount(0);
            helper.assertTrue(crude >= 1000, "pumpjack lifted only " + crude + " mB");
            int left = 0;
            for (BlockPos p : BlockPos.betweenClosed(new BlockPos(2, 0, 2), new BlockPos(4, 1, 4))) {
                left += helper.getLevel().getFluidState(helper.absolutePos(p)).isSource() ? 1 : 0;
            }
            helper.assertTrue(left < 18, "reservoir didn't drain (" + left + " blocks left)");
            // Hand the crude to the refinery, as a pipe would.
            refinery.getTanks().add(com.powerfactor.oil.RefineryBlockEntity.CRUDE, crude);
        });
        helper.runAfterDelay(220, () -> {
            helper.assertTrue(refinery.getTanks().amount(com.powerfactor.oil.RefineryBlockEntity.DIESEL) > 200, "no diesel");
            helper.assertTrue(refinery.getTanks().amount(com.powerfactor.oil.RefineryBlockEntity.JET) > 150, "no jet fuel");
            helper.succeed();
        });
    }

    private PowerFactorGameTests() {}
}
