package com.powerfactor.registry;

import com.powerfactor.mob.MobGrinderBlockEntity;
import com.powerfactor.mob.MobGrinderMenu;
import com.powerfactor.mob.SoulSnareItem;
import com.powerfactor.mob.SpawnerCageBlockEntity;
import com.powerfactor.mob.SpawnerCageMenu;
import com.powerfactor.quantum.QuantumMachineBlock;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/** Mob farming: the Soul Snare, the Spawner Cage and the Mob Grinder. */
public final class ModMob {
    public static final DeferredItem<SoulSnareItem> SOUL_SNARE = ModItems.ITEMS.registerItem("soul_snare", SoulSnareItem::new);
    public static final DeferredBlock<QuantumMachineBlock> SPAWNER_CAGE = ModQuantum.machine("spawner_cage",
            p -> new QuantumMachineBlock(() -> ModMob.SPAWNER_CAGE_BE.get(), p), 8, Rarity.RARE);
    public static final DeferredBlock<QuantumMachineBlock> MOB_GRINDER = ModQuantum.machine("mob_grinder",
            p -> new QuantumMachineBlock(() -> ModMob.MOB_GRINDER_BE.get(), p), 4, Rarity.UNCOMMON);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpawnerCageBlockEntity>> SPAWNER_CAGE_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("spawner_cage", () -> new BlockEntityType<>(SpawnerCageBlockEntity::new, SPAWNER_CAGE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MobGrinderBlockEntity>> MOB_GRINDER_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("mob_grinder", () -> new BlockEntityType<>(MobGrinderBlockEntity::new, MOB_GRINDER.get()));
    public static final DeferredHolder<MenuType<?>, MenuType<SpawnerCageMenu>> SPAWNER_CAGE_MENU = ModMenus.MENUS.register("spawner_cage",
            () -> IMenuTypeExtension.create(SpawnerCageMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<MobGrinderMenu>> MOB_GRINDER_MENU = ModMenus.MENUS.register("mob_grinder",
            () -> IMenuTypeExtension.create(MobGrinderMenu::new));

    static {
        ModStorage.TAB.add(SOUL_SNARE);
    }

    public static void init() {}

    private ModMob() {}
}
