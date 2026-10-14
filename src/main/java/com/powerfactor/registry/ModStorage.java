package com.powerfactor.registry;

import com.powerfactor.storage.CellTier;
import com.powerfactor.storage.DataCableBlock;
import com.powerfactor.storage.DriveBayBlockEntity;
import com.powerfactor.storage.DriveBayMenu;
import com.powerfactor.storage.IoPortBlockEntity;
import com.powerfactor.storage.IoPortMenu;
import com.powerfactor.storage.NodeBlock;
import com.powerfactor.storage.StorageCellItem;
import com.powerfactor.storage.StorageControllerBlockEntity;
import com.powerfactor.storage.TerminalBlockEntity;
import com.powerfactor.storage.TerminalMenu;
import com.powerfactor.storage.WirelessTerminalItem;
import com.powerfactor.storage.craft.AssemblerBlockEntity;
import com.powerfactor.storage.craft.CraftingProcessorBlockEntity;
import com.powerfactor.storage.craft.EncodedPatternItem;
import com.powerfactor.storage.craft.PatternProviderBlockEntity;
import com.powerfactor.storage.craft.PatternRackMenu;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/** The storage network and autocrafting: blocks, items, block entities and menus. */
public final class ModStorage {
    /** Everything registered here and in {@link ModQuantum} shows in the Quantum creative tab instead of the main one. */
    public static final List<DeferredItem<? extends Item>> TAB = new ArrayList<>();

