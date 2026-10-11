package com.powerfactor.registry;

import com.powerfactor.PowerFactor;
import com.powerfactor.item.DrillItem;
import com.powerfactor.item.TreeTapItem;
import com.powerfactor.upgrade.UpgradeItem;
import com.powerfactor.upgrade.UpgradeType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registration order is creative tab order. */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PowerFactor.MODID);

    // ---------------------------------------------------------------- machines and wiring
    public static final DeferredItem<BlockItem> COAL_TURBINE = ITEMS.registerSimpleBlockItem("coal_turbine", ModBlocks.COAL_TURBINE);
    public static final DeferredItem<BlockItem> REDSTONE_ENGINE = ITEMS.registerSimpleBlockItem("redstone_engine", ModBlocks.REDSTONE_ENGINE);
    public static final DeferredItem<BlockItem> STIRLING_ENGINE = ITEMS.registerSimpleBlockItem("stirling_engine", ModBlocks.STIRLING_ENGINE);
    public static final DeferredItem<BlockItem> CRUSHER = ITEMS.registerSimpleBlockItem("crusher", ModBlocks.CRUSHER);
    public static final DeferredItem<BlockItem> ELECTRIC_FURNACE = ITEMS.registerSimpleBlockItem("electric_furnace", ModBlocks.ELECTRIC_FURNACE);
    static {
        for (var block : ModBlocks.RECIPE_MACHINES.values()) {
            ITEMS.registerSimpleBlockItem(block);
        }
    }

    public static final DeferredItem<BlockItem> FLUID_TANK = ITEMS.registerSimpleBlockItem("fluid_tank", ModBlocks.FLUID_TANK);
    public static final DeferredItem<BlockItem> ELECTRIC_PUMP = ITEMS.registerSimpleBlockItem("electric_pump", ModBlocks.ELECTRIC_PUMP);
    public static final DeferredItem<BlockItem> FLUID_PIPE = ITEMS.registerSimpleBlockItem("fluid_pipe", ModBlocks.FLUID_PIPE);
    public static final DeferredItem<BlockItem> ITEM_PIPE = ITEMS.registerSimpleBlockItem("item_pipe", ModBlocks.ITEM_PIPE);
    public static final DeferredItem<BlockItem> CHARGER = ITEMS.registerSimpleBlockItem("charger", ModBlocks.CHARGER);
    public static final DeferredItem<BlockItem> CAPACITOR_BANK = ITEMS.registerSimpleBlockItem("capacitor_bank", ModBlocks.CAPACITOR_BANK);
    public static final DeferredItem<BlockItem> AUTO_CAPACITOR_BANK = ITEMS.registerSimpleBlockItem("auto_capacitor_bank", ModBlocks.AUTO_CAPACITOR_BANK);
    public static final DeferredItem<BlockItem> COPPER_WIRE = ITEMS.registerSimpleBlockItem("copper_wire", ModBlocks.COPPER_WIRE);
    public static final DeferredItem<BlockItem> SCADA_TERMINAL = ITEMS.registerSimpleBlockItem("scada_terminal", ModBlocks.SCADA_TERMINAL);
    public static final DeferredItem<BlockItem> FIBER_OPTIC_CABLE = ITEMS.registerSimpleBlockItem("fiber_optic_cable", ModBlocks.FIBER_OPTIC_CABLE);

    // ---------------------------------------------------------------- tools
    public static final DeferredItem<DrillItem> ELECTRIC_DRILL = ITEMS.registerItem("electric_drill", DrillItem::new,
            p -> DrillItem.properties(p).rarity(Rarity.UNCOMMON));

    // ---------------------------------------------------------------- upgrade cards
    public static final DeferredItem<UpgradeItem> SPEED_UPGRADE = upgrade("speed_upgrade", UpgradeType.SPEED);
    public static final DeferredItem<UpgradeItem> EFFICIENCY_UPGRADE = upgrade("efficiency_upgrade", UpgradeType.EFFICIENCY);
    public static final DeferredItem<UpgradeItem> CAPACITOR_UPGRADE = upgrade("capacitor_upgrade", UpgradeType.CAPACITOR);
    public static final DeferredItem<UpgradeItem> VFD_UPGRADE = upgrade("vfd_upgrade", UpgradeType.VFD);
    public static final DeferredItem<UpgradeItem> SURGE_UPGRADE = upgrade("surge_upgrade", UpgradeType.SURGE);
    public static final DeferredItem<UpgradeItem> CAPACITY_UPGRADE = upgrade("capacity_upgrade", UpgradeType.CAPACITY);

    // ---------------------------------------------------------------- components
    public static final DeferredItem<Item> COPPER_COIL = ITEMS.registerSimpleItem("copper_coil");
    public static final DeferredItem<Item> ELECTRIC_MOTOR = ITEMS.registerSimpleItem("electric_motor");
    public static final DeferredItem<Item> BRASS_GEAR = ITEMS.registerSimpleItem("brass_gear");
    public static final DeferredItem<Item> IRON_GEAR = ITEMS.registerSimpleItem("iron_gear");
    public static final DeferredItem<Item> TURBINE_BLADE = ITEMS.registerSimpleItem("turbine_blade");
    public static final DeferredItem<Item> CAPACITOR = ITEMS.registerSimpleItem("capacitor");
    public static final DeferredItem<Item> OPTICAL_FIBER = ITEMS.registerSimpleItem("optical_fiber");
    public static final DeferredItem<Item> CONTROL_CIRCUIT = ITEMS.registerSimpleItem("control_circuit");
    public static final DeferredItem<Item> DRILL_HEAD = ITEMS.registerSimpleItem("drill_head");

    // ---------------------------------------------------------------- Phase B intermediates
    public static final DeferredItem<Item> GRAPHITE = ITEMS.registerSimpleItem("graphite");
    public static final DeferredItem<Item> GRAPHITE_ELECTRODE = ITEMS.registerItem("graphite_electrode", Item::new, p -> p.durability(64));
    public static final DeferredItem<Item> SILICON = ITEMS.registerSimpleItem("silicon");
    public static final DeferredItem<Item> MICA = ITEMS.registerSimpleItem("mica");
    public static final DeferredItem<Item> LATEX = ITEMS.registerSimpleItem("latex");
    public static final DeferredItem<Item> RUBBER = ITEMS.registerSimpleItem("rubber");
    public static final DeferredItem<TreeTapItem> TREE_TAP = ITEMS.registerItem("tree_tap", TreeTapItem::new, p -> p.durability(128));
    public static final DeferredItem<Item> ADVANCED_CIRCUIT = ITEMS.registerSimpleItem("advanced_circuit");
    public static final DeferredItem<Item> STEEL_GEAR = ITEMS.registerSimpleItem("steel_gear");
    public static final DeferredItem<Item> HEATING_COIL = ITEMS.registerSimpleItem("heating_coil");
    public static final DeferredItem<Item> FINE_COPPER_WIRE = ITEMS.registerSimpleItem("fine_copper_wire");
    public static final DeferredItem<Item> FINE_SILVER_WIRE = ITEMS.registerSimpleItem("fine_silver_wire");
    public static final DeferredItem<Item> FINE_ALUMINUM_WIRE = ITEMS.registerSimpleItem("fine_aluminum_wire");
    public static final DeferredItem<BlockItem> RUBBER_LOG = ITEMS.registerSimpleBlockItem("rubber_log", ModBlocks.RUBBER_LOG);
    public static final DeferredItem<BlockItem> RUBBER_PLANKS = ITEMS.registerSimpleBlockItem("rubber_planks", ModBlocks.RUBBER_PLANKS);
    public static final DeferredItem<BlockItem> RUBBER_LEAVES = ITEMS.registerSimpleBlockItem("rubber_leaves", ModBlocks.RUBBER_LEAVES);
    public static final DeferredItem<BlockItem> RUBBER_SAPLING = ITEMS.registerSimpleBlockItem("rubber_sapling", ModBlocks.RUBBER_SAPLING);

    // ---------------------------------------------------------------- materials
    public static final DeferredItem<BlockItem> ZINC_ORE = ITEMS.registerSimpleBlockItem("zinc_ore", ModBlocks.ZINC_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_ZINC_ORE = ITEMS.registerSimpleBlockItem("deepslate_zinc_ore", ModBlocks.DEEPSLATE_ZINC_ORE);
    public static final DeferredItem<BlockItem> RAW_ZINC_BLOCK = ITEMS.registerSimpleBlockItem("raw_zinc_block", ModBlocks.RAW_ZINC_BLOCK);
    public static final DeferredItem<BlockItem> ZINC_BLOCK = ITEMS.registerSimpleBlockItem("zinc_block", ModBlocks.ZINC_BLOCK);
    public static final DeferredItem<BlockItem> BRASS_BLOCK = ITEMS.registerSimpleBlockItem("brass_block", ModBlocks.BRASS_BLOCK);
    public static final DeferredItem<Item> RAW_ZINC = ITEMS.registerSimpleItem("raw_zinc");
    public static final DeferredItem<Item> ZINC_INGOT = ITEMS.registerSimpleItem("zinc_ingot");
    public static final DeferredItem<Item> ZINC_NUGGET = ITEMS.registerSimpleItem("zinc_nugget");
    public static final DeferredItem<Item> BRASS_INGOT = ITEMS.registerSimpleItem("brass_ingot");
    public static final DeferredItem<Item> BRASS_NUGGET = ITEMS.registerSimpleItem("brass_nugget");
    public static final DeferredItem<Item> IRON_DUST = ITEMS.registerSimpleItem("iron_dust");
    public static final DeferredItem<Item> GOLD_DUST = ITEMS.registerSimpleItem("gold_dust");
    public static final DeferredItem<Item> COPPER_DUST = ITEMS.registerSimpleItem("copper_dust");
    public static final DeferredItem<Item> ZINC_DUST = ITEMS.registerSimpleItem("zinc_dust");
    public static final DeferredItem<Item> BRASS_DUST = ITEMS.registerSimpleItem("brass_dust");
    public static final DeferredItem<Item> COAL_DUST = ITEMS.registerSimpleItem("coal_dust");

    private static DeferredItem<UpgradeItem> upgrade(String name, UpgradeType type) {
        return ITEMS.registerItem(name, p -> new UpgradeItem(type, p));
    }

    private ModItems() {}
}
