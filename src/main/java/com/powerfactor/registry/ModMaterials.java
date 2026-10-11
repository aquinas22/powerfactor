package com.powerfactor.registry;

import com.powerfactor.PowerFactor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Metals and minerals registered in bulk: every metal gets an ingot, nugget, dust, plate and
 * storage block; mined metals also get stone and deepslate ores, a raw item and a raw block.
 * Zinc and brass predate this table and live in ModItems/ModBlocks, but get plates here.
 */
public final class ModMaterials {
    public record Metal(String name, boolean mined, MapColor color) {}

    public static final List<Metal> METALS = List.of(
            new Metal("tin", true, MapColor.COLOR_LIGHT_GRAY),
            new Metal("lead", true, MapColor.COLOR_BLUE),
            new Metal("silver", true, MapColor.SNOW),
            new Metal("aluminum", false, MapColor.SNOW),
            new Metal("steel", false, MapColor.COLOR_GRAY),
            new Metal("bronze", false, MapColor.COLOR_ORANGE),
            new Metal("electrum", false, MapColor.GOLD));

    /** Plates for metals registered elsewhere. */
    public static final List<String> EXTRA_PLATES = List.of("iron", "copper", "gold", "zinc", "brass");

    public static final Map<String, DeferredItem<Item>> INGOTS = new LinkedHashMap<>();
    public static final Map<String, DeferredItem<Item>> NUGGETS = new LinkedHashMap<>();
    public static final Map<String, DeferredItem<Item>> DUSTS = new LinkedHashMap<>();
    public static final Map<String, DeferredItem<Item>> PLATES = new LinkedHashMap<>();
    public static final Map<String, DeferredBlock<Block>> STORAGE = new LinkedHashMap<>();
    public static final Map<String, DeferredBlock<DropExperienceBlock>> ORES = new LinkedHashMap<>();
    public static final Map<String, DeferredBlock<DropExperienceBlock>> DEEPSLATE_ORES = new LinkedHashMap<>();
    public static final Map<String, DeferredItem<Item>> RAW = new LinkedHashMap<>();
    public static final Map<String, DeferredBlock<Block>> RAW_BLOCKS = new LinkedHashMap<>();

    // Bauxite is aluminum ore; it has no "raw aluminum".
    public static final String BAUXITE = "bauxite";

    public static void register() {
        for (Metal m : METALS) {
            if (m.mined()) {
                ore(m.name(), m.name());
            }
        }
        ore(BAUXITE, BAUXITE);
        for (Metal m : METALS) {
            INGOTS.put(m.name(), ModItems.ITEMS.registerSimpleItem(m.name() + "_ingot"));
            NUGGETS.put(m.name(), ModItems.ITEMS.registerSimpleItem(m.name() + "_nugget"));
            DUSTS.put(m.name(), ModItems.ITEMS.registerSimpleItem(m.name() + "_dust"));
            PLATES.put(m.name(), ModItems.ITEMS.registerSimpleItem(m.name() + "_plate"));
            DeferredBlock<Block> block = ModBlocks.BLOCKS.registerSimpleBlock(m.name() + "_block",
                    () -> BlockBehaviour.Properties.of().mapColor(m.color()).instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                            .requiresCorrectToolForDrops().strength(5.0F, 6.0F).sound(SoundType.METAL));
            STORAGE.put(m.name(), block);
            ModItems.ITEMS.registerSimpleBlockItem(block);
        }
        for (String name : EXTRA_PLATES) {
            PLATES.put(name, ModItems.ITEMS.registerSimpleItem(name + "_plate"));
        }
    }

    private static void ore(String name, String rawName) {
        DeferredBlock<DropExperienceBlock> stone = ModBlocks.BLOCKS.registerBlock(name + "_ore", p -> new DropExperienceBlock(ConstantInt.of(0), p),
                () -> BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM)
                        .requiresCorrectToolForDrops().strength(3.0F, 3.0F));
        DeferredBlock<DropExperienceBlock> deep = ModBlocks.BLOCKS.registerBlock("deepslate_" + name + "_ore",
                p -> new DropExperienceBlock(ConstantInt.of(0), p),
                () -> BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).instrument(NoteBlockInstrument.BASEDRUM)
                        .requiresCorrectToolForDrops().strength(4.5F, 3.0F).sound(SoundType.DEEPSLATE));
        ORES.put(name, stone);
        DEEPSLATE_ORES.put(name, deep);
        ModItems.ITEMS.registerSimpleBlockItem(stone);
        ModItems.ITEMS.registerSimpleBlockItem(deep);
        String raw = name.equals(BAUXITE) ? BAUXITE : "raw_" + rawName;
        RAW.put(name, ModItems.ITEMS.registerSimpleItem(raw));
        DeferredBlock<Block> rawBlock = ModBlocks.BLOCKS.registerSimpleBlock(name.equals(BAUXITE) ? "bauxite_block" : "raw_" + rawName + "_block",
                () -> BlockBehaviour.Properties.of().mapColor(MapColor.RAW_IRON).instrument(NoteBlockInstrument.BASEDRUM)
                        .requiresCorrectToolForDrops().strength(5.0F, 6.0F));
        RAW_BLOCKS.put(name, rawBlock);
        ModItems.ITEMS.registerSimpleBlockItem(rawBlock);
    }

    private ModMaterials() {}
}
