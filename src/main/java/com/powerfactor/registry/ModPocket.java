package com.powerfactor.registry;

import com.powerfactor.pocket.PocketBlock;
import com.powerfactor.pocket.PocketBlockEntity;
import com.powerfactor.pocket.PocketPortBlock;
import com.powerfactor.pocket.PocketWallBlock;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/** Pocket dimensions: the door, the room's shell and port, and the expander. */
public final class ModPocket {
    public static final DeferredBlock<PocketBlock> POCKET = ModBlocks.BLOCKS.registerBlock("dimensional_pocket", PocketBlock::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(4F, 1200F).sound(SoundType.AMETHYST).requiresCorrectToolForDrops()
                    .noOcclusion().lightLevel(s -> 10).pushReaction(PushReaction.BLOCK));
    public static final DeferredBlock<PocketWallBlock> POCKET_WALL = ModBlocks.BLOCKS.registerBlock("pocket_wall", PocketWallBlock::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(-1F, 3_600_000F).noLootTable().lightLevel(s -> 15)
                    .pushReaction(PushReaction.BLOCK).isValidSpawn((s, l, p, e) -> false));
    public static final DeferredBlock<PocketPortBlock> POCKET_PORT = ModBlocks.BLOCKS.registerBlock("pocket_port", PocketPortBlock::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(2F, 1200F).sound(SoundType.AMETHYST).lightLevel(s -> 8));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PocketBlockEntity>> POCKET_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("dimensional_pocket", () -> new BlockEntityType<>(PocketBlockEntity::new, POCKET.get()));
    public static final DeferredItem<Item> SPATIAL_EXPANDER = ModItems.ITEMS.registerItem("spatial_expander", p -> new Item(p.rarity(Rarity.RARE)));

    static {
        ModStorage.TAB.add(ModItems.ITEMS.registerItem("dimensional_pocket", p -> new com.powerfactor.pocket.PocketItem(POCKET.get(), p.rarity(Rarity.EPIC).useBlockDescriptionPrefix())));
        ModStorage.TAB.add(ModItems.ITEMS.registerSimpleBlockItem(POCKET_PORT, p -> p.rarity(Rarity.RARE)));
        ModStorage.TAB.add(SPATIAL_EXPANDER);
    }

    public static void init() {}

    private ModPocket() {}
}
