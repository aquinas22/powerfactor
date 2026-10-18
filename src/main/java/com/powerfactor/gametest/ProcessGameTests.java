package com.powerfactor.gametest;

import com.powerfactor.chem.ChemicalReactorBlockEntity;
import com.powerfactor.chem.OreDissolverBlockEntity;
import com.powerfactor.chem.OreMetal;
import com.powerfactor.registry.ModChem;
import com.powerfactor.registry.ModFluids;
import com.powerfactor.registry.ModWash;
import com.powerfactor.wash.WashPlantBlockEntity;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Ore multiplication: the acid route and the wash plant. */
final class ProcessGameTests {
    static final List<String> NAMES = List.of("chemical_reactor_makes_acid", "ore_dissolver_grows_crystals", "chunk_loader_forces_and_grows",
            "wireless_redstone_carries_signal", "item_magnet_pulls_items",
            "console_speed_upgrades", "time_field_accelerates_and_pays", "tesseract_folds_space", "wireless_chargers_reach_players",
            "dosimeter_counts_carried_dose");
    static final List<String> HUGE = List.of("wash_plant_washes_ore", "farm_gantry_tends_field", "cage_spawns_and_grinder_harvests", "fission_reactor_goes_critical", "high_pressure_turbine_runs", "vein_miner_takes_connected_ore", "black_hole_ignites_and_holds", "drone_hub_surveys_and_mines", "sodium_reactor_loops_through_exchanger",
            "tnt_chips_bedrock_into_a_pocket_void", "elevator_rides_and_chunk_card_holds", "power_net_moves_power_by_priority",
            "red_alloy_runs_far_and_gates_think");

    static void register(DeferredRegister<Consumer<GameTestHelper>> functions) {
        functions.register("chemical_reactor_makes_acid", () -> ProcessGameTests::reactor);
        functions.register("ore_dissolver_grows_crystals", () -> ProcessGameTests::dissolver);
        functions.register("wash_plant_washes_ore", () -> ProcessGameTests::washPlant);
        functions.register("chunk_loader_forces_and_grows", () -> ProcessGameTests::chunkLoader);
        functions.register("wireless_redstone_carries_signal", () -> ProcessGameTests::wireless);
        functions.register("item_magnet_pulls_items", () -> ProcessGameTests::magnet);
        functions.register("console_speed_upgrades", () -> ProcessGameTests::speedCards);
        functions.register("time_field_accelerates_and_pays", () -> ProcessGameTests::timeField);
        functions.register("tesseract_folds_space", () -> ProcessGameTests::tesseract);
        functions.register("wireless_chargers_reach_players", () -> ProcessGameTests::chargers);
        functions.register("farm_gantry_tends_field", () -> ProcessGameTests::farmGantry);
        functions.register("cage_spawns_and_grinder_harvests", () -> ProcessGameTests::mobFarm);
        functions.register("pocket_room_ports_and_grows", () -> ProcessGameTests::pocketRoom);
        functions.register("fission_reactor_goes_critical", () -> ProcessGameTests::fission);
        functions.register("high_pressure_turbine_runs", () -> ProcessGameTests::hpTurbine);
        functions.register("vein_miner_takes_connected_ore", () -> ProcessGameTests::veinMiner);
        functions.register("black_hole_ignites_and_holds", () -> ProcessGameTests::blackHole);
        functions.register("drone_hub_surveys_and_mines", () -> ProcessGameTests::droneHub);
        functions.register("sodium_reactor_loops_through_exchanger", () -> ProcessGameTests::sodiumLoop);
        functions.register("tnt_chips_bedrock_into_a_pocket_void", () -> ProcessGameTests::bedrockAndVoid);
        functions.register("elevator_rides_and_chunk_card_holds", () -> ProcessGameTests::elevatorAndChunkCard);
        functions.register("power_net_moves_power_by_priority", () -> ProcessGameTests::powerNet);
        functions.register("red_alloy_runs_far_and_gates_think", () -> ProcessGameTests::redstone);
        functions.register("dosimeter_counts_carried_dose", () -> ProcessGameTests::dosimeter);
    }

