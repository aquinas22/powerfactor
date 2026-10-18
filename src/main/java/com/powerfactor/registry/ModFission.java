package com.powerfactor.registry;

import com.powerfactor.fission.CoriumBlock;
import com.powerfactor.fission.FuelRodItem;
import com.powerfactor.fission.HazmatItem;
import com.powerfactor.fission.HeatExchangerBlockEntity;
import com.powerfactor.fission.RadiationMeterItem;
import com.powerfactor.fission.IsotopeCentrifugeBlockEntity;
import com.powerfactor.fission.IsotopeCentrifugeMenu;
import com.powerfactor.fission.ReactorControllerBlockEntity;
import com.powerfactor.fission.ReactorMenu;
import com.powerfactor.fission.ReactorPartBlock;
import com.powerfactor.fission.ReactorPartBlockEntity;
import com.powerfactor.quantum.QuantumMachineBlock;
import java.util.function.Function;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/** Fission: uranium, the fuel cycle, the reactor and its parts, radiation gear. */
public final class ModFission {
    private static BlockBehaviour.Properties hull() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5F, 1200F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops();
    }

    private static <B extends Block> DeferredBlock<B> block(String id, Function<BlockBehaviour.Properties, B> f, java.util.function.Supplier<BlockBehaviour.Properties> p,
            Rarity rarity) {
        DeferredBlock<B> b = ModBlocks.BLOCKS.registerBlock(id, f, p);
        ModStorage.TAB.add(ModItems.ITEMS.registerSimpleBlockItem(b, i -> i.rarity(rarity)));
        return b;
    }

    private static DeferredItem<Item> item(String id, Rarity rarity) {
        DeferredItem<Item> i = ModItems.ITEMS.registerItem(id, p -> new Item(p.rarity(rarity)));
        ModStorage.TAB.add(i);
        return i;
    }

    // Ore and materials.
    public static final DeferredBlock<Block> URANIUM_ORE = block("uranium_ore", Block::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3F, 3F).requiresCorrectToolForDrops().lightLevel(s -> 3), Rarity.COMMON);
    public static final DeferredBlock<Block> DEEPSLATE_URANIUM_ORE = block("deepslate_uranium_ore", Block::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(4.5F, 3F).sound(SoundType.DEEPSLATE).requiresCorrectToolForDrops()
                    .lightLevel(s -> 3), Rarity.COMMON);
    public static final DeferredItem<Item> RAW_URANIUM = item("raw_uranium", Rarity.COMMON);
    public static final DeferredItem<Item> URANIUM_INGOT = item("uranium_ingot", Rarity.COMMON);
    public static final DeferredItem<Item> ENRICHED_URANIUM = item("enriched_uranium", Rarity.UNCOMMON);
    public static final DeferredItem<Item> DEPLETED_URANIUM = item("depleted_uranium", Rarity.COMMON);
    public static final DeferredItem<Item> PLUTONIUM = item("plutonium", Rarity.RARE);
    public static final DeferredItem<Item> NUCLEAR_WASTE = item("nuclear_waste", Rarity.UNCOMMON);
    public static final DeferredItem<FuelRodItem> FUEL_ROD = ModItems.ITEMS.registerItem("fuel_rod", p -> new FuelRodItem(false, p.rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<FuelRodItem> MOX_FUEL_ROD = ModItems.ITEMS.registerItem("mox_fuel_rod", p -> new FuelRodItem(true, p.rarity(Rarity.RARE)));
    public static final DeferredItem<Item> SPENT_FUEL_ROD = ModItems.ITEMS.registerItem("spent_fuel_rod", p -> new Item(p.rarity(Rarity.UNCOMMON).stacksTo(1)));
    public static final DeferredItem<Item> SPENT_MOX_ROD = ModItems.ITEMS.registerItem("spent_mox_rod", p -> new Item(p.rarity(Rarity.RARE).stacksTo(1)));
    public static final DeferredItem<HazmatItem> HAZMAT_HELMET = ModItems.ITEMS.registerItem("hazmat_helmet", p -> new HazmatItem(ArmorType.HELMET, p));
    public static final DeferredItem<HazmatItem> HAZMAT_SUIT = ModItems.ITEMS.registerItem("hazmat_suit", p -> new HazmatItem(ArmorType.CHESTPLATE, p));
    public static final DeferredItem<HazmatItem> HAZMAT_LEGGINGS = ModItems.ITEMS.registerItem("hazmat_leggings", p -> new HazmatItem(ArmorType.LEGGINGS, p));
    public static final DeferredItem<HazmatItem> HAZMAT_BOOTS = ModItems.ITEMS.registerItem("hazmat_boots", p -> new HazmatItem(ArmorType.BOOTS, p));

    // The reactor.
    private static com.powerfactor.multiblock.Connected.Style host() {
        return com.powerfactor.multiblock.Connected.Style.host("fission", () -> ModFission.REACTOR_CONTROLLER.get(), () -> ModFission.CONTROL_ROD_DRIVE.get());
    }

    public static final DeferredBlock<Block> REACTOR_CASING = block("reactor_casing", p -> new com.powerfactor.multiblock.ConnectedBlock(host(), p), ModFission::hull, Rarity.UNCOMMON);
    public static final DeferredBlock<Block> REACTOR_GLASS = block("reactor_glass", p -> new com.powerfactor.multiblock.ConnectedGlassBlock(
            com.powerfactor.multiblock.Connected.Style.glass("fission", () -> ModFission.REACTOR_CONTROLLER.get()), p),
            () -> hull().sound(SoundType.GLASS).noOcclusion().isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false), Rarity.UNCOMMON);
    public static final DeferredBlock<ReactorPartBlock> REACTOR_PORT = block("reactor_port", p -> new ReactorPartBlock(false, com.powerfactor.multiblock.Connected.Style.module("fission"), p), ModFission::hull, Rarity.UNCOMMON);
    public static final DeferredBlock<ReactorPartBlock> LOGIC_ADAPTER = block("reactor_logic_adapter", p -> new ReactorPartBlock(true, com.powerfactor.multiblock.Connected.Style.module("fission"), p), ModFission::hull,
            Rarity.UNCOMMON);
    public static final DeferredBlock<Block> CONTROL_ROD_DRIVE = block("control_rod_drive", Block::new, () -> hull().noOcclusion(), Rarity.UNCOMMON);
    public static final DeferredBlock<Block> FUEL_ASSEMBLY = block("fuel_assembly", com.powerfactor.fission.FuelAssemblyBlock::new, () -> hull().noOcclusion(), Rarity.UNCOMMON);
    public static final DeferredBlock<Block> GRAPHITE = block("graphite_moderator", Block::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(3F, 6F).sound(SoundType.STONE).requiresCorrectToolForDrops(), Rarity.COMMON);
    public static final DeferredBlock<QuantumMachineBlock> REACTOR_CONTROLLER = ModQuantum.machine("reactor_controller",
            p -> new QuantumMachineBlock(() -> ModFission.REACTOR_CONTROLLER_BE.get(), p), 10, Rarity.RARE);
    public static final DeferredBlock<CoriumBlock> CORIUM = ModBlocks.BLOCKS.registerBlock("corium", CoriumBlock::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(50F, 1200F).lightLevel(s -> 15).randomTicks().noLootTable());
    public static final DeferredBlock<QuantumMachineBlock> CENTRIFUGE = ModQuantum.machine("isotope_centrifuge",
            p -> new QuantumMachineBlock(() -> ModFission.CENTRIFUGE_BE.get(), p), 8, Rarity.UNCOMMON);

    public static final DeferredBlock<com.powerfactor.multiblock.MultiblockControllerBlock> HP_TURBINE = block("high_pressure_turbine",
            p -> new com.powerfactor.multiblock.MultiblockControllerBlock(() -> ModFission.HP_TURBINE_BE.get(), p), () -> hull().noOcclusion(), Rarity.RARE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.powerfactor.steam.HighPressureTurbineBlockEntity>> HP_TURBINE_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("high_pressure_turbine", () -> new BlockEntityType<>(com.powerfactor.steam.HighPressureTurbineBlockEntity::new,
                    HP_TURBINE.get()));
    /** Steam generator for loop coolants (heavy water, sodium, molten salt). */
    public static final DeferredBlock<QuantumMachineBlock> HEAT_EXCHANGER = ModQuantum.machine("heat_exchanger",
            p -> new QuantumMachineBlock(() -> ModFission.HEAT_EXCHANGER_BE.get(), p), 7, Rarity.UNCOMMON);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HeatExchangerBlockEntity>> HEAT_EXCHANGER_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("heat_exchanger", () -> new BlockEntityType<>(HeatExchangerBlockEntity::new, HEAT_EXCHANGER.get()));
    public static final DeferredItem<RadiationMeterItem> GEIGER_COUNTER = ModItems.ITEMS.registerItem("geiger_counter",
            p -> new RadiationMeterItem(false, p.stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<RadiationMeterItem> DOSIMETER = ModItems.ITEMS.registerItem("dosimeter",
            p -> new RadiationMeterItem(true, p.stacksTo(1).rarity(Rarity.UNCOMMON)));
    /** Soda ash from burnt kelp: reduced with coal it gives liquid sodium, and it fluxes molten salt. */
    public static final DeferredItem<Item> SODA_ASH = item("soda_ash", Rarity.COMMON);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReactorControllerBlockEntity>> REACTOR_CONTROLLER_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("reactor_controller", () -> new BlockEntityType<>(ReactorControllerBlockEntity::new, REACTOR_CONTROLLER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReactorPartBlockEntity>> REACTOR_PART_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("reactor_part", () -> new BlockEntityType<>(ReactorPartBlockEntity::new, REACTOR_PORT.get(), LOGIC_ADAPTER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IsotopeCentrifugeBlockEntity>> CENTRIFUGE_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("isotope_centrifuge", () -> new BlockEntityType<>(IsotopeCentrifugeBlockEntity::new, CENTRIFUGE.get()));
    public static final DeferredHolder<MenuType<?>, MenuType<ReactorMenu>> REACTOR_MENU = ModMenus.MENUS.register("reactor_controller",
            () -> IMenuTypeExtension.create(ReactorMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<IsotopeCentrifugeMenu>> CENTRIFUGE_MENU = ModMenus.MENUS.register("isotope_centrifuge",
            () -> IMenuTypeExtension.create(IsotopeCentrifugeMenu::new));

    static {
        for (var i : new DeferredItem<?>[] {FUEL_ROD, MOX_FUEL_ROD, SPENT_FUEL_ROD, SPENT_MOX_ROD, HAZMAT_HELMET, HAZMAT_SUIT, HAZMAT_LEGGINGS, HAZMAT_BOOTS,
                GEIGER_COUNTER, DOSIMETER}) {
            ModStorage.TAB.add(i);
        }
    }

    public static void init() {}

    private ModFission() {}
}
