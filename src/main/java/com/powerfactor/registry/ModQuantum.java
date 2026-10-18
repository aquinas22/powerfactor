package com.powerfactor.registry;

import com.powerfactor.exotic.ExoticMaterial;
import com.powerfactor.exotic.ExoticTool;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/** Exotic ores and metals, the quantum component chain, exotic tools, quantum machines and the arsenal. */
public final class ModQuantum {
    // ---------------------------------------------------------------- ores
    /** Deep and very rare; hidden from caves (never generated touching air). */
    public static final DeferredBlock<DropExperienceBlock> ADAMANTIUM_ORE = ore("deepslate_adamantium_ore", MapColor.DEEPSLATE, 25F, 1200F, 0,
            SoundType.DEEPSLATE, 3, 7);
    /** Electric teal veins in End stone; they glow faintly. */
    public static final DeferredBlock<DropExperienceBlock> FARADAENIUM_ORE = ore("faradaenium_ore", MapColor.SAND, 6F, 9F, 7,
            SoundType.STONE, 3, 6);
    /** One lonely block per sixteen chunks or so, and nearly indestructible. */
    public static final DeferredBlock<DropExperienceBlock> CORTANIUM_ORE = ore("cortanium_ore", MapColor.COLOR_RED, 60F, 3_600_000F, 4,
            SoundType.ANCIENT_DEBRIS, 8, 14);
    /** Violet qubit crystals in deep slate; the root of the quantum chain. */
    public static final DeferredBlock<DropExperienceBlock> QUBIT_ORE = ore("qubit_ore", MapColor.COLOR_PURPLE, 6F, 6F, 9,
            SoundType.AMETHYST, 4, 8);

    private static DeferredBlock<DropExperienceBlock> ore(String id, MapColor color, float hardness, float resistance, int light, SoundType sound,
            int xpMin, int xpMax) {
        DeferredBlock<DropExperienceBlock> b = ModBlocks.BLOCKS.registerBlock(id, p -> new DropExperienceBlock(UniformInt.of(xpMin, xpMax), p),
                () -> BlockBehaviour.Properties.of().mapColor(color).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops()
                        .strength(hardness, resistance).sound(sound).lightLevel(s -> light));
        ModStorage.TAB.add(ModItems.ITEMS.registerSimpleBlockItem(b, p -> p.rarity(Rarity.UNCOMMON)));
        return b;
    }

    // ---------------------------------------------------------------- materials
    public static final DeferredItem<Item> RAW_ADAMANTIUM = item("raw_adamantium", Rarity.UNCOMMON);
    public static final DeferredItem<Item> ADAMANTIUM_INGOT = item("adamantium_ingot", Rarity.RARE);
    public static final DeferredItem<Item> RAW_FARADAENIUM = item("raw_faradaenium", Rarity.UNCOMMON);
    public static final DeferredItem<Item> FARADAENIUM_INGOT = item("faradaenium_ingot", Rarity.RARE);
    public static final DeferredItem<Item> CORTANIUM_SHARD = item("cortanium_shard", Rarity.EPIC);
    public static final DeferredItem<Item> CORTANIUM_INGOT = item("cortanium_ingot", Rarity.EPIC);
    public static final DeferredItem<Item> QUBIT_CRYSTAL = item("qubit_crystal", Rarity.UNCOMMON);
    public static final DeferredItem<Item> QUANTUM_ALLOY_INGOT = item("quantum_alloy_ingot", Rarity.EPIC);

    public static final Map<String, DeferredBlock<Block>> STORAGE_BLOCKS = new LinkedHashMap<>();

    static {
        for (String m : new String[] {"adamantium", "faradaenium", "cortanium", "qubit_crystal", "quantum_alloy"}) {
            DeferredBlock<Block> b = ModBlocks.BLOCKS.registerSimpleBlock(m + "_block",
                    () -> BlockBehaviour.Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(8F, 1200F)
                            .sound(m.equals("qubit_crystal") ? SoundType.AMETHYST : SoundType.NETHERITE_BLOCK)
                            .lightLevel(s -> m.equals("faradaenium") || m.equals("qubit_crystal") || m.equals("quantum_alloy") ? 10 : 0));
            STORAGE_BLOCKS.put(m, b);
            ModStorage.TAB.add(ModItems.ITEMS.registerSimpleBlockItem(b, p -> p.rarity(Rarity.RARE)));
        }
    }

