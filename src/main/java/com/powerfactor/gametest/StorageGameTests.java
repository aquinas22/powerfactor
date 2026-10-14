package com.powerfactor.gametest;

import com.powerfactor.registry.ModComponents;
import com.powerfactor.registry.ModStorage;
import com.powerfactor.storage.CellTier;
import com.powerfactor.storage.DriveBayBlockEntity;
import com.powerfactor.storage.IoPortBlockEntity;
import com.powerfactor.storage.NodeBlock;
import com.powerfactor.storage.StorageCellItem;
import com.powerfactor.storage.StorageControllerBlockEntity;
import com.powerfactor.storage.StorageNetwork;
import com.powerfactor.storage.GridPayloads;
import com.powerfactor.storage.TerminalBlockEntity;
import com.powerfactor.storage.TerminalMenu;
import com.powerfactor.storage.craft.AssemblerBlockEntity;
import com.powerfactor.storage.craft.CraftingService;
import com.powerfactor.storage.craft.EncodedPatternItem;
import com.powerfactor.storage.craft.PatternData;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** Storage network and autocrafting, exercised in a real level. Uses the 9x3x3 arena. */
final class StorageGameTests {
    static final List<String> NAMES = List.of("storage_cell_stores_and_survives", "storage_offline_without_power", "storage_bus_exposes_chest",
            "storage_importer_and_exporter", "storage_autocrafts_planks", "storage_conflicting_controllers",
            "storage_recipe_transfer", "storage_fluid_cells");

    static void register(DeferredRegister<Consumer<GameTestHelper>> functions) {
        functions.register("storage_cell_stores_and_survives", () -> StorageGameTests::cellStoresAndSurvives);
        functions.register("storage_offline_without_power", () -> StorageGameTests::offlineWithoutPower);
        functions.register("storage_bus_exposes_chest", () -> StorageGameTests::busExposesChest);
        functions.register("storage_importer_and_exporter", () -> StorageGameTests::importerAndExporter);
        functions.register("storage_autocrafts_planks", () -> StorageGameTests::autocraftsPlanks);
        functions.register("storage_conflicting_controllers", () -> StorageGameTests::conflictingControllers);
        functions.register("storage_recipe_transfer", () -> StorageGameTests::recipeTransfer);
        functions.register("storage_fluid_cells", () -> StorageGameTests::fluidCells);
    }

    private static StorageControllerBlockEntity controller(GameTestHelper h, BlockPos pos, boolean powered) {
        h.setBlock(pos, ModStorage.STORAGE_CONTROLLER.get().defaultBlockState());
        StorageControllerBlockEntity c = h.getBlockEntity(pos, StorageControllerBlockEntity.class);
        if (powered) c.getEnergy().set(StorageControllerBlockEntity.CAPACITY);
        return c;
    }

    private static DriveBayBlockEntity drive(GameTestHelper h, BlockPos pos, CellTier tier) {
        h.setBlock(pos, ModStorage.DRIVE_BAY.get().defaultBlockState());
        DriveBayBlockEntity d = h.getBlockEntity(pos, DriveBayBlockEntity.class);
        d.cells().setItem(0, new ItemStack(ModStorage.CELLS.get(tier).get()));
        return d;
    }

    private static void port(GameTestHelper h, BlockPos pos, net.neoforged.neoforge.registries.DeferredBlock<NodeBlock> block, Direction facing) {
        h.setBlock(pos, block.get().defaultBlockState().setValue(NodeBlock.FACING, facing));
    }

    private static StorageNetwork net(StorageControllerBlockEntity c) {
        StorageNetwork n = c.ownNetwork();
        if (n == null) throw new IllegalStateException("no network yet");
        return n;
    }

