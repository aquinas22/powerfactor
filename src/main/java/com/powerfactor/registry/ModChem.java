package com.powerfactor.registry;

import com.powerfactor.chem.ChemicalReactorBlockEntity;
import com.powerfactor.chem.ChemicalReactorMenu;
import com.powerfactor.chem.OreDissolverBlockEntity;
import com.powerfactor.chem.OreDissolverMenu;
import com.powerfactor.chem.OreMetal;
import com.powerfactor.quantum.QuantumMachineBlock;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/** Ore multiplication chemistry: sulfur, sulfuric acid, the Chemical Reactor, the Ore Dissolver and its crystals. */
public final class ModChem {
    public static final DeferredItem<Item> SULFUR_DUST = ModItems.ITEMS.registerSimpleItem("sulfur_dust");
    public static final DeferredItem<Item> QUANTUM_CATALYST = ModItems.ITEMS.registerItem("quantum_catalyst",
            p -> new Item(p.durability(96).rarity(Rarity.EPIC)));
    public static final Map<OreMetal, DeferredItem<Item>> CRYSTALS = new EnumMap<>(OreMetal.class);

    static {
        for (OreMetal m : OreMetal.values()) CRYSTALS.put(m, ModItems.ITEMS.registerSimpleItem(m.id() + "_crystal"));
    }

    public static final DeferredBlock<QuantumMachineBlock> CHEMICAL_REACTOR = ModQuantum.machine("chemical_reactor",
            p -> new QuantumMachineBlock(() -> ModChem.CHEMICAL_REACTOR_BE.get(), p), 9, Rarity.UNCOMMON);
    public static final DeferredBlock<QuantumMachineBlock> ORE_DISSOLVER = ModQuantum.machine("ore_dissolver",
            p -> new QuantumMachineBlock(() -> ModChem.ORE_DISSOLVER_BE.get(), p), 9, Rarity.RARE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChemicalReactorBlockEntity>> CHEMICAL_REACTOR_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("chemical_reactor", () -> new BlockEntityType<>(ChemicalReactorBlockEntity::new, CHEMICAL_REACTOR.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OreDissolverBlockEntity>> ORE_DISSOLVER_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("ore_dissolver", () -> new BlockEntityType<>(OreDissolverBlockEntity::new, ORE_DISSOLVER.get()));
    public static final DeferredHolder<MenuType<?>, MenuType<ChemicalReactorMenu>> CHEMICAL_REACTOR_MENU = ModMenus.MENUS.register("chemical_reactor",
            () -> IMenuTypeExtension.create(ChemicalReactorMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<OreDissolverMenu>> ORE_DISSOLVER_MENU = ModMenus.MENUS.register("ore_dissolver",
            () -> IMenuTypeExtension.create(OreDissolverMenu::new));

    static {
        ModStorage.TAB.add(SULFUR_DUST);
        ModStorage.TAB.add(QUANTUM_CATALYST);
        CRYSTALS.values().forEach(ModStorage.TAB::add);
    }

    public static void init() {}

    private ModChem() {}
}