    // ---------------------------------------------------------------- quantum component chain
    public static final DeferredItem<Item> QUANTUM_DUST = item("quantum_dust", Rarity.UNCOMMON);
    public static final DeferredItem<Item> ENTANGLED_PAIR = item("entangled_pair", Rarity.RARE);
    public static final DeferredItem<Item> QUANTUM_PHOTOMASK = tab(ModItems.ITEMS.registerItem("quantum_photomask",
            p -> new com.powerfactor.item.PhotomaskItem("quantum", p.rarity(Rarity.RARE))));
    public static final DeferredItem<Item> QUANTUM_PROCESSOR = item("quantum_processor", Rarity.EPIC);
    public static final DeferredItem<Item> EXOTIC_MATTER = item("exotic_matter", Rarity.EPIC);
    public static final DeferredItem<Item> SINGULARITY = item("singularity", Rarity.EPIC);

    private static DeferredItem<Item> item(String id, Rarity rarity) {
        return tab(ModItems.ITEMS.registerItem(id, p -> new Item(p.rarity(rarity))));
    }

    static <T extends Item> DeferredItem<T> tab(DeferredItem<T> item) {
        ModStorage.TAB.add(item);
        return item;
    }

    // ---------------------------------------------------------------- exotic tools
    public static final Map<ExoticMaterial, Map<ExoticTool.Kind, DeferredItem<Item>>> TOOLS = new EnumMap<>(ExoticMaterial.class);

    static {
        for (ExoticMaterial m : ExoticMaterial.values()) {
            Map<ExoticTool.Kind, DeferredItem<Item>> set = new EnumMap<>(ExoticTool.Kind.class);
            for (ExoticTool.Kind k : ExoticTool.Kind.values()) {
                set.put(k, tab(ModItems.ITEMS.registerItem(m.id + "_" + k.id(), p -> ExoticTool.create(m, k, p))));
            }
            TOOLS.put(m, set);
        }
    }

    // ---------------------------------------------------------------- exotic armor
    public static final Map<ExoticMaterial, Map<net.minecraft.world.item.equipment.ArmorType, DeferredItem<com.powerfactor.exotic.ExoticArmor>>> ARMOR =
            new EnumMap<>(ExoticMaterial.class);

    static {
        var types = List.of(net.minecraft.world.item.equipment.ArmorType.HELMET, net.minecraft.world.item.equipment.ArmorType.CHESTPLATE,
                net.minecraft.world.item.equipment.ArmorType.LEGGINGS, net.minecraft.world.item.equipment.ArmorType.BOOTS);
        for (ExoticMaterial m : ExoticMaterial.values()) {
            Map<net.minecraft.world.item.equipment.ArmorType, DeferredItem<com.powerfactor.exotic.ExoticArmor>> set =
                    new EnumMap<>(net.minecraft.world.item.equipment.ArmorType.class);
            for (var t : types) {
                set.put(t, tab(ModItems.ITEMS.registerItem(m.id + "_" + t.getName(), p -> new com.powerfactor.exotic.ExoticArmor(m, t, p))));
            }
            ARMOR.put(m, set);
        }
    }

    // ---------------------------------------------------------------- quarries
    public static final net.neoforged.neoforge.registries.DeferredBlock<com.powerfactor.quantum.QuarryBlock> QUARRY = machine("quarry",
            p -> new com.powerfactor.quantum.QuarryBlock(com.powerfactor.quantum.QuarryBlockEntity.Tier.INDUSTRIAL, p), 8, Rarity.RARE);
    public static final net.neoforged.neoforge.registries.DeferredBlock<com.powerfactor.quantum.QuarryBlock> QUANTUM_QUARRY = machine("quantum_quarry",
            p -> new com.powerfactor.quantum.QuarryBlock(com.powerfactor.quantum.QuarryBlockEntity.Tier.QUANTUM, p), 13, Rarity.EPIC);