    private static void cellStoresAndSurvives(GameTestHelper h) {
        StorageControllerBlockEntity c = controller(h, new BlockPos(0, 1, 1), true);
        DriveBayBlockEntity d = drive(h, new BlockPos(1, 1, 1), CellTier.K1);
        h.runAfterDelay(4, () -> {
            StorageNetwork n = net(c);
            ItemResource cobble = ItemResource.of(Items.COBBLESTONE);
            h.assertValueEqual(n.insert(cobble, 100, false), 100L, "inserted");
            h.assertValueEqual(n.count(cobble), 100L, "view count");
            h.assertValueEqual(n.extract(cobble, 30, false), 30L, "extracted");
            h.assertValueEqual(n.extract(cobble, 1000, true), 70L, "simulated extract");
            // A 1k cell holds (1024 - 8) * 8 of one type; the rest is refused, nothing lost.
            long put = n.insert(cobble, 100_000, false);
            h.assertValueEqual(put, (1024L - 8) * 8 - 70, "fills to the byte budget");
            ItemStack cell = d.cells().getItem(0);
            h.assertValueEqual(StorageCellItem.contents(cell).items(), (1024L - 8) * 8, "cell item records contents");
            // Pull the cell: the contents travel with it, and the network forgets them.
            ItemStack removed = d.cells().removeItemNoUpdate(0);
            d.cells().setChanged();
            h.runAfterDelay(2, () -> {
                h.assertValueEqual(n.count(cobble), 0L, "network after removal");
                h.assertValueEqual(StorageCellItem.contents(removed).items(), (1024L - 8) * 8, "removed cell keeps items");
                d.cells().setItem(1, removed);
                h.runAfterDelay(2, () -> {
                    h.assertValueEqual(n.count(cobble), (1024L - 8) * 8, "reinserted cell");
                    h.succeed();
                });
            });
        });
    }

    private static void offlineWithoutPower(GameTestHelper h) {
        StorageControllerBlockEntity c = controller(h, new BlockPos(0, 1, 1), false);
        drive(h, new BlockPos(1, 1, 1), CellTier.K4);
        h.runAfterDelay(4, () -> {
            StorageNetwork n = net(c);
            h.assertFalse(n.online(), "unpowered network is offline");
            h.assertValueEqual(n.insert(ItemResource.of(Items.DIRT), 5, false), 0L, "offline insert");
            c.getEnergy().set(StorageControllerBlockEntity.CAPACITY);
            h.runAfterDelay(2, () -> {
                h.assertTrue(n.online(), "powered network comes online");
                h.assertTrue(h.getBlockState(new BlockPos(1, 1, 1)).getValue(NodeBlock.ACTIVE), "drive lights up");
                h.assertValueEqual(n.insert(ItemResource.of(Items.DIRT), 5, false), 5L, "online insert");
                h.succeed();
            });
        });
    }

    private static void busExposesChest(GameTestHelper h) {
        StorageControllerBlockEntity c = controller(h, new BlockPos(0, 1, 1), true);
        port(h, new BlockPos(1, 1, 1), ModStorage.STORAGE_BUS, Direction.EAST);
        h.setBlock(new BlockPos(2, 1, 1), Blocks.CHEST);
        ChestBlockEntity chest = h.getBlockEntity(new BlockPos(2, 1, 1), ChestBlockEntity.class);
        chest.setItem(0, new ItemStack(Items.IRON_INGOT, 10));
        h.runAfterDelay(25, () -> {
            StorageNetwork n = net(c);
            ItemResource iron = ItemResource.of(Items.IRON_INGOT);
            h.assertValueEqual(n.count(iron), 10L, "chest contents visible");
            h.assertValueEqual(n.extract(iron, 4, false), 4L, "extract through bus");
            h.assertValueEqual(chest.getItem(0).getCount(), 6, "chest drained");
            h.assertValueEqual(n.insert(ItemResource.of(Items.DIAMOND), 5, false), 5L, "insert through bus");
            h.assertTrue(chest.getItem(1).is(Items.DIAMOND) || chest.getItem(0).is(Items.DIAMOND), "diamonds in chest");
            h.succeed();
        });
    }

