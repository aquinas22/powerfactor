package com.powerfactor.registry;

import com.powerfactor.farm.FarmGantryBlockEntity;
import com.powerfactor.farm.FarmGantryMenu;
import com.powerfactor.quantum.QuantumMachineBlock;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Farming automation. */
public final class ModFarm {
    public static final DeferredBlock<QuantumMachineBlock> FARM_GANTRY = ModQuantum.machine("farm_gantry",
            p -> new QuantumMachineBlock(() -> ModFarm.FARM_GANTRY_BE.get(), p), 6, Rarity.UNCOMMON);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FarmGantryBlockEntity>> FARM_GANTRY_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("farm_gantry", () -> new BlockEntityType<>(FarmGantryBlockEntity::new, FARM_GANTRY.get()));
    public static final DeferredHolder<MenuType<?>, MenuType<FarmGantryMenu>> FARM_GANTRY_MENU = ModMenus.MENUS.register("farm_gantry",
            () -> IMenuTypeExtension.create(FarmGantryMenu::new));

    public static void init() {}

    private ModFarm() {}
}
