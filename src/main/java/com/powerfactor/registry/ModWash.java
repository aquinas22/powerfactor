package com.powerfactor.registry;

import com.powerfactor.chem.OreMetal;
import com.powerfactor.multiblock.CasingBlock;
import com.powerfactor.multiblock.MultiblockControllerBlock;
import com.powerfactor.wash.WashPlantBlockEntity;
import com.powerfactor.wash.WashPlantMenu;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.inventory.MenuType;
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

/** The Wash Plant: a placer-mine wash line (trommel, sluice, shaker table) for 4x ore. */
public final class ModWash {
    public static final Map<OreMetal, DeferredItem<Item>> CONCENTRATES = new EnumMap<>(OreMetal.class);

    static {
        for (OreMetal m : OreMetal.values()) CONCENTRATES.put(m, ModItems.ITEMS.registerSimpleItem(m.id() + "_concentrate"));
    }

    public static final DeferredBlock<MultiblockControllerBlock> WASH_PLANT = ModQuantum.machine("wash_plant",
            p -> new MultiblockControllerBlock(() -> ModWash.WASH_PLANT_BE.get(), p), 6, Rarity.RARE);
    public static final DeferredBlock<CasingBlock> WASH_FRAME = casing("wash_plant_frame", Block.box(3, 0, 3, 13, 16, 13));
    public static final DeferredBlock<CasingBlock> SLUICE_BOX = casing("sluice_box", Block.box(1, 0, 0, 15, 9, 16));
    public static final DeferredBlock<CasingBlock> TROMMEL = casing("trommel", Block.box(0, 0, 0, 16, 16, 16));
    public static final DeferredBlock<CasingBlock> FEED_HOPPER = casing("feed_hopper", Block.box(1, 0, 1, 15, 16, 15));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WashPlantBlockEntity>> WASH_PLANT_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("wash_plant", () -> new BlockEntityType<>(WashPlantBlockEntity::new, WASH_PLANT.get()));
    public static final DeferredHolder<MenuType<?>, MenuType<WashPlantMenu>> WASH_PLANT_MENU = ModMenus.MENUS.register("wash_plant",
            () -> IMenuTypeExtension.create(WashPlantMenu::new));

    private static DeferredBlock<CasingBlock> casing(String id, VoxelShape shape) {
        DeferredBlock<CasingBlock> b = ModBlocks.BLOCKS.registerBlock(id, p -> new CasingBlock(shape, p),
                () -> BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4F, 10F).sound(SoundType.METAL).requiresCorrectToolForDrops()
                        .noOcclusion());
        ModStorage.TAB.add(ModItems.ITEMS.registerSimpleBlockItem(b, p -> p.rarity(Rarity.UNCOMMON)));
        return b;
    }

    static {
        CONCENTRATES.values().forEach(ModStorage.TAB::add);
    }

    public static void init() {}

    private ModWash() {}
}
