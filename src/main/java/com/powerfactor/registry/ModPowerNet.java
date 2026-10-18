package com.powerfactor.registry;

import com.powerfactor.powernet.ConfiguratorItem;
import com.powerfactor.powernet.PowerNetMenu;
import com.powerfactor.powernet.PowerNodeBlock;
import com.powerfactor.powernet.PowerNodeBlockEntity;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/** Wireless power networks: Plug, Point, three Cells, the Controller and the Net Configurator. */
public final class ModPowerNet {
    private static DeferredBlock<PowerNodeBlock> node(String id, PowerNodeBlockEntity.Kind kind, Rarity rarity) {
        boolean small = kind == PowerNodeBlockEntity.Kind.PLUG || kind == PowerNodeBlockEntity.Kind.POINT;
        DeferredBlock<PowerNodeBlock> b = ModBlocks.BLOCKS.registerBlock(id, p -> new PowerNodeBlock(kind, p), () -> {
            var p = BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(3F, 1200F).sound(SoundType.METAL)
                    .requiresCorrectToolForDrops().lightLevel(s -> 7);
            return small ? p.noOcclusion() : p;
        });
        ModStorage.TAB.add(ModItems.ITEMS.registerSimpleBlockItem(b, p -> p.rarity(rarity)));
        return b;
    }

    public static final DeferredBlock<PowerNodeBlock> PLUG = node("power_plug", PowerNodeBlockEntity.Kind.PLUG, Rarity.UNCOMMON);
    public static final DeferredBlock<PowerNodeBlock> POINT = node("power_point", PowerNodeBlockEntity.Kind.POINT, Rarity.UNCOMMON);
    public static final DeferredBlock<PowerNodeBlock> CELL = node("power_cell", PowerNodeBlockEntity.Kind.CELL, Rarity.UNCOMMON);
    public static final DeferredBlock<PowerNodeBlock> DENSE_CELL = node("dense_power_cell", PowerNodeBlockEntity.Kind.DENSE_CELL, Rarity.RARE);
    public static final DeferredBlock<PowerNodeBlock> QUANTUM_CELL = node("quantum_power_cell", PowerNodeBlockEntity.Kind.QUANTUM_CELL, Rarity.EPIC);
    public static final DeferredBlock<PowerNodeBlock> CONTROLLER = node("power_net_controller", PowerNodeBlockEntity.Kind.CONTROLLER, Rarity.RARE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PowerNodeBlockEntity>> NODE_BE = ModBlockEntities.BLOCK_ENTITIES.register("power_node",
            () -> new BlockEntityType<>(PowerNodeBlockEntity::new, PLUG.get(), POINT.get(), CELL.get(), DENSE_CELL.get(), QUANTUM_CELL.get(), CONTROLLER.get()));
    public static final DeferredHolder<MenuType<?>, MenuType<PowerNetMenu>> MENU = ModMenus.MENUS.register("power_net",
            () -> IMenuTypeExtension.create(PowerNetMenu::new));
    public static final DeferredItem<ConfiguratorItem> CONFIGURATOR = ModItems.ITEMS.registerItem("net_configurator", p -> new ConfiguratorItem(p.stacksTo(1)));

    static {
        ModStorage.TAB.add(CONFIGURATOR);
    }

    public static void init() {}

    private ModPowerNet() {}
}
