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
        FUNCTIONS.register("mixed_copper_cables_share_weakest_grid", () -> PowerFactorGameTests::mixedCopperCables);
        FUNCTIONS.register("wire_rebuilds_when_device_attached", () -> PowerFactorGameTests::wireDeviceAttachment);
        FUNCTIONS.register("fresh_wire_requires_committed_power", () -> PowerFactorGameTests::freshWirePower);
        FUNCTIONS.register("breaker_chain_rejects_mixed_voltages", () -> PowerFactorGameTests::breakerVoltageConflict);
        FUNCTIONS.register("fixed_capacitor_over_corrects", () -> PowerFactorGameTests::fixedCapacitorOverCorrects);
        FUNCTIONS.register("auto_capacitor_bank_holds_unity_pf", () -> PowerFactorGameTests::autoCapacitorHoldsUnity);
        FUNCTIONS.register("stirling_engine_powers_crusher", () -> PowerFactorGameTests::stirlingEnginePowersCrusher);
        FUNCTIONS.register("electric_furnace_smelts", () -> PowerFactorGameTests::electricFurnaceSmelts);
        FUNCTIONS.register("capacitor_upgrades_correct_motor_pf", () -> PowerFactorGameTests::capacitorUpgradesCorrectPf);
        FUNCTIONS.register("side_config_controls_faces", () -> PowerFactorGameTests::sideConfigControlsFaces);
        FUNCTIONS.register("fluid_faces_follow_configuration", () -> PowerFactorGameTests::fluidFacesFollowConfiguration);
        FUNCTIONS.register("processing_output_pause_and_reload", () -> PowerFactorGameTests::processingOutputPause);
        FUNCTIONS.register("processing_recipe_swap_resets_progress", () -> PowerFactorGameTests::processingRecipeSwap);
        FUNCTIONS.register("washer_pauses_for_water_and_resumes", () -> PowerFactorGameTests::washerWaterPause);
        FUNCTIONS.register("processing_redstone_and_power_pause", () -> PowerFactorGameTests::processingControlPause);
        FUNCTIONS.register("processing_animation_tracks_upgrades", () -> PowerFactorGameTests::processingAnimationSpeed);
        FUNCTIONS.register("hand_tool_plate_and_wire", () -> PowerFactorGameTests::handToolRecipes);
        FUNCTIONS.register("synthetic_rubber_coal_and_charcoal", () -> PowerFactorGameTests::syntheticRubber);
        FUNCTIONS.register("graphite_bootstrap_in_alloy_furnace", () -> PowerFactorGameTests::graphiteBootstrap);
        FUNCTIONS.register("wind_base_foreign_energy_and_broken_tower", () -> PowerFactorGameTests::windBaseEnergy);
        FUNCTIONS.register("generator_shared_energy_conserved", () -> PowerFactorGameTests::generatorSharesEnergy);
        FUNCTIONS.register("power_meter_tracks_live_and_cold_grid", () -> PowerFactorGameTests::meterTracksGrid);
        FUNCTIONS.register("battery_reports_committed_external_transfers", () -> PowerFactorGameTests::batteryTransferReadings);
        FUNCTIONS.register("battery_support_does_not_recharge_itself", () -> PowerFactorGameTests::batterySupportRouting);
        FUNCTIONS.register("battery_charges_from_surplus", () -> PowerFactorGameTests::batterySurplusCharging);
        FUNCTIONS.register("redstone_direct_selection", () -> PowerFactorGameTests::redstoneDirectSelection);
        FUNCTIONS.register("wire_cutters_respect_permissions", () -> PowerFactorGameTests::wireCuttersPermissions);
        FUNCTIONS.register("wrench_respects_protection_mods", () -> PowerFactorGameTests::wrenchRespectsProtection);
        FUNCTIONS.register("rubber_tree_canopy_and_latex", () -> PowerFactorGameTests::rubberTreeCanopy);
        FUNCTIONS.register("wire_coating_uses_common_rubber_tag", () -> PowerFactorGameTests::wireCommonRubber);
        if (Boolean.getBoolean("powerfactor.testFixtures")) {
            FUNCTIONS.register("tagged_hydrogen_powers_fuel_cell", () -> PowerFactorGameTests::taggedHydrogenFuelCell);
            FUNCTIONS.register("tagged_steam_pipes_to_turbine", () -> PowerFactorGameTests::taggedSteamTurbine);
            FUNCTIONS.register("tagged_crude_runs_refinery", () -> PowerFactorGameTests::taggedCrudeRefinery);
            FUNCTIONS.register("tagged_hot_water_cools", () -> PowerFactorGameTests::taggedHotWaterCooling);
            FUNCTIONS.register("crusher_reserves_whole_output_batch", () -> h -> outputBatch(h, true));
            FUNCTIONS.register("centrifuge_reserves_whole_output_batch", () -> h -> outputBatch(h, false));
            FUNCTIONS.register("recipe_rejects_oversized_product", () -> h -> unavailableProduct(h, false));
            FUNCTIONS.register("recipe_waits_for_missing_tagged_product", () -> h -> unavailableProduct(h, true));
        }
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
        FUNCTIONS.register("multiblock_auto_build", () -> PowerFactorGameTests::multiblockAutoBuild);
        FUNCTIONS.register("wind_tower_auto_build", () -> PowerFactorGameTests::windTowerAutoBuild);
        FUNCTIONS.register("press_makes_plate", () -> h -> workshopRecipe(h, com.powerfactor.machine.RecipeMachine.MECHANICAL_PRESS,
                new net.minecraft.world.item.ItemStack[] {new net.minecraft.world.item.ItemStack(Items.IRON_INGOT)},
                com.powerfactor.registry.ModMaterials.PLATES.get("iron").get(), 1, null, 0));
        FUNCTIONS.register("press_gear_die", () -> h -> workshopRecipe(h, com.powerfactor.machine.RecipeMachine.MECHANICAL_PRESS,
                new net.minecraft.world.item.ItemStack[] {new net.minecraft.world.item.ItemStack(Items.IRON_INGOT, 4)},
                ModItems.IRON_GEAR.get(), 1, null, 0));
        FUNCTIONS.register("press_requires_die", () -> PowerFactorGameTests::pressRequiresDie);
        FUNCTIONS.register("steam_plant_recycles_water", () -> PowerFactorGameTests::steamPlantRecyclesWater);
        FUNCTIONS.register("steam_pipe_rejects_water", () -> PowerFactorGameTests::steamPipeRejectsWater);
        FUNCTIONS.register("steam_turbine_preserves_backed_up_steam", () -> PowerFactorGameTests::steamTurbinePreservesSteam);
        FUNCTIONS.register("steam_structure_ownership", () -> PowerFactorGameTests::steamStructureOwnership);
        FUNCTIONS.register("cooling_tower_needs_open_vent", () -> PowerFactorGameTests::coolingTowerNeedsVent);
        FUNCTIONS.register("ambient_generators_need_heat", () -> PowerFactorGameTests::ambientGeneratorsNeedHeat);
        FUNCTIONS.register("rain_collector_needs_rain_and_sky", () -> PowerFactorGameTests::rainCollectorNeedsRain);
        FUNCTIONS.register("power_armor_shield_and_landing", () -> PowerFactorGameTests::powerArmorShieldAndLanding);
        FUNCTIONS.register("power_armor_servo_shutdown", () -> PowerFactorGameTests::powerArmorServoShutdown);
        FUNCTIONS.register("power_armor_jetpack_socket", () -> PowerFactorGameTests::powerArmorJetpackSocket);
        FUNCTIONS.register("power_armor_charges_worn_equipment", () -> PowerFactorGameTests::powerArmorChargesEquipment);
        FUNCTIONS.register("power_armor_visor_preserves_potions", () -> PowerFactorGameTests::powerArmorVisor);
        FUNCTIONS.register("power_armor_damage_event", () -> PowerFactorGameTests::powerArmorDamageEvent);
        FUNCTIONS.register("power_armor_rebreather", () -> PowerFactorGameTests::powerArmorRebreather);
        FUNCTIONS.register("grapple_hits_and_reels", () -> PowerFactorGameTests::grappleHitsAndReels);
        FUNCTIONS.register("grapple_swing_and_empty_battery", () -> PowerFactorGameTests::grappleSwingAndBattery);
        FUNCTIONS.register("grapple_releases_broken_anchor", () -> PowerFactorGameTests::grappleBrokenAnchor);
        FUNCTIONS.register("grapple_tracks_moving_entity", () -> PowerFactorGameTests::grappleMovingAnchor);
        FUNCTIONS.register("grapple_requires_device_and_preserves_release", () -> PowerFactorGameTests::grappleDeviceAndRelease);
        FUNCTIONS.register("wingsuit_deploy_and_fold", () -> PowerFactorGameTests::wingsuitDeployAndFold);
        FUNCTIONS.register("wingsuit_dual_armor_socket", () -> PowerFactorGameTests::wingsuitArmorSocket);
        FUNCTIONS.register("wingsuit_equipment_change_and_restore", () -> PowerFactorGameTests::wingsuitChangeAndRestore);
        FUNCTIONS.register("rover_climbs_and_pays", () -> PowerFactorGameTests::roverClimbsAndPays);
        FUNCTIONS.register("rover_rejects_input_and_stops", () -> PowerFactorGameTests::roverInputAndShutdown);
        FUNCTIONS.register("rover_pad_charges_entity", () -> PowerFactorGameTests::roverPadCharges);
        FUNCTIONS.register("rover_pack_and_restore", () -> PowerFactorGameTests::roverPackAndRestore);
        FUNCTIONS.register("rover_submerged_stops_motor", () -> PowerFactorGameTests::roverSubmergedStops);
        FUNCTIONS.register("mixer_makes_brass_dust", () -> h -> workshopRecipe(h, com.powerfactor.machine.RecipeMachine.MIXER,
                new net.minecraft.world.item.ItemStack[] {new net.minecraft.world.item.ItemStack(ModItems.COPPER_DUST.get(), 3),
                        new net.minecraft.world.item.ItemStack(ModItems.ZINC_DUST.get())}, ModItems.BRASS_DUST.get(), 4, null, 0));
        FUNCTIONS.register("centrifuge_separates_dirt", () -> h -> workshopRecipe(h, com.powerfactor.machine.RecipeMachine.CENTRIFUGE,
                new net.minecraft.world.item.ItemStack[] {new net.minecraft.world.item.ItemStack(Items.DIRT, 4)}, Items.CLAY_BALL, 1, Items.SAND, 1));
        FUNCTIONS.register("sawmill_improves_log_yield", () -> h -> workshopRecipe(h, com.powerfactor.machine.RecipeMachine.SAWMILL,
                new net.minecraft.world.item.ItemStack[] {new net.minecraft.world.item.ItemStack(Items.OAK_LOG)}, Items.OAK_PLANKS, 6, Items.STICK, 1));
    }

    public static void register(IEventBus modBus) {
        String enabled = System.getProperty("neoforge.enabledGameTestNamespaces", "");
        if (!enabled.contains(PowerFactor.MODID)) {
            return;
        }
        FUNCTIONS.register(modBus);
        modBus.addListener(PowerFactorGameTests::onRegisterTests);
        if (Boolean.getBoolean("powerfactor.testFixtures")) modBus.addListener(PowerFactorGameTests::onFixturePacks);
    }

    /** Extra recipes and external material stand-ins are enabled only by runGameTestServer. */
    private static void onFixturePacks(net.neoforged.neoforge.event.AddPackFindersEvent event) {
        event.addPackFinders(PowerFactor.id("gametest-fixtures"), net.minecraft.server.packs.PackType.SERVER_DATA,
                net.minecraft.network.chat.Component.literal("PowerFactor GameTest fixtures"),
                net.minecraft.server.packs.repository.PackSource.BUILT_IN, true, net.minecraft.server.packs.repository.Pack.Position.TOP);
    }

    private static void onRegisterTests(RegisterGameTestsEvent event) {
        var env = event.registerEnvironment(PowerFactor.id("default"));
        java.util.Set<String> wide = java.util.Set.of("long_transformer_chain", "breaker_splits_grid", "tesla_coil_zaps_zombie",
                "bare_wire_shocks_coated_does_not", "mixed_copper_cables_share_weakest_grid", "breaker_chain_rejects_mixed_voltages");
        java.util.Set<String> tall = java.util.Set.of("chainsaw_fells_tree", "pumpjack_and_refinery", "multiblock_auto_build", "wind_tower_auto_build",
                "tagged_steam_pipes_to_turbine", "tagged_crude_runs_refinery", "tagged_hot_water_cools",
                "steam_plant_recycles_water", "steam_turbine_preserves_backed_up_steam", "steam_structure_ownership", "cooling_tower_needs_open_vent",
                "grapple_hits_and_reels", "grapple_swing_and_empty_battery", "grapple_releases_broken_anchor", "grapple_tracks_moving_entity", "wingsuit_deploy_and_fold",
                "rover_climbs_and_pays", "rover_rejects_input_and_stops", "rover_pad_charges_entity", "rover_pack_and_restore", "rover_submerged_stops_motor");
        for (var fn : FUNCTIONS.getEntries()) {
            String structure = fn.getId().getPath().equals("rubber_tree_canopy_and_latex") ? "empty_11x16x11"
                    : wide.contains(fn.getId().getPath()) ? "empty_9x3x3" : tall.contains(fn.getId().getPath()) || fn.getId().getPath().startsWith("wind_base_") ? "empty_7x8x7"
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
            cell.setRedstoneMode(com.powerfactor.machine.MachineBlockEntity.RedstoneMode.HIGH);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = (com.powerfactor.generator.DieselMenu.FuelCell) cell.createMenu(19, player.getInventory(), player);
            helper.assertTrue((menu.get(com.powerfactor.generator.DieselMenu.DATA_FLAGS) & com.powerfactor.generator.FuelCellBlockEntity.FLAG_DISABLED) != 0,
                    "fuel-cell menu failed to expose redstone shutdown");
            int hydrogen = cell.getTank().amount(0);
            helper.runAfterDelay(5, () -> {
                helper.assertValueEqual(cell.getTank().amount(0), hydrogen, "disabled fuel cell consumed hydrogen");
                helper.assertTrue((menu.get(com.powerfactor.generator.DieselMenu.DATA_FLAGS) & 1) == 0, "disabled fuel cell remained online");
                cell.setRedstoneMode(com.powerfactor.machine.MachineBlockEntity.RedstoneMode.IGNORE);
                helper.runAfterDelay(30, () -> {
                    helper.assertTrue(cell.getTank().amount(0) < hydrogen, "fuel cell failed to resume after redstone changed");
                    helper.assertTrue((menu.get(com.powerfactor.generator.DieselMenu.DATA_FLAGS) & com.powerfactor.generator.FuelCellBlockEntity.FLAG_DISABLED) == 0,
                            "fuel-cell menu retained disabled state after resuming");
                    helper.succeed();
                });
            });
        });
    }

    private static void meterTracksGrid(GameTestHelper helper) {
        var source = new BlockPos(0, 1, 1); var at = CENTER.north();
        placeTurbine(helper, source, Direction.NORTH); insert(helper, source, Items.COAL, 8);
        var crusher = placeCrusher(helper, CENTER.east()); insert(helper, CENTER.east(), Items.RAW_IRON, 16);
        helper.setBlock(at, ModBlocks.POWER_METER.get().defaultBlockState()); placeWire(helper, CENTER);
        var meter = helper.getBlockEntity(at, com.powerfactor.generator.PowerMeterBlockEntity.class);
        helper.runAfterDelay(RUN_TICKS, () -> {
            var tag = meter.getUpdateTag(helper.getLevel().registryAccess());
            helper.assertTrue(tag.getBooleanOr("meter_powered", false), "live grid did not light meter");
            helper.assertTrue(tag.getFloatOr("meter_volts", 0) > 0 && tag.getFloatOr("meter_load", 0) > 0, "meter needles lack live voltage/current");
            helper.assertBlockProperty(at, MachineBlock.RUNNING, true);
            helper.setBlock(source, Blocks.AIR);
        });
        helper.runAfterDelay(RUN_TICKS + 30, () -> {
            var tag = meter.getUpdateTag(helper.getLevel().registryAccess());
            helper.assertFalse(tag.getBooleanOr("meter_powered", true), "cold grid left meter lit");
            helper.assertTrue(tag.getFloatOr("meter_volts", -1) == 0 && tag.getFloatOr("meter_load", -1) == 0, "cold grid retained live needles");
            helper.assertValueEqual(meter.scadaReading().status(), com.powerfactor.scada.ScadaReading.Status.OFF, "cold meter SCADA status");
            helper.succeed();
        });
    }

    private static void batteryTransferReadings(GameTestHelper helper) {
        helper.setBlock(CENTER, ModBlocks.BATTERY_BANK.get().defaultBlockState());
        var battery = helper.getBlockEntity(CENTER, com.powerfactor.generator.BatteryBankBlockEntity.class);
        var handler = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(CENTER), Direction.NORTH);
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var menu = (com.powerfactor.generator.DieselMenu.Battery) battery.createMenu(21, player.getInventory(), player);
        try (var tx = Transaction.openRoot()) { helper.assertValueEqual(handler.insert(1024, tx), 1024, "battery insertion"); tx.commit(); }
        battery.serverTick(helper.getLevel(), helper.absolutePos(CENTER), battery.getBlockState());
        helper.assertValueEqual(menu.get(com.powerfactor.generator.DieselMenu.DATA_EE), 1024, "external charging readout");
        helper.assertValueEqual(battery.getUpdateTag(helper.getLevel().registryAccess()).getIntOr("battery_flow", 0), 1, "charging animation direction");
        try (var tx = Transaction.openRoot()) { handler.extract(256, tx); }
        battery.serverTick(helper.getLevel(), helper.absolutePos(CENTER), battery.getBlockState());
        helper.assertValueEqual(menu.get(com.powerfactor.generator.DieselMenu.DATA_FE), 0, "aborted extraction appeared in readout");
        try (var tx = Transaction.openRoot()) { helper.assertValueEqual(handler.extract(256, tx), 256, "battery extraction"); tx.commit(); }
        battery.serverTick(helper.getLevel(), helper.absolutePos(CENTER), battery.getBlockState());
        helper.assertValueEqual(menu.get(com.powerfactor.generator.DieselMenu.DATA_FE), 256, "external discharging readout");
        helper.assertValueEqual(handler.getAmountAsInt(), 768, "battery transfer conservation");
        helper.assertValueEqual(menu.storedEnergy(), 768, "sub-thousand FE menu precision");
        helper.assertValueEqual(battery.getUpdateTag(helper.getLevel().registryAccess()).getIntOr("energy", -1), 768, "client battery charge snapshot");
        helper.assertValueEqual(battery.getUpdateTag(helper.getLevel().registryAccess()).getIntOr("battery_flow", 0), -1, "discharging animation direction");
        helper.succeed();
    }

    private static void batterySupportRouting(GameTestHelper helper) {
        var at = CENTER.west();
        helper.setBlock(at, ModBlocks.BATTERY_BANK.get().defaultBlockState());
        var battery = helper.getBlockEntity(at, com.powerfactor.generator.BatteryBankBlockEntity.class);
        try (var tx = Transaction.openRoot()) {
            for (int i = 0; i < 32; i++) battery.getEnergy().insert(1024, tx);
            tx.commit();
        }
        // Three transformer primaries give a sustained reactive load without another FE source.
        for (var face : java.util.List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST))
            helper.setBlock(CENTER.relative(face), ModBlocks.TRANSFORMER.get().defaultBlockState().setValue(MachineBlock.FACING, face));
        placeWire(helper, CENTER);
        var grid = com.powerfactor.grid.GridManager.get(helper.getLevel()).gridAt(helper.absolutePos(CENTER));
        try (var tx = Transaction.openRoot()) { grid.handler().insert(100, tx); tx.commit(); }
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var menu = (com.powerfactor.generator.DieselMenu.Battery) battery.createMenu(23, player.getInventory(), player);
        final int[] export = {0}, recharge = {0};
        long start = helper.getLevel().getGameTime();
        helper.onEachTick(() -> {
            long elapsed = helper.getLevel().getGameTime() - start;
            if (elapsed >= 10 && elapsed <= 30) {
                export[0] += menu.get(com.powerfactor.generator.DieselMenu.DATA_FE);
                recharge[0] += menu.get(com.powerfactor.generator.DieselMenu.DATA_EE);
            }
        });
        helper.runAfterDelay(35, () -> {
            helper.assertTrue(export[0] > 0, "battery never supported the sagging grid");
            helper.assertValueEqual(recharge[0], 0, "supporting battery accepted its own grid exports");
            battery.setRedstoneMode(com.powerfactor.machine.MachineBlockEntity.RedstoneMode.HIGH);
            helper.runAfterDelay(5, () -> {
                helper.assertValueEqual(menu.get(com.powerfactor.generator.DieselMenu.DATA_FE), 0, "redstone-disabled battery exported to grid");
                helper.assertTrue((menu.get(com.powerfactor.generator.DieselMenu.DATA_FLAGS) & com.powerfactor.generator.BatteryBankBlockEntity.FLAG_DISABLED) != 0,
                        "battery menu failed to report disabled control");
                helper.assertValueEqual(battery.role(Direction.EAST), com.powerfactor.api.SidedGridDevice.Role.LOAD, "disabled bank still claimed source role");
                helper.assertValueEqual(battery.scadaReading().status(), com.powerfactor.scada.ScadaReading.Status.OFF, "disabled battery SCADA status");
                try (var tx = Transaction.openRoot()) {
                    for (int i = 0; i < 32; i++) battery.getEnergy().extract(1024, tx);
                    tx.commit();
                }
                battery.setRedstoneMode(com.powerfactor.machine.MachineBlockEntity.RedstoneMode.IGNORE);
                battery.serverTick(helper.getLevel(), helper.absolutePos(at), battery.getBlockState());
                helper.assertTrue((menu.get(com.powerfactor.generator.DieselMenu.DATA_FLAGS) & 1) == 0, "empty bank claimed grid support");
                helper.assertValueEqual(battery.scadaReading().status(), com.powerfactor.scada.ScadaReading.Status.WARNING, "empty battery SCADA status");
                helper.succeed();
            });
        });
    }

    private static void batterySurplusCharging(GameTestHelper helper) {
        BlockPos at = CENTER.west(), consumer = CENTER.east();
        helper.setBlock(at, ModBlocks.BATTERY_BANK.get().defaultBlockState());
        helper.setBlock(consumer, ModBlocks.CHARGING_PAD.get().defaultBlockState());
        var battery = helper.getBlockEntity(at, com.powerfactor.generator.BatteryBankBlockEntity.class);
        var pad = helper.getBlockEntity(consumer, com.powerfactor.device.ChargingPadBlockEntity.class);
        placeWire(helper, CENTER);
        var grid = com.powerfactor.grid.GridManager.get(helper.getLevel()).gridAt(helper.absolutePos(CENTER));
        try (var tx = Transaction.openRoot()) { grid.handler().insert(100, tx); tx.commit(); }
        helper.runAfterDelay(2, () -> {
            helper.assertValueEqual(pad.getEnergy().getAmountAsInt(), 100, "battery charging took power needed by another load");
            helper.assertValueEqual(battery.getEnergy().getAmountAsInt(), 0, "battery received power before ordinary load filled");
            try (var tx = Transaction.openRoot()) {
                for (int i = 0; i <= com.powerfactor.device.ChargingPadBlockEntity.CAPACITY / com.powerfactor.device.ChargingPadBlockEntity.INTAKE; i++)
                    pad.getEnergy().insert(com.powerfactor.device.ChargingPadBlockEntity.INTAKE, tx);
                grid.handler().insert(100, tx); tx.commit();
            }
        });
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(battery.getEnergy().getAmountAsInt() > 0 && battery.getEnergy().getAmountAsInt() <= 100,
                    "surplus power did not reach battery");
            helper.assertValueEqual(pad.getEnergy().getAmountAsInt(), pad.getEnergy().getCapacityAsInt(), "full ordinary load lost power");
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
        for (BlockPos p : BlockPos.betweenClosed(new BlockPos(3, 0, 3), new BlockPos(5, 1, 5))) {
            helper.setBlock(p, oil);
        }
        BlockPos jack = new BlockPos(4, 3, 4);
        helper.setBlock(jack, ModBlocks.PUMPJACK.get().defaultBlockState());
        helper.setBlock(jack.above(), ModBlocks.PUMPJACK.get().defaultBlockState().setValue(com.powerfactor.machine.TallMachineBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        var pump = helper.getBlockEntity(jack, com.powerfactor.oil.PumpjackBlockEntity.class);
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) helper.setBlock(new BlockPos(x, 2, z), ModBlocks.PUMPJACK_CASING.get().defaultBlockState());
        }
        feed(helper, pump.getEnergy(), 400);
        // Refinery multiblock: controller front-centre of a 3x3 casing base (extending +Z), column on the middle.
        BlockPos col = new BlockPos(1, 3, 0);
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 3, z), ModBlocks.REFINERY_CASING.get().defaultBlockState());
            }
        }
        helper.setBlock(col, ModBlocks.REFINERY.get().defaultBlockState());
        var refinery = helper.getBlockEntity(col, com.powerfactor.oil.RefineryBlockEntity.class);
        helper.runAfterDelay(25, () -> helper.assertFalse(refinery.formed(), "formed without its column"));
        helper.runAfterDelay(30, () -> {
            for (int y = 4; y <= 6; y++) {
                helper.setBlock(new BlockPos(1, y, 1), ModBlocks.DISTILLATION_COLUMN.get().defaultBlockState());
            }
        });
        helper.runAfterDelay(60, () -> helper.assertTrue(refinery.formed(), "complete structure didn't form"));
        feed(helper, refinery.getEnergy(), 500);
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(pump.formed(), "pumpjack foundation did not form");
            helper.assertTrue(helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(new BlockPos(3, 2, 3)), Direction.NORTH)
                    == pump.getEnergy(), "foundation did not expose controller energy");
            int crude = pump.getTank().amount(0);
            helper.assertTrue(crude >= 1000, "pumpjack lifted only " + crude + " mB");
            int left = 0;
            for (BlockPos p : BlockPos.betweenClosed(new BlockPos(3, 0, 3), new BlockPos(5, 1, 5))) {
                left += helper.getLevel().getFluidState(helper.absolutePos(p)).isSource() ? 1 : 0;
            }
            helper.assertTrue(left < 18, "reservoir didn't drain (" + left + " blocks left)");
            // Hand the crude to the refinery, as a pipe would.
            refinery.getTanks().add(com.powerfactor.oil.RefineryBlockEntity.CRUDE, crude);
        });
        final int[] stoppedOil = {0};
        helper.runAfterDelay(140, () -> {
            helper.setBlock(new BlockPos(3, 2, 3), Blocks.AIR);
        });
        helper.runAfterDelay(170, () -> {
            helper.assertFalse(pump.formed(), "pumpjack kept operating after foundation was broken");
            stoppedOil[0] = pump.getTank().amount(0);
        });
        helper.runAfterDelay(220, () -> {
            helper.assertValueEqual(pump.getTank().amount(0), stoppedOil[0], "oil pumped with broken foundation");
            helper.assertTrue(refinery.getTanks().amount(com.powerfactor.oil.RefineryBlockEntity.DIESEL) > 200, "no diesel");
            helper.assertTrue(refinery.getTanks().amount(com.powerfactor.oil.RefineryBlockEntity.JET) > 150, "no jet fuel");
            helper.succeed();
        });
    }

    private static void workshopRecipe(GameTestHelper helper, com.powerfactor.machine.RecipeMachine kind,
            net.minecraft.world.item.ItemStack[] inputs, net.minecraft.world.item.Item product, int count,
            net.minecraft.world.item.Item secondary, int secondaryCount) {
        var machine = placeRecipeMachine(helper, CENTER, kind);
        for (var input : inputs) insert(helper, CENTER, input.getItem(), input.getCount());
        if (kind == com.powerfactor.machine.RecipeMachine.MECHANICAL_PRESS) {
            insert(helper, CENTER, product == ModItems.IRON_GEAR.get() ? ModItems.GEAR_DIE.get() : ModItems.PLATE_DIE.get(), 1);
        }
        helper.runAfterDelay(240, () -> {
            for (int i = 0; i < kind.inputs; i++) helper.assertTrue(machine.slot(i).isEmpty(), "input not consumed");
            helper.assertTrue(machine.slot(kind.inputs).is(product), "wrong main product: " + machine.slot(kind.inputs));
            helper.assertValueEqual(machine.slot(kind.inputs).getCount(), count, "main product count");
            if (kind == com.powerfactor.machine.RecipeMachine.MECHANICAL_PRESS) {
                helper.assertValueEqual(machine.slot(kind.electrodeSlot()).getDamageValue(), 1, "press die wear");
            }
            if (secondary != null) {
                helper.assertTrue(machine.slot(kind.inputs + 1).is(secondary), "wrong secondary product");
                helper.assertValueEqual(machine.slot(kind.inputs + 1).getCount(), secondaryCount, "secondary product count");
            }
            helper.succeed();
        });
    }

    private static void pressRequiresDie(GameTestHelper helper) {
        var machine = placeRecipeMachine(helper, CENTER, com.powerfactor.machine.RecipeMachine.MECHANICAL_PRESS);
        insert(helper, CENTER, Items.IRON_INGOT, 4);
        helper.runAfterDelay(100, () -> {
            helper.assertValueEqual(machine.slot(0).getCount(), 4, "press used input without a die");
            helper.assertTrue(machine.slot(1).isEmpty(), "press produced output without a die");
            insert(helper, CENTER, ModItems.PLATE_DIE.get(), 1);
        });
        helper.runAfterDelay(450, () -> {
            helper.assertTrue(machine.slot(1).is(com.powerfactor.registry.ModMaterials.PLATES.get("iron").get()), "bulk input chose gears with a plate die");
            helper.assertValueEqual(machine.slot(1).getCount(), 4, "bulk plate count");
            helper.assertValueEqual(machine.slot(2).getDamageValue(), 4, "bulk die wear");
            helper.succeed();
        });
    }

    private static void multiblockAutoBuild(GameTestHelper helper) {
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        BlockPos origin = new BlockPos(4, 1, 3), absolute = helper.absolutePos(origin);
        helper.setBlock(origin, ModBlocks.REFINERY.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.EAST));
        var structure = com.powerfactor.oil.RefineryBlockEntity.STRUCTURE;
        var inventory = player.getInventory();
        inventory.setItem(0, new net.minecraft.world.item.ItemStack(ModBlocks.REFINERY_CASING.get(), 7));
        inventory.setItem(1, new net.minecraft.world.item.ItemStack(ModBlocks.DISTILLATION_COLUMN.get(), 3));
        helper.assertFalse(com.powerfactor.multiblock.StructureBuilder.assemble(helper.getLevel(), absolute, Direction.EAST, player, structure),
                "built with too few casings");
        helper.assertValueEqual(structure.missing(helper.getLevel(), absolute, Direction.EAST).size(), 11, "parts missing after shortage");
        inventory.getItem(0).grow(1);
        BlockPos obstruction = structure.worldPos(absolute, Direction.EAST, structure.parts().getFirst());
        helper.getLevel().setBlock(obstruction, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        helper.assertFalse(com.powerfactor.multiblock.StructureBuilder.assemble(helper.getLevel(), absolute, Direction.EAST, player, structure),
                "overwrote obstruction");
        helper.assertValueEqual(inventory.getItem(0).getCount(), 8, "casings after obstruction");
        helper.getLevel().removeBlock(obstruction, false);
        java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.EntityMultiPlaceEvent> deny = event -> {
            if (event.getEntity() == player) event.setCanceled(true);
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(deny);
        try {
            helper.assertFalse(com.powerfactor.multiblock.StructureBuilder.assemble(helper.getLevel(), absolute, Direction.EAST, player, structure),
                    "ignored placement protection");
            helper.assertValueEqual(structure.missing(helper.getLevel(), absolute, Direction.EAST).size(), 11, "rollback after denied placement");
            helper.assertValueEqual(inventory.getItem(0).getCount(), 8, "casings after denied placement");
        } finally {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(deny);
        }
        helper.assertTrue(com.powerfactor.multiblock.StructureBuilder.assemble(helper.getLevel(), absolute, Direction.EAST, player, structure),
                "valid rotated structure did not build");
        helper.assertTrue(inventory.getItem(0).isEmpty() && inventory.getItem(1).isEmpty(), "did not consume exactly the needed parts");
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(helper.getBlockEntity(origin, com.powerfactor.oil.RefineryBlockEntity.class).formed(), "auto-built refinery did not form");
            helper.succeed();
        });
    }

    private static void windTowerAutoBuild(GameTestHelper helper) {
        BlockPos origin = new BlockPos(3, 7, 3), absolute = helper.absolutePos(origin);
        helper.setBlock(origin, ModBlocks.WIND_TURBINE.get().defaultBlockState());
        var turbine = helper.getBlockEntity(origin, com.powerfactor.generator.WindTurbineBlockEntity.class);
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(ModBlocks.WIND_TURBINE_TOWER.get(), 8));
        helper.assertTrue(com.powerfactor.multiblock.StructureBuilder.assemble(helper.getLevel(), absolute, Direction.NORTH, player,
                turbine.buildStructure()), "tower did not build");
        helper.assertValueEqual(player.getInventory().getItem(0).getCount(), 2, "tower sections remaining");
        helper.assertValueEqual(helper.getBlockState(origin.below(6)).getValue(com.powerfactor.generator.WindTowerBlock.TAPER), 5, "bottom taper");
        helper.assertTrue(helper.getBlockState(origin.below(6)).getValue(com.powerfactor.generator.WindTowerBlock.BASE), "bottom lacks footing");
        helper.assertFalse(helper.getBlockState(origin.below()).getValue(com.powerfactor.generator.WindTowerBlock.BASE), "top has a footing");
        helper.succeed();
    }

    private static void formStructure(GameTestHelper helper, BlockPos origin, com.powerfactor.multiblock.Structure structure) {
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        helper.assertTrue(com.powerfactor.multiblock.StructureBuilder.assemble(helper.getLevel(), helper.absolutePos(origin), Direction.NORTH, player, structure),
                "structure did not assemble");
    }

    private static void fluidPipe(GameTestHelper helper, BlockPos pos, boolean steam, java.util.Map<Direction, com.powerfactor.logistics.PipeSide> sides) {
        var state = (steam ? ModBlocks.STEAM_PIPE : ModBlocks.FLUID_PIPE).get().defaultBlockState();
        for (var entry : sides.entrySet()) state = state.setValue(com.powerfactor.logistics.PipeBlock.SIDES.get(entry.getKey()), entry.getValue());
        helper.setBlock(pos, state);
    }

    private static void steamPlantRecyclesWater(GameTestHelper helper) {
        var boilerPos = new BlockPos(1, 0, 1); var turbinePos = new BlockPos(5, 0, 1); var coolerPos = new BlockPos(3, 0, 6);
        helper.setBlock(boilerPos, ModBlocks.STEAM_BOILER.get().defaultBlockState());
        helper.setBlock(turbinePos, ModBlocks.STEAM_TURBINE.get().defaultBlockState());
        formStructure(helper, boilerPos, com.powerfactor.steam.SteamBoilerBlockEntity.STRUCTURE);
        formStructure(helper, turbinePos, com.powerfactor.steam.SteamTurbineBlockEntity.STRUCTURE);
        helper.setBlock(coolerPos, ModBlocks.COOLING_TOWER.get().defaultBlockState());
        helper.setBlock(coolerPos.above(), ModBlocks.COOLING_TOWER.get().defaultBlockState().setValue(com.powerfactor.machine.TallMachineBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        var boiler = helper.getBlockEntity(boilerPos, com.powerfactor.steam.SteamBoilerBlockEntity.class);
        var turbine = helper.getBlockEntity(turbinePos, com.powerfactor.steam.SteamTurbineBlockEntity.class);
        var cooling = helper.getBlockEntity(coolerPos, com.powerfactor.steam.CoolingTowerBlockEntity.class);
        boiler.getTanks().add(0, 16000);
        insert(helper, boilerPos, Items.COAL, 8);
        var crusherPos = new BlockPos(6, 0, 6);
        var crusher = placeCrusher(helper, crusherPos);
        insert(helper, crusherPos, Items.RAW_IRON, 16);
        helper.setBlock(new BlockPos(4, 0, 6), ModBlocks.ALUMINUM_CABLE.get().defaultBlockState());
        helper.setBlock(new BlockPos(5, 0, 6), ModBlocks.BATTERY_BANK.get().defaultBlockState());
        helper.runAfterDelay(25, () -> {
            var ex = com.powerfactor.logistics.PipeSide.EXTRACT; var in = com.powerfactor.logistics.PipeSide.INSERT; var pipe = com.powerfactor.logistics.PipeSide.PIPE;
            fluidPipe(helper, new BlockPos(3, 0, 2), true, java.util.Map.of(Direction.WEST, ex, Direction.EAST, in));
            fluidPipe(helper, new BlockPos(3, 0, 3), false, java.util.Map.of(Direction.WEST, in, Direction.SOUTH, pipe));
            fluidPipe(helper, new BlockPos(3, 0, 4), false, java.util.Map.of(Direction.NORTH, pipe, Direction.SOUTH, pipe));
            fluidPipe(helper, new BlockPos(3, 0, 5), false, java.util.Map.of(Direction.NORTH, pipe, Direction.SOUTH, in, Direction.EAST, ex, Direction.WEST, pipe));
            fluidPipe(helper, new BlockPos(2, 0, 5), false, java.util.Map.of(Direction.EAST, pipe, Direction.SOUTH, pipe));
            fluidPipe(helper, new BlockPos(2, 0, 6), false, java.util.Map.of(Direction.NORTH, pipe, Direction.EAST, ex));
            helper.assertTrue(boiler.formed() && turbine.formed(), "plant did not form");
            helper.assertTrue(helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(new BlockPos(2, 0, 1)), Direction.NORTH)
                    == boiler.getFuelHandler(), "boiler casing does not expose fuel input");
            var devices = com.powerfactor.scada.ScadaNetwork.discover(helper.getLevel(), helper.absolutePos(new BlockPos(3, 0, 2)));
            helper.assertTrue(devices.get(helper.absolutePos(boilerPos)) == boiler && devices.get(helper.absolutePos(turbinePos)) == turbine,
                    "SCADA could not discover the steam controllers through casing ports");
            helper.assertValueEqual(devices.size(), 2, "SCADA duplicated multiblock controllers");
            var fiber = ModBlocks.FIBER_OPTIC_CABLE.get();
            var connections = fiber.withConnections(helper.getLevel(), helper.absolutePos(new BlockPos(3, 0, 2)), fiber.defaultBlockState());
            helper.assertTrue(connections.getValue(com.powerfactor.grid.ConnectorBlock.CONNECTIONS.get(Direction.WEST))
                    && connections.getValue(com.powerfactor.grid.ConnectorBlock.CONNECTIONS.get(Direction.EAST)), "fiber did not render its casing connections");
        });
        helper.runAfterDelay(650, () -> {
            helper.assertTrue(turbine.model().breakerClosed(), "steam turbine did not synchronize");
            helper.assertTrue(crusher.slot(1).getCount() > 0, "turbine casing did not power the crusher");
            helper.assertTrue(turbine.model().lastOutput().ee() > 0, "steam alternator supplied no reactive power");
            helper.assertTrue(cooling.getEnergy().getAmountAsInt() > 0, "steam generator did not power the cooling loop");
            double water = boiler.getTanks().amount(0) + boiler.getTanks().amount(1) / 10.0
                    + turbine.getTanks().amount(0) / 10.0 + turbine.getTanks().amount(1)
                    + turbine.model().fuelEnergy() / 200.0 + turbine.condensateRemainderFe() / 200.0
                    + cooling.getTanks().amount(0) + cooling.getTanks().amount(1);
            helper.assertTrue(Math.abs(water - 16000) < 0.001, "steam loop lost or created water: " + water);
            helper.assertTrue(cooling.getTanks().amount(0) < 1000, "hot water never reached cooling");
            var registries = helper.getLevel().registryAccess();
            var savedBoiler = (com.powerfactor.steam.SteamBoilerBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                    boiler.getBlockPos(), boiler.getBlockState(), boiler.saveWithFullMetadata(registries), registries);
            var savedTurbine = (com.powerfactor.steam.SteamTurbineBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                    turbine.getBlockPos(), turbine.getBlockState(), turbine.saveWithFullMetadata(registries), registries);
            helper.assertTrue(savedBoiler != null && savedTurbine != null, "steam controllers could not load saved data");
            helper.assertValueEqual(savedBoiler.model().heat(), boiler.model().heat(), "saved boiler heat");
            helper.assertTrue(savedBoiler.model().fuel() == boiler.model().fuel(), "saved boiler fuel");
            helper.assertValueEqual(savedBoiler.getTanks().amount(0), boiler.getTanks().amount(0), "saved boiler water");
            helper.assertValueEqual(savedTurbine.model().rpm(), turbine.model().rpm(), "saved turbine RPM");
            helper.assertTrue(savedTurbine.model().fuelEnergy() == turbine.model().fuelEnergy()
                    && savedTurbine.condensateRemainderFe() == turbine.condensateRemainderFe(), "saved fractional condensate energy");
            helper.assertValueEqual(savedTurbine.getTanks().amount(1), turbine.getTanks().amount(1), "saved hot water");
            helper.setBlock(turbinePos.offset(-1, 1, 2), Blocks.AIR);
        });
        helper.runAfterDelay(690, () -> {
            helper.assertFalse(turbine.formed(), "turbine kept its formed state after a rotor frame was broken");
            helper.assertTrue(turbine.ratedCe() == 0, "broken turbine still supplies a grid");
            helper.succeed();
        });
    }

    /** Lava is a tagged stand-in only inside the fixture pack; components identify its origin. */
    private static net.neoforged.neoforge.transfer.fluid.FluidResource externalFluid() {
        var stack = new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 1);
        var data = new net.minecraft.nbt.CompoundTag(); data.putString("test_origin", "external");
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(data));
        return net.neoforged.neoforge.transfer.fluid.FluidResource.of(stack);
    }

    private static void taggedHydrogenFuelCell(GameTestHelper helper) {
        helper.setBlock(CENTER, ModBlocks.FUEL_CELL.get().defaultBlockState());
        var cell = helper.getBlockEntity(CENTER, com.powerfactor.generator.FuelCellBlockEntity.class);
        var crusher = placeCrusher(helper, CENTER.east());
        var input = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(CENTER), Direction.NORTH);
        var foreign = externalFluid();
        try (var tx = Transaction.openRoot()) {
            helper.assertValueEqual(input.insert(net.neoforged.neoforge.transfer.fluid.FluidResource.of(net.minecraft.world.level.material.Fluids.WATER), 10, tx), 0,
                    "empty fuel cell accepted untagged water");
            helper.assertValueEqual(input.insert(foreign, 2000, tx), 2000, "external hydrogen rejected");
        }
        helper.assertValueEqual(cell.getTank().amount(0), 0, "aborted hydrogen insertion changed tank");
        try (var tx = Transaction.openRoot()) { input.insert(foreign, 2000, tx); tx.commit(); }
        try (var tx = Transaction.openRoot()) {
            helper.assertValueEqual(input.insert(net.neoforged.neoforge.transfer.fluid.FluidResource.of(com.powerfactor.registry.ModFluids.HYDROGEN.get()), 10, tx), 0,
                    "fuel cell mixed distinct hydrogen fluids");
            helper.assertValueEqual(input.extract(foreign, 10, tx), 0, "input-only hydrogen tank drained through pipe");
        }
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(crusher.getEnergy().getAmountAsInt() > 500, "external hydrogen failed to power crusher");
            helper.assertTrue(cell.getTank().amount(0) > 0 && cell.getTank().amount(0) < 2000, "external hydrogen was not consumed");
            helper.assertTrue(cell.getTank().getResource(0).equals(foreign), "consumption converted foreign hydrogen or lost components");
            var saved = (com.powerfactor.generator.FuelCellBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                    cell.getBlockPos(), cell.getBlockState(), cell.saveWithFullMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
            helper.assertTrue(saved.getTank().getResource(0).equals(foreign) && saved.getTank().amount(0) == cell.getTank().amount(0),
                    "saved foreign hydrogen lost its identity, components or amount");
            cell.getTank().add(0, -cell.getTank().amount(0));
            try (var tx = Transaction.openRoot()) {
                helper.assertValueEqual(input.insert(net.neoforged.neoforge.transfer.fluid.FluidResource.of(com.powerfactor.registry.ModFluids.HYDROGEN.get()), 10, tx), 10,
                        "drained tank could not switch hydrogen provider"); tx.commit();
            }
            helper.succeed();
        });
    }

    private static void taggedSteamTurbine(GameTestHelper helper) {
        var at = new BlockPos(3, 0, 2); var from = new BlockPos(3, 0, 0);
        helper.setBlock(at, ModBlocks.STEAM_TURBINE.get().defaultBlockState());
        formStructure(helper, at, com.powerfactor.steam.SteamTurbineBlockEntity.STRUCTURE);
        var turbine = helper.getBlockEntity(at, com.powerfactor.steam.SteamTurbineBlockEntity.class);
        helper.setBlock(from, ModBlocks.FLUID_TANK.get().defaultBlockState());
        var source = helper.getBlockEntity(from, com.powerfactor.logistics.FluidTankBlockEntity.class);
        var foreign = externalFluid();
        try (var tx = Transaction.openRoot()) { source.getTank().insert(foreign, 24000, tx); tx.commit(); }
        fluidPipe(helper, new BlockPos(3, 0, 1), true, java.util.Map.of(Direction.NORTH, com.powerfactor.logistics.PipeSide.EXTRACT,
                Direction.SOUTH, com.powerfactor.logistics.PipeSide.INSERT));
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(source.fluid().isEmpty(), "steam pipe failed to extract tagged steam");
            helper.assertTrue(turbine.getTanks().getResource(0).equals(foreign), "steam pipe or turbine changed fluid components");
        });
        helper.runAfterDelay(300, () -> {
            helper.assertTrue(turbine.getEnergy().getAmountAsInt() > 1000, "tagged steam failed to generate energy");
            helper.assertTrue(turbine.getTanks().amount(0) > 0 && turbine.getTanks().getResource(0).equals(foreign), "consuming steam changed the remaining fluid");
            helper.assertTrue(turbine.getTanks().amount(1) > 0 && turbine.getTanks().getResource(1).getFluid().isSame(com.powerfactor.registry.ModFluids.HOT_WATER.get()),
                    "external steam failed to return native condensate");
            helper.succeed();
        });
    }

    private static void taggedCrudeRefinery(GameTestHelper helper) {
        var at = new BlockPos(3, 0, 1);
        helper.setBlock(at, ModBlocks.REFINERY.get().defaultBlockState());
        formStructure(helper, at, com.powerfactor.oil.RefineryBlockEntity.STRUCTURE);
        var refinery = helper.getBlockEntity(at, com.powerfactor.oil.RefineryBlockEntity.class);
        var foreign = externalFluid();
        var input = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(at), Direction.NORTH);
        try (var tx = Transaction.openRoot()) {
            helper.assertValueEqual(input.insert(foreign, 1000, tx), 1000, "external crude oil rejected"); tx.commit();
        }
        try (var tx = Transaction.openRoot()) {
            helper.assertValueEqual(refinery.getTanks().insert(1, net.neoforged.neoforge.transfer.fluid.FluidResource.of(com.powerfactor.registry.ModFluids.DIESEL.source().get()), 100, tx), 0,
                    "refinery output accepted pipe insertion");
        }
        try (var tx = Transaction.openRoot()) { refinery.getEnergy().insert(500, tx); tx.commit(); }
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(refinery.formed(), "tagged-oil refinery did not form");
            helper.assertValueEqual(refinery.getTanks().amount(0), 950, "external crude consumption");
            helper.assertTrue(refinery.getTanks().getResource(0).equals(foreign), "remaining crude changed identity or components");
            helper.assertValueEqual(refinery.getTanks().amount(1), 25, "diesel yield from external crude");
            helper.assertValueEqual(refinery.getTanks().amount(2), 20, "jet-fuel yield from external crude");
            helper.succeed();
        });
    }

    private static void taggedHotWaterCooling(GameTestHelper helper) {
        var origin = helper.absolutePos(new BlockPos(3, 0, 3));
        int height = helper.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                origin.getX(), origin.getZ()) - origin.getY() + 1;
        var at = new BlockPos(3, height, 3);
        helper.setBlock(at, ModBlocks.COOLING_TOWER.get().defaultBlockState());
        helper.setBlock(at.above(), ModBlocks.COOLING_TOWER.get().defaultBlockState().setValue(com.powerfactor.machine.TallMachineBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        var cooler = helper.getBlockEntity(at, com.powerfactor.steam.CoolingTowerBlockEntity.class);
        var foreign = externalFluid();
        var input = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(at), Direction.NORTH);
        try (var tx = Transaction.openRoot()) { helper.assertValueEqual(input.insert(foreign, 2000, tx), 2000, "external hot water rejected"); tx.commit(); }
        feed(helper, cooler.getEnergy(), 1000);
        helper.runAfterDelay(40, () -> {
            int hot = cooler.getTanks().amount(0), cold = cooler.getTanks().amount(1);
            helper.assertTrue(cold > 0 && hot > 0 && hot + cold == 2000, "cooling changed total water or failed to run");
            helper.assertTrue(cooler.getTanks().getResource(0).equals(foreign), "remaining hot water changed identity or components");
            helper.assertTrue(cooler.getTanks().getResource(1).getFluid().isSame(net.minecraft.world.level.material.Fluids.WATER), "cooler produced no cold water");
            helper.succeed();
        });
    }

    private static void steamPipeRejectsWater(GameTestHelper helper) {
        var from = new BlockPos(0, 1, 1); var to = new BlockPos(2, 1, 1);
        helper.setBlock(from, ModBlocks.FLUID_TANK.get().defaultBlockState());
        helper.setBlock(to, ModBlocks.FLUID_TANK.get().defaultBlockState());
        var source = helper.getBlockEntity(from, com.powerfactor.logistics.FluidTankBlockEntity.class);
        var target = helper.getBlockEntity(to, com.powerfactor.logistics.FluidTankBlockEntity.class);
        try (var tx = Transaction.openRoot()) {
            source.getTank().insert(net.neoforged.neoforge.transfer.fluid.FluidResource.of(net.minecraft.world.level.material.Fluids.WATER), 1000, tx); tx.commit();
        }
        fluidPipe(helper, CENTER, true, java.util.Map.of(Direction.WEST, com.powerfactor.logistics.PipeSide.EXTRACT, Direction.EAST, com.powerfactor.logistics.PipeSide.INSERT));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(target.fluid().isEmpty(), "steam pipe transported water");
            try (var tx = Transaction.openRoot()) {
                source.getTank().extract(net.neoforged.neoforge.transfer.fluid.FluidResource.of(net.minecraft.world.level.material.Fluids.WATER), 1000, tx);
                source.getTank().insert(net.neoforged.neoforge.transfer.fluid.FluidResource.of(com.powerfactor.registry.ModFluids.STEAM.get()), 6000, tx); tx.commit();
            }
        });
        helper.runAfterDelay(70, () -> {
            helper.assertTrue(target.fluid().is(com.powerfactor.registry.ModFluids.STEAM.get()) && target.fluid().getAmount() == 6000,
                    "steam pipe failed to move its gas");
            helper.succeed();
        });
    }

    private static void steamTurbinePreservesSteam(GameTestHelper helper) {
        var at = new BlockPos(3, 0, 1);
        helper.setBlock(at, ModBlocks.STEAM_TURBINE.get().defaultBlockState());
        formStructure(helper, at, com.powerfactor.steam.SteamTurbineBlockEntity.STRUCTURE);
        var turbine = helper.getBlockEntity(at, com.powerfactor.steam.SteamTurbineBlockEntity.class);
        turbine.getTanks().add(0, 10000); turbine.getTanks().add(1, 32000);
        helper.runAfterDelay(40, () -> {
            helper.assertValueEqual(turbine.getTanks().amount(0), 10000, "steam consumed with full condensate output");
            helper.assertValueEqual(turbine.getEnergy().getAmountAsInt(), 0, "energy created with blocked exhaust");
            turbine.getTanks().add(1, -32000);
        });
        helper.runAfterDelay(300, () -> {
            helper.assertTrue(turbine.getEnergy().getAmountAsInt() > 1000, "turbine failed to restart after condensate drained");
            helper.assertTrue(turbine.getTanks().amount(1) > 0, "no condensate returned");
            helper.succeed();
        });
    }

    private static void steamStructureOwnership(GameTestHelper helper) {
        var a = new BlockPos(1, 0, 1); var b = new BlockPos(4, 0, 1);
        helper.setBlock(a, ModBlocks.STEAM_BOILER.get().defaultBlockState());
        helper.setBlock(b, ModBlocks.STEAM_BOILER.get().defaultBlockState());
        formStructure(helper, a, com.powerfactor.steam.SteamBoilerBlockEntity.STRUCTURE);
        formStructure(helper, b, com.powerfactor.steam.SteamBoilerBlockEntity.STRUCTURE);
        var second = helper.getBlockEntity(b, com.powerfactor.steam.SteamBoilerBlockEntity.class);
        helper.runAfterDelay(25, () -> {
            var claimed = helper.getBlockEntity(b.offset(1, 0, 0), com.powerfactor.multiblock.CasingBlockEntity.class);
            claimed.link(helper.absolutePos(a));
        });
        helper.runAfterDelay(55, () -> {
            helper.assertFalse(second.formed(), "controller stole another structure's part");
            helper.assertFalse(com.powerfactor.multiblock.StructureBuilder.assemble(helper.getLevel(), helper.absolutePos(b), Direction.NORTH,
                    helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE), second.buildStructure()), "auto-build reused a claimed part");
            helper.succeed();
        });
    }

    private static void coolingTowerNeedsVent(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(3, 0, 3));
        // Test markers may themselves obstruct the vent; test above them, with an explicit glass roof.
        int height = helper.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                origin.getX(), origin.getZ()) - origin.getY() + 1;
        var at = new BlockPos(3, height, 3);
        helper.setBlock(at, ModBlocks.COOLING_TOWER.get().defaultBlockState());
        helper.setBlock(at.above(), ModBlocks.COOLING_TOWER.get().defaultBlockState().setValue(com.powerfactor.machine.TallMachineBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        var cooling = helper.getBlockEntity(at, com.powerfactor.steam.CoolingTowerBlockEntity.class);
        cooling.getTanks().add(0, 1000);
        feed(helper, cooling.getEnergy(), 400);
        helper.setBlock(at.above(2), Blocks.GLASS);
        helper.runAfterDelay(25, () -> {
            helper.assertValueEqual(cooling.getTanks().amount(0), 1000, "cooler worked with a blocked vent");
            helper.setBlock(at.above(2), Blocks.AIR);
        });
        helper.runAfterDelay(125, () -> {
            helper.assertValueEqual(cooling.getTanks().amount(0), 0, "cooler did not drain hot water");
            helper.assertValueEqual(cooling.getTanks().amount(1), 1000, "cooler did not conserve water");
            helper.succeed();
        });
    }

    private static void ambientGeneratorsNeedHeat(GameTestHelper helper) {
        var geo = new BlockPos(0, 1, 1); var thermo = new BlockPos(2, 1, 1);
        helper.setBlock(geo, ModBlocks.GEOTHERMAL_GENERATOR.get().defaultBlockState());
        helper.setBlock(thermo, ModBlocks.THERMOELECTRIC_GENERATOR.get().defaultBlockState());
        var geothermal = helper.getBlockEntity(geo, com.powerfactor.generator.AmbientGeneratorBlockEntity.class);
        var thermoelectric = helper.getBlockEntity(thermo, com.powerfactor.generator.AmbientGeneratorBlockEntity.class);
        helper.runAfterDelay(10, () -> {
            helper.assertValueEqual(geothermal.getEnergy().getAmountAsInt(), 0, "geothermal generated without heat");
            helper.setBlock(geo.below(), Blocks.MAGMA_BLOCK); helper.setBlock(thermo.below(), Blocks.MAGMA_BLOCK);
        });
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(geothermal.getEnergy().getAmountAsInt() > 0, "magma did not power geothermal generator");
            helper.assertValueEqual(thermoelectric.getEnergy().getAmountAsInt(), 0, "thermoelectric worked without a cold face");
            helper.setBlock(thermo.above(), Blocks.PACKED_ICE);
        });
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(thermoelectric.getEnergy().getAmountAsInt() > 0, "opposite hot/cold faces did not generate");
            helper.succeed();
        });
    }

    private static void rainCollectorNeedsRain(GameTestHelper helper) {
        // The test harness's transparent markers can block precipitation while admitting sky light.
        // Put both catchments above the actual column height, then give one a deliberate roof.
        BlockPos origin = helper.absolutePos(new BlockPos(0, 0, 1));
        BlockPos other = helper.absolutePos(new BlockPos(2, 0, 1));
        int height = Math.max(helper.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, origin.getX(), origin.getZ()),
                helper.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, other.getX(), other.getZ())) - origin.getY() + 1;
        var open = new BlockPos(0, height, 1); var covered = new BlockPos(2, height, 1);
        helper.setBlock(open, ModBlocks.RAIN_COLLECTOR.get().defaultBlockState());
        helper.setBlock(covered, ModBlocks.RAIN_COLLECTOR.get().defaultBlockState());
        helper.setBlock(covered.above(), Blocks.STONE);
        var collector = helper.getBlockEntity(open, com.powerfactor.device.RainCollectorBlockEntity.class);
        var roofed = helper.getBlockEntity(covered, com.powerfactor.device.RainCollectorBlockEntity.class);
        helper.getLevel().getServer().setWeatherParameters(0, 100, true, false);
        final int[] before = {0};
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(collector.getTank().amount(0) > 0, "open collector caught no rain");
            helper.assertValueEqual(roofed.getTank().amount(0), 0, "roofed collector generated water");
            helper.getLevel().getServer().setWeatherParameters(1000, 0, false, false);
        });
        helper.runAfterDelay(60, () -> before[0] = collector.getTank().amount(0));
        helper.runAfterDelay(85, () -> {
            helper.assertValueEqual(collector.getTank().amount(0), before[0], "collector generated water in clear weather");
            helper.succeed();
        });
    }

    private static net.minecraft.world.item.ItemStack armor(net.minecraft.world.item.Item item, int energy) {
        var stack = new net.minecraft.world.item.ItemStack(item); stack.set(com.powerfactor.registry.ModComponents.ENERGY.get(), energy); return stack;
    }
    private static void modules(GameTestHelper helper, net.minecraft.world.item.ItemStack stack, net.minecraft.world.item.ItemStack... modules) {
        var items = net.minecraft.core.NonNullList.withSize(com.powerfactor.item.ToolModules.SLOTS, net.minecraft.world.item.ItemStack.EMPTY);
        for (int i = 0; i < modules.length; i++) items.set(i, modules[i]);
        com.powerfactor.item.ToolModules.install(stack, items, helper.getLevel().registryAccess());
    }
    private static void powerArmorShieldAndLanding(GameTestHelper helper) {
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var chest = armor(ModItems.POWER_CHESTPLATE.get(), 1000);
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, chest);
        float damage = com.powerfactor.item.PowerArmorServer.shield(player, 10);
        helper.assertTrue(Math.abs(damage - 9.2F) < 0.001, "charged chest shield did not absorb 8%: " + damage);
        helper.assertValueEqual(com.powerfactor.item.PoweredItem.energy(chest), 904, "shield energy cost");
        chest.set(com.powerfactor.registry.ModComponents.ENERGY.get(), 12);
        helper.assertTrue(Math.abs(com.powerfactor.item.PowerArmorServer.shield(player, 10) - 9.9F) < 0.001, "partial battery granted full shield");
        helper.assertValueEqual(com.powerfactor.item.PoweredItem.energy(chest), 0, "partial shield budget");
        helper.assertTrue(com.powerfactor.item.PowerArmorServer.shield(player, 10) == 10, "flat armor shielded for free");
        var boots = armor(ModItems.POWER_BOOTS.get(), 500);
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, boots);
        helper.assertTrue(com.powerfactor.item.PowerArmorServer.landing(player, 10) == 5, "landing cushion distance");
        helper.assertValueEqual(com.powerfactor.item.PoweredItem.energy(boots), 0, "landing cushion cost");
        helper.assertTrue(com.powerfactor.item.PowerArmorServer.landing(player, 10) == 10, "empty boots cushioned a fall");
        helper.succeed();
    }
    private static void powerArmorServoShutdown(GameTestHelper helper) {
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        double baseSpeed = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        double baseJump = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH);
        var legs = armor(ModItems.POWER_LEGGINGS.get(), 1000);
        modules(helper, legs, new net.minecraft.world.item.ItemStack(ModItems.DRILL_MODULES.get(com.powerfactor.item.DrillModule.Type.SPEED).get(), 2),
                new net.minecraft.world.item.ItemStack(ModItems.DRILL_MODULES.get(com.powerfactor.item.DrillModule.Type.JUMP).get(), 2));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, legs);
        com.powerfactor.item.PowerArmorServer.tick(player);
        helper.assertTrue(Math.abs(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) - baseSpeed * 1.55) < 0.001, "servo speed attribute");
        helper.assertTrue(Math.abs(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH) - baseJump - 0.26) < 0.001, "servo jump attribute");
        com.powerfactor.item.PowerArmorServer.control(player, 0); com.powerfactor.item.PowerArmorServer.tick(player);
        helper.assertTrue(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) == baseSpeed, "disabled servos left a speed modifier");
        com.powerfactor.item.PowerArmorServer.control(player, 0);
        legs.set(com.powerfactor.registry.ModComponents.ENERGY.get(), 0); com.powerfactor.item.PowerArmorServer.tick(player);
        helper.assertTrue(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH) == baseJump, "empty servo battery left a jump modifier");
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.item.ItemStack.EMPTY);
        com.powerfactor.item.PowerArmorServer.tick(player);
        helper.assertTrue(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT) == 0.6, "removed leggings left a step modifier");
        helper.succeed();
    }
    private static void powerArmorJetpackSocket(GameTestHelper helper) {
        helper.setBlock(CENTER, ModBlocks.UPGRADE_TABLE.get().defaultBlockState());
        var table = helper.getBlockEntity(CENTER, com.powerfactor.upgrade.UpgradeTableBlockEntity.class);
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var chest = armor(ModItems.POWER_CHESTPLATE.get(), 600000);
        var pack = armor(ModItems.ELECTRIC_JETPACK.get(), 12345);
        modules(helper, pack, new net.minecraft.world.item.ItemStack(ModItems.DRILL_MODULES.get(com.powerfactor.item.DrillModule.Type.THRUSTER).get(), 2));
        table.tool().setItem(0, chest);
        var menu = new com.powerfactor.upgrade.UpgradeTableMenu(1, player.getInventory(), table);
        helper.assertTrue(menu.accepts(pack, 0), "chest does not accept a jetpack socket");
        menu.modules().setItem(0, pack.copy());
        helper.assertFalse(menu.accepts(pack, 1), "second jetpack accepted");
        helper.assertTrue(com.powerfactor.item.JetpackItem.worn(chest), "fitted jetpack did not enable flight");
        helper.assertTrue(Math.abs(com.powerfactor.item.JetpackItem.thrust(chest) - 1.7) < 0.001, "fitted jetpack lost its thruster upgrades");
        var saved = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(table.getBlockPos(), table.getBlockState(),
                table.saveWithFullMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        var loaded = ((com.powerfactor.upgrade.UpgradeTableBlockEntity) saved).tool().getItem(0);
        helper.assertValueEqual(com.powerfactor.item.PoweredItem.energy(com.powerfactor.item.PowerArmorItem.jetpack(loaded)), 12345, "saved nested jetpack charge");
        var removed = menu.modules().removeItem(0, 1);
        helper.assertValueEqual(com.powerfactor.item.PoweredItem.energy(removed), 12345, "removed jetpack charge");
        helper.assertFalse(com.powerfactor.item.JetpackItem.worn(chest), "removed jetpack still enables flight");
        helper.assertValueEqual(com.powerfactor.item.PoweredItem.energy(chest), 600000, "jetpack insertion changed chest charge");
        helper.succeed();
    }
    private static void powerArmorChargesEquipment(GameTestHelper helper) {
        helper.setBlock(CENTER, ModBlocks.CHARGING_PAD.get().defaultBlockState());
        var pad = helper.getBlockEntity(CENTER, com.powerfactor.device.ChargingPadBlockEntity.class);
        var player = helper.makeMockServerPlayerInLevel(); player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var absolute = helper.absolutePos(CENTER); player.setPos(absolute.getX() + 0.5, absolute.getY() + 0.26, absolute.getZ() + 0.5);
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, armor(ModItems.POWER_HELMET.get(), 0));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, armor(ModItems.POWER_CHESTPLATE.get(), 0));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, armor(ModItems.POWER_LEGGINGS.get(), 0));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, armor(ModItems.POWER_BOOTS.get(), 0));
        feed(helper, pad.getEnergy(), 4000);
        helper.runAfterDelay(35, () -> {
            for (var part : com.powerfactor.item.PowerArmorItem.Part.values()) {
                helper.assertTrue(com.powerfactor.item.PoweredItem.energy(com.powerfactor.item.PowerArmorServer.piece(player, part)) > 0,
                        "charging pad missed worn " + part);
            }
            player.discard(); helper.succeed();
        });
    }
    private static void powerArmorVisor(GameTestHelper helper) {
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var head = armor(ModItems.POWER_HELMET.get(), 1000);
        modules(helper, head, new net.minecraft.world.item.ItemStack(ModItems.DRILL_MODULES.get(com.powerfactor.item.DrillModule.Type.NIGHT_VISION).get()));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, head);
        com.powerfactor.item.PowerArmorServer.tick(player);
        helper.assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION), "powered visor gave no vision");
        helper.assertValueEqual(com.powerfactor.item.PoweredItem.energy(head), 996, "visor cost");
        com.powerfactor.item.PowerArmorServer.control(player, 1); com.powerfactor.item.PowerArmorServer.tick(player);
        helper.assertFalse(player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION), "disabled visor left its effect");
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION, 1200));
        com.powerfactor.item.PowerArmorServer.control(player, 1); com.powerfactor.item.PowerArmorServer.tick(player);
        com.powerfactor.item.PowerArmorServer.control(player, 1); com.powerfactor.item.PowerArmorServer.tick(player);
        helper.assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION), "visor removed an external night-vision potion");
        helper.succeed();
    }

    private static void powerArmorDamageEvent(GameTestHelper helper) {
        var charged = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var flat = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var powered = armor(ModItems.POWER_CHESTPLATE.get(), 5000);
        charged.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, powered);
        flat.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, armor(ModItems.POWER_CHESTPLATE.get(), 0));
        charged.tick(); flat.tick();
        charged.hurtServer(helper.getLevel(), charged.damageSources().cactus(), 10);
        flat.hurtServer(helper.getLevel(), flat.damageSources().cactus(), 10);
        helper.assertTrue(charged.getHealth() > flat.getHealth(), "damage event did not apply the powered shield: charged="
                + charged.getHealth() + ", flat=" + flat.getHealth() + ", FE=" + com.powerfactor.item.PoweredItem.energy(powered));
        helper.assertTrue(com.powerfactor.item.PoweredItem.energy(powered) < 5000, "actual damage did not spend shield FE");
        int energy = com.powerfactor.item.PoweredItem.energy(powered);
        charged.invulnerableTime = 0;
        charged.hurtServer(helper.getLevel(), charged.damageSources().magic(), 1);
        helper.assertValueEqual(com.powerfactor.item.PoweredItem.energy(powered), energy, "armor shield intercepted bypassing damage");
        var boots = armor(ModItems.POWER_BOOTS.get(), 500);
        charged.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, boots);
        charged.setHealth(20); charged.invulnerableTime = 0;
        charged.causeFallDamage(8, 1, charged.damageSources().fall());
        helper.assertTrue(charged.getHealth() == 20, "powered boots did not intercept the actual fall event");
        helper.assertValueEqual(com.powerfactor.item.PoweredItem.energy(boots), 0, "fall event energy cost");
        charged.causeFallDamage(8, 1, charged.damageSources().fall());
        helper.assertTrue(charged.getHealth() < 20, "empty boots still canceled the fall event");
        helper.succeed();
    }

    private static void powerArmorRebreather(GameTestHelper helper) {
        helper.setBlock(CENTER, Blocks.WATER); helper.setBlock(CENTER.above(), Blocks.WATER);
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var pos = helper.absolutePos(CENTER);
        player.setPos(pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5);
        var head = armor(ModItems.POWER_HELMET.get(), 1000);
        modules(helper, head, new net.minecraft.world.item.ItemStack(ModItems.DRILL_MODULES.get(com.powerfactor.item.DrillModule.Type.REBREATHER).get()));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, head);
        player.tick(); player.setAirSupply(100);
        helper.assertTrue(player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER), "rebreather fixture is not underwater");
        com.powerfactor.item.PowerArmorServer.tick(player);
        helper.assertTrue(player.getAirSupply() > 100, "powered rebreather restored no air");
        helper.assertTrue(com.powerfactor.item.PoweredItem.energy(head) < 1000, "rebreather consumed no FE");
        head.set(com.powerfactor.registry.ModComponents.ENERGY.get(), 0); player.setAirSupply(100);
        com.powerfactor.item.PowerArmorServer.tick(player);
        helper.assertValueEqual(player.getAirSupply(), 100, "empty helmet restored air");
        helper.succeed();
    }

    private static net.minecraft.world.entity.player.Player movementPlayer(GameTestHelper helper, BlockPos pos) {
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var at = helper.absolutePos(pos); player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, armor(ModItems.GRAPPLE_HOOK.get(), 10000));
        return player;
    }
    private static com.powerfactor.movement.GrappleHookEntity anchoredHook(GameTestHelper helper, net.minecraft.world.entity.player.Player player, BlockPos anchor) {
        helper.setBlock(anchor, Blocks.STONE);
        var hook = new com.powerfactor.movement.GrappleHookEntity(helper.getLevel(), player);
        helper.getLevel().addFreshEntity(hook);
        var at = helper.absolutePos(anchor);
        hook.attach(new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(at).add(-0.5, 0, 0), Direction.WEST, at, false));
        return hook;
    }
    private static void grappleHitsAndReels(GameTestHelper helper) {
        var player = movementPlayer(helper, new BlockPos(1, 1, 3));
        var anchor = new BlockPos(5, 2, 3); helper.setBlock(anchor, Blocks.STONE);
        var hook = new com.powerfactor.movement.GrappleHookEntity(helper.getLevel(), player);
        var aim = net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(anchor)).subtract(hook.position());
        hook.shoot(aim.x, aim.y, aim.z, 3, 0); helper.getLevel().addFreshEntity(hook);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(hook.anchored(), "projectile did not catch the solid anchor");
            helper.assertTrue(hook.shouldRenderAtSqrDistance(64 * 64), "small hook culling hides a full-range cable");
            helper.assertTrue(player.getDeltaMovement().x > 0, "reel supplied no motion toward the anchor");
            helper.assertTrue(com.powerfactor.item.PoweredItem.energy(player.getMainHandItem()) < 10000, "reeling consumed no FE");
            hook.discard(); helper.succeed();
        });
    }
    private static void grappleSwingAndBattery(GameTestHelper helper) {
        var player = movementPlayer(helper, new BlockPos(1, 1, 3)); player.setShiftKeyDown(true);
        var hook = anchoredHook(helper, player, new BlockPos(5, 4, 3));
        float length = hook.ropeLength();
        hook.tick();
        helper.assertValueEqual(com.powerfactor.item.PoweredItem.energy(player.getMainHandItem()), 10000, "swing ran the reel motor");
        helper.assertTrue(hook.ropeLength() == length, "swing shortened the fixed cable");
        player.setShiftKeyDown(false); player.getMainHandItem().set(com.powerfactor.registry.ModComponents.ENERGY.get(), 0);
        hook.tick(); helper.assertTrue(hook.isRemoved(), "empty grapple kept its powered reel active");
        helper.succeed();
    }
    private static void grappleBrokenAnchor(GameTestHelper helper) {
        var player = movementPlayer(helper, new BlockPos(1, 1, 3)); var at = new BlockPos(5, 4, 3);
        var hook = anchoredHook(helper, player, at); helper.setBlock(at, Blocks.AIR); hook.tick();
        helper.assertTrue(hook.isRemoved(), "broken terrain anchor kept the cable alive");
        var blocked = anchoredHook(helper, player, at);
        helper.setBlock(new BlockPos(3, 3, 3), Blocks.STONE); blocked.tick();
        helper.assertTrue(blocked.isRemoved(), "obstructed cable kept pulling through terrain");
        helper.setBlock(new BlockPos(3, 3, 3), Blocks.AIR);
        var dropped = anchoredHook(helper, player, at);
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY); dropped.tick();
        helper.assertTrue(dropped.isRemoved(), "dropping grapple left an active cable"); helper.succeed();
    }
    private static void grappleMovingAnchor(GameTestHelper helper) {
        var player = movementPlayer(helper, new BlockPos(1, 1, 3));
        var target = helper.spawn(net.minecraft.world.entity.EntityTypes.PIG, new BlockPos(5, 2, 3));
        target.setNoAi(true); target.setNoGravity(true);
        var hook = new com.powerfactor.movement.GrappleHookEntity(helper.getLevel(), player);
        var aim = target.getBoundingBox().getCenter().subtract(hook.position()); hook.shoot(aim.x, aim.y, aim.z, 3, 0);
        helper.getLevel().addFreshEntity(hook);
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(hook.anchored() && hook.targetId() == target.getId(), "hook did not attach to the moving entity");
            var before = hook.position(); target.setPos(target.position().add(0, 0, 1)); hook.tick();
            helper.assertTrue(Math.abs(hook.getZ() - before.z - 1) < 0.001, "entity anchor did not carry the hook");
            target.discard(); hook.tick(); helper.assertTrue(hook.isRemoved(), "removed moving anchor kept its hook"); helper.succeed();
        });
    }
    private static void grappleDeviceAndRelease(GameTestHelper helper) {
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var at = helper.absolutePos(CENTER); player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        helper.assertFalse(com.powerfactor.movement.GrappleServer.toggle(player), "grapple cast without the device");
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, armor(ModItems.GRAPPLE_HOOK.get(), 99));
        helper.assertFalse(com.powerfactor.movement.GrappleServer.toggle(player), "grapple cast without launch energy");
        player.getMainHandItem().set(com.powerfactor.registry.ModComponents.ENERGY.get(), 10000);
        helper.assertTrue(com.powerfactor.movement.GrappleServer.toggle(player), "charged grapple did not launch");
        helper.assertValueEqual(com.powerfactor.item.PoweredItem.energy(player.getMainHandItem()), 9900, "launch cost");
        player.setDeltaMovement(0.4, 0.3, 0.7);
        com.powerfactor.movement.GrappleServer.release(player);
        helper.assertTrue(player.getDeltaMovement().equals(new net.minecraft.world.phys.Vec3(0.4, 0.3, 0.7)), "release destroyed momentum");
        helper.succeed();
    }
    private static void wingsuitDeployAndFold(GameTestHelper helper) {
        var player = movementPlayer(helper, new BlockPos(3, 4, 3));
        helper.assertFalse(com.powerfactor.movement.WingsuitServer.toggle(player), "wingsuit deployed without equipment");
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(ModItems.WINGSUIT.get()));
        player.setOnGround(true); helper.assertFalse(com.powerfactor.movement.WingsuitServer.toggle(player), "wingsuit opened on the ground");
        player.setOnGround(false); helper.assertTrue(com.powerfactor.movement.WingsuitServer.toggle(player), "airborne wingsuit did not deploy");
        helper.assertTrue(player.isFallFlying(), "deployed wingsuit gave no glide state");
        helper.assertTrue(player.getAttributeValue(net.neoforged.neoforge.common.NeoForgeMod.GLIDING_FLIGHT) > 0, "wingsuit gave no flight attribute");
        player.setOnGround(true); com.powerfactor.movement.WingsuitServer.tick(player);
        helper.assertFalse(player.isFallFlying(), "grounded wingsuit kept gliding");
        helper.assertTrue(player.getAttributeValue(net.neoforged.neoforge.common.NeoForgeMod.GLIDING_FLIGHT) == 0, "grounded wingsuit left a flight modifier");
        helper.succeed();
    }
    private static void wingsuitArmorSocket(GameTestHelper helper) {
        helper.setBlock(CENTER, ModBlocks.UPGRADE_TABLE.get().defaultBlockState());
        var table = helper.getBlockEntity(CENTER, com.powerfactor.upgrade.UpgradeTableBlockEntity.class);
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var chest = armor(ModItems.POWER_CHESTPLATE.get(), 500000); table.tool().setItem(0, chest);
        var menu = new com.powerfactor.upgrade.UpgradeTableMenu(1, player.getInventory(), table);
        player.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(ModItems.WINGSUIT.get()));
        menu.quickMoveStack(player, com.powerfactor.upgrade.UpgradeTableMenu.FIRST_INV + 27);
        helper.assertTrue(com.powerfactor.item.WingsuitItem.worn(chest), "shift-moving wingsuit did not fit its socket");
        var pack = armor(ModItems.ELECTRIC_JETPACK.get(), 12345);
        helper.assertTrue(menu.accepts(pack, 1), "jetpack could not coexist with wingsuit"); menu.modules().setItem(1, pack);
        helper.assertFalse(menu.accepts(new net.minecraft.world.item.ItemStack(ModItems.WINGSUIT.get()), 2), "second wingsuit accepted");
        var saved = (com.powerfactor.upgrade.UpgradeTableBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(table.getBlockPos(),
                table.getBlockState(), table.saveWithFullMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        var loaded = saved.tool().getItem(0);
        helper.assertTrue(com.powerfactor.item.WingsuitItem.worn(loaded) && com.powerfactor.item.JetpackItem.worn(loaded), "saved chest lost a flight device");
        menu.modules().removeItem(0, 1);
        helper.assertFalse(com.powerfactor.item.WingsuitItem.worn(chest), "removed wingsuit still grants a glide device");
        helper.assertTrue(com.powerfactor.item.JetpackItem.worn(chest), "removing wingsuit removed jetpack too");
        helper.succeed();
    }
    private static void wingsuitChangeAndRestore(GameTestHelper helper) {
        var player = movementPlayer(helper, CENTER.above(4));
        var suit = new net.minecraft.world.item.ItemStack(ModItems.WINGSUIT.get()); player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, suit);
        com.powerfactor.movement.WingsuitServer.toggle(player);
        helper.assertTrue(suit.getOrDefault(com.powerfactor.registry.ModComponents.WINGSUIT_OPEN.get(), false), "deployed flag was not stored on equipment");
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.item.ItemStack.EMPTY);
        com.powerfactor.movement.WingsuitServer.tick(player);
        helper.assertFalse(player.isFallFlying(), "removing equipment left glide active");
        helper.assertTrue(player.getAttributeValue(net.neoforged.neoforge.common.NeoForgeMod.GLIDING_FLIGHT) == 0, "removing equipment left its flight attribute");
        var restored = movementPlayer(helper, CENTER.above(4)); restored.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, suit.copy());
        restored.tick();
        helper.assertTrue(restored.isFallFlying(), "stored deployment did not restore before the movement tick");
        com.powerfactor.movement.WingsuitServer.fold(restored); helper.succeed();
    }

    private static com.powerfactor.vehicle.RoverEntity testRover(GameTestHelper helper, int energy) {
        for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        var rover = new com.powerfactor.vehicle.RoverEntity(com.powerfactor.registry.ModEntities.ELECTRIC_ROVER.get(), helper.getLevel());
        var at = helper.absolutePos(new BlockPos(3, 1, 1)); rover.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        rover.restore(armor(ModItems.ELECTRIC_ROVER.get(), energy)); rover.setOnGround(true); helper.getLevel().addFreshEntity(rover); return rover;
    }
    private static void roverClimbsAndPays(GameTestHelper helper) {
        var rover = testRover(helper, 10000); var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.startRiding(rover); double initialY = rover.getY();
        for (int x = 0; x < 7; x++) helper.setBlock(new BlockPos(x, 1, 4), Blocks.STONE);
        for (int i = 0; i < 11; i++) { helper.assertTrue(rover.input(player, 1, 0, false, false), "driver input rejected"); rover.tick(); }
        helper.assertTrue(rover.getY() >= initialY + 0.99, "rover did not climb the one-block slope: " + rover.position());
        helper.assertValueEqual(rover.charge(), 10000 - 11 * com.powerfactor.vehicle.RoverEntity.DRIVE_FE, "motor did not pay exact FE");
        var front = rover.position();
        for (int x = 0; x < 7; x++) { helper.setBlock(new BlockPos(x, 2, 5), Blocks.STONE); helper.setBlock(new BlockPos(x, 3, 5), Blocks.STONE); }
        for (int i = 0; i < 5; i++) { rover.input(player, 1, 0, false, false); rover.tick(); }
        helper.assertTrue(rover.getZ() < helper.absolutePos(new BlockPos(3, 1, 5)).getZ(), "rover drove through a two-block wall");
        player.stopRiding(); rover.discard(); helper.succeed();
    }
    private static void roverInputAndShutdown(GameTestHelper helper) {
        var rover = testRover(helper, 500); var driver = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var stranger = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL); driver.startRiding(rover);
        helper.assertFalse(rover.input(stranger, 1, 1, false, true), "non-driver could control rover");
        helper.assertFalse(rover.input(driver, Float.NaN, 0, false, false), "non-finite input accepted");
        helper.assertFalse(rover.isClientAuthoritative(), "rider can submit vehicle positions");
        rover.input(driver, 100, 0, false, true); rover.tick();
        helper.assertValueEqual(rover.charge(), 450, "boost cost wrong");
        rover.restore(armor(ModItems.ELECTRIC_ROVER.get(), 0)); rover.setDeltaMovement(0, 0, 0.2);
        rover.input(driver, 1, 0, false, true); rover.tick();
        helper.assertFalse(rover.motorActive(), "empty battery ran the motor"); helper.assertTrue(rover.speed() < 0.2, "empty battery accelerated");
        rover.restore(armor(ModItems.ELECTRIC_ROVER.get(), 10000)); rover.setDeltaMovement(0, 0, 0);
        rover.input(driver, 1, 0, false, false);
        for (int i = 0; i < 11; i++) rover.tick();
        helper.assertTrue(rover.braking() && !rover.motorActive(), "stale input did not stop the motor");
        driver.stopRiding(); rover.tick(); helper.assertTrue(rover.braking(), "unoccupied rover did not brake");
        rover.discard(); helper.succeed();
    }
    private static void roverPadCharges(GameTestHelper helper) {
        var rover = testRover(helper, 0); var padPos = new BlockPos(3, 0, 3);
        helper.setBlock(padPos, ModBlocks.CHARGING_PAD.get().defaultBlockState());
        var at = helper.absolutePos(padPos); rover.setPos(at.getX() + 0.5, at.getY() + 0.2, at.getZ() + 0.5);
        var pad = helper.getBlockEntity(padPos, com.powerfactor.device.ChargingPadBlockEntity.class);
        try (var tx = Transaction.openRoot()) { pad.getEnergy().insert(2500, tx); tx.commit(); }
        helper.assertTrue(rover.getCapability(Capabilities.Energy.ENTITY, null) == rover.energyHandler(), "vehicle FE capability missing");
        pad.serverTick(helper.getLevel(), at, pad.getBlockState());
        helper.assertValueEqual(rover.charge(), 1000, "parked rover did not charge");
        helper.assertValueEqual(pad.getEnergy().getAmountAsInt(), 1500, "pad transfer lost or created energy");
        var handler = net.neoforged.neoforge.transfer.access.ItemAccess.forStack(rover.packed()).getCapability(Capabilities.Energy.ITEM);
        helper.assertTrue(handler != null && handler.getAmountAsInt() == 1000, "packed rover lost rechargeable battery");
        rover.discard(); helper.succeed();
    }
    private static void roverPackAndRestore(GameTestHelper helper) {
        var rover = testRover(helper, 123456); var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        rover.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 17);
        var restored = new com.powerfactor.vehicle.RoverEntity(com.powerfactor.registry.ModEntities.ELECTRIC_ROVER.get(), helper.getLevel());
        restored.restoreFrom(rover);
        helper.assertValueEqual(restored.charge(), 123456, "entity save/load lost synchronized charge");
        helper.assertTrue(restored.health() == 63, "entity save/load lost hull damage");
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(ModItems.WRENCH.get()));
        helper.assertTrue(player.startRiding(rover), "packing fixture failed to mount"); player.setShiftKeyDown(true);
        rover.interact(player, net.minecraft.world.InteractionHand.MAIN_HAND, rover.position());
        helper.assertFalse(rover.isRemoved(), "occupied rover packed away"); player.stopRiding();
        var packed = rover.packed(); rover.interact(player, net.minecraft.world.InteractionHand.MAIN_HAND, rover.position());
        helper.assertTrue(rover.isRemoved(), "wrench did not pack stopped empty rover");
        var unpacked = new com.powerfactor.vehicle.RoverEntity(com.powerfactor.registry.ModEntities.ELECTRIC_ROVER.get(), helper.getLevel()); unpacked.restore(packed);
        helper.assertTrue(unpacked.charge() == 123456 && unpacked.health() == 63, "packing changed battery or hull");
        player.setShiftKeyDown(false); player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(com.powerfactor.registry.ModMaterials.INGOTS.get("steel").get(), 2));
        unpacked.interact(player, net.minecraft.world.InteractionHand.MAIN_HAND, unpacked.position());
        helper.assertTrue(unpacked.health() == 73 && player.getMainHandItem().getCount() == 1, "steel repair payment wrong");
        unpacked.setPos(rover.position()); helper.getLevel().addFreshEntity(unpacked); player.startRiding(unpacked);
        unpacked.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 100);
        helper.assertTrue(unpacked.isRemoved() && !player.isPassenger(), "destroyed rover retained driver");
        var drops = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, unpacked.getBoundingBox().inflate(3));
        helper.assertValueEqual(drops.stream().filter(e -> e.getItem().is(ModItems.RUBBER.get())).mapToInt(e -> e.getItem().getCount()).sum(), 2, "wreck creates extra rubber");
        helper.assertValueEqual(drops.stream().filter(e -> e.getItem().is(com.powerfactor.registry.ModMaterials.INGOTS.get("steel").get())).mapToInt(e -> e.getItem().getCount()).sum(), 4, "wreck steel salvage wrong");
        helper.succeed();
    }

    private static void roverSubmergedStops(GameTestHelper helper) {
        var rover = testRover(helper, 1000); var driver = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL); driver.startRiding(rover);
        for (int x = 0; x < 7; x++) for (int y = 1; y < 4; y++) for (int z = 0; z < 7; z++) helper.setBlock(new BlockPos(x, y, z), Blocks.WATER);
        rover.setDeltaMovement(0, 0, 0.6); rover.input(driver, 1, 0, false, true); rover.tick();
        helper.assertTrue(rover.isInWater(), "wet rover fixture is dry");
        helper.assertTrue(rover.braking() && !rover.motorActive(), "submerged rover ran propulsion");
        helper.assertTrue(rover.speed() < 0.5, "submerged rover lacked drag");
        helper.assertValueEqual(rover.charge(), 1000, "disabled submerged motor consumed FE");
        driver.stopRiding(); rover.discard();
        for (int x = 0; x < 7; x++) for (int y = 1; y < 4; y++) for (int z = 0; z < 7; z++) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        helper.succeed();
    }

    private static void fluidFacesFollowConfiguration(GameTestHelper helper) {
        var machine=placeRecipeMachine(helper,CENTER,com.powerfactor.machine.RecipeMachine.ORE_WASHER);
        var at=helper.absolutePos(CENTER);var water=net.neoforged.neoforge.transfer.fluid.FluidResource.of(net.minecraft.world.level.material.Fluids.WATER);
        var front=helper.getLevel().getCapability(Capabilities.Fluid.BLOCK,at,Direction.NORTH);
        var side=helper.getLevel().getCapability(Capabilities.Fluid.BLOCK,at,Direction.EAST);
        try(var tx=Transaction.openRoot()) {helper.assertValueEqual(front.insert(water,100,tx),0,"closed front admitted fluid");helper.assertValueEqual(side.insert(water,600,tx),600,"open side rejected fluid");tx.commit();}
        machine.cycleSide(com.powerfactor.machine.SideConfig.Face.LEFT,1);
        try(var tx=Transaction.openRoot()) {helper.assertValueEqual(side.insert(water,100,tx),0,"cached closed fluid face accepted input");helper.assertValueEqual(side.extract(water,100,tx),0,"cached closed fluid face allowed extraction");}
        machine.cycleSide(com.powerfactor.machine.SideConfig.Face.LEFT,2);
        try(var tx=Transaction.openRoot()) {helper.assertValueEqual(side.insert(water,100,tx),0,"output face accepted fluid input");helper.assertValueEqual(side.extract(water,500,tx),500,"output face failed to drain");tx.commit();}
        helper.assertValueEqual(machine.getTank().getAmountAsInt(0),100,"side transfer lost or duplicated water");
        machine.cycleSide(com.powerfactor.machine.SideConfig.Face.LEFT,2); // OUTPUT -> NONE
        helper.getLevel().setBlock(at,machine.getBlockState().setValue(MachineBlock.FACING,Direction.WEST),net.minecraft.world.level.block.Block.UPDATE_ALL);
        try(var tx=Transaction.openRoot()) {helper.assertValueEqual(side.insert(water,200,tx),200,"cached fluid handler did not follow rotated faces");}
        helper.assertValueEqual(machine.getTank().getAmountAsInt(0),100,"aborted external transfer changed water");helper.succeed();
    }

    private static net.minecraft.world.item.crafting.CraftingRecipe crafting(GameTestHelper helper, net.minecraft.world.item.crafting.CraftingInput input) {
        var found = helper.getLevel().getServer().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, helper.getLevel());
        helper.assertTrue(found.isPresent(), "no crafting recipe for " + input.items()); return found.orElseThrow().value();
    }
    private static void handToolRecipes(GameTestHelper helper) {
        var hammer = new net.minecraft.world.item.ItemStack(ModItems.FORGE_HAMMER.get()); hammer.setDamageValue(30);
        hammer.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Bench Hammer"));
        var input = net.minecraft.world.item.crafting.CraftingInput.of(3,1, java.util.List.of(new net.minecraft.world.item.ItemStack(Items.COPPER_INGOT), new net.minecraft.world.item.ItemStack(Items.COPPER_INGOT), hammer));
        var recipe = crafting(helper,input); var product = recipe.assemble(input);
        helper.assertTrue(product.is(com.powerfactor.registry.ModMaterials.PLATES.get("copper").get()) && product.getCount()==1,"manual plate yield wrong");
        var remainder=recipe.getRemainingItems(input).get(2);
        helper.assertTrue(remainder.is(ModItems.FORGE_HAMMER.get()) && remainder.getDamageValue()==31 && "Bench Hammer".equals(remainder.getHoverName().getString()),"crafting did not return the worn, named hammer");
        hammer.setDamageValue(127); helper.assertTrue(recipe.getRemainingItems(input).get(2).isEmpty(),"exhausted hammer returned");
        var cutters=new net.minecraft.world.item.ItemStack(ModItems.WIRE_CUTTERS.get());
        input=net.minecraft.world.item.crafting.CraftingInput.of(2,1,java.util.List.of(new net.minecraft.world.item.ItemStack(com.powerfactor.registry.ModMaterials.PLATES.get("copper").get()),cutters));
        product=crafting(helper,input).assemble(input);
        helper.assertTrue(product.is(ModItems.FINE_COPPER_WIRE.get()) && product.getCount()==2,"plate cutting yield wrong");
        helper.assertValueEqual(crafting(helper,input).getRemainingItems(input).get(1).getDamageValue(),1,"cutters did not wear once");
        input=net.minecraft.world.item.crafting.CraftingInput.of(1,1,java.util.List.of(new net.minecraft.world.item.ItemStack(ModItems.FINE_COPPER_WIRE.get())));
        helper.assertTrue(crafting(helper,input).assemble(input).is(ModItems.COPPER_WIRE.get()),"drawer wire cannot make a cable");
        helper.succeed();
    }
    private static void syntheticRubber(GameTestHelper helper) {
        for(var coal:java.util.List.of(Items.COAL,Items.CHARCOAL)){
            var input=net.minecraft.world.item.crafting.CraftingInput.of(3,1,java.util.List.of(new net.minecraft.world.item.ItemStack(Items.SLIME_BALL),new net.minecraft.world.item.ItemStack(Items.CLAY_BALL),new net.minecraft.world.item.ItemStack(coal)));
            var result=crafting(helper,input).assemble(input); helper.assertTrue(result.is(ModItems.RUBBER.get()) && result.getCount()==2,"synthetic rubber recipe failed for "+coal);
        } helper.succeed();
    }
    private static void graphiteBootstrap(GameTestHelper helper) {
        var machine=placeRecipeMachine(helper,CENTER,com.powerfactor.machine.RecipeMachine.ALLOY_FURNACE);
        insert(helper,CENTER,ModItems.COAL_DUST.get(),4);
        helper.runAfterDelay(340,()->{
            helper.assertTrue(machine.slot(2).is(ModItems.GRAPHITE.get()),"alloy furnace did not make starting graphite");
            var plate=com.powerfactor.registry.ModMaterials.PLATES.get("iron").get();
            var input=net.minecraft.world.item.crafting.CraftingInput.of(3,3,java.util.List.of(
                new net.minecraft.world.item.ItemStack(ModItems.GRAPHITE_ELECTRODE.get()),new net.minecraft.world.item.ItemStack(Items.GLASS),new net.minecraft.world.item.ItemStack(ModItems.GRAPHITE_ELECTRODE.get()),
                new net.minecraft.world.item.ItemStack(plate),new net.minecraft.world.item.ItemStack(ModBlocks.RECIPE_MACHINES.get(com.powerfactor.machine.RecipeMachine.ALLOY_FURNACE).get()),new net.minecraft.world.item.ItemStack(plate),
                new net.minecraft.world.item.ItemStack(plate),new net.minecraft.world.item.ItemStack(ModItems.CONTROL_CIRCUIT.get()),new net.minecraft.world.item.ItemStack(plate)));
            helper.assertTrue(crafting(helper,input).assemble(input).is(ModBlocks.RECIPE_MACHINES.get(com.powerfactor.machine.RecipeMachine.ARC_FURNACE).get().asItem()),"arc furnace still requires its own steel or silicon");helper.succeed();
        });
    }
    private static void windBaseEnergy(GameTestHelper helper) {
        var base=new BlockPos(3,1,3);
        for(int y=6;y>=1;y--) helper.setBlock(new BlockPos(3,y,3),ModBlocks.WIND_TURBINE_TOWER.get().defaultBlockState());
        var head=new BlockPos(3,7,3);helper.setBlock(head,ModBlocks.WIND_TURBINE.get().defaultBlockState());
        var wind=helper.getBlockEntity(head,com.powerfactor.generator.WindTurbineBlockEntity.class);
        ((net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler)wind.getEnergy()).set(250);
        var handler=helper.getLevel().getCapability(Capabilities.Energy.BLOCK,helper.absolutePos(base),Direction.EAST);
        helper.assertTrue(handler!=null && handler.getAmountAsInt()==250,"foreign cable cannot see wind energy at the foot");
        try(var tx=Transaction.openRoot()){helper.assertValueEqual(handler.extract(50,tx),50,"wind foot rejects extraction");}
        helper.assertValueEqual(handler.getAmountAsInt(),250,"simulation drained turbine");
        try(var tx=Transaction.openRoot()){helper.assertValueEqual(handler.extract(60,tx),60,"committed extraction failed");tx.commit();}
        helper.assertValueEqual(wind.getEnergy().getAmountAsInt(),190,"foot extraction did not debit nacelle");
        var foreign = new net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler(1000,1000,0);
        try (var tx = Transaction.openRoot()) { helper.assertValueEqual(net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil.move(handler,foreign,40,tx),40,"standard energy transfer failed"); }
        helper.assertTrue(foreign.getAmountAsInt()==0 && wind.getEnergy().getAmountAsInt()==190,"aborted cross-mod transfer moved FE");
        try (var tx = Transaction.openRoot()) { net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil.move(handler,foreign,40,tx);tx.commit(); }
        helper.assertTrue(foreign.getAmountAsInt()==40 && wind.getEnergy().getAmountAsInt()==150,"cross-mod FE transfer lost energy");
        var wire=base.east();helper.setBlock(wire,ModBlocks.COPPER_WIRE.get().withConnections(helper.getLevel(),helper.absolutePos(wire),ModBlocks.COPPER_WIRE.get().defaultBlockState()));
        helper.assertTrue(helper.getBlockState(wire).getValue(com.powerfactor.grid.ConnectorBlock.CONNECTIONS.get(Direction.WEST)),"wire has no arm at wind foot");
        helper.assertTrue(helper.getBlockState(base).getValue(com.powerfactor.generator.WindTowerBlock.CONNECTED),"connected footing did not change appearance");
        helper.setBlock(new BlockPos(3,4,3),Blocks.AIR);
        try(var tx=Transaction.openRoot()){helper.assertValueEqual(handler.extract(50,tx),0,"cached cable extracted through a broken tower");tx.commit();}
        helper.assertValueEqual(wind.getEnergy().getAmountAsInt(),150,"broken tower lost stored energy");helper.succeed();
    }
    private static void generatorSharesEnergy(GameTestHelper helper) {
        helper.setBlock(CENTER,ModBlocks.SOLAR_PANEL.get().defaultBlockState());
        var solar=helper.getBlockEntity(CENTER,com.powerfactor.generator.SolarPanelBlockEntity.class);
        helper.setBlock(CENTER.north(),ModBlocks.CHARGING_PAD.get().defaultBlockState());helper.setBlock(CENTER.east(),ModBlocks.CHARGING_PAD.get().defaultBlockState());
        var a=helper.getBlockEntity(CENTER.north(),com.powerfactor.device.ChargingPadBlockEntity.class).getEnergy();
        var b=helper.getBlockEntity(CENTER.east(),com.powerfactor.device.ChargingPadBlockEntity.class).getEnergy();
        ((net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler)solar.getEnergy()).set(100);
        solar.serverTick(helper.getLevel(),helper.absolutePos(CENTER),solar.getBlockState());
        helper.assertTrue(a.getAmountAsInt()>0 && Math.abs(a.getAmountAsInt()-b.getAmountAsInt())<=1,"generator starved one direction");
        helper.assertValueEqual(a.getAmountAsInt()+b.getAmountAsInt()+solar.getEnergy().getAmountAsInt(),100+solar.scadaReading().fe(),"generator created or lost FE");
        for (var block : java.util.List.of(ModBlocks.COAL_TURBINE.get(), ModBlocks.DIESEL_GENERATOR.get())) {
            ((net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler)a).set(0); ((net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler)b).set(0);
            helper.setBlock(CENTER,block.defaultBlockState());
            var generator=(com.powerfactor.machine.MachineBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(CENTER));
            EnergyHandler energy=generator instanceof com.powerfactor.machine.CoalTurbineBlockEntity coal ? coal.getEnergy() : ((com.powerfactor.generator.DieselGeneratorBlockEntity)generator).getEnergy();
            ((net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler)energy).set(100);generator.serverTick(helper.getLevel(),helper.absolutePos(CENTER),generator.getBlockState());
            helper.assertTrue(a.getAmountAsInt()==50 && b.getAmountAsInt()==50 && energy.getAmountAsInt()==0,"generator did not share stored FE across front and side: "+block);
        }
        var source=new net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler(1000,0,0);source.set(100);
        var x=new net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler(1000,1000,0);var y=new net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler(1000,1000,0);
        com.powerfactor.machine.EnergyOut.distribute(source,java.util.List.of(x,x,y),0);
        helper.assertTrue(x.getAmountAsInt()==50 && y.getAmountAsInt()==50 && source.getAmountAsInt()==0,"duplicated external capability got extra share");helper.succeed();
    }
    private static void redstoneDirectSelection(GameTestHelper helper) {
        var player=helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        for(var block:java.util.List.of(ModBlocks.CRUSHER.get(),ModBlocks.COAL_TURBINE.get(),ModBlocks.DIESEL_GENERATOR.get(),ModBlocks.STEAM_BOILER.get())){
            helper.setBlock(CENTER,block.defaultBlockState());var machine=(com.powerfactor.machine.MachineBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(CENTER));
            var menu=machine.createMenu(17,player.getInventory(),player);
            for(var mode:com.powerfactor.machine.MachineBlockEntity.RedstoneMode.values()){
                helper.assertTrue(menu.clickMenuButton(player,com.powerfactor.machine.ProcessingMenu.BUTTON_REDSTONE_SET+mode.ordinal()),"redstone selector rejected "+block);
                helper.assertTrue(machine.redstoneMode()==mode,"redstone icon selected wrong mode");
            }
            helper.assertFalse(menu.clickMenuButton(player,33),"invalid redstone mode accepted");
        }helper.succeed();
    }
    private static void wireCuttersPermissions(GameTestHelper helper) {
        var player=helper.makeMockServerPlayerInLevel();player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
        var at=helper.absolutePos(CENTER);player.setPos(at.getX()+0.5,at.getY(),at.getZ()+0.5);
        helper.setBlock(CENTER,ModBlocks.COPPER_WIRE.get().defaultBlockState());var cutters=new net.minecraft.world.item.ItemStack(ModItems.WIRE_CUTTERS.get());player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,cutters);
        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(at),Direction.UP,at,false);
        var context=new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
        cutters.getItem().onItemUseFirst(cutters,context);
        helper.assertTrue(helper.getBlockState(CENTER).is(ModBlocks.COPPER_WIRE.get()) && cutters.getDamageValue()==0,"cutters bypassed adventure permissions");
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);cutters.getItem().onItemUseFirst(cutters,context);
        helper.assertTrue(helper.getBlockState(CENTER).isAir() && cutters.getDamageValue()==1,"cutters did not dismantle cable for one use");helper.succeed();
    }
    private static void wrenchRespectsProtection(GameTestHelper helper) {
        helper.setBlock(CENTER,ModBlocks.CRUSHER.get().defaultBlockState());var player=helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var at=helper.absolutePos(CENTER);var wrench=new net.minecraft.world.item.ItemStack(ModItems.WRENCH.get());player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,wrench);player.setShiftKeyDown(true);
        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(at),Direction.UP,at,false);
        var context=new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
        java.util.function.Consumer<net.neoforged.neoforge.event.level.block.BreakBlockEvent> deny=event->{if(event.getPos().equals(at))event.setCanceled(true);};
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(deny);
        try {wrench.getItem().onItemUseFirst(wrench,context);helper.assertTrue(helper.getBlockState(CENTER).is(ModBlocks.CRUSHER.get()),"wrench bypassed protection event");}
        finally {net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(deny);}
        wrench.getItem().onItemUseFirst(wrench,context);helper.assertTrue(helper.getBlockState(CENTER).isAir(),"unprotected dismantling failed");
        helper.assertTrue(player.getInventory().contains(new net.minecraft.world.item.ItemStack(ModBlocks.CRUSHER.get())),"dismantled machine was not returned");helper.succeed();
    }

    private static void rubberTreeCanopy(GameTestHelper helper) {
        for(int x=0;x<11;x++)for(int z=0;z<11;z++)helper.setBlock(new BlockPos(x,0,z),Blocks.GRASS_BLOCK);
        var feature=helper.getLevel().registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE).getOrThrow(ResourceKey.create(Registries.CONFIGURED_FEATURE,PowerFactor.id("rubber_tree"))).value();
        helper.assertTrue(feature.place(helper.getLevel(),helper.getLevel().getChunkSource().getGenerator(),net.minecraft.util.RandomSource.create(1934),helper.absolutePos(new BlockPos(5,1,5))),"rubber tree failed to grow");
        int leaves=0,natural=0,latex=0;java.util.Set<Integer> xs=new java.util.HashSet<>();
        for(var p:BlockPos.betweenClosed(0,1,0,10,15,10)){var state=helper.getBlockState(p);
            if(state.is(ModBlocks.RUBBER_LEAVES.get())){leaves++;xs.add(p.getX());}
            if(state.is(ModBlocks.RUBBER_LOG.get()) && state.getValue(com.powerfactor.world.RubberLogBlock.NATURAL)){natural++;if(state.getValue(com.powerfactor.world.RubberLogBlock.LATEX))latex++;}
        }
        helper.assertTrue(leaves>40 && xs.size()>=5 && natural>=6 && latex>0,"rubber canopy or renewable latex missing");helper.succeed();
    }

    private static void mixedCopperCables(GameTestHelper helper) {
        var copper = new BlockPos(1,1,1); var insulated = copper.east(); var target = insulated.east();
        placeTurbine(helper, copper.west(), Direction.NORTH);
        placeWire(helper, copper);
        var cable = ModBlocks.INSULATED_COPPER_CABLE.get();
        helper.setBlock(insulated, cable.withConnections(helper.getLevel(), helper.absolutePos(insulated), cable.defaultBlockState()));
        var crusher = placeCrusher(helper, target);
        var manager = com.powerfactor.grid.GridManager.get(helper.getLevel());
        var grid = manager.gridAt(helper.absolutePos(insulated));
        helper.assertTrue(grid != null && grid == manager.gridAt(helper.absolutePos(copper)), "same-voltage wires built separate grids");
        helper.assertValueEqual(grid.ratingCe(), com.powerfactor.grid.CableTier.COPPER.ratingCe, "mixed run exceeded weakest wire rating");
        helper.assertTrue(helper.getBlockState(copper).getValue(com.powerfactor.grid.ConnectorBlock.CONNECTIONS.get(Direction.EAST))
                && helper.getBlockState(insulated).getValue(com.powerfactor.grid.ConnectorBlock.CONNECTIONS.get(Direction.WEST)), "mixed cable seam has no physical arms");
        insert(helper, copper.west(), Items.COAL, 4); insert(helper, target, Items.RAW_IRON, 4);
        helper.runAfterDelay(RUN_TICKS + 80, () -> {
            helper.assertTrue(crusher.slot(1).is(ModItems.IRON_DUST.get()), "mixed cable run did not power processing");
            helper.succeed();
        });
    }

    private static void wireDeviceAttachment(GameTestHelper helper) {
        placeWire(helper, CENTER);
        var level = helper.getLevel(); var at = helper.absolutePos(CENTER);
        var manager = com.powerfactor.grid.GridManager.get(level);
        var old = manager.gridAt(at); var cached = old.handler();
        helper.setBlock(CENTER.east(), ModBlocks.CHARGING_PAD.get().defaultBlockState());
        var pad = helper.getBlockEntity(CENTER.east(), com.powerfactor.device.ChargingPadBlockEntity.class);
        var updated = manager.gridAt(at);
        helper.assertTrue(!old.isValid() && updated != old, "new device did not invalidate the old endpoint list");
        try (var tx = Transaction.openRoot()) {helper.assertValueEqual(cached.insert(100,tx),0,"old cached grid still accepted energy");}
        try (var tx = Transaction.openRoot()) {helper.assertValueEqual(updated.handler().insert(100,tx),100,"rebuilt grid rejected power");tx.commit();}
        helper.runAfterDelay(2, () -> {
            helper.assertValueEqual(pad.getEnergy().getAmountAsInt(),100,"newly attached device did not receive power");
            helper.succeed();
        });
    }

    private static void freshWirePower(GameTestHelper helper) {
        placeWire(helper, CENTER);
        var level = helper.getLevel(); var at = helper.absolutePos(CENTER);
        var grid = com.powerfactor.grid.GridManager.get(level).gridAt(at);
        var husk = helper.spawn(net.minecraft.world.entity.EntityTypes.HUSK, CENTER); husk.setNoAi(true);
        float health = husk.getHealth(); var wire = ModBlocks.COPPER_WIRE.get();
        helper.assertTrue(!grid.energized(), "fresh unpowered wire reports a live grid");
        wire.stepOn(level, at, helper.getBlockState(CENTER), husk);
        helper.assertValueEqual(husk.getHealth(),health,"fresh unpowered wire caused a shock");
        try (var tx = Transaction.openRoot()) {helper.assertValueEqual(grid.handler().insert(100,tx),100,"simulation refused FE");}
        helper.assertTrue(!grid.energized() && grid.handler().getAmountAsInt() == 0,"aborted transfer energized a wire");
        try (var tx = Transaction.openRoot()) {grid.handler().insert(100,tx);tx.commit();}
        helper.assertTrue(grid.energized(), "committed power did not energize the wire immediately");
        wire.stepOn(level, at, helper.getBlockState(CENTER), husk);
        helper.assertTrue(husk.getHealth() < health,"powered bare wire failed to shock");
        helper.succeed();
    }

    private static void breakerVoltageConflict(GameTestHelper helper) {
        var copper = new BlockPos(1,1,1); var aluminum = new BlockPos(5,1,1);
        placeWire(helper,copper);
        for (int x = 2; x <= 4; x++) helper.setBlock(new BlockPos(x,1,1),ModBlocks.CIRCUIT_BREAKER.get().defaultBlockState());
        var mv = ModBlocks.ALUMINUM_CABLE.get();
        helper.setBlock(aluminum,mv.withConnections(helper.getLevel(),helper.absolutePos(aluminum),mv.defaultBlockState()));
        var level=helper.getLevel(); var manager=com.powerfactor.grid.GridManager.get(level);
        var low=manager.gridAt(helper.absolutePos(copper));var high=manager.gridAt(helper.absolutePos(aluminum));
        helper.assertTrue(low!=high && low.isValid() && high.isValid(),"breaker chain overlapped incompatible grids");
        helper.assertTrue(manager.gridAt(helper.absolutePos(new BlockPos(3,1,1)))==null,"breaker chain bridged voltages");
        for (int i=0;i<3;i++) helper.assertTrue(manager.gridAt(helper.absolutePos(copper))==low && manager.gridAt(helper.absolutePos(aluminum))==high,"query order rebuilt incompatible grids");
        var lv=ModBlocks.INSULATED_COPPER_CABLE.get();
        helper.setBlock(aluminum,lv.withConnections(level,helper.absolutePos(aluminum),lv.defaultBlockState()));
        helper.assertTrue(!low.isValid() && !high.isValid(),"remote terminal change failed to invalidate breaker-chain grids");
        var joined=manager.gridAt(helper.absolutePos(copper));
        helper.assertTrue(joined!=null && joined==manager.gridAt(helper.absolutePos(aluminum)),"compatible terminal did not reconnect breaker chain");
        helper.assertValueEqual(joined.ratingCe(),com.powerfactor.grid.CableTier.COPPER.ratingCe,"breaker bypassed weakest cable limit");
        helper.succeed();
    }

    private static void wireCommonRubber(GameTestHelper helper) {
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        for (var block : java.util.List.of(ModBlocks.COPPER_WIRE.get(), ModBlocks.ALUMINUM_CABLE.get())) {
            for (var item : Boolean.getBoolean("powerfactor.testFixtures") ? java.util.List.of(ModItems.RUBBER.get(), Items.HONEYCOMB) : java.util.List.of(ModItems.RUBBER.get())) {
                helper.setBlock(CENTER, block.defaultBlockState());
                var rubber = new net.minecraft.world.item.ItemStack(item, 2);
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, rubber);
                helper.useBlock(CENTER, player);
                helper.assertTrue(helper.getBlockState(CENTER).getValue(WireBlock.COATED), "tagged rubber did not coat " + block + ": " + item);
                helper.assertValueEqual(rubber.getCount(), 1, "coating did not consume exactly one rubber");
                helper.useBlock(CENTER, player);
                helper.assertValueEqual(rubber.getCount(), 1, "already-coated cable consumed extra rubber");
            }
        }
        helper.setBlock(CENTER, ModBlocks.COPPER_WIRE.get().defaultBlockState());
        var wrong = new net.minecraft.world.item.ItemStack(Items.SLIME_BALL, 2);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, wrong);
        helper.useBlock(CENTER, player);
        helper.assertTrue(!helper.getBlockState(CENTER).getValue(WireBlock.COATED) && wrong.getCount() == 2, "untagged slime coated a cable");
        helper.succeed();
    }

    private static void outputBatch(GameTestHelper helper, boolean crusher) {
        com.powerfactor.machine.ProcessingBlockEntity machine = crusher ? placeCrusher(helper, CENTER)
                : placeRecipeMachine(helper, CENTER, com.powerfactor.machine.RecipeMachine.CENTRIFUGE);
        helper.onEachTick(() -> fillEnergy(machine));
        machine.setSlot(0, new net.minecraft.world.item.ItemStack(Items.PAPER, 2));
        machine.setSlot(2, new net.minecraft.world.item.ItemStack(Items.FLINT, 30));
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(machine.workState() == com.powerfactor.machine.ProcessingBlockEntity.WorkState.OUTPUT_FULL, "combined bonuses did not block an overflowing batch");
            helper.assertTrue(machine.slot(0).getCount() == 2 && machine.slot(1).isEmpty() && machine.slot(2).getCount() == 30, "blocked batch consumed input or modified output");
            machine.setSlot(2, net.minecraft.world.item.ItemStack.EMPTY);
            helper.runAfterDelay(10, () -> {
                helper.assertTrue(machine.slot(1).is(Items.STONE) && machine.slot(1).getCount() == 1, "batch did not produce exactly one main product");
                helper.assertTrue(machine.slot(2).is(Items.FLINT) && machine.slot(2).getCount() == 40, "bonus batch lost, changed or overfilled items");
                helper.assertValueEqual(machine.slot(0).getCount(), 1, "second overflowing batch consumed input");
                helper.succeed();
            });
        });
    }

    private static void unavailableProduct(GameTestHelper helper, boolean missingTag) {
        var machine = placeRecipeMachine(helper, CENTER, com.powerfactor.machine.RecipeMachine.ROLLING_MILL);
        machine.setSlot(0, new net.minecraft.world.item.ItemStack(missingTag ? Items.PAPER : Items.FEATHER, 2));
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(machine.workState() == (missingTag ? com.powerfactor.machine.ProcessingBlockEntity.WorkState.OUTPUT_UNAVAILABLE
                    : com.powerfactor.machine.ProcessingBlockEntity.WorkState.OUTPUT_FULL), "invalid product did not report its blocker");
            helper.assertTrue(machine.slot(0).getCount() == 2 && machine.slot(1).isEmpty() && machine.workProgress() == 0, "invalid product consumed materials or accumulated work");
            helper.succeed();
        });
    }

    private static void processingAnimationSpeed(GameTestHelper helper) {
        var machine = placeRecipeMachine(helper, CENTER, com.powerfactor.machine.RecipeMachine.ROLLING_MILL);
        insert(helper, CENTER, Items.IRON_INGOT, 16);
        helper.runAfterDelay(10, () -> {
            double normal = machine.getUpdateTag(helper.getLevel().registryAccess()).getDoubleOr("visual_speed", -1);
            helper.assertTrue(normal > 0.9 && normal < 1.1, "normal drive speed missing from animation packet");
            machine.upgrades().setItem(0, new net.minecraft.world.item.ItemStack(ModItems.SPEED_UPGRADE.get(), 2));
            helper.runAfterDelay(10, () -> {
                double boosted = machine.getUpdateTag(helper.getLevel().registryAccess()).getDoubleOr("visual_speed", -1);
                helper.assertTrue(boosted > normal * 1.8 && Math.abs(boosted - machine.drive().speed()) < 0.001, "upgraded processing speed is not sent to the renderer");
                machine.setSlot(1, new net.minecraft.world.item.ItemStack(com.powerfactor.registry.ModMaterials.PLATES.get("iron").get(), 64));
                helper.runAfterDelay(5, () -> {
                    helper.assertTrue(machine.getUpdateTag(helper.getLevel().registryAccess()).getDoubleOr("visual_speed", -1) == 0, "blocked upgraded machine did not stop its animation");
                    helper.succeed();
                });
            });
        });
    }

    private static void processingOutputPause(GameTestHelper helper) {
        var machine = placeRecipeMachine(helper, CENTER, com.powerfactor.machine.RecipeMachine.ROLLING_MILL);
        insert(helper, CENTER, Items.IRON_INGOT, 4);
        helper.runAfterDelay(25, () -> {
            double paid = machine.workProgress();
            helper.assertTrue(paid > 10, "rolling mill never began work");
            machine.setSlot(1, new net.minecraft.world.item.ItemStack(com.powerfactor.registry.ModMaterials.PLATES.get("iron").get(), 64));
            helper.runAfterDelay(20, () -> {
                helper.assertTrue(machine.workProgress() == paid, "blocked output erased or advanced paid progress");
                helper.assertTrue(machine.workState() == com.powerfactor.machine.ProcessingBlockEntity.WorkState.OUTPUT_FULL && !machine.getBlockState().getValue(MachineBlock.RUNNING), "blocked output still runs");
                helper.assertTrue(machine.getUpdateTag(helper.getLevel().registryAccess()).getDoubleOr("visual_speed", -1) == 0, "blocked machine still sends moving animation");
                helper.assertValueEqual(machine.slot(0).getCount(), 4, "blocked machine consumed input");
                var level = helper.getLevel();
                var restored = (com.powerfactor.machine.RecipeMachineBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                        machine.getBlockPos(), machine.getBlockState(), machine.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
                helper.assertTrue(restored != null && restored.workProgress() == paid, "saved blocked progress was lost");
                restored.setLevel(level); level.setBlockEntity(restored);
                helper.onEachTick(() -> fillEnergy(restored));
                restored.setSlot(1, net.minecraft.world.item.ItemStack.EMPTY);
                helper.runAfterDelay(80, () -> {
                    helper.assertValueEqual(restored.slot(1).getCount(), 1, "resumed recipe did not finish once");
                    helper.assertValueEqual(restored.slot(0).getCount(), 3, "resumed recipe lost or duplicated input");
                    helper.succeed();
                });
            });
        });
    }

    private static void processingRecipeSwap(GameTestHelper helper) {
        var machine = placeRecipeMachine(helper, CENTER, com.powerfactor.machine.RecipeMachine.WIRE_DRAWER);
        insert(helper, CENTER, Items.COPPER_INGOT, 2);
        helper.runAfterDelay(70, () -> {
            helper.assertTrue(machine.workProgress() > 40 && machine.slot(1).isEmpty(), "wire drawer did not reach partial work");
            machine.setSlot(0, new net.minecraft.world.item.ItemStack(com.powerfactor.registry.ModMaterials.INGOTS.get("silver").get(), 2));
            helper.runAfterDelay(40, () -> {
                helper.assertTrue(machine.slot(1).isEmpty() && machine.workProgress() < 50, "new recipe inherited copper progress");
                helper.runAfterDelay(70, () -> {
                    helper.assertTrue(machine.slot(1).is(ModItems.FINE_SILVER_WIRE.get()), "replacement recipe did not run");
                    helper.assertValueEqual(machine.slot(1).getCount(), 3, "replacement recipe yield");
                    helper.assertValueEqual(machine.slot(0).getCount(), 1, "replacement recipe input count");
                    helper.succeed();
                });
            });
        });
    }

    private static void washerWaterPause(GameTestHelper helper) {
        var machine = placeRecipeMachine(helper, CENTER, com.powerfactor.machine.RecipeMachine.ORE_WASHER);
        machine.getTank().set(0, net.neoforged.neoforge.transfer.fluid.FluidResource.of(net.minecraft.world.level.material.Fluids.WATER), 1000);
        insert(helper, CENTER, Items.RAW_IRON, 2);
        helper.runAfterDelay(35, () -> {
            double paid = machine.workProgress();
            helper.assertTrue(paid > 10, "washer never began work");
            machine.getTank().set(0, net.neoforged.neoforge.transfer.fluid.FluidResource.EMPTY, 0);
            helper.runAfterDelay(20, () -> {
                helper.assertTrue(machine.workProgress() == paid && !machine.getBlockState().getValue(MachineBlock.RUNNING), "water shortage erased progress or kept the drum running");
                helper.assertTrue(machine.workState() == com.powerfactor.machine.ProcessingBlockEntity.WorkState.NEEDS_FLUID, "washer did not report missing water");
                helper.assertValueEqual(machine.slot(0).getCount(), 2, "dry washer consumed ore");
                machine.getTank().set(0, net.neoforged.neoforge.transfer.fluid.FluidResource.of(net.minecraft.world.level.material.Fluids.WATER), 250);
                helper.runAfterDelay(140, () -> {
                    helper.assertTrue(machine.slot(1).is(ModItems.IRON_DUST.get()) && machine.slot(1).getCount() == 3, "refilled washer did not finish once");
                    helper.assertValueEqual(machine.slot(0).getCount(), 1, "refilled washer input count");
                    helper.assertValueEqual(machine.getTank().getAmountAsInt(0), 0, "washer fluid cost");
                    helper.succeed();
                });
            });
        });
    }

    private static void processingControlPause(GameTestHelper helper) {
        var crusher = placeCrusher(helper, CENTER); fillEnergy(crusher);
        insert(helper, CENTER, Items.RAW_IRON, 1);
        helper.runAfterDelay(20, () -> {
            double paid = crusher.workProgress(); int energy = crusher.getEnergy().getAmountAsInt();
            helper.assertTrue(paid > 5, "crusher never began work");
            crusher.setRedstoneMode(com.powerfactor.machine.MachineBlockEntity.RedstoneMode.HIGH);
            helper.runAfterDelay(15, () -> {
                helper.assertTrue(crusher.workState() == com.powerfactor.machine.ProcessingBlockEntity.WorkState.DISABLED && crusher.workProgress() == paid, "redstone pause lost paid work");
                helper.assertValueEqual(crusher.getEnergy().getAmountAsInt(), energy, "disabled crusher consumed FE");
                ((net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler)crusher.getEnergy()).set(0);
                crusher.setRedstoneMode(com.powerfactor.machine.MachineBlockEntity.RedstoneMode.IGNORE);
                helper.runAfterDelay(10, () -> {
                    helper.assertTrue(crusher.workState() == com.powerfactor.machine.ProcessingBlockEntity.WorkState.NO_ENERGY && crusher.workProgress() == paid, "power pause lost work or status");
                    fillEnergy(crusher);
                    helper.runAfterDelay(110, () -> {
                        helper.assertTrue(crusher.slot(1).is(ModItems.IRON_DUST.get()) && crusher.slot(1).getCount() == 2, "crusher did not resume after controls and power returned");
                        helper.succeed();
                    });
                });
            });
        });
    }

    private PowerFactorGameTests() {}
}