    static <B extends Block> DeferredBlock<B> machine(String id, java.util.function.Function<BlockBehaviour.Properties, B> factory, int light,
            Rarity rarity) {
        DeferredBlock<B> b = ModBlocks.BLOCKS.registerBlock(id, factory, () -> BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                .strength(4F, 12F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().noOcclusion()
                .lightLevel(s -> s.hasProperty(com.powerfactor.machine.MachineBlock.RUNNING) && s.getValue(com.powerfactor.machine.MachineBlock.RUNNING)
                        ? light : Math.max(0, light - 8)));
        ModStorage.TAB.add(ModItems.ITEMS.registerSimpleBlockItem(b, p -> p.rarity(rarity)));
        return b;
    }

    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,
            net.minecraft.world.level.block.entity.BlockEntityType<com.powerfactor.quantum.QuarryBlockEntity>> QUARRY_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("quarry", () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(
                    (pos, state) -> new com.powerfactor.quantum.QuarryBlockEntity(com.powerfactor.quantum.QuarryBlockEntity.Tier.INDUSTRIAL, pos, state),
                    QUARRY.get()));
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,
            net.minecraft.world.level.block.entity.BlockEntityType<com.powerfactor.quantum.QuarryBlockEntity>> QUANTUM_QUARRY_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("quantum_quarry", () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(
                    (pos, state) -> new com.powerfactor.quantum.QuarryBlockEntity(com.powerfactor.quantum.QuarryBlockEntity.Tier.QUANTUM, pos, state),
                    QUANTUM_QUARRY.get()));
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.inventory.MenuType<?>,
            net.minecraft.world.inventory.MenuType<com.powerfactor.quantum.QuarryMenu>> QUARRY_MENU = ModMenus.MENUS.register("quarry",
            () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(com.powerfactor.quantum.QuarryMenu::new));

    // ---------------------------------------------------------------- orbital collector
    public static final DeferredItem<Item> COSMIC_DUST = item("cosmic_dust", Rarity.RARE);
    public static final DeferredBlock<com.powerfactor.quantum.QuantumMachineBlock> ORBITAL_COLLECTOR = machine("orbital_collector",
            p -> new com.powerfactor.quantum.QuantumMachineBlock(() -> ModQuantum.ORBITAL_COLLECTOR_BE.get(), p), 8, Rarity.EPIC);
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,
            net.minecraft.world.level.block.entity.BlockEntityType<com.powerfactor.quantum.OrbitalCollectorBlockEntity>> ORBITAL_COLLECTOR_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("orbital_collector", () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(
                    com.powerfactor.quantum.OrbitalCollectorBlockEntity::new, ORBITAL_COLLECTOR.get()));
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.inventory.MenuType<?>,
            net.minecraft.world.inventory.MenuType<com.powerfactor.quantum.OrbitalCollectorMenu>> ORBITAL_COLLECTOR_MENU =
            ModMenus.MENUS.register("orbital_collector",
                    () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(com.powerfactor.quantum.OrbitalCollectorMenu::new));

    // ---------------------------------------------------------------- space elevator
    public static final DeferredItem<Item> NANOTUBE_TETHER = item("nanotube_tether", Rarity.RARE);
    public static final DeferredBlock<com.powerfactor.quantum.SpaceElevatorBlock> SPACE_ELEVATOR = machine("space_elevator",
            com.powerfactor.quantum.SpaceElevatorBlock::new, 15, Rarity.EPIC);
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,
            net.minecraft.world.level.block.entity.BlockEntityType<com.powerfactor.quantum.SpaceElevatorBlockEntity>> SPACE_ELEVATOR_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("space_elevator", () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(
                    com.powerfactor.quantum.SpaceElevatorBlockEntity::new, SPACE_ELEVATOR.get()));
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.inventory.MenuType<?>,
            net.minecraft.world.inventory.MenuType<com.powerfactor.quantum.SpaceElevatorMenu>> SPACE_ELEVATOR_MENU =
            ModMenus.MENUS.register("space_elevator",
                    () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(com.powerfactor.quantum.SpaceElevatorMenu::new));

    // ---------------------------------------------------------------- logic controller
    public static final DeferredBlock<com.powerfactor.device.LogicControllerBlock> LOGIC_CONTROLLER = machine("logic_controller",
            com.powerfactor.device.LogicControllerBlock::new, 6, Rarity.UNCOMMON);
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,
            net.minecraft.world.level.block.entity.BlockEntityType<com.powerfactor.device.LogicControllerBlockEntity>> LOGIC_CONTROLLER_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("logic_controller", () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(
                    com.powerfactor.device.LogicControllerBlockEntity::new, LOGIC_CONTROLLER.get()));
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.inventory.MenuType<?>,
            net.minecraft.world.inventory.MenuType<com.powerfactor.device.LogicControllerMenu>> LOGIC_CONTROLLER_MENU =
            ModMenus.MENUS.register("logic_controller",
                    () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(com.powerfactor.device.LogicControllerMenu::new));

    // ---------------------------------------------------------------- gravity lift
    public static final DeferredBlock<com.powerfactor.logistics.GravityLiftBlock> GRAVITY_LIFT = machine("gravity_lift",
            com.powerfactor.logistics.GravityLiftBlock::new, 10, Rarity.RARE);
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,
            net.minecraft.world.level.block.entity.BlockEntityType<com.powerfactor.logistics.GravityLiftBlockEntity>> GRAVITY_LIFT_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("gravity_lift", () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(
                    com.powerfactor.logistics.GravityLiftBlockEntity::new, GRAVITY_LIFT.get()));

    // ---------------------------------------------------------------- robot arm
    public static final DeferredBlock<com.powerfactor.logistics.RobotArmBlock> ROBOT_ARM = machine("robot_arm",
            com.powerfactor.logistics.RobotArmBlock::new, 4, Rarity.UNCOMMON);
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,
            net.minecraft.world.level.block.entity.BlockEntityType<com.powerfactor.logistics.RobotArmBlockEntity>> ROBOT_ARM_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("robot_arm", () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(
                    com.powerfactor.logistics.RobotArmBlockEntity::new, ROBOT_ARM.get()));

    // ---------------------------------------------------------------- meteors
    public static final DeferredBlock<Block> METEORITE = ModBlocks.BLOCKS.registerSimpleBlock("meteorite",
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(8F, 30F).sound(SoundType.ANCIENT_DEBRIS)
                    .requiresCorrectToolForDrops().lightLevel(s -> 3));
    static {
        ModStorage.TAB.add(ModItems.ITEMS.registerSimpleBlockItem(METEORITE, p -> p.rarity(Rarity.RARE)));
    }
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.entity.EntityType<?>,
            net.minecraft.world.entity.EntityType<com.powerfactor.world.MeteorEntity>> METEOR = ModEntities.ENTITIES.register("meteor",
            () -> net.minecraft.world.entity.EntityType.Builder.<com.powerfactor.world.MeteorEntity>of(com.powerfactor.world.MeteorEntity::new,
                    net.minecraft.world.entity.MobCategory.MISC).sized(1.2F, 1.2F).clientTrackingRange(32).updateInterval(1).fireImmune()
                    .build(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
                            com.powerfactor.PowerFactor.id("meteor"))));