    private static int fill(net.neoforged.neoforge.transfer.ResourceHandler<FluidResource> h, net.minecraft.world.level.material.Fluid fluid, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int n = h.insert(FluidResource.of(fluid), amount, tx);
            tx.commit();
            return n;
        }
    }

    /** One sulfur, 500 water and 250 hydrogen become 500 mB acid; pipes can drain the acid but not fill it. */
    private static void reactor(GameTestHelper h) {
        BlockPos p = new BlockPos(4, 1, 1);
        h.setBlock(p, ModChem.CHEMICAL_REACTOR.get().defaultBlockState());
        var be = h.getBlockEntity(p, ChemicalReactorBlockEntity.class);
        be.getEnergy().set(ChemicalReactorBlockEntity.CAPACITY);
        h.assertValueEqual(fill(be.getTanks(), Fluids.WATER, 1000), 1000, "water accepted");
        h.assertValueEqual(fill(be.getTanks(), ModFluids.HYDROGEN.get(), 500), 500, "hydrogen accepted");
        h.assertValueEqual(fill(be.getTanks(), ModFluids.SULFURIC_ACID.source().get(), 100), 0, "acid can't be piped in");
        be.slots().setItem(0, new ItemStack(ModChem.SULFUR_DUST.get(), 3));
        h.runAfterDelay(ChemicalReactorBlockEntity.TIME * 2 + 10, () -> {
            h.assertValueEqual(be.getTanks().amount(ChemicalReactorBlockEntity.ACID), 1000, "two batches of acid");
            h.assertValueEqual(be.slots().getItem(0).getCount(), 1, "two sulfur used");
            h.assertValueEqual(be.getTanks().amount(ChemicalReactorBlockEntity.WATER), 0, "water used");
            h.runAfterDelay(ChemicalReactorBlockEntity.TIME + 10, () -> {
                h.assertValueEqual(be.getTanks().amount(ChemicalReactorBlockEntity.ACID), 1000, "no third batch without water");
                h.succeed();
            });
        });
    }

    /** Raw iron dissolves into four crystals; with a catalyst in, five, and the catalyst wears. */
    private static void dissolver(GameTestHelper h) {
        BlockPos p = new BlockPos(4, 1, 1);
        h.setBlock(p, ModChem.ORE_DISSOLVER.get().defaultBlockState());
        var be = h.getBlockEntity(p, OreDissolverBlockEntity.class);
        be.getEnergy().set(OreDissolverBlockEntity.CAPACITY);
        fill(be.getTank(), ModFluids.SULFURIC_ACID.source().get(), 1000);
        // Automation: ore goes in through the item handler; dirt is refused.
        var items = h.getLevel().getCapability(Capabilities.Item.BLOCK, h.absolutePos(p), Direction.UP);
        try (Transaction tx = Transaction.openRoot()) {
            h.assertValueEqual(items.insert(ItemResource.of(Items.RAW_IRON), 2, tx), 2, "raw iron accepted");
            h.assertValueEqual(items.insert(ItemResource.of(Items.DIRT), 1, tx), 0, "dirt refused");
            tx.commit();
        }
        h.runAfterDelay(OreDissolverBlockEntity.TIME + 10, () -> {
            ItemStack out = be.slots().getItem(OreDissolverBlockEntity.SLOT_OUT);
            h.assertTrue(out.is(ModChem.CRYSTALS.get(OreMetal.IRON).get()), "iron crystals: " + out);
            h.assertValueEqual(out.getCount(), 4, "four crystals");
            h.assertValueEqual(be.getTank().amount(0), 750, "acid used");
            be.slots().setItem(OreDissolverBlockEntity.SLOT_CATALYST, new ItemStack(ModChem.QUANTUM_CATALYST.get()));
            h.runAfterDelay(OreDissolverBlockEntity.TIME + 10, () -> {
                h.assertValueEqual(be.slots().getItem(OreDissolverBlockEntity.SLOT_OUT).getCount(), 9, "five more with the catalyst");
                h.assertValueEqual(be.slots().getItem(OreDissolverBlockEntity.SLOT_CATALYST).getDamageValue(), 1, "catalyst wore");
                h.succeed();
            });
        });
    }

    /** Built part by part, it forms; ore fed through the feed hopper casing washes to 4 concentrate per ore. */
    private static void washPlant(GameTestHelper h) {
        BlockPos c = new BlockPos(5, 1, 1);
        h.setBlock(c, ModWash.WASH_PLANT.get().defaultBlockState().setValue(com.powerfactor.machine.MachineBlock.FACING, Direction.NORTH));
        BlockPos abs = h.absolutePos(c);
        BlockPos hopper = null;
        for (var part : WashPlantBlockEntity.STRUCTURE.parts()) {
            BlockPos at = WashPlantBlockEntity.STRUCTURE.worldPos(abs, Direction.NORTH, part);
            h.getLevel().setBlockAndUpdate(at, part.block().get().defaultBlockState());
            if (part.block().get() == ModWash.FEED_HOPPER.get()) hopper = at;
        }
        var be = h.getBlockEntity(c, WashPlantBlockEntity.class);
        be.getEnergy().set(WashPlantBlockEntity.CAPACITY);
        fill(be.getWater(), Fluids.WATER, 3000);
        BlockPos feed = hopper;
        h.runAfterDelay(25, () -> {
            h.assertTrue(be.getBlockState().getValue(com.powerfactor.multiblock.CasingBlock.FORMED), "plant formed");
            var items = h.getLevel().getCapability(Capabilities.Item.BLOCK, feed, Direction.UP);
            h.assertTrue(items != null, "feed hopper takes items");
            try (Transaction tx = Transaction.openRoot()) {
                h.assertValueEqual(items.insert(ItemResource.of(Items.RAW_IRON), 2, tx), 2, "raw iron into the hopper");
                tx.commit();
            }
            h.runAfterDelay(WashPlantBlockEntity.TIME * 2 + 10, () -> {
                int conc = 0;
                for (int i = WashPlantBlockEntity.INPUTS; i < WashPlantBlockEntity.INPUTS + WashPlantBlockEntity.OUTPUTS; i++) {
                    ItemStack s = be.slots().getItem(i);
                    if (s.is(ModWash.CONCENTRATES.get(OreMetal.IRON).get())) conc += s.getCount();
                }
                h.assertValueEqual(conc, 8, "four concentrate per ore");
                h.assertValueEqual(be.getWater().amount(0), 1000, "1,000 mB per load");
                h.succeed();
            });
        });
    }

    /** Placed, it holds chunk tickets; a range card grows it to 5x5; broken, it lets go and drops the card. */
    private static void chunkLoader(GameTestHelper h) {
        BlockPos p = new BlockPos(4, 1, 1);
        h.setBlock(p, com.powerfactor.registry.ModUtility.CHUNK_LOADER.get().defaultBlockState());
        var be = h.getBlockEntity(p, com.powerfactor.util.ChunkLoaderBlockEntity.class);
        h.runAfterDelay(5, () -> {
            h.assertTrue(net.neoforged.neoforge.common.world.chunk.ForcedChunkManager.hasForcedChunks(h.getLevel()), "chunks forced");
            h.assertValueEqual(be.radius(), 1, "3x3 to start");
            h.assertTrue(be.changeCards(false), "card fitted");
            h.runAfterDelay(5, () -> {
                h.assertValueEqual(be.radius(), 2, "5x5 with a card");
                h.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                h.runAfterDelay(5, () -> {
                    // Removing our own ticket again changes nothing if the loader already gave it back (other tests may hold theirs).
                    BlockPos at = h.absolutePos(p);
                    h.assertFalse(com.powerfactor.util.ChunkLoaderBlockEntity.TICKETS.forceChunk(h.getLevel(), at, at.getX() >> 4, at.getZ() >> 4, false, true),
                            "tickets released");
                    h.assertItemEntityPresent(com.powerfactor.registry.ModUtility.RANGE_UPGRADE.get(), p, 2);
                    h.succeed();
                });
            });
        });
    }

    /** A powered transmitter lights every receiver on its channel and none on another. */
    private static void wireless(GameTestHelper h) {
        var tx = com.powerfactor.registry.ModUtility.WIRELESS_TRANSMITTER.get().defaultBlockState().setValue(com.powerfactor.util.WirelessBlock.CHANNEL, 5);
        var rx = com.powerfactor.registry.ModUtility.WIRELESS_RECEIVER.get().defaultBlockState();
        h.setBlock(new BlockPos(1, 1, 1), tx);
        h.setBlock(new BlockPos(7, 1, 1), rx.setValue(com.powerfactor.util.WirelessBlock.CHANNEL, 5));
        h.setBlock(new BlockPos(7, 1, 2), rx.setValue(com.powerfactor.util.WirelessBlock.CHANNEL, 6));
        h.setBlock(new BlockPos(0, 1, 1), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState());
        h.runAfterDelay(10, () -> {
            h.assertTrue(h.getBlockState(new BlockPos(7, 1, 1)).getValue(com.powerfactor.util.WirelessBlock.LIT), "same channel on");
            h.assertFalse(h.getBlockState(new BlockPos(7, 1, 2)).getValue(com.powerfactor.util.WirelessBlock.LIT), "other channel off");
            h.assertValueEqual(h.getLevel().getSignal(h.absolutePos(new BlockPos(7, 1, 1)), net.minecraft.core.Direction.UP), 15, "full signal");
            h.setBlock(new BlockPos(0, 1, 1), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            h.runAfterDelay(15, () -> {
                h.assertFalse(h.getBlockState(new BlockPos(7, 1, 1)).getValue(com.powerfactor.util.WirelessBlock.LIT), "off when unpowered");
                h.succeed();
            });
        });
    }

    /** A switched-on magnet in the inventory draws a dropped item in; a freshly thrown one (pickup delay) stays put. */
    private static void magnet(GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var start = h.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 1, 1.5));
        player.teleportTo(start.x, start.y, start.z);
        ItemStack magnet = new ItemStack(com.powerfactor.registry.ModUtility.MAGNET.get());
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, magnet, t -> t.putBoolean("magnet", true));
        player.getInventory().add(magnet);
        var far = h.absoluteVec(new net.minecraft.world.phys.Vec3(7.5, 1.2, 1.5));
        var loose = new net.minecraft.world.entity.item.ItemEntity(h.getLevel(), far.x, far.y, far.z, new ItemStack(Items.IRON_INGOT));
        loose.setNoPickUpDelay();
        var thrown = new net.minecraft.world.entity.item.ItemEntity(h.getLevel(), far.x, far.y, far.z - 1, new ItemStack(Items.GOLD_INGOT));
        thrown.setPickUpDelay(400);
        h.getLevel().addFreshEntity(loose);
        h.getLevel().addFreshEntity(thrown);
        h.onEachTick(() -> player.getInventory().tick());   // mock players don't tick their own inventory
        h.runAfterDelay(40, () -> {
            h.assertTrue(!loose.isAlive() || loose.position().distanceTo(player.position()) < 2.5, "loose item drawn in");
            h.assertTrue(thrown.isAlive() && thrown.position().distanceTo(player.position()) > 4, "thrown item left alone: " + thrown.position());
            h.succeed();
        });
    }

    /** Two Speed cards in the reactor's bay finish a batch in half the time; a non-console card is refused. */
    private static void speedCards(GameTestHelper h) {
        BlockPos p = new BlockPos(4, 1, 1);
        h.setBlock(p, ModChem.CHEMICAL_REACTOR.get().defaultBlockState());
        var be = h.getBlockEntity(p, ChemicalReactorBlockEntity.class);
        be.getEnergy().set(ChemicalReactorBlockEntity.CAPACITY);
        fill(be.getTanks(), Fluids.WATER, 2000);
        fill(be.getTanks(), ModFluids.HYDROGEN.get(), 1000);
        be.slots().setItem(0, new ItemStack(ModChem.SULFUR_DUST.get(), 4));
        var speed = com.powerfactor.registry.ModItems.SPEED_UPGRADE.get();
        h.assertTrue(be.upgrades().accepts(0, new ItemStack(speed)), "speed card fits");
        h.assertFalse(be.upgrades().accepts(1, new ItemStack(com.powerfactor.registry.ModItems.VFD_UPGRADE.get())), "VFD card refused");
        be.upgrades().setItem(0, new ItemStack(speed, 2));
        h.runAfterDelay(ChemicalReactorBlockEntity.TIME / 2 + 3, () -> {
            h.assertValueEqual(be.getTanks().amount(ChemicalReactorBlockEntity.ACID), 500, "one batch in half the time");
            h.succeed();
        });
    }

    /** A x4 field beside a reactor makes a batch in a quarter of the time and charges (16 - 1) x 40 + 9 x 8 FE/t for it. */
    private static void timeField(GameTestHelper h) {
        BlockPos r = new BlockPos(4, 1, 1), f = new BlockPos(6, 1, 1);
        h.setBlock(r, ModChem.CHEMICAL_REACTOR.get().defaultBlockState());
        h.setBlock(f, com.powerfactor.registry.ModTime.TIME_FIELD.get().defaultBlockState());
        var be = h.getBlockEntity(r, ChemicalReactorBlockEntity.class);
        var field = h.getBlockEntity(f, com.powerfactor.dilation.TimeFieldBlockEntity.class);
        be.getEnergy().set(ChemicalReactorBlockEntity.CAPACITY);
        field.getEnergy().set(com.powerfactor.dilation.TimeFieldBlockEntity.CAPACITY);
        fill(be.getTanks(), Fluids.WATER, 2000);
        fill(be.getTanks(), ModFluids.HYDROGEN.get(), 1000);
        be.slots().setItem(0, new ItemStack(ModChem.SULFUR_DUST.get(), 4));
        field.setSpeed(2);   // x4
        int before = field.getEnergy().getAmountAsInt();
        h.runAfterDelay(ChemicalReactorBlockEntity.TIME / 4 + 4, () -> {
            h.assertTrue(be.getTanks().amount(ChemicalReactorBlockEntity.ACID) >= 500, "a batch in a quarter of the time");
            int spent = before - field.getEnergy().getAmountAsInt();
            int perTick = com.powerfactor.dilation.TimeFieldBlockEntity.fieldCost(4) + com.powerfactor.dilation.TimeFieldBlockEntity.targetCost(4);
            h.assertTrue(spent >= perTick * (ChemicalReactorBlockEntity.TIME / 4), "paid for dilation: " + spent);
            field.setSpeed(0);
            h.succeed();
        });
    }

    private static com.powerfactor.tesseract.TesseractBlockEntity tess(GameTestHelper h, BlockPos p, int freq, int mode) {
        h.setBlock(p, com.powerfactor.registry.ModTesseract.TESSERACT.get().defaultBlockState());
        var t = h.getBlockEntity(p, com.powerfactor.tesseract.TesseractBlockEntity.class);
        t.setFrequency(freq);
        for (var c : com.powerfactor.tesseract.TesseractBlockEntity.Channel.values()) t.setMode(c, mode);
        return t;
    }

    /** What goes into a sender lands beside the receiver on its frequency, and nowhere else; full destinations refuse. */
    private static void tesseract(GameTestHelper h) {
        var send = tess(h, new BlockPos(1, 1, 1), 42, com.powerfactor.tesseract.TesseractBlockEntity.SEND);
        tess(h, new BlockPos(7, 1, 1), 42, com.powerfactor.tesseract.TesseractBlockEntity.RECEIVE);
        tess(h, new BlockPos(4, 1, 1), 43, com.powerfactor.tesseract.TesseractBlockEntity.RECEIVE);
        h.setBlock(new BlockPos(7, 2, 1), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        h.setBlock(new BlockPos(4, 2, 1), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        h.setBlock(new BlockPos(8, 1, 1), com.powerfactor.registry.ModBlocks.FLUID_TANK.get().defaultBlockState());
        h.setBlock(new BlockPos(7, 1, 2), com.powerfactor.registry.ModBlocks.BATTERY_BANK.get().defaultBlockState());
        h.runAfterDelay(3, () -> {
            try (Transaction tx = Transaction.openRoot()) {
                h.assertValueEqual(send.items().insert(ItemResource.of(Items.DIAMOND), 10, tx), 10, "items accepted");
                h.assertValueEqual(send.fluids().insert(FluidResource.of(Fluids.WATER), 4000, tx), 4000, "fluid accepted");
                h.assertTrue(send.energy().insert(800, tx) > 0, "energy accepted");
                tx.commit();
            }
            var chest = h.getBlockEntity(new BlockPos(7, 2, 1), net.minecraft.world.level.block.entity.ChestBlockEntity.class);
            h.assertValueEqual(chest.getItem(0).getCount(), 10, "diamonds arrived");
            h.assertTrue(h.getBlockEntity(new BlockPos(4, 2, 1), net.minecraft.world.level.block.entity.ChestBlockEntity.class).isEmpty(), "other frequency untouched");
            var tank = h.getLevel().getCapability(Capabilities.Fluid.BLOCK, h.absolutePos(new BlockPos(8, 1, 1)), null);
            h.assertValueEqual((int) tank.getAmountAsLong(0), 4000, "water arrived");
            // Fill the chest: the excess is refused, not lost.
            try (Transaction tx = Transaction.openRoot()) {
                int took = send.items().insert(ItemResource.of(Items.DIRT), 64 * 30, tx);
                h.assertValueEqual(took, 64 * 27 - 64, "only what fits");
                tx.commit();
            }
            h.succeed();
        });
    }

    private static ItemStack emptyDrill() {
        ItemStack drill = new ItemStack(com.powerfactor.registry.ModItems.ELECTRIC_DRILL.get());
        drill.set(com.powerfactor.registry.ModComponents.ENERGY.get(), 0);
        return drill;
    }

    /** The wireless charger fills a nearby player's drill and ignores one out of range; the dimensional one reaches a bound player anywhere. */
    private static void chargers(GameTestHelper h) {
        h.setBlock(new BlockPos(1, 1, 1), com.powerfactor.registry.ModCharging.WIRELESS_CHARGER.get().defaultBlockState());
        h.setBlock(new BlockPos(7, 1, 1), com.powerfactor.registry.ModCharging.DIMENSIONAL_CHARGER.get().defaultBlockState());
        var wireless = h.getBlockEntity(new BlockPos(1, 1, 1), com.powerfactor.charging.WirelessChargerBlockEntity.class);
        var dim = h.getBlockEntity(new BlockPos(7, 1, 1), com.powerfactor.charging.WirelessChargerBlockEntity.class);
        wireless.getEnergy().set(1_000_000);
        dim.getEnergy().set(1_000_000);
        var near = h.makeMockServerPlayerInLevel();
        var far = h.makeMockServerPlayerInLevel();
        near.teleportTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 1, 2.5)).x, h.absolutePos(BlockPos.ZERO).getY() + 1, h.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 1, 2.5)).z);
        far.teleportTo(near.getX() + 300, near.getY() + 40, near.getZ());
        near.getInventory().setItem(0, emptyDrill());
        far.getInventory().setItem(0, emptyDrill());
        dim.toggleBound(far.getUUID());
        h.runAfterDelay(20, () -> {
            int a = near.getInventory().getItem(0).getOrDefault(com.powerfactor.registry.ModComponents.ENERGY.get(), 0);
            int b = far.getInventory().getItem(0).getOrDefault(com.powerfactor.registry.ModComponents.ENERGY.get(), 0);
            h.assertTrue(a > 0, "nearby drill charged: " + a);
            h.assertTrue(b > 0, "bound far drill charged: " + b);
            h.assertTrue(dim.getEnergy().getAmountAsInt() <= 1_000_000 - b * 4 / 3, "dimensional loss paid");
            h.succeed();
        });
    }

    /** The gantry's first three squares: ripe wheat is cut and replanted, dirt is tilled, bare farmland is sown. */
    private static void farmGantry(GameTestHelper h) {
        BlockPos g = new BlockPos(5, 1, 0);
        h.setBlock(g, com.powerfactor.registry.ModFarm.FARM_GANTRY.get().defaultBlockState()
                .setValue(com.powerfactor.machine.MachineBlock.FACING, h.getTestRotation().rotate(Direction.SOUTH)));   // field into the arena
        var be = h.getBlockEntity(g, com.powerfactor.farm.FarmGantryBlockEntity.class);
        be.getEnergy().set(com.powerfactor.farm.FarmGantryBlockEntity.CAPACITY);
        be.slots().setItem(0, new ItemStack(Items.WHEAT_SEEDS, 8));
        // Absolute positions straight from the gantry (GameTestHelper.relativePos mis-inverts unrotated tests).
        var lvl = h.getLevel();
        BlockPos c0 = be.cellPos(-4, 1), c1 = be.cellPos(-3, 1), c2 = be.cellPos(-2, 1);
        lvl.setBlockAndUpdate(c0, net.minecraft.world.level.block.Blocks.FARMLAND.defaultBlockState());
        lvl.setBlockAndUpdate(c0.above(), net.minecraft.world.level.block.Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7));
        lvl.setBlockAndUpdate(c1, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState());
        lvl.setBlockAndUpdate(c2, net.minecraft.world.level.block.Blocks.FARMLAND.defaultBlockState());
        h.runAfterDelay(100, () -> {
            var wheat = lvl.getBlockState(c0.above());
            // Replanted, so no longer ripe; a random tick may already have grown it a stage.
            h.assertTrue(wheat.is(net.minecraft.world.level.block.Blocks.WHEAT) && wheat.getValue(net.minecraft.world.level.block.CropBlock.AGE) < 7,
                    "cut and replanted: " + wheat + " " + be.debugState());
            h.assertTrue(lvl.getBlockState(c1).is(net.minecraft.world.level.block.Blocks.FARMLAND)
                    && lvl.getBlockState(c1.above()).is(net.minecraft.world.level.block.Blocks.WHEAT), "dirt tilled and sown");
            h.assertTrue(lvl.getBlockState(c2.above()).is(net.minecraft.world.level.block.Blocks.WHEAT), "farmland sown");
            boolean gotWheat = false;
            for (int i = com.powerfactor.farm.FarmGantryBlockEntity.SEEDS; i < com.powerfactor.farm.FarmGantryBlockEntity.SEEDS + com.powerfactor.farm.FarmGantryBlockEntity.OUT; i++) {
                gotWheat |= be.slots().getItem(i).is(Items.WHEAT);
            }
            h.assertTrue(gotWheat, "wheat in the harvest slots");
            h.succeed();
        });
    }

    /** A cage with a pig's soul makes pigs two blocks out; a grinder facing back at that spot cuts them down and keeps the pork. */
    private static void mobFarm(GameTestHelper h) {
        Direction south = h.getTestRotation().rotate(Direction.SOUTH);
        BlockPos cage = new BlockPos(5, 1, 2);
        h.setBlock(cage, com.powerfactor.registry.ModMob.SPAWNER_CAGE.get().defaultBlockState().setValue(com.powerfactor.machine.MachineBlock.FACING, south));
        var cageBe = h.getBlockEntity(cage, com.powerfactor.mob.SpawnerCageBlockEntity.class);
        cageBe.getEnergy().set(com.powerfactor.mob.SpawnerCageBlockEntity.CAPACITY);
        ItemStack snare = new ItemStack(com.powerfactor.registry.ModMob.SOUL_SNARE.get());
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, snare, t -> t.putString("soul", "minecraft:pig"));
        cageBe.slots().setItem(0, snare);
        cageBe.upgrades().setItem(0, new ItemStack(com.powerfactor.registry.ModItems.SPEED_UPGRADE.get(), 4));
        // The grinder sits 5 out from the cage, facing back: its zone centre (3 in front) is the cage's spawn point (2 out).
        BlockPos spawn = h.absolutePos(cage).relative(south, 2);
        BlockPos grinderAbs = spawn.relative(south, 3);
        h.getLevel().setBlockAndUpdate(grinderAbs, com.powerfactor.registry.ModMob.MOB_GRINDER.get().defaultBlockState()
                .setValue(com.powerfactor.machine.MachineBlock.FACING, south.getOpposite()));
        var grinder = (com.powerfactor.mob.MobGrinderBlockEntity) h.getLevel().getBlockEntity(grinderAbs);
        grinder.getEnergy().set(com.powerfactor.mob.MobGrinderBlockEntity.CAPACITY);
        h.succeedWhen(() -> {
            boolean pork = false;
            for (int i = 0; i < com.powerfactor.mob.MobGrinderBlockEntity.OUT; i++) pork |= grinder.slots().getItem(i).is(Items.PORKCHOP);
            h.assertTrue(pork, "porkchops in the grinder");
            h.assertTrue(grinder.xp() > 0, "experience stored");
        });
    }

    /** A pocket makes its room, passes items through its port to a chest inside, and grows without losing the chest. */
    private static void pocketRoom(GameTestHelper h) {
        BlockPos door = new BlockPos(2, 1, 2);
        if (h.getLevel().getServer().getLevel(com.powerfactor.pocket.PocketRooms.DIMENSION) == null) com.powerfactor.pocket.PocketRooms.testLevel = h.getLevel();
        h.setBlock(door, com.powerfactor.registry.ModPocket.POCKET.get().defaultBlockState());
        var be = h.getBlockEntity(door, com.powerfactor.pocket.PocketBlockEntity.class);
        var room = be.ensureRoom(h.getLevel());
        var pocket = com.powerfactor.pocket.PocketRooms.level(h.getLevel().getServer());
        h.assertTrue(pocket != null, "pocket dimension exists");
        BlockPos o = com.powerfactor.pocket.PocketRooms.origin(room.id);
        h.assertTrue(pocket.getBlockState(o.below()).is(com.powerfactor.registry.ModPocket.POCKET_WALL.get()), "floor built");
        h.assertTrue(pocket.getBlockState(o.offset(4, 0, 0)).is(com.powerfactor.registry.ModPocket.POCKET_WALL.get()), "7-wide room walls at 4");
        pocket.setBlockAndUpdate(o, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        pocket.setBlockAndUpdate(o.east(), com.powerfactor.registry.ModPocket.POCKET_PORT.get().defaultBlockState()
                .setValue(com.powerfactor.pocket.PocketPortBlock.FACING, Direction.WEST));
        var handler = h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, h.absolutePos(door), Direction.UP);
        h.assertTrue(handler != null, "pocket passes the chest's inventory through");
        try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            h.assertTrue(handler.insert(net.neoforged.neoforge.transfer.item.ItemResource.of(Items.DIAMOND), 5, tx) == 5, "inserted 5");
            tx.commit();
        }
        var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) pocket.getBlockEntity(o);
        h.assertTrue(chest.getItem(0).is(Items.DIAMOND) && chest.getItem(0).getCount() == 5, "diamonds in the chest inside");
        h.assertTrue(be.grow(h.getLevel()), "grows");
        h.assertTrue(pocket.getBlockState(o.offset(4, 0, 0)).isAir(), "old wall lifted");
        h.assertTrue(pocket.getBlockState(o.offset(7, 0, 0)).is(com.powerfactor.registry.ModPocket.POCKET_WALL.get()), "13-wide room walls at 7");
        h.assertTrue(pocket.getBlockState(o).is(net.minecraft.world.level.block.Blocks.CHEST), "chest kept");
        h.succeed();
    }

    /** A 5x5x5 reactor with one fuel column in a water pool: it forms, goes critical with the rods pulled, boils water into steam and SCRAMs. */
    private static void fission(GameTestHelper h) {
        var F = com.powerfactor.registry.ModFission.class;
        var lvl = h.getLevel();
        BlockPos o = h.absolutePos(new BlockPos(3, 1, 3));
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 5; z++) {
                    boolean shell = x == 0 || x == 4 || y == 0 || y == 4 || z == 0 || z == 4;
                    var st = shell ? com.powerfactor.registry.ModFission.REACTOR_CASING.get().defaultBlockState()
                            : x == 2 && z == 2 ? com.powerfactor.registry.ModFission.FUEL_ASSEMBLY.get().defaultBlockState()
                            : net.minecraft.world.level.block.Blocks.WATER.defaultBlockState();
                    lvl.setBlock(o.offset(x, y, z), st, 2);
                }
            }
        }
        lvl.setBlock(o.offset(2, 4, 2), com.powerfactor.registry.ModFission.CONTROL_ROD_DRIVE.get().defaultBlockState(), 2);
        // Controller in the north wall (z = 0), facing out (north).
        BlockPos ctrl = o.offset(2, 2, 0);
        lvl.setBlockAndUpdate(ctrl, com.powerfactor.registry.ModFission.REACTOR_CONTROLLER.get().defaultBlockState()
                .setValue(com.powerfactor.machine.MachineBlock.FACING, Direction.NORTH));
        var r = (com.powerfactor.fission.ReactorControllerBlockEntity) lvl.getBlockEntity(ctrl);
        r.tanks().add(0, 60_000);
        for (int i = 0; i < 3; i++) r.fuel().setItem(i, new ItemStack(com.powerfactor.registry.ModFission.FUEL_ROD.get()));
        h.runAfterDelay(80, () -> {
            h.assertTrue(r.shape() != null, "reactor formed: " + r.problem().getString());
            h.assertTrue(r.assemblyCount() == 3, "three assemblies, got " + r.assemblyCount());
            // The shell joins into one connected casing: trim only where it faces outside.
            var corner = lvl.getBlockState(o.offset(0, 4, 0));
            h.assertTrue(corner.getValue(com.powerfactor.multiblock.CasingBlock.FORMED), "shell formed");
            h.assertTrue(corner.getValue(com.powerfactor.multiblock.Connected.SIDES.get(Direction.EAST))
                    && corner.getValue(com.powerfactor.multiblock.Connected.SIDES.get(Direction.DOWN))
                    && !corner.getValue(com.powerfactor.multiblock.Connected.SIDES.get(Direction.NORTH))
                    && !corner.getValue(com.powerfactor.multiblock.Connected.SIDES.get(Direction.UP)), "corner joins along the shell only: " + corner);
            h.assertTrue(lvl.getBlockState(o.offset(1, 2, 0)).getValue(com.powerfactor.multiblock.Connected.SIDES.get(Direction.EAST)), "casing joins the controller");
            h.assertTrue(lvl.getBlockState(o.offset(1, 4, 2)).getValue(com.powerfactor.multiblock.Connected.SIDES.get(Direction.EAST)), "roof joins the rod drive");
            h.assertTrue(r.reset(), "reset from the initial SCRAM");
            r.setTarget(0.3);
            r.setAuto(true);
            h.runAfterDelay(620, () -> {
                h.assertTrue(!r.scrammed(), "still running on Auto: alarms=" + Integer.toBinaryString(r.alarms()) + " T=" + r.core().fuelTemp);
                h.assertTrue(r.core().n > 0.05, "critical: n=" + r.core().n + " rho=" + r.core().reactivity + " rods=" + r.rods()[0]);
                h.assertTrue(r.tanks().amount(1) > 0, "steam made");
                r.scram(lvl, "test");
                h.runAfterDelay(40, () -> {
                    h.assertTrue(r.scrammed() && r.rods()[0] > 0.99, "rods home after SCRAM: " + r.rods()[0]);
                    // Break the far corner: the shell falls apart into separate casings again.
                    lvl.setBlockAndUpdate(o.offset(4, 4, 4), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                    h.runAfterDelay(50, () -> {
                        var c = lvl.getBlockState(o.offset(0, 4, 0));
                        h.assertTrue(!c.getValue(com.powerfactor.multiblock.CasingBlock.FORMED)
                                && !c.getValue(com.powerfactor.multiblock.Connected.SIDES.get(Direction.EAST)), "shell released: " + c);
                        h.succeed();
                    });
                });
            });
        });
    }

    /** The High-Pressure Turbine forms from its 5x3x9 pattern, spins up on steam and delivers far beyond an ordinary turbine's 800 FE/t. */
    private static void hpTurbine(GameTestHelper h) {
        BlockPos at = new BlockPos(5, 1, 1);
        h.setBlock(at, com.powerfactor.registry.ModFission.HP_TURBINE.get().defaultBlockState());
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        h.assertTrue(com.powerfactor.multiblock.StructureBuilder.assemble(h.getLevel(), h.absolutePos(at), Direction.NORTH, player,
                com.powerfactor.steam.HighPressureTurbineBlockEntity.STRUCTURE), "structure did not assemble");
        // A sink for its output: a creative-style energy sponge is overkill; let the buffer fill and read the model's output instead.
        h.runAfterDelay(60, () -> {
            var t = h.getBlockEntity(at, com.powerfactor.steam.HighPressureTurbineBlockEntity.class);
            h.assertTrue(t != null && t.formed(), "formed");
            h.assertTrue(t.tier().ratedFe() == 50_000, "rated 50,000 FE/t");
            for (int i = 0; i < 12; i++) h.runAfterDelay(i * 20, () -> t.getTanks().add(com.powerfactor.steam.SteamTurbineBlockEntity.STEAM, 60_000));
            h.runAfterDelay(300, () -> {
                h.assertTrue(t.model().rpm() > 3000, "spun up: " + t.model().rpm() + " rpm");
                h.assertTrue(t.getEnergy().getAmountAsInt() > 20_000, "made power beyond a small turbine: " + t.getEnergy().getAmountAsInt());
                h.succeed();
            });
        });
    }

    /**
     * A fitted pickaxe swung with the vein key held takes the touching iron ore, stone and deepslate alike
     * (one c:ores/iron vein), leaves an unconnected ore and the stone around it alone, and wears the tool.
     */
    private static void veinMiner(GameTestHelper h) {
        var lvl = h.getLevel();
        BlockPos o = h.absolutePos(new BlockPos(2, 2, 2));
        var IRON = net.minecraft.world.level.block.Blocks.IRON_ORE.defaultBlockState();
        var DEEP = net.minecraft.world.level.block.Blocks.DEEPSLATE_IRON_ORE.defaultBlockState();
        for (int x = 0; x < 6; x++) lvl.setBlockAndUpdate(o.offset(x, 0, 0), x % 2 == 0 ? IRON : DEEP);
        lvl.setBlockAndUpdate(o.offset(1, 1, 1), IRON);                       // diagonal neighbour: part of the vein
        lvl.setBlockAndUpdate(o.offset(0, 0, 4), IRON);                       // separate: must survive
        lvl.setBlockAndUpdate(o.offset(0, 1, 0), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(lvl);
        player.setPos(o.getX() + 0.5, o.getY() + 1, o.getZ() - 1.5);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        pick.set(com.powerfactor.registry.ModComponents.VEIN_MINER.get(), net.minecraft.util.Unit.INSTANCE);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, pick);
        com.powerfactor.item.VeinMiner.setHolding(player.getUUID(), true);
        h.assertTrue(player.gameMode.destroyBlock(o), "first block broken");
        h.runAfterDelay(20, () -> {
            com.powerfactor.item.VeinMiner.setHolding(player.getUUID(), false);
            for (int x = 0; x < 6; x++) h.assertTrue(lvl.getBlockState(o.offset(x, 0, 0)).isAir(), "vein block " + x + " mined");
            h.assertTrue(lvl.getBlockState(o.offset(1, 1, 1)).isAir(), "diagonal ore mined");
            h.assertTrue(lvl.getBlockState(o.offset(0, 0, 4)).is(net.minecraft.world.level.block.Blocks.IRON_ORE), "separate ore untouched");
            h.assertTrue(lvl.getBlockState(o.offset(0, 1, 0)).is(net.minecraft.world.level.block.Blocks.STONE), "stone untouched");
            h.assertTrue(pick.getDamageValue() >= 6, "tool wore per block: " + pick.getDamageValue());
            h.succeed();
        });
    }

    /** The containment sphere forms, a Singularity and 500M FE ignite a hole of mass 1,000, and auto-feed holds it there while it makes net power. */
    private static void blackHole(GameTestHelper h) {
        BlockPos at = new BlockPos(5, 1, 2);
        h.setBlock(at, com.powerfactor.registry.ModBlackHole.CONTROLLER.get().defaultBlockState());
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        h.assertTrue(com.powerfactor.multiblock.StructureBuilder.assemble(h.getLevel(), h.absolutePos(at), Direction.NORTH, player,
                com.powerfactor.blackhole.BlackHoleBlockEntity.STRUCTURE), "structure did not assemble");
        var bh = h.getBlockEntity(at, com.powerfactor.blackhole.BlackHoleBlockEntity.class);
        bh.getEnergy().set(com.powerfactor.blackhole.BlackHoleBlockEntity.CAPACITY);
        bh.feed().setItem(0, new ItemStack(com.powerfactor.registry.ModQuantum.SINGULARITY.get()));
        for (int i = 1; i < 9; i++) bh.feed().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        h.runAfterDelay(40, () -> {
            h.assertTrue(bh.formed(), "formed");
            // Casing joins everything in the sphere; glass joins only glass and the injector, keeping its window frame.
            var side = com.powerfactor.multiblock.Connected.SIDES;
            var casing = h.getBlockState(at.offset(-1, 0, 0));
            h.assertTrue(casing.getValue(side.get(Direction.EAST)) && casing.getValue(side.get(Direction.WEST))
                    && casing.getValue(side.get(Direction.UP)) && !casing.getValue(side.get(Direction.NORTH)), "casing joins: " + casing);
            // Any casing position, borders and corners included, takes the structure's glass instead.
            var corner = com.powerfactor.blackhole.BlackHoleBlockEntity.STRUCTURE.parts().getFirst();
            h.assertTrue(corner.block().get() == com.powerfactor.registry.ModBlackHole.CASING.get()
                    && corner.matches(com.powerfactor.registry.ModBlackHole.GLASS.get().defaultBlockState())
                    && !corner.matches(com.powerfactor.registry.ModQuantum.FUSION_GLASS.get().defaultBlockState()), "glass stands in for casing");
            var glass = h.getBlockState(at.offset(-1, 1, 0));
            h.assertTrue(glass.getValue(side.get(Direction.EAST)) && glass.getValue(side.get(Direction.UP))
                    && !glass.getValue(side.get(Direction.WEST)) && !glass.getValue(side.get(Direction.DOWN)), "glass frame: " + glass);
            h.assertTrue(bh.ignite(h.getLevel()), "ignites");
            h.runAfterDelay(160, () -> {
                h.assertTrue(bh.state() == com.powerfactor.blackhole.BlackHoleBlockEntity.STATE_RUNNING, "running after spin-up, state " + bh.state());
                int before = bh.getEnergy().getAmountAsInt();
                h.runAfterDelay(200, () -> {
                    double m = bh.core().mass;
                    h.assertTrue(m > 900 && m < 1100, "held near 1,000: " + m);
                    h.assertTrue(bh.core().net > 500_000, "net power: " + bh.core().net);
                    h.assertTrue(bh.getEnergy().getAmountAsInt() > before || bh.getEnergy().getAmountAsInt() > 1_900_000_000, "energy banked");
                    h.succeed();
                });
            });
        });
    }

    /**
     * A 5x5x5 reactor with glass walls and casing edges (glass edges don't form) runs on liquid sodium: the coolant tank takes sodium and then
     * refuses water, the core heats sodium instead of boiling, a Heat Exchanger turns the hot sodium and water into steam and
     * cold sodium, and once shut down and cool the console purges the tanks.
     */
    private static void sodiumLoop(GameTestHelper h) {
        var lvl = h.getLevel();
        BlockPos o = h.absolutePos(new BlockPos(3, 1, 3));
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 5; z++) {
                    int faces = (x == 0 || x == 4 ? 1 : 0) + (y == 0 || y == 4 ? 1 : 0) + (z == 0 || z == 4 ? 1 : 0);
                    var st = faces > 0 ? com.powerfactor.registry.ModFission.REACTOR_GLASS.get().defaultBlockState()
                            : x == 2 && z == 2 ? com.powerfactor.registry.ModFission.FUEL_ASSEMBLY.get().defaultBlockState()
                            : net.minecraft.world.level.block.Blocks.WATER.defaultBlockState();
                    lvl.setBlock(o.offset(x, y, z), st, 2);
                }
            }
        }
        lvl.setBlock(o.offset(2, 4, 2), com.powerfactor.registry.ModFission.CONTROL_ROD_DRIVE.get().defaultBlockState(), 2);
        BlockPos ctrl = o.offset(2, 2, 0);
        lvl.setBlockAndUpdate(ctrl, com.powerfactor.registry.ModFission.REACTOR_CONTROLLER.get().defaultBlockState()
                .setValue(com.powerfactor.machine.MachineBlock.FACING, Direction.NORTH));
        h.assertValueEqual(com.powerfactor.fission.ReactorStructure.scan(lvl, ctrl, Direction.SOUTH).problem().key(),
                "gui.powerfactor.fission.problem.edge", "glass edges refused");
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 5; z++) {
                    int faces = (x == 0 || x == 4 ? 1 : 0) + (y == 0 || y == 4 ? 1 : 0) + (z == 0 || z == 4 ? 1 : 0);
                    if (faces >= 2) lvl.setBlock(o.offset(x, y, z), com.powerfactor.registry.ModFission.REACTOR_CASING.get().defaultBlockState(), 2);
                }
            }
        }
        var r = (com.powerfactor.fission.ReactorControllerBlockEntity) lvl.getBlockEntity(ctrl);
        var sodium = com.powerfactor.registry.ModFluids.LIQUID_SODIUM.source().get();
        h.assertValueEqual(fill(r.tanks(), sodium, 30_000), 30_000, "sodium accepted as coolant");
        h.assertValueEqual(fill(r.tanks(), net.minecraft.world.level.material.Fluids.WATER, 1000), 0, "water refused while sodium is in");
        h.assertTrue(r.tanks().coolant() == com.powerfactor.fission.ReactorCore.Coolant.LIQUID_SODIUM, "running on sodium");
        for (int i = 0; i < 3; i++) r.fuel().setItem(i, new ItemStack(com.powerfactor.registry.ModFission.FUEL_ROD.get()));
        h.runAfterDelay(80, () -> {
            h.assertTrue(r.shape() != null, "glass-edged reactor formed: " + r.problem().getString());
            var edge = lvl.getBlockState(o);
            h.assertTrue(edge.getValue(com.powerfactor.multiblock.CasingBlock.FORMED)
                    && edge.getValue(com.powerfactor.multiblock.Connected.SIDES.get(Direction.EAST)), "glass corner joins: " + edge);
            h.assertTrue(r.reset(), "reset");
            r.setTarget(0.3);
            r.setAuto(true);
            h.runAfterDelay(500, () -> {
                h.assertTrue(!r.scrammed(), "running: alarms=" + Integer.toBinaryString(r.alarms()) + " T=" + r.core().fuelTemp);
                var out = r.tanks().getResource(com.powerfactor.fission.ReactorTanks.OUTPUT);
                h.assertTrue(!out.isEmpty() && out.getFluid().isSame(com.powerfactor.registry.ModFluids.HOT_SODIUM.source().get())
                        && r.tanks().amount(1) > 0, "hot sodium out, not steam: " + out + " x" + r.tanks().amount(1));
                // The exchanger raises steam from it and returns the sodium cold.
                BlockPos hx = h.absolutePos(new BlockPos(9, 1, 3));
                lvl.setBlockAndUpdate(hx, com.powerfactor.registry.ModFission.HEAT_EXCHANGER.get().defaultBlockState());
                var ex = (com.powerfactor.fission.HeatExchangerBlockEntity) lvl.getBlockEntity(hx);
                int hot = r.tanks().amount(1);
                h.assertValueEqual(fill(ex.getTanks(), com.powerfactor.registry.ModFluids.HOT_SODIUM.source().get(), hot), hot, "exchanger takes hot sodium");
                h.assertValueEqual(fill(ex.getTanks(), net.minecraft.world.level.material.Fluids.WATER, 8000), 8000, "exchanger takes water");
                h.assertValueEqual(fill(ex.getTanks(), sodium, 100), 0, "cold side can't be filled");
                h.runAfterDelay(20, () -> {
                    var t = ex.getTanks();
                    h.assertTrue(t.amount(com.powerfactor.fission.HeatExchangerBlockEntity.STEAM) > 0, "steam raised");
                    h.assertTrue(t.amount(com.powerfactor.fission.HeatExchangerBlockEntity.COLD) > 0
                            && t.getResource(com.powerfactor.fission.HeatExchangerBlockEntity.COLD).getFluid().isSame(sodium), "sodium back cold");
                    h.assertValueEqual(t.amount(com.powerfactor.fission.HeatExchangerBlockEntity.COLD) + t.amount(com.powerfactor.fission.HeatExchangerBlockEntity.HOT),
                            hot, "no sodium lost");
                    // Shut down; purge refuses while hot, works once cool.
                    r.scram(lvl, "test");
                    r.core().fuelTemp = 900;
                    h.assertTrue(!r.purge(), "no purge while hot");
                    r.core().fuelTemp = 320;
                    h.assertTrue(r.purge() && r.tanks().amount(0) == 0 && r.tanks().amount(1) == 0, "purged");
                    h.assertValueEqual(fill(r.tanks(), net.minecraft.world.level.material.Fluids.WATER, 1000), 1000, "water accepted after purge");
                    h.succeed();
                });
            });
        });
    }

    /**
     * TNT on a bedrock floor chips Bedrock Fragments loose (the bedrock stays). A Pocket Void holds nine kinds by the
     * thousand: it stacks matching items, starts new kinds in empty cells, keeps a locked cell's kind when emptied,
     * and in Void mode swallows the overflow of a full cell.
     */
    private static void bedrockAndVoid(GameTestHelper h) {
        var lvl = h.getLevel();
        for (int x = 1; x < 10; x++) for (int z = 1; z < 10; z++) h.setBlock(new BlockPos(x, 1, z), net.minecraft.world.level.block.Blocks.BEDROCK);
        int[][] spots = {{2, 2}, {8, 2}, {2, 8}, {8, 8}, {5, 5}, {5, 2}};
        for (int[] s : spots) {
            var tnt = new net.minecraft.world.entity.item.PrimedTnt(lvl, h.absolutePos(new BlockPos(s[0], 2, s[1])).getX() + 0.5,
                    h.absolutePos(new BlockPos(s[0], 2, s[1])).getY(), h.absolutePos(new BlockPos(s[0], 2, s[1])).getZ() + 0.5, null);
            tnt.setFuse(1);
            lvl.addFreshEntity(tnt);
        }
        h.runAfterDelay(30, () -> {
            var area = new net.minecraft.world.phys.AABB(h.absolutePos(BlockPos.ZERO)).inflate(16);
            int chips = lvl.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area,
                    e -> e.getItem().is(com.powerfactor.registry.ModVoid.BEDROCK_FRAGMENT.get())).stream().mapToInt(e -> e.getItem().getCount()).sum();
            h.assertTrue(chips > 0, "TNT chipped bedrock: " + chips);
            h.assertTrue(h.getBlockState(new BlockPos(5, 1, 5)).is(net.minecraft.world.level.block.Blocks.BEDROCK), "bedrock itself survives");
            // The Pocket Void.
            var c = com.powerfactor.pocketvoid.VoidContents.EMPTY;
            int cap = 2048;
            var r = c.insert(new ItemStack(Items.COBBLESTONE, 64), cap, true, false);
            h.assertValueEqual(r.taken(), 64, "new kind starts a cell");
            c = r.contents();
            for (int i = 0; i < 40; i++) c = c.insert(new ItemStack(Items.COBBLESTONE, 64), cap, false, false).contents();
            h.assertValueEqual(c.slot(0).count(), 2048, "a cell holds two thousand");
            r = c.insert(new ItemStack(Items.COBBLESTONE, 10), cap, false, false);
            h.assertValueEqual(r.taken(), 0, "full cell refuses without Void");
            r = c.insert(new ItemStack(Items.COBBLESTONE, 10), cap, false, true);
            h.assertValueEqual(r.taken(), 10, "Void swallows the overflow");
            h.assertValueEqual(c.insert(new ItemStack(Items.DIRT, 5), cap, false, false).taken(), 0, "Matching mode won't start a new kind");
            c = c.insert(new ItemStack(Items.DIRT, 5), cap, true, false).contents();
            c = c.toggleLock(1);
            var t = c.take(1, 64);
            h.assertValueEqual(t.stack().getCount(), 5, "took the dirt back");
            h.assertTrue(t.contents().slot(1).item().isPresent() && t.contents().slot(1).locked(), "locked cell keeps its kind empty");
            var stack = new ItemStack(com.powerfactor.registry.ModVoid.POCKET_VOID.get());
            com.powerfactor.pocketvoid.PocketVoidItem.set(stack, t.contents());
            h.assertValueEqual(com.powerfactor.pocketvoid.PocketVoidItem.contents(stack).slot(0).count(), 2048, "contents ride on the item");
            h.succeed();
        });
    }

    /**
     * Jumping on an elevator lifts the player to the next one of the same colour (skipping other colours and floors
     * without headroom); sneaking drops back. A Chunk Loader Card in a machine's upgrade slots takes a ticket.
     */
    private static void elevatorAndChunkCard(GameTestHelper h) {
        var E = com.powerfactor.registry.ModVoid.ELEVATOR.get();
        var color = com.powerfactor.elevator.ElevatorBlock.COLOR;
        h.setBlock(new BlockPos(5, 1, 5), E.defaultBlockState());
        h.setBlock(new BlockPos(5, 4, 5), E.defaultBlockState().setValue(color, net.minecraft.world.item.DyeColor.RED));   // other shaft
        h.setBlock(new BlockPos(5, 7, 5), E.defaultBlockState());
        h.setBlock(new BlockPos(5, 9, 5), net.minecraft.world.level.block.Blocks.STONE);    // no headroom over the second white one...
        h.setBlock(new BlockPos(5, 11, 5), E.defaultBlockState());                          // ...so it goes on to this one
        var lvl = h.getLevel();
        BlockPos base = h.absolutePos(new BlockPos(5, 1, 5));
        h.assertTrue(base.above(10).equals(com.powerfactor.elevator.ElevatorBlock.target(lvl, base, true)), "skips red and blocked landings");
        var player = h.makeMockServerPlayerInLevel();
        player.teleportTo(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
        h.assertTrue(com.powerfactor.elevator.ElevatorBlock.ride(player, true), "rode up");
        h.assertValueEqual((int) Math.floor(player.getY()), base.getY() + 11, "landed on the top floor");
        h.assertTrue(com.powerfactor.elevator.ElevatorBlock.ride(player, false), "rode down");
        h.assertValueEqual((int) Math.floor(player.getY()), base.getY() + 1, "back at the bottom");
        // Chunk Loader Card.
        BlockPos m = new BlockPos(2, 1, 2);
        h.setBlock(m, com.powerfactor.registry.ModChem.CHEMICAL_REACTOR.get().defaultBlockState());
        var be = h.getBlockEntity(m, ChemicalReactorBlockEntity.class);
        var card = new ItemStack(com.powerfactor.registry.ModItems.CHUNK_LOADER_UPGRADE.get());
        h.assertTrue(be.upgrades().accepts(1, card), "every machine takes the chunk card");
        be.upgrades().setItem(1, card);
        h.runAfterDelay(25, () -> {
            h.assertTrue(be.chunkForced(), "chunk ticket taken");
            be.upgrades().setItem(1, ItemStack.EMPTY);
            h.runAfterDelay(25, () -> {
                h.assertTrue(!be.chunkForced(), "ticket released when the card comes out");
                h.succeed();
            });
        });
    }

    /**
     * A wireless power network: nothing moves without a controller; with one, a Plug fed by a creative source powers
     * Points wirelessly, a Surge Point is served first when the Plug's limit runs short, and once everything is fed the
     * surplus fills a Cell.
     */
    private static void powerNet(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var data = com.powerfactor.powernet.PowerNets.get(server);
        var net = data.create("Test Grid", 0x19C6D8, java.util.UUID.randomUUID());
        h.setBlock(new BlockPos(1, 1, 1), com.powerfactor.registry.ModStorage.CREATIVE_POWER_SOURCE.get());
        h.setBlock(new BlockPos(7, 1, 1), com.powerfactor.registry.ModChem.CHEMICAL_REACTOR.get());
        h.setBlock(new BlockPos(7, 1, 4), com.powerfactor.registry.ModChem.CHEMICAL_REACTOR.get());
        h.setBlock(new BlockPos(2, 1, 1), com.powerfactor.registry.ModPowerNet.PLUG.get());
        h.setBlock(new BlockPos(6, 1, 1), com.powerfactor.registry.ModPowerNet.POINT.get());
        h.setBlock(new BlockPos(6, 1, 4), com.powerfactor.registry.ModPowerNet.POINT.get());
        h.setBlock(new BlockPos(4, 1, 7), com.powerfactor.registry.ModPowerNet.CELL.get());
        var plug = h.getBlockEntity(new BlockPos(2, 1, 1), com.powerfactor.powernet.PowerNodeBlockEntity.class);
        var pointA = h.getBlockEntity(new BlockPos(6, 1, 1), com.powerfactor.powernet.PowerNodeBlockEntity.class);
        var pointB = h.getBlockEntity(new BlockPos(6, 1, 4), com.powerfactor.powernet.PowerNodeBlockEntity.class);
        var cell = h.getBlockEntity(new BlockPos(4, 1, 7), com.powerfactor.powernet.PowerNodeBlockEntity.class);
        var a = h.getBlockEntity(new BlockPos(7, 1, 1), ChemicalReactorBlockEntity.class);
        var b = h.getBlockEntity(new BlockPos(7, 1, 4), ChemicalReactorBlockEntity.class);
        for (var d : List.of(plug, pointA, pointB, cell)) d.setNetwork(net.id);
        plug.applySettings("", 0, 1000, false, false, false);
        pointB.applySettings("Surge", 0, 800_000, true, false, false);
        h.runAfterDelay(10, () -> {
            h.assertValueEqual(a.getEnergy().getAmountAsInt() + b.getEnergy().getAmountAsInt(), 0, "no controller, no power");
            h.setBlock(new BlockPos(4, 1, 4), com.powerfactor.registry.ModPowerNet.CONTROLLER.get());
            h.getBlockEntity(new BlockPos(4, 1, 4), com.powerfactor.powernet.PowerNodeBlockEntity.class).setNetwork(net.id);
            h.runAfterDelay(30, () -> {
                h.assertTrue(com.powerfactor.powernet.PowerNets.running(server, net.id), "network running");
                h.assertTrue(b.getEnergy().getAmountAsInt() > 0, "surge point fed: " + b.getEnergy().getAmountAsInt());
                h.assertValueEqual(a.getEnergy().getAmountAsInt(), 0, "the 1,000 FE/t plug can't spare any for the other point");
                plug.applySettings("", 0, 1000, false, true, false);   // lift the limit
                h.runAfterDelay(140, () -> {
                    h.assertTrue(a.getEnergy().getAmountAsInt() > 0, "both points fed once the plug is unlimited");
                    h.assertTrue(cell.energy() > 0, "surplus stored in the cell: " + cell.energy());
                    h.assertTrue(net.stored > 0 && net.capacity == com.powerfactor.powernet.PowerNodeBlockEntity.Kind.CELL.capacity,
                            "stats: stored " + net.stored + " of " + net.capacity);
                    data.delete(server, net.id);
                    h.succeed();
                });
            });
        });
    }

    /**
     * Red alloy carries a signal ten blocks with no fade; blue insulated wire beside it stays dark; a red signal crosses a
     * bundled cable to red insulated wire on the far side while blue through the same bundle stays off; an AND gate needs
     * both inputs; a NOT gate lights with no input; an Illumar lamp lights when powered.
     */
    private static void redstone(GameTestHelper h) {
        var stone = net.minecraft.world.level.block.Blocks.SMOOTH_STONE;
        for (int x = 0; x < 11; x++) for (int z = 0; z < 11; z++) h.setBlock(new BlockPos(x, 1, z), stone);
        var alloy = com.powerfactor.registry.ModRedstone.RED_ALLOY_WIRE.get();
        var blue = com.powerfactor.registry.ModRedstone.INSULATED.get(net.minecraft.world.item.DyeColor.BLUE).get();
        var red = com.powerfactor.registry.ModRedstone.INSULATED.get(net.minecraft.world.item.DyeColor.RED).get();
        var bundled = com.powerfactor.registry.ModRedstone.BUNDLED_CABLE.get();
        // Row z=1: red alloy x 1..10 fed by a redstone block at x=0, a vanilla lamp at the end (x=10 is the lamp, on the floor level).
        for (int x = 1; x < 10; x++) h.setBlock(new BlockPos(x, 2, 1), alloy);
        h.setBlock(new BlockPos(10, 2, 1), net.minecraft.world.level.block.Blocks.REDSTONE_LAMP);
        // Row z=3: blue insulated alongside, its own lamp.
        for (int x = 1; x < 10; x++) h.setBlock(new BlockPos(x, 2, 3), blue);
        h.setBlock(new BlockPos(10, 2, 3), net.minecraft.world.level.block.Blocks.REDSTONE_LAMP);
        // Row z=5: red insulated x1..3, bundled x4..6, red insulated x7..9, lamp; row z=6 blue insulated x7..9 off the same bundle, lamp.
        for (int x = 1; x <= 3; x++) h.setBlock(new BlockPos(x, 2, 5), red);
        for (int x = 4; x <= 6; x++) h.setBlock(new BlockPos(x, 2, 5), bundled);
        for (int x = 7; x <= 9; x++) h.setBlock(new BlockPos(x, 2, 5), red);
        h.setBlock(new BlockPos(10, 2, 5), net.minecraft.world.level.block.Blocks.REDSTONE_LAMP);
        h.setBlock(new BlockPos(6, 2, 6), blue);
        h.setBlock(new BlockPos(6, 2, 7), blue);
        h.setBlock(new BlockPos(6, 2, 8), net.minecraft.world.level.block.Blocks.REDSTONE_LAMP);
        // Gates on row z=9..10: an AND (output north to a lamp) with one input powered; a NOT on its own.
        var and = com.powerfactor.registry.ModRedstone.GATES.get(com.powerfactor.redstone.GateBlock.Type.AND).get();
        var not = com.powerfactor.registry.ModRedstone.GATES.get(com.powerfactor.redstone.GateBlock.Type.NOT).get();
        h.setBlock(new BlockPos(2, 2, 9), and.defaultBlockState().setValue(com.powerfactor.redstone.GateBlock.FACING, Direction.NORTH));
        h.setBlock(new BlockPos(2, 2, 8), com.powerfactor.registry.ModRedstone.LAMPS.get(net.minecraft.world.item.DyeColor.LIME).get());
        h.setBlock(new BlockPos(8, 2, 10), not.defaultBlockState().setValue(com.powerfactor.redstone.GateBlock.FACING, Direction.NORTH));
        h.setBlock(new BlockPos(8, 2, 9), net.minecraft.world.level.block.Blocks.REDSTONE_LAMP);
        // Power: the red alloy run, the red insulated run, and the AND's left input.
        h.setBlock(new BlockPos(0, 2, 1), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);
        h.setBlock(new BlockPos(0, 2, 5), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);
        h.setBlock(new BlockPos(1, 2, 9), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);   // west = AND's left when facing north
        var lit = net.minecraft.world.level.block.RedstoneLampBlock.LIT;
        h.runAfterDelay(10, () -> {
            h.assertTrue(h.getBlockState(new BlockPos(10, 2, 1)).getValue(lit), "red alloy carries ten blocks without fading");
            h.assertTrue(h.getBlockState(new BlockPos(5, 2, 1)).getValue(com.powerfactor.redstone.WireBlock.POWERED), "the run is live");
            h.assertFalse(h.getBlockState(new BlockPos(10, 2, 3)).getValue(lit), "blue insulated beside it stays dark");
            h.assertTrue(h.getBlockState(new BlockPos(10, 2, 5)).getValue(lit), "red crosses the bundled cable");
            h.assertFalse(h.getBlockState(new BlockPos(6, 2, 8)).getValue(lit), "blue in the same bundle stays off");
            h.assertFalse(h.getBlockState(new BlockPos(2, 2, 9)).getValue(com.powerfactor.redstone.GateBlock.OUT), "AND with one input is off");
            h.assertTrue(h.getBlockState(new BlockPos(8, 2, 10)).getValue(com.powerfactor.redstone.GateBlock.OUT), "NOT with no input is on");
            h.assertTrue(h.getBlockState(new BlockPos(8, 2, 9)).getValue(lit), "NOT lights its lamp");
            h.setBlock(new BlockPos(3, 2, 9), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);   // east = right input
            h.setBlock(new BlockPos(0, 2, 1), net.minecraft.world.level.block.Blocks.AIR);
            h.runAfterDelay(10, () -> {
                h.assertTrue(h.getBlockState(new BlockPos(2, 2, 9)).getValue(com.powerfactor.redstone.GateBlock.OUT), "AND with both inputs is on");
                h.assertTrue(h.getBlockState(new BlockPos(2, 2, 8)).getValue(com.powerfactor.redstone.IllumarLampBlock.LIT), "the Illumar lamp lights");
                h.assertFalse(h.getBlockState(new BlockPos(5, 2, 1)).getValue(com.powerfactor.redstone.WireBlock.POWERED), "the run goes dark");
                h.assertFalse(h.getBlockState(new BlockPos(10, 2, 1)).getValue(lit), "and its lamp goes out");
                h.succeed();
            });
        });
    }

    /** Carrying spent fuel gives a dose rate the dosimeter adds up; a full Hazmat Suit keeps it at zero. */
    private static void dosimeter(GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.getInventory().add(new ItemStack(com.powerfactor.registry.ModFission.SPENT_FUEL_ROD.get(), 2));
        double rate = com.powerfactor.fission.Radiation.carried(player);
        h.assertTrue(rate >= 3, "two spent rods: dose rate " + rate);
        com.powerfactor.fission.Radiation.addDose(player, rate);
        h.assertTrue(com.powerfactor.fission.Radiation.total(player) >= 3, "dosimeter adds it up");
        h.assertFalse(com.powerfactor.fission.Radiation.shielded(player), "no suit yet");
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(com.powerfactor.registry.ModFission.HAZMAT_HELMET.get()));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(com.powerfactor.registry.ModFission.HAZMAT_SUIT.get()));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new ItemStack(com.powerfactor.registry.ModFission.HAZMAT_LEGGINGS.get()));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(com.powerfactor.registry.ModFission.HAZMAT_BOOTS.get()));
        h.assertTrue(com.powerfactor.fission.Radiation.shielded(player), "a full Hazmat Suit shields");
        h.succeed();
    }

    /** The hub's AI finds a small gold vein under its arena, sends a drone, and the drone brings the raw gold home. */
    private static void droneHub(GameTestHelper h) {
        BlockPos at = new BlockPos(5, 6, 5);
        h.setBlock(at, com.powerfactor.registry.ModDrone.HUB.get().defaultBlockState());
        var hub = h.getBlockEntity(at, com.powerfactor.drone.DroneHubBlockEntity.class);
        hub.setRadius(4);
        hub.getEnergy().set(com.powerfactor.drone.DroneHubBlockEntity.CAPACITY);
        hub.slots().setItem(0, new ItemStack(com.powerfactor.registry.ModDrone.DRONE_ITEM.get()));
        BlockPos ore = h.absolutePos(new BlockPos(7, 2, 3));
        for (int k = 0; k < 3; k++) h.getLevel().setBlockAndUpdate(ore.offset(k, 0, 0), net.minecraft.world.level.block.Blocks.GOLD_ORE.defaultBlockState());
        h.succeedWhen(() -> {
            boolean gold = false;
            for (int i = com.powerfactor.drone.DroneHubBlockEntity.BAY; i < hub.slots().getContainerSize(); i++) gold |= hub.slots().getItem(i).is(Items.RAW_GOLD);
            h.assertTrue(gold, "raw gold delivered to the hub");
            for (int k = 0; k < 3; k++) h.assertTrue(h.getLevel().getBlockState(ore.offset(k, 0, 0)).isAir(), "ore " + k + " mined");
        });
    }
}
