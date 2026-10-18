package com.powerfactor.registry;

import com.powerfactor.charging.WirelessChargerBlock;
import com.powerfactor.charging.WirelessChargerBlockEntity;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Wireless and Dimensional Wireless Chargers. */
public final class ModCharging {
    public static final DeferredBlock<WirelessChargerBlock> WIRELESS_CHARGER = ModQuantum.machine("wireless_charger",
            p -> new WirelessChargerBlock(() -> ModCharging.WIRELESS_CHARGER_BE.get(), p), 10, Rarity.RARE);
    public static final DeferredBlock<WirelessChargerBlock> DIMENSIONAL_CHARGER = ModQuantum.machine("dimensional_charger",
            p -> new WirelessChargerBlock(() -> ModCharging.DIMENSIONAL_CHARGER_BE.get(), p), 14, Rarity.EPIC);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WirelessChargerBlockEntity>> WIRELESS_CHARGER_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("wireless_charger", () -> new BlockEntityType<>(WirelessChargerBlockEntity::new, WIRELESS_CHARGER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WirelessChargerBlockEntity>> DIMENSIONAL_CHARGER_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("dimensional_charger", () -> new BlockEntityType<>(WirelessChargerBlockEntity::new, DIMENSIONAL_CHARGER.get()));

    public static void init() {}

    private ModCharging() {}
}
