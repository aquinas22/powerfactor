package com.powerfactor.registry;

import com.powerfactor.laser.LaserNodeBlock;
import com.powerfactor.laser.LaserNodeBlockEntity;
import com.powerfactor.laser.LaserNodeMenu;
import com.powerfactor.laser.LaserWrenchItem;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/** Laser transport: nodes, the wrench that links them, and the lens they're built around. */
public final class ModLaser {
    public static final DeferredBlock<LaserNodeBlock> LASER_NODE = ModBlocks.BLOCKS.registerBlock("laser_node", LaserNodeBlock::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5F, 6F).sound(SoundType.COPPER).noOcclusion()
                    .lightLevel(s -> 6));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaserNodeBlockEntity>> LASER_NODE_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("laser_node", () -> new BlockEntityType<>(LaserNodeBlockEntity::new, LASER_NODE.get()));
    public static final DeferredHolder<MenuType<?>, MenuType<LaserNodeMenu>> LASER_NODE_MENU = ModMenus.MENUS.register("laser_node",
            () -> IMenuTypeExtension.create(LaserNodeMenu::new));
    public static final DeferredItem<LaserWrenchItem> LASER_WRENCH = ModItems.ITEMS.registerItem("laser_wrench", LaserWrenchItem::new);
    public static final DeferredItem<Item> PHOTONIC_LENS = ModItems.ITEMS.registerItem("photonic_lens", p -> new Item(p.rarity(Rarity.UNCOMMON)));

    static {
        ModStorage.TAB.add(ModItems.ITEMS.registerSimpleBlockItem(LASER_NODE, p -> p.rarity(Rarity.UNCOMMON)));
        ModStorage.TAB.add(LASER_WRENCH);
        ModStorage.TAB.add(PHOTONIC_LENS);
    }

    public static void init() {}

    private ModLaser() {}
}
