package com.powerfactor.registry;

import com.powerfactor.redstone.GateBlock;
import com.powerfactor.redstone.GateBlockEntity;
import com.powerfactor.redstone.IllumarLampBlock;
import com.powerfactor.redstone.ScrewdriverItem;
import com.powerfactor.redstone.WireBlock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/** Project-Red-style redstone: red alloy, insulated and bundled wire, sixteen gates, Illumar lamps, the screwdriver. */
public final class ModRedstone {
    private static BlockBehaviour.Properties wire() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.NONE).instabreak().noCollision().sound(SoundType.WOOL).pushReaction(PushReaction.DESTROY)
                .noOcclusion();
    }

    private static <B extends net.minecraft.world.level.block.Block> DeferredBlock<B> block(String id, java.util.function.Function<BlockBehaviour.Properties, B> f,
            java.util.function.Supplier<BlockBehaviour.Properties> p) {
        DeferredBlock<B> b = ModBlocks.BLOCKS.registerBlock(id, f, p);
        TAB.add(ModItems.ITEMS.registerSimpleBlockItem(b));
        return b;
    }

    /** Everything here, in creative tab order. */
    public static final List<DeferredItem<?>> TAB = new ArrayList<>();

    public static final DeferredItem<Item> RED_ALLOY_INGOT = ModItems.ITEMS.registerSimpleItem("red_alloy_ingot");
    public static final DeferredItem<ScrewdriverItem> SCREWDRIVER = ModItems.ITEMS.registerItem("screwdriver", p -> new ScrewdriverItem(p.stacksTo(1)));
    public static final DeferredBlock<WireBlock> RED_ALLOY_WIRE = block("red_alloy_wire", p -> new WireBlock(WireBlock.Kind.ALLOY, -1, p), ModRedstone::wire);
    public static final Map<DyeColor, DeferredBlock<WireBlock>> INSULATED = new EnumMap<>(DyeColor.class);
    public static final DeferredBlock<WireBlock> BUNDLED_CABLE;
    public static final Map<GateBlock.Type, DeferredBlock<GateBlock>> GATES = new EnumMap<>(GateBlock.Type.class);
    public static final Map<DyeColor, DeferredBlock<IllumarLampBlock>> LAMPS = new EnumMap<>(DyeColor.class);
    public static final Map<DyeColor, DeferredBlock<IllumarLampBlock>> INVERTED_LAMPS = new EnumMap<>(DyeColor.class);

    static {
        TAB.add(RED_ALLOY_INGOT);
        TAB.add(SCREWDRIVER);
        for (DyeColor c : DyeColor.values()) {
            INSULATED.put(c, block(c.getName() + "_insulated_wire", p -> new WireBlock(WireBlock.Kind.INSULATED, c.getId(), p), ModRedstone::wire));
        }
        BUNDLED_CABLE = block("bundled_cable", p -> new WireBlock(WireBlock.Kind.BUNDLED, -1, p), ModRedstone::wire);
        for (GateBlock.Type t : GateBlock.Type.values()) {
            DeferredBlock<GateBlock> gate = ModBlocks.BLOCKS.registerBlock(t.id() + "_gate", p -> new GateBlock(t, p),
                    () -> BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instabreak().sound(SoundType.STONE).pushReaction(PushReaction.DESTROY)
                            .noOcclusion());
            TAB.add(ModItems.ITEMS.registerItem(t.id() + "_gate", p -> new com.powerfactor.redstone.GateItem(gate.get(), p.useBlockDescriptionPrefix())));
            GATES.put(t, gate);
        }
        for (boolean inv : new boolean[] {false, true}) {
            for (DyeColor c : DyeColor.values()) {
                var b = block((inv ? "inverted_" : "") + c.getName() + "_illumar_lamp", p -> new IllumarLampBlock(c.getId(), inv, p),
                        () -> BlockBehaviour.Properties.of().mapColor(c.getMapColor()).strength(0.6F).sound(SoundType.GLASS)
                                .lightLevel(s -> s.getValue(IllumarLampBlock.LIT) ? 15 : 0).noOcclusion());
                (inv ? INVERTED_LAMPS : LAMPS).put(c, b);
            }
        }
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GateBlockEntity>> GATE_BE = ModBlockEntities.BLOCK_ENTITIES.register("logic_gate",
            () -> new BlockEntityType<>(GateBlockEntity::new, GATES.values().stream().map(DeferredBlock::get).toArray(net.minecraft.world.level.block.Block[]::new)));

    public static void init() {}

    private ModRedstone() {}
}