    // ---------------------------------------------------------------- blocks
    public static final DeferredBlock<NodeBlock> STORAGE_CONTROLLER = node("storage_controller", () -> ModStorage.CONTROLLER_BE.get(),
            NodeBlock.Placement.HORIZONTAL, null, 12);
    public static final DeferredBlock<DataCableBlock> DATA_CABLE = ModBlocks.BLOCKS.registerBlock("data_cable", p -> new DataCableBlock(p, 0),
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.4F).sound(SoundType.WOOL).noOcclusion());
    public static final DeferredBlock<DataCableBlock> QUANTUM_DATA_CABLE = ModBlocks.BLOCKS.registerBlock("quantum_data_cable",
            p -> new DataCableBlock(p, 1), () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(0.6F)
                    .sound(SoundType.AMETHYST).noOcclusion().lightLevel(s -> 7));
    public static final DeferredBlock<NodeBlock> DRIVE_BAY = node("drive_bay", () -> ModStorage.DRIVE_BAY_BE.get(), NodeBlock.Placement.HORIZONTAL,
            null, 4);
    public static final DeferredBlock<NodeBlock> GRID_TERMINAL = node("grid_terminal", () -> ModStorage.GRID_TERMINAL_BE.get(),
            NodeBlock.Placement.PLAYER, Block.box(1, 1, 9, 15, 15, 16), 9);
    public static final DeferredBlock<NodeBlock> CRAFTING_TERMINAL = node("crafting_terminal", () -> ModStorage.CRAFTING_TERMINAL_BE.get(),
            NodeBlock.Placement.PLAYER, Block.box(1, 1, 9, 15, 15, 16), 9);
    public static final DeferredBlock<NodeBlock> PATTERN_TERMINAL = node("pattern_terminal", () -> ModStorage.PATTERN_TERMINAL_BE.get(),
            NodeBlock.Placement.PLAYER, Block.box(1, 1, 9, 15, 15, 16), 9);
    public static final DeferredBlock<NodeBlock> FLUID_TERMINAL = node("fluid_terminal", () -> ModStorage.FLUID_TERMINAL_BE.get(),
            NodeBlock.Placement.PLAYER, Block.box(1, 1, 9, 15, 15, 16), 9);
    public static final DeferredBlock<NodeBlock> STORAGE_BUS = node("storage_bus", () -> ModStorage.STORAGE_BUS_BE.get(), NodeBlock.Placement.AGAINST,
            Block.box(2, 2, 0, 14, 14, 5), 3);
    public static final DeferredBlock<NodeBlock> IMPORTER = node("importer", () -> ModStorage.IMPORTER_BE.get(), NodeBlock.Placement.AGAINST,
            Block.box(3, 3, 0, 13, 13, 6), 3);
    public static final DeferredBlock<NodeBlock> EXPORTER = node("exporter", () -> ModStorage.EXPORTER_BE.get(), NodeBlock.Placement.AGAINST,
            Block.box(3, 3, 0, 13, 13, 6), 3);
    public static final DeferredBlock<NodeBlock> CRAFTING_PROCESSOR = node("crafting_processor", () -> ModStorage.PROCESSOR_BE.get(),
            NodeBlock.Placement.HORIZONTAL, null, 6);
    public static final DeferredBlock<NodeBlock> QUANTUM_CRAFTING_PROCESSOR = node("quantum_crafting_processor",
            () -> ModStorage.QUANTUM_PROCESSOR_BE.get(), NodeBlock.Placement.HORIZONTAL, null, 11);
    public static final DeferredBlock<NodeBlock> ASSEMBLER = node("assembler", () -> ModStorage.ASSEMBLER_BE.get(), NodeBlock.Placement.HORIZONTAL,
            null, 8);
    public static final DeferredBlock<NodeBlock> PATTERN_PROVIDER = node("pattern_provider", () -> ModStorage.PATTERN_PROVIDER_BE.get(),
            NodeBlock.Placement.HORIZONTAL, null, 5);

    public static final DeferredBlock<com.powerfactor.device.CreativePowerSource> CREATIVE_POWER_SOURCE = ModBlocks.BLOCKS.registerBlock(
            "creative_power_source", com.powerfactor.device.CreativePowerSource::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(-1F, 3_600_000F).noLootTable().lightLevel(s -> 12));

    private static DeferredBlock<NodeBlock> node(String id, Supplier<BlockEntityType<? extends com.powerfactor.storage.NodeBlockEntity>> type,
            NodeBlock.Placement placement, VoxelShape shape, int light) {
        return ModBlocks.BLOCKS.registerBlock(id, p -> new NodeBlock(type, placement, shape, p),
                () -> BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 8.0F).sound(SoundType.NETHERITE_BLOCK)
                        .requiresCorrectToolForDrops().noOcclusion().lightLevel(s -> s.getValue(NodeBlock.ACTIVE) ? light : 0));
    }

    // ---------------------------------------------------------------- block entities
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StorageControllerBlockEntity>> CONTROLLER_BE = be("storage_controller",
            StorageControllerBlockEntity::new, STORAGE_CONTROLLER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DriveBayBlockEntity>> DRIVE_BAY_BE = be("drive_bay",
            DriveBayBlockEntity::new, DRIVE_BAY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TerminalBlockEntity>> GRID_TERMINAL_BE = be("grid_terminal",
            (p, s) -> new TerminalBlockEntity(TerminalBlockEntity.Kind.GRID, p, s), GRID_TERMINAL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TerminalBlockEntity>> CRAFTING_TERMINAL_BE = be("crafting_terminal",
            (p, s) -> new TerminalBlockEntity(TerminalBlockEntity.Kind.CRAFTING, p, s), CRAFTING_TERMINAL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TerminalBlockEntity>> PATTERN_TERMINAL_BE = be("pattern_terminal",
            (p, s) -> new TerminalBlockEntity(TerminalBlockEntity.Kind.PATTERN, p, s), PATTERN_TERMINAL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.powerfactor.storage.fluid.FluidTerminalBlockEntity>> FLUID_TERMINAL_BE =
            be("fluid_terminal", com.powerfactor.storage.fluid.FluidTerminalBlockEntity::new, FLUID_TERMINAL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IoPortBlockEntity>> STORAGE_BUS_BE = be("storage_bus",
            (p, s) -> new IoPortBlockEntity(IoPortBlockEntity.Kind.STORAGE_BUS, p, s), STORAGE_BUS);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IoPortBlockEntity>> IMPORTER_BE = be("importer",
            (p, s) -> new IoPortBlockEntity(IoPortBlockEntity.Kind.IMPORTER, p, s), IMPORTER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IoPortBlockEntity>> EXPORTER_BE = be("exporter",
            (p, s) -> new IoPortBlockEntity(IoPortBlockEntity.Kind.EXPORTER, p, s), EXPORTER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CraftingProcessorBlockEntity>> PROCESSOR_BE = be("crafting_processor",
            (p, s) -> new CraftingProcessorBlockEntity(false, p, s), CRAFTING_PROCESSOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CraftingProcessorBlockEntity>> QUANTUM_PROCESSOR_BE = be(
            "quantum_crafting_processor", (p, s) -> new CraftingProcessorBlockEntity(true, p, s), QUANTUM_CRAFTING_PROCESSOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AssemblerBlockEntity>> ASSEMBLER_BE = be("assembler",
            AssemblerBlockEntity::new, ASSEMBLER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PatternProviderBlockEntity>> PATTERN_PROVIDER_BE = be("pattern_provider",
            PatternProviderBlockEntity::new, PATTERN_PROVIDER);

    private static <T extends net.minecraft.world.level.block.entity.BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> be(String id,
            BlockEntityType.BlockEntitySupplier<T> factory, DeferredBlock<? extends Block> block) {
        return ModBlockEntities.BLOCK_ENTITIES.register(id, () -> new BlockEntityType<>(factory, block.get()));
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.powerfactor.device.CreativePowerSource.Entity>> CREATIVE_POWER_BE =
            be("creative_power_source", com.powerfactor.device.CreativePowerSource.Entity::new, CREATIVE_POWER_SOURCE);

    // ---------------------------------------------------------------- menus
    public static final DeferredHolder<MenuType<?>, MenuType<DriveBayMenu>> DRIVE_BAY_MENU = ModMenus.MENUS.register("drive_bay",
            () -> IMenuTypeExtension.create(DriveBayMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<IoPortMenu>> IO_PORT_MENU = ModMenus.MENUS.register("io_port",
            () -> IMenuTypeExtension.create(IoPortMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<PatternRackMenu>> PATTERN_RACK_MENU = ModMenus.MENUS.register("pattern_rack",
            () -> IMenuTypeExtension.create(PatternRackMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<com.powerfactor.storage.fluid.FluidTerminalMenu>> FLUID_TERMINAL_MENU =
            ModMenus.MENUS.register("fluid_terminal", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                    com.powerfactor.storage.fluid.FluidTerminalMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<TerminalMenu>> TERMINAL_MENU = ModMenus.MENUS.register("storage_terminal",
            () -> IMenuTypeExtension.create(TerminalMenu::new));

    // ---------------------------------------------------------------- items
    static {
        for (var b : List.of(STORAGE_CONTROLLER, DATA_CABLE, QUANTUM_DATA_CABLE, DRIVE_BAY, GRID_TERMINAL, CRAFTING_TERMINAL, PATTERN_TERMINAL,
                FLUID_TERMINAL, STORAGE_BUS, IMPORTER, EXPORTER, CRAFTING_PROCESSOR, QUANTUM_CRAFTING_PROCESSOR, ASSEMBLER, PATTERN_PROVIDER)) {
            TAB.add(ModItems.ITEMS.registerSimpleBlockItem(b, p -> b == QUANTUM_CRAFTING_PROCESSOR || b == QUANTUM_DATA_CABLE ? p.rarity(Rarity.EPIC) : p));
        }
        TAB.add(ModItems.ITEMS.registerSimpleBlockItem(CREATIVE_POWER_SOURCE, p -> p.rarity(Rarity.EPIC)));
    }

    public static final Map<CellTier, DeferredItem<StorageCellItem>> CELLS = new EnumMap<>(CellTier.class);

    static {
        for (CellTier t : CellTier.values()) {
            DeferredItem<StorageCellItem> cell = ModItems.ITEMS.registerItem(t.id, p -> new StorageCellItem(t, p));
            CELLS.put(t, cell);
            TAB.add(cell);
        }
    }

    public static final Map<com.powerfactor.storage.fluid.FluidCellTier, DeferredItem<com.powerfactor.storage.fluid.FluidCellItem>> FLUID_CELLS =
            new EnumMap<>(com.powerfactor.storage.fluid.FluidCellTier.class);

    static {
        for (var t : com.powerfactor.storage.fluid.FluidCellTier.values()) {
            DeferredItem<com.powerfactor.storage.fluid.FluidCellItem> cell = ModItems.ITEMS.registerItem(t.id,
                    p -> new com.powerfactor.storage.fluid.FluidCellItem(t, p));
            FLUID_CELLS.put(t, cell);
            TAB.add(cell);
        }
    }

    public static final DeferredItem<Item> BLANK_PATTERN = tab(ModItems.ITEMS.registerSimpleItem("blank_pattern"));
    public static final DeferredItem<EncodedPatternItem> CRAFTING_PATTERN = tab(ModItems.ITEMS.registerItem("crafting_pattern",
            p -> new EncodedPatternItem(false, p)));
    public static final DeferredItem<EncodedPatternItem> PROCESSING_PATTERN = tab(ModItems.ITEMS.registerItem("processing_pattern",
            p -> new EncodedPatternItem(true, p)));
    public static final DeferredItem<WirelessTerminalItem> WIRELESS_TERMINAL = tab(ModItems.ITEMS.registerItem("wireless_terminal",
            p -> new WirelessTerminalItem(false, p.rarity(Rarity.UNCOMMON))));
    public static final DeferredItem<WirelessTerminalItem> QUANTUM_WIRELESS_TERMINAL = tab(ModItems.ITEMS.registerItem("quantum_wireless_terminal",
            p -> new WirelessTerminalItem(true, p.rarity(Rarity.EPIC))));
    /** Storage components the cells are built around. */
    public static final DeferredItem<Item> DATA_CORE = tab(ModItems.ITEMS.registerSimpleItem("data_core"));
    public static final DeferredItem<Item> CELL_HOUSING = tab(ModItems.ITEMS.registerSimpleItem("cell_housing"));

    static <T extends Item> DeferredItem<T> tab(DeferredItem<T> item) {
        TAB.add(item);
        return item;
    }

    public static void init() {}

    private ModStorage() {}
}
