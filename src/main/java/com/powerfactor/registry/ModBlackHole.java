package com.powerfactor.registry;

import com.powerfactor.blackhole.BlackHoleBlockEntity;
import com.powerfactor.blackhole.BlackHoleMenu;
import com.powerfactor.multiblock.CasingBlock;
import com.powerfactor.multiblock.MultiblockControllerBlock;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;

/** The Black Hole Power Plant: its containment sphere and controller. */
public final class ModBlackHole {
    private static DeferredBlock<CasingBlock> casing(String id, com.powerfactor.multiblock.Connected.Style style, int light) {
        DeferredBlock<CasingBlock> b = ModBlocks.BLOCKS.registerBlock(id, p -> new com.powerfactor.multiblock.ConnectedCasingBlock(style, Shapes.block(), p),
                () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(10F, 3600F).sound(SoundType.NETHERITE_BLOCK)
                        .requiresCorrectToolForDrops().noOcclusion().lightLevel(s -> s.getValue(CasingBlock.FORMED) ? light : light / 3));
        ModStorage.TAB.add(ModItems.ITEMS.registerSimpleBlockItem(b, p -> p.rarity(Rarity.EPIC)));
        return b;
    }

    private static com.powerfactor.multiblock.Connected.Style host() {
        return com.powerfactor.multiblock.Connected.Style.host("black_hole", () -> ModBlackHole.CONTROLLER.get());
    }

    public static final DeferredBlock<CasingBlock> CASING = casing("containment_casing", host(), 0);
    public static final DeferredBlock<CasingBlock> COIL = casing("containment_coil", com.powerfactor.multiblock.Connected.Style.panel("black_hole", "coil", () -> ModBlackHole.CONTROLLER.get()), 10);
    public static final DeferredBlock<CasingBlock> GLASS = casing("event_horizon_glass", com.powerfactor.multiblock.Connected.Style.glass("black_hole", () -> ModBlackHole.CONTROLLER.get()), 4);
    public static final DeferredBlock<CasingBlock> INJECTOR = casing("accretion_injector", com.powerfactor.multiblock.Connected.Style.module("black_hole"), 8);
    public static final DeferredBlock<CasingBlock> COLLECTOR = casing("hawking_collector", com.powerfactor.multiblock.Connected.Style.module("black_hole"), 12);
    public static final DeferredBlock<MultiblockControllerBlock> CONTROLLER = ModQuantum.machine("black_hole_controller",
            p -> new MultiblockControllerBlock(() -> ModBlackHole.CONTROLLER_BE.get(), p), 15, Rarity.EPIC);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlackHoleBlockEntity>> CONTROLLER_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("black_hole_controller", () -> new BlockEntityType<>(BlackHoleBlockEntity::new, CONTROLLER.get()));
    public static final DeferredHolder<MenuType<?>, MenuType<BlackHoleMenu>> MENU = ModMenus.MENUS.register("black_hole_controller",
            () -> IMenuTypeExtension.create(BlackHoleMenu::new));

    public static void init() {}

    private ModBlackHole() {}
}
