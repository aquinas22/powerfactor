package com.powerfactor.registry;

import com.powerfactor.PowerFactor;
import com.powerfactor.drone.DroneHubBlockEntity;
import com.powerfactor.drone.DroneHubMenu;
import com.powerfactor.drone.MiningDroneEntity;
import com.powerfactor.quantum.QuantumMachineBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/** AI mining drones: the hub and its drones. */
public final class ModDrone {
    public static final DeferredBlock<QuantumMachineBlock> HUB = ModQuantum.machine("drone_hub",
            p -> new QuantumMachineBlock(() -> ModDrone.HUB_BE.get(), p), 10, Rarity.EPIC);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DroneHubBlockEntity>> HUB_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("drone_hub", () -> new BlockEntityType<>(DroneHubBlockEntity::new, HUB.get()));
    public static final DeferredHolder<MenuType<?>, MenuType<DroneHubMenu>> HUB_MENU = ModMenus.MENUS.register("drone_hub",
            () -> IMenuTypeExtension.create(DroneHubMenu::new));
    public static final DeferredItem<Item> DRONE_ITEM = ModItems.ITEMS.registerItem("mining_drone", p -> new Item(p.rarity(Rarity.RARE).stacksTo(4)));
    public static final DeferredHolder<EntityType<?>, EntityType<MiningDroneEntity>> DRONE = ModEntities.ENTITIES.register("mining_drone",
            () -> EntityType.Builder.<MiningDroneEntity>of(MiningDroneEntity::new, MobCategory.MISC).sized(0.6F, 0.3F).clientTrackingRange(8).updateInterval(2)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, PowerFactor.id("mining_drone"))));

    static {
        ModStorage.TAB.add(DRONE_ITEM);
    }

    public static void init() {}

    private ModDrone() {}
}
