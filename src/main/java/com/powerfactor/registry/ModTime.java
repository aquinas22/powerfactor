package com.powerfactor.registry;

import com.powerfactor.dilation.TimeFieldBlockEntity;
import com.powerfactor.dilation.TimeFieldMenu;
import com.powerfactor.quantum.QuantumMachineBlock;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;

/** The Time Dilation Field. */
public final class ModTime {
    public static final DeferredBlock<QuantumMachineBlock> TIME_FIELD = ModQuantum.machine("time_dilation_field",
            p -> new QuantumMachineBlock(() -> ModTime.TIME_FIELD_BE.get(), p), 12, Rarity.EPIC);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TimeFieldBlockEntity>> TIME_FIELD_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("time_dilation_field", () -> new BlockEntityType<>(TimeFieldBlockEntity::new, TIME_FIELD.get()));
    public static final DeferredHolder<MenuType<?>, MenuType<TimeFieldMenu>> TIME_FIELD_MENU = ModMenus.MENUS.register("time_dilation_field",
            () -> IMenuTypeExtension.create(TimeFieldMenu::new));

    public static void init() {}

    private ModTime() {}
}
