package com.powerfactor.registry;

import com.powerfactor.defense.TurretBlock;
import com.powerfactor.defense.TurretBlockEntity;
import com.powerfactor.defense.TurretMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Defence turrets: plasma and railgun. */
public final class ModDefense {
    public static final DeferredBlock<TurretBlock> PLASMA_TURRET = ModQuantum.machine("plasma_turret",
            p -> new TurretBlock(() -> ModDefense.PLASMA_TURRET_BE.get(), p), 10, Rarity.RARE);
    public static final DeferredBlock<TurretBlock> RAIL_TURRET = ModQuantum.machine("railgun_turret",
            p -> new TurretBlock(() -> ModDefense.RAIL_TURRET_BE.get(), p), 12, Rarity.EPIC);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TurretBlockEntity>> PLASMA_TURRET_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("plasma_turret", () -> new BlockEntityType<>(
                    (pos, state) -> new TurretBlockEntity(TurretBlockEntity.Tier.PLASMA, pos, state), PLASMA_TURRET.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TurretBlockEntity>> RAIL_TURRET_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("railgun_turret", () -> new BlockEntityType<>(
                    (pos, state) -> new TurretBlockEntity(TurretBlockEntity.Tier.RAIL, pos, state), RAIL_TURRET.get()));
    public static final DeferredHolder<MenuType<?>, MenuType<TurretMenu>> TURRET_MENU = ModMenus.MENUS.register("turret",
            () -> IMenuTypeExtension.create(TurretMenu::new));

    public static final DeferredBlock<com.powerfactor.quantum.QuantumMachineBlock> SHIELD_GENERATOR = ModQuantum.machine("shield_generator",
            p -> new com.powerfactor.quantum.QuantumMachineBlock(() -> ModDefense.SHIELD_GENERATOR_BE.get(), p), 12, Rarity.EPIC);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.powerfactor.defense.ShieldGeneratorBlockEntity>> SHIELD_GENERATOR_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("shield_generator", () -> new BlockEntityType<>(
                    com.powerfactor.defense.ShieldGeneratorBlockEntity::new, SHIELD_GENERATOR.get()));
    public static final DeferredHolder<MenuType<?>, MenuType<com.powerfactor.defense.ShieldGeneratorMenu>> SHIELD_MENU = ModMenus.MENUS.register(
            "shield_generator", () -> IMenuTypeExtension.create(com.powerfactor.defense.ShieldGeneratorMenu::new));

    public static void init() {}

    private ModDefense() {}
}