    private static void importerAndExporter(GameTestHelper h) {
        StorageControllerBlockEntity c = controller(h, new BlockPos(3, 1, 1), true);
        drive(h, new BlockPos(4, 1, 1), CellTier.K4);
        // Importer at x=2 pulls from the chest at x=1; exporter at x=5 pushes into the chest at x=6.
        port(h, new BlockPos(2, 1, 1), ModStorage.IMPORTER, Direction.WEST);
        h.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        port(h, new BlockPos(5, 1, 1), ModStorage.EXPORTER, Direction.EAST);
        h.setBlock(new BlockPos(6, 1, 1), Blocks.CHEST);
        ChestBlockEntity source = h.getBlockEntity(new BlockPos(1, 1, 1), ChestBlockEntity.class);
        ChestBlockEntity sink = h.getBlockEntity(new BlockPos(6, 1, 1), ChestBlockEntity.class);
        source.setItem(0, new ItemStack(Items.REDSTONE, 20));
        source.setItem(1, new ItemStack(Items.GOLD_INGOT, 3));
        IoPortBlockEntity exporter = h.getBlockEntity(new BlockPos(5, 1, 1), IoPortBlockEntity.class);
        exporter.filters().setItem(0, new ItemStack(Items.GOLD_INGOT));
        h.runAfterDelay(80, () -> {
            StorageNetwork n = net(c);
            h.assertTrue(source.isEmpty(), "importer emptied the source chest");
            h.assertValueEqual(n.count(ItemResource.of(Items.REDSTONE)), 20L, "redstone stored");
            h.assertValueEqual(sink.countItem(Items.GOLD_INGOT), 3, "exporter pushed only gold");
            h.assertValueEqual(sink.countItem(Items.REDSTONE), 0, "exporter ignored redstone");
            h.succeed();
        });
    }

    private static void autocraftsPlanks(GameTestHelper h) {
        StorageControllerBlockEntity c = controller(h, new BlockPos(0, 1, 1), true);
        drive(h, new BlockPos(1, 1, 1), CellTier.K4);
        h.setBlock(new BlockPos(2, 1, 1), ModStorage.ASSEMBLER.get().defaultBlockState());
        h.setBlock(new BlockPos(3, 1, 1), ModStorage.CRAFTING_PROCESSOR.get().defaultBlockState());
        AssemblerBlockEntity assembler = h.getBlockEntity(new BlockPos(2, 1, 1), AssemblerBlockEntity.class);
        List<PatternData.Entry> grid = new ArrayList<>();
        grid.add(new PatternData.Entry(ItemResource.of(Items.OAK_LOG), 1));
        for (int i = 1; i < 9; i++) grid.add(PatternData.Entry.EMPTY);
        PatternData planks = new PatternData(false, grid, List.of(new PatternData.Entry(ItemResource.of(Items.OAK_PLANKS), 4)));
        assembler.patternSlots().setItem(0, EncodedPatternItem.encode(planks));
        h.runAfterDelay(4, () -> {
            StorageNetwork n = net(c);
            n.insert(ItemResource.of(Items.OAK_LOG), 5, false);
            h.assertTrue(n.craftable(ItemResource.of(Items.OAK_PLANKS)), "planks craftable");
            var missing = CraftingService.order(n, ItemResource.of(Items.OAK_PLANKS), 64, "test", true);
            h.assertTrue(missing.result() == CraftingService.Result.PREVIEW && !missing.plan().complete(), "too few logs is refused");
            var outcome = CraftingService.order(n, ItemResource.of(Items.OAK_PLANKS), 10, "test", true);
            h.assertTrue(outcome.result() == CraftingService.Result.STARTED, "job started: " + outcome.result());
            h.assertValueEqual(n.count(ItemResource.of(Items.OAK_LOG)), 2L, "three logs reserved");
            h.runAfterDelay(140, () -> {
                h.assertValueEqual(n.count(ItemResource.of(Items.OAK_PLANKS)), 12L, "three crafts make 12 planks");
                h.assertValueEqual(n.count(ItemResource.of(Items.OAK_LOG)), 2L, "logs consumed");
                h.assertTrue(n.jobs().isEmpty(), "job finished");
                h.succeed();
            });
        });
    }

