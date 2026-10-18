package com.powerfactor.registry;

import com.powerfactor.elevator.ElevatorBlock;
import com.powerfactor.pocketvoid.PocketVoidItem;
import com.powerfactor.pocketvoid.PocketVoidMenu;
import com.powerfactor.pocketvoid.VoidContents;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/** Bedrock fragments and what they make (Essence of Bedrock, the Pocket Void), fulgurite, and the elevator. */
public final class ModVoid {
    /** Knocked off bedrock by TNT (pocketvoid/BedrockFragments). */
    public static final DeferredItem<Item> BEDROCK_FRAGMENT = ModItems.ITEMS.registerItem("bedrock_fragment", p -> new Item(p.rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<Item> ESSENCE_OF_BEDROCK = ModItems.ITEMS.registerItem("essence_of_bedrock", p -> new Item(p.rarity(Rarity.UNCOMMON)));
    /** Glassy lightning tubes from sand struck by lightning (pocketvoid/Fulgurites). */
    public static final DeferredItem<Item> FULGURITE = ModItems.ITEMS.registerItem("fulgurite", p -> new Item(p.rarity(Rarity.UNCOMMON)));
    public static final DeferredBlock<Block> FUSED_SAND = ModBlocks.BLOCKS.registerBlock("fused_sand", Block::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(1.2F, 3F).sound(SoundType.GLASS).lightLevel(s -> 3));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<VoidContents>> CONTENTS = ModComponents.COMPONENTS.registerComponentType(
            "pocket_void", b -> b.persistent(VoidContents.CODEC).networkSynchronized(VoidContents.STREAM_CODEC));
    public static final DeferredItem<PocketVoidItem> POCKET_VOID = ModItems.ITEMS.registerItem("pocket_void",
            p -> new PocketVoidItem(2_048, p.stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<PocketVoidItem> DEEP_POCKET_VOID = ModItems.ITEMS.registerItem("deep_pocket_void",
            p -> new PocketVoidItem(65_536, p.stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredItem<Item> VOID_CORE = ModItems.ITEMS.registerItem("void_core", p -> new Item(p.rarity(Rarity.RARE)));
    public static final DeferredHolder<MenuType<?>, MenuType<PocketVoidMenu>> MENU = ModMenus.MENUS.register("pocket_void",
            () -> IMenuTypeExtension.create(PocketVoidMenu::new));

    public static final DeferredBlock<ElevatorBlock> ELEVATOR = ModBlocks.BLOCKS.registerBlock("elevator", ElevatorBlock::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).strength(0.8F).sound(SoundType.WOOL));

    static {
        ModItems.ITEMS.registerSimpleBlockItem(ELEVATOR);
        ModItems.ITEMS.registerSimpleBlockItem(FUSED_SAND);
    }

    public static void init() {}

    private ModVoid() {}
}
