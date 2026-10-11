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
        for (var fn : FUNCTIONS.getEntries()) {
            event.registerTest(fn.getId(), new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, fn.getId()),
                    new TestData<>(env, PowerFactor.id("empty_3x3x3"), MAX_TICKS, 0, true)));
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

    private PowerFactorGameTests() {}
}
