package com.powerfactor.registry;

import com.powerfactor.PowerFactor;
import com.powerfactor.item.DrillItem;
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