    // ---------------------------------------------------------------- teleporter
    public static final DeferredBlock<com.powerfactor.quantum.TeleporterBlock> TELEPORTER = machine("quantum_teleporter",
            com.powerfactor.quantum.TeleporterBlock::new, 10, Rarity.EPIC);
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,
            net.minecraft.world.level.block.entity.BlockEntityType<com.powerfactor.quantum.TeleporterBlockEntity>> TELEPORTER_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("quantum_teleporter", () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(
                    com.powerfactor.quantum.TeleporterBlockEntity::new, TELEPORTER.get()));

    // ---------------------------------------------------------------- collider and zero-point generator
    public static final DeferredBlock<com.powerfactor.quantum.QuantumMachineBlock> PARTICLE_COLLIDER = machine("particle_collider",
            p -> new com.powerfactor.quantum.QuantumMachineBlock(() -> ModQuantum.COLLIDER_BE.get(), p), 12, Rarity.EPIC);
    public static final DeferredBlock<com.powerfactor.quantum.QuantumMachineBlock> ZERO_POINT_GENERATOR = machine("zero_point_generator",
            p -> new com.powerfactor.quantum.QuantumMachineBlock(() -> ModQuantum.ZERO_POINT_BE.get(), p), 15, Rarity.EPIC);
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,
            net.minecraft.world.level.block.entity.BlockEntityType<com.powerfactor.quantum.ColliderBlockEntity>> COLLIDER_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("particle_collider", () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(
                    com.powerfactor.quantum.ColliderBlockEntity::new, PARTICLE_COLLIDER.get()));
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,
            net.minecraft.world.level.block.entity.BlockEntityType<com.powerfactor.quantum.ZeroPointBlockEntity>> ZERO_POINT_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("zero_point_generator", () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(
                    com.powerfactor.quantum.ZeroPointBlockEntity::new, ZERO_POINT_GENERATOR.get()));
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.inventory.MenuType<?>,
            net.minecraft.world.inventory.MenuType<com.powerfactor.quantum.ColliderMenu>> COLLIDER_MENU = ModMenus.MENUS.register("particle_collider",
            () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(com.powerfactor.quantum.ColliderMenu::new));
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.inventory.MenuType<?>,
            net.minecraft.world.inventory.MenuType<com.powerfactor.quantum.ZeroPointMenu>> ZERO_POINT_MENU = ModMenus.MENUS.register("zero_point_generator",
            () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(com.powerfactor.quantum.ZeroPointMenu::new));

