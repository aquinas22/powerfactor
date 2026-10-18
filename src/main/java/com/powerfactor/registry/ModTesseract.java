package com.powerfactor.registry;

import com.powerfactor.tesseract.TesseractBlock;
import com.powerfactor.tesseract.TesseractBlockEntity;
import com.powerfactor.tesseract.TesseractMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;

/** The Tesseract: interdimensional item, fluid and energy transfer. */
public final class ModTesseract {
    public static final DeferredBlock<TesseractBlock> TESSERACT = ModBlocks.BLOCKS.registerBlock("tesseract", TesseractBlock::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(6F, 1200F).sound(SoundType.AMETHYST)
                    .requiresCorrectToolForDrops().noOcclusion().lightLevel(s -> 10));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TesseractBlockEntity>> TESSERACT_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("tesseract", () -> new BlockEntityType<>(TesseractBlockEntity::new, TESSERACT.get()));
    public static final DeferredHolder<MenuType<?>, MenuType<TesseractMenu>> TESSERACT_MENU = ModMenus.MENUS.register("tesseract",
            () -> IMenuTypeExtension.create(TesseractMenu::new));

    static {
        ModStorage.TAB.add(ModItems.ITEMS.registerSimpleBlockItem(TESSERACT, p -> p.rarity(Rarity.EPIC)));
    }

    public static void init() {}

    private ModTesseract() {}
}