    private static void conflictingControllers(GameTestHelper h) {
        StorageControllerBlockEntity a = controller(h, new BlockPos(0, 1, 1), true);
        h.setBlock(new BlockPos(1, 1, 1), ModStorage.DATA_CABLE.get().defaultBlockState());
        StorageControllerBlockEntity b = controller(h, new BlockPos(2, 1, 1), true);
        h.runAfterDelay(4, () -> {
            h.assertTrue(net(a).conflict() && net(b).conflict(), "two controllers conflict");
            h.setBlock(new BlockPos(1, 1, 1), Blocks.AIR);
            h.runAfterDelay(3, () -> {
                h.assertFalse(net(a).conflict(), "separated networks recover");
                h.assertTrue(net(a).online(), "online again");
                h.succeed();
            });
        });
    }

    /** A recipe viewer's "+" button: fills the crafting grid from the network and writes pattern ghosts. */
    private static void recipeTransfer(GameTestHelper h) {
        StorageControllerBlockEntity c = controller(h, new BlockPos(0, 1, 1), true);
        drive(h, new BlockPos(1, 1, 1), CellTier.K4);
        h.setBlock(new BlockPos(2, 1, 1), ModStorage.CRAFTING_TERMINAL.get().defaultBlockState());
        h.setBlock(new BlockPos(3, 1, 1), ModStorage.PATTERN_TERMINAL.get().defaultBlockState());
        TerminalBlockEntity crafting = h.getBlockEntity(new BlockPos(2, 1, 1), TerminalBlockEntity.class);
        TerminalBlockEntity pattern = h.getBlockEntity(new BlockPos(3, 1, 1), TerminalBlockEntity.class);
        var player = h.makeMockServerPlayerInLevel();
        h.runAfterDelay(4, () -> {
            StorageNetwork n = net(c);
            n.insert(ItemResource.of(Items.OAK_PLANKS), 3, false);
            n.insert(ItemResource.of(Items.BIRCH_PLANKS), 40, false);
            // Crafting table: four planks in a square; any plank works, the network has more birch.
            List<ItemStack> anyPlank = List.of(new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.BIRCH_PLANKS));
            List<List<ItemStack>> table = new ArrayList<>();
            for (int i = 0; i < 9; i++) table.add(i % 3 < 2 && i < 6 ? anyPlank : List.of());
            TerminalMenu cm = new TerminalMenu(1, player.getInventory(), TerminalBlockEntity.Kind.CRAFTING, crafting);
            cm.handleTransfer(player, new GridPayloads.Transfer(1, false, false, table, List.of(new ItemStack(Items.CRAFTING_TABLE))));
            // Slot 0 is the result; 1..9 the grid. Oak comes first in the alternatives, so it is used while it lasts.
            int filled = 0;
            for (int i = 1; i <= 9; i++) if (!cm.getSlot(i).getItem().isEmpty()) filled++;
            h.assertValueEqual(filled, 4, "four grid slots filled");
            h.assertValueEqual(n.count(ItemResource.of(Items.OAK_PLANKS)) + n.count(ItemResource.of(Items.BIRCH_PLANKS)), 39L, "four planks pulled");
            // Shift-transfer fills whole, even stacks.
            cm.handleTransfer(player, new GridPayloads.Transfer(1, false, true, table, List.of()));
            int each = cm.getSlot(1).getItem().getCount();
            h.assertTrue(each >= 10, "max transfer stacks up: " + each);
            for (int i : new int[] {1, 2, 4, 5}) h.assertValueEqual(cm.getSlot(i).getItem().getCount(), each, "even stacks");
            h.assertValueEqual(n.count(ItemResource.of(Items.OAK_PLANKS)) + n.count(ItemResource.of(Items.BIRCH_PLANKS)) + 4L * each, 43L,
                    "nothing lost");
            // Pattern terminal: a smelting recipe becomes a processing pattern with merged inputs.
            TerminalMenu pm = new TerminalMenu(2, player.getInventory(), TerminalBlockEntity.Kind.PATTERN, pattern);
            List<List<ItemStack>> ore = List.of(List.of(new ItemStack(Items.RAW_IRON, 2)), List.of(new ItemStack(Items.RAW_IRON, 1)),
                    List.of(new ItemStack(Items.COAL)));
            pm.handleTransfer(player, new GridPayloads.Transfer(2, true, false, ore, List.of(new ItemStack(Items.IRON_INGOT, 3))));
            h.assertTrue(pm.processingMode(), "switched to processing");
            h.assertValueEqual(pm.getSlot(0).getItem().getCount(), 3, "raw iron merged to three");
            h.assertTrue(pm.getSlot(1).getItem().is(Items.COAL), "coal second");
            h.assertTrue(pm.getSlot(9).getItem().is(Items.IRON_INGOT) && pm.getSlot(9).getItem().getCount() == 3, "output ghost");
            h.succeed();
        });
    }

    /**
     * Fluid cells through the controller's fluid capability: aborted transactions change nothing,
     * committed ones are saved on the cell, contents survive leaving the drive, and the Fluid Terminal
     * fills and empties a bucket against the network.
     */
    private static void fluidCells(GameTestHelper h) {
        StorageControllerBlockEntity c = controller(h, new BlockPos(0, 1, 1), true);
        h.setBlock(new BlockPos(1, 1, 1), ModStorage.DRIVE_BAY.get().defaultBlockState());
        DriveBayBlockEntity drive = h.getBlockEntity(new BlockPos(1, 1, 1), DriveBayBlockEntity.class);
        drive.cells().setItem(0, new ItemStack(ModStorage.FLUID_CELLS.get(com.powerfactor.storage.fluid.FluidCellTier.K4).get()));
        h.setBlock(new BlockPos(2, 1, 1), ModStorage.FLUID_TERMINAL.get().defaultBlockState());
        var terminal = h.getBlockEntity(new BlockPos(2, 1, 1), com.powerfactor.storage.fluid.FluidTerminalBlockEntity.class);
        var water = net.neoforged.neoforge.transfer.fluid.FluidResource.of(net.minecraft.world.level.material.Fluids.WATER);
        h.runAfterDelay(4, () -> {
            StorageNetwork n = net(c);
            var cap = h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK, h.absolutePos(new BlockPos(0, 1, 1)), null);
            h.assertTrue(cap != null, "controller exposes fluids");
            try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                h.assertValueEqual(cap.insert(water, 5_000, tx), 5_000, "insert inside a transaction");
                h.assertValueEqual(n.fluidCount(water), 5_000L, "visible while open");
            }
            h.assertValueEqual(n.fluidCount(water), 0L, "aborted insert rolled back");
            try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                cap.insert(water, 5_000, tx);
                tx.commit();
            }
            h.assertValueEqual(n.fluidCount(water), 5_000L, "committed insert stored");
            ItemStack cell = drive.cells().getItem(0);
            h.assertValueEqual(com.powerfactor.storage.fluid.FluidCellItem.contents(cell).total(), 5_000L, "saved on the cell item");
            // Out of the drive and back in: the fluid travels with the cell.
            ItemStack out = drive.cells().removeItemNoUpdate(0);
            drive.cells().setChanged();
            h.assertValueEqual(n.fluidCount(water), 0L, "gone with the cell");
            drive.cells().setItem(0, out);
            h.assertValueEqual(n.fluidCount(water), 5_000L, "back with the cell");
            // Fluid Terminal: an empty bucket fills, a full bucket pours back.
            var player = h.makeMockServerPlayerInLevel();
            var menu = (com.powerfactor.storage.fluid.FluidTerminalMenu) terminal.createMenu(7, player.getInventory(), player);
            player.containerMenu = menu;
            menu.setCarried(new ItemStack(Items.BUCKET));
            menu.click(player, water);
            h.assertTrue(menu.getCarried().is(Items.WATER_BUCKET), "bucket filled from the network");
            h.assertValueEqual(n.fluidCount(water), 4_000L, "a bucket's worth left the network");
            menu.click(player, net.neoforged.neoforge.transfer.fluid.FluidResource.EMPTY);
            h.assertTrue(menu.getCarried().is(Items.BUCKET), "bucket poured back");
            h.assertValueEqual(n.fluidCount(water), 5_000L, "conserved");
            h.succeed();
        });
    }

    private StorageGameTests() {}
}