    // ---------------------------------------------------------------- starforge and paxels
    public static final DeferredBlock<com.powerfactor.multiblock.MultiblockControllerBlock> STARFORGE = machine("starforge",
            p -> new com.powerfactor.multiblock.MultiblockControllerBlock(() -> ModQuantum.STARFORGE_BE.get(), p), 13, Rarity.EPIC);
    public static final DeferredBlock<com.powerfactor.multiblock.CasingBlock> STARFORGE_FRAME = casing("starforge_frame",
            com.powerfactor.multiblock.Connected.Style.host("starforge", () -> ModQuantum.STARFORGE.get(),
                    () -> ModQuantum.STORAGE_BLOCKS.get("adamantium").get(), () -> ModQuantum.STORAGE_BLOCKS.get("qubit_crystal").get()), 0);
    public static final DeferredBlock<com.powerfactor.multiblock.CasingBlock> STARFORGE_PYLON = casing("starforge_pylon",
            Block.box(3, 0, 3, 13, 16, 13), 10);
    public static final DeferredBlock<com.powerfactor.multiblock.CasingBlock> STARFORGE_CRUCIBLE = casing("starforge_crucible",
            Block.box(1, 0, 1, 15, 12, 15), 13);

    // ---------------------------------------------------------------- fusion reactor
    public static final DeferredBlock<com.powerfactor.multiblock.MultiblockControllerBlock> FUSION_REACTOR = machine("fusion_reactor",
            p -> new com.powerfactor.multiblock.MultiblockControllerBlock(() -> ModQuantum.FUSION_REACTOR_BE.get(), p), 14, Rarity.EPIC);
    public static final DeferredBlock<com.powerfactor.multiblock.CasingBlock> FUSION_CASING = casing("fusion_casing",
            com.powerfactor.multiblock.Connected.Style.host("fusion", () -> ModQuantum.FUSION_REACTOR.get()), 0);
    public static final DeferredBlock<com.powerfactor.multiblock.CasingBlock> FUSION_MAGNET = casing("fusion_magnet",
            com.powerfactor.multiblock.Connected.Style.panel("fusion", "magnet", () -> ModQuantum.FUSION_REACTOR.get()), 9);
    public static final DeferredBlock<com.powerfactor.multiblock.CasingBlock> FUSION_GLASS = casing("fusion_glass",
            com.powerfactor.multiblock.Connected.Style.glass("fusion", () -> ModQuantum.FUSION_REACTOR.get()), 4);
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,
            net.minecraft.world.level.block.entity.BlockEntityType<com.powerfactor.quantum.FusionReactorBlockEntity>> FUSION_REACTOR_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("fusion_reactor", () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(
                    com.powerfactor.quantum.FusionReactorBlockEntity::new, FUSION_REACTOR.get()));
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.inventory.MenuType<?>,
            net.minecraft.world.inventory.MenuType<com.powerfactor.quantum.FusionMenu>> FUSION_MENU = ModMenus.MENUS.register("fusion_reactor",
            () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(com.powerfactor.quantum.FusionMenu::new));

    private static DeferredBlock<com.powerfactor.multiblock.CasingBlock> casing(String id, com.powerfactor.multiblock.Connected.Style style, int light) {
        return casing(id, p -> new com.powerfactor.multiblock.ConnectedCasingBlock(style, net.minecraft.world.phys.shapes.Shapes.block(), p), light);
    }

    private static DeferredBlock<com.powerfactor.multiblock.CasingBlock> casing(String id, net.minecraft.world.phys.shapes.VoxelShape shape, int light) {
        return casing(id, p -> new com.powerfactor.multiblock.CasingBlock(shape, p), light);
    }

