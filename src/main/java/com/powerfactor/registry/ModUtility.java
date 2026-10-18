package com.powerfactor.registry;

import com.powerfactor.util.ChunkLoaderBlock;
import com.powerfactor.util.ChunkLoaderBlockEntity;
import com.powerfactor.util.MagnetItem;
import com.powerfactor.util.WirelessBlock;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/** Small conveniences: the chunk loader, wireless redstone and the item magnet. */
public final class ModUtility {
    public static final DeferredBlock<ChunkLoaderBlock> CHUNK_LOADER = ModBlocks.BLOCKS.registerBlock("chunk_loader", ChunkLoaderBlock::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(5F, 1200F).sound(SoundType.STONE).requiresCorrectToolForDrops()
                    .noOcclusion().lightLevel(s -> 7));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChunkLoaderBlockEntity>> CHUNK_LOADER_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("chunk_loader", () -> new BlockEntityType<>(ChunkLoaderBlockEntity::new, CHUNK_LOADER.get()));
    public static final DeferredBlock<WirelessBlock> WIRELESS_TRANSMITTER = ModBlocks.BLOCKS.registerBlock("wireless_transmitter",
            p -> new WirelessBlock(true, p), () -> BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5F).sound(SoundType.COPPER)
                    .noOcclusion().lightLevel(s -> s.getValue(WirelessBlock.LIT) ? 7 : 0));
    public static final DeferredBlock<WirelessBlock> WIRELESS_RECEIVER = ModBlocks.BLOCKS.registerBlock("wireless_receiver",
            p -> new WirelessBlock(false, p), () -> BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5F).sound(SoundType.COPPER)
                    .noOcclusion().lightLevel(s -> s.getValue(WirelessBlock.LIT) ? 7 : 0));
    public static final DeferredItem<MagnetItem> MAGNET = ModItems.ITEMS.registerItem("item_magnet", MagnetItem::new);
    /** Grows the reach of area machines: chunk loader, wireless charger, time dilation field. */
    public static final DeferredItem<net.minecraft.world.item.Item> RANGE_UPGRADE = ModItems.ITEMS.registerItem("range_upgrade",
            p -> new net.minecraft.world.item.Item(p.rarity(Rarity.UNCOMMON)));

    static {
        ModStorage.TAB.add(ModItems.ITEMS.registerSimpleBlockItem(CHUNK_LOADER, p -> p.rarity(Rarity.UNCOMMON)));
        ModStorage.TAB.add(ModItems.ITEMS.registerSimpleBlockItem(WIRELESS_TRANSMITTER));
        ModStorage.TAB.add(ModItems.ITEMS.registerSimpleBlockItem(WIRELESS_RECEIVER));
        ModStorage.TAB.add(MAGNET);
        ModStorage.TAB.add(RANGE_UPGRADE);
    }

    public static void init() {}

    private ModUtility() {}
}