    private static DeferredBlock<com.powerfactor.multiblock.CasingBlock> casing(String id,
            java.util.function.Function<BlockBehaviour.Properties, com.powerfactor.multiblock.CasingBlock> factory, int light) {
        DeferredBlock<com.powerfactor.multiblock.CasingBlock> b = ModBlocks.BLOCKS.registerBlock(id, factory,
                () -> BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6F, 1200F).sound(SoundType.NETHERITE_BLOCK)
                        .requiresCorrectToolForDrops().noOcclusion()
                        .lightLevel(s -> s.getValue(com.powerfactor.multiblock.CasingBlock.FORMED) ? light : light / 3));
        ModStorage.TAB.add(ModItems.ITEMS.registerSimpleBlockItem(b, p -> p.rarity(Rarity.RARE)));
        return b;
    }

    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,
            net.minecraft.world.level.block.entity.BlockEntityType<com.powerfactor.quantum.StarforgeBlockEntity>> STARFORGE_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("starforge", () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(
                    com.powerfactor.quantum.StarforgeBlockEntity::new, STARFORGE.get()));
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.inventory.MenuType<?>,
            net.minecraft.world.inventory.MenuType<com.powerfactor.quantum.StarforgeMenu>> STARFORGE_MENU = ModMenus.MENUS.register("starforge",
            () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(com.powerfactor.quantum.StarforgeMenu::new));
    public static final DeferredItem<com.powerfactor.exotic.PaxelItem> DIAMOND_PAXEL = tab(ModItems.ITEMS.registerItem("diamond_paxel",
            p -> new com.powerfactor.exotic.PaxelItem(net.minecraft.world.item.ToolMaterial.DIAMOND, 4F, p.rarity(Rarity.UNCOMMON))));
    public static final DeferredItem<com.powerfactor.exotic.PaxelItem> NETHERITE_PAXEL = tab(ModItems.ITEMS.registerItem("netherite_paxel",
            p -> new com.powerfactor.exotic.PaxelItem(net.minecraft.world.item.ToolMaterial.NETHERITE, 4F, p.rarity(Rarity.RARE).fireResistant())));

    // ---------------------------------------------------------------- arsenal
    public static final DeferredItem<com.powerfactor.weapon.RailgunItem> RAILGUN = tab(ModItems.ITEMS.registerItem("railgun",
            p -> new com.powerfactor.weapon.RailgunItem(p.rarity(Rarity.EPIC))));
    public static final DeferredItem<com.powerfactor.weapon.PlasmaRifleItem> PLASMA_RIFLE = tab(ModItems.ITEMS.registerItem("plasma_rifle",
            p -> new com.powerfactor.weapon.PlasmaRifleItem(p.rarity(Rarity.RARE))));
    public static final DeferredItem<com.powerfactor.weapon.SingularityLauncherItem> SINGULARITY_LAUNCHER = tab(ModItems.ITEMS.registerItem(
            "singularity_launcher", p -> new com.powerfactor.weapon.SingularityLauncherItem(p.rarity(Rarity.EPIC))));
    public static final DeferredItem<com.powerfactor.weapon.QuantumBladeItem> QUANTUM_BLADE = tab(ModItems.ITEMS.registerItem("quantum_blade",
            p -> new com.powerfactor.weapon.QuantumBladeItem(p.rarity(Rarity.EPIC))));
    public static final DeferredItem<com.powerfactor.weapon.QuantumDisassemblerItem> QUANTUM_DISASSEMBLER = tab(ModItems.ITEMS.registerItem(
            "quantum_disassembler", p -> new com.powerfactor.weapon.QuantumDisassemblerItem(p.rarity(Rarity.EPIC))));

    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.entity.EntityType<?>,
            net.minecraft.world.entity.EntityType<com.powerfactor.weapon.PlasmaBoltEntity>> PLASMA_BOLT = ModEntities.ENTITIES.register("plasma_bolt",
            () -> net.minecraft.world.entity.EntityType.Builder.<com.powerfactor.weapon.PlasmaBoltEntity>of(com.powerfactor.weapon.PlasmaBoltEntity::new,
                    net.minecraft.world.entity.MobCategory.MISC).sized(0.3F, 0.3F).clientTrackingRange(8).updateInterval(1)
                    .build(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
                            com.powerfactor.PowerFactor.id("plasma_bolt"))));
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.entity.EntityType<?>,
            net.minecraft.world.entity.EntityType<com.powerfactor.weapon.SingularityEntity>> SINGULARITY_ENTITY = ModEntities.ENTITIES.register("singularity",
            () -> net.minecraft.world.entity.EntityType.Builder.<com.powerfactor.weapon.SingularityEntity>of(com.powerfactor.weapon.SingularityEntity::new,
                    net.minecraft.world.entity.MobCategory.MISC).sized(0.5F, 0.5F).clientTrackingRange(16).updateInterval(1)
                    .build(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
                            com.powerfactor.PowerFactor.id("singularity"))));

    public static void init() {}

    private ModQuantum() {}
}
