package com.powerfactor.registry;

import com.powerfactor.PowerFactor;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Hydrogen: a gas, so it has no bucket and no block; it lives in tanks and pipes. */
public final class ModFluids {
    public static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, PowerFactor.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, PowerFactor.MODID);

    public static final DeferredHolder<FluidType, FluidType> HYDROGEN_TYPE = TYPES.register("hydrogen", () -> new FluidType(
            FluidType.Properties.create().descriptionId("fluid.powerfactor.hydrogen").density(-90).viscosity(9).temperature(293)));

    public static final DeferredHolder<Fluid, FlowingFluid> HYDROGEN = FLUIDS.register("hydrogen",
            () -> new BaseFlowingFluid.Source(hydrogenProperties()));
    public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_HYDROGEN = FLUIDS.register("flowing_hydrogen",
            () -> new BaseFlowingFluid.Flowing(hydrogenProperties()));

    public static final DeferredHolder<FluidType, FluidType> STEAM_TYPE = TYPES.register("steam", () -> new FluidType(
            FluidType.Properties.create().descriptionId("fluid.powerfactor.steam").density(-100).viscosity(15).temperature(473)));
    public static final DeferredHolder<Fluid, FlowingFluid> STEAM = FLUIDS.register("steam",
            () -> new BaseFlowingFluid.Source(new BaseFlowingFluid.Properties(STEAM_TYPE, ModFluids.STEAM, ModFluids.FLOWING_STEAM)));
    public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_STEAM = FLUIDS.register("flowing_steam",
            () -> new BaseFlowingFluid.Flowing(new BaseFlowingFluid.Properties(STEAM_TYPE, ModFluids.STEAM, ModFluids.FLOWING_STEAM)));
    public static final DeferredHolder<FluidType, FluidType> HOT_WATER_TYPE = TYPES.register("hot_water", () -> new FluidType(
            FluidType.Properties.create().descriptionId("fluid.powerfactor.hot_water").density(1000).viscosity(800).temperature(368)));
    public static final DeferredHolder<Fluid, FlowingFluid> HOT_WATER = FLUIDS.register("hot_water",
            () -> new BaseFlowingFluid.Source(new BaseFlowingFluid.Properties(HOT_WATER_TYPE, ModFluids.HOT_WATER, ModFluids.FLOWING_HOT_WATER)));
    public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_HOT_WATER = FLUIDS.register("flowing_hot_water",
            () -> new BaseFlowingFluid.Flowing(new BaseFlowingFluid.Properties(HOT_WATER_TYPE, ModFluids.HOT_WATER, ModFluids.FLOWING_HOT_WATER)));

    private static BaseFlowingFluid.Properties hydrogenProperties() {
        return new BaseFlowingFluid.Properties(HYDROGEN_TYPE, HYDROGEN, FLOWING_HYDROGEN);
    }

    /** A liquid with a world block and a bucket: crude oil, diesel and jet fuel. */
    public record Liquid(DeferredHolder<FluidType, FluidType> type, DeferredHolder<Fluid, FlowingFluid> source,
                         DeferredHolder<Fluid, FlowingFluid> flowing, net.neoforged.neoforge.registries.DeferredBlock<net.minecraft.world.level.block.LiquidBlock> block,
                         net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.BucketItem> bucket, int tint) {}

    public static final Liquid CRUDE_OIL = liquid("crude_oil", 900, 4000, 0xFF1C1612, 2, 30);
    public static final Liquid DIESEL = liquid("diesel", 830, 1600, 0xFFC89A32, 1, 10);
    public static final Liquid JET_FUEL = liquid("jet_fuel", 800, 1100, 0xFFE6D9A4, 1, 8);
    /** Ore Dissolver feed, made in the Chemical Reactor. Hazardous: it burns what swims in it (chem/AcidEffects). */
    public static final Liquid SULFURIC_ACID = liquid("sulfuric_acid", 1830, 2600, 0xFFE4E25A, 1, 12);

    private static Liquid liquid(String id, int density, int viscosity, int tint, int levelDecrease, int tickRate) {
        DeferredHolder<FluidType, FluidType> type = TYPES.register(id, () -> new FluidType(FluidType.Properties.create()
                .descriptionId("fluid.powerfactor." + id).density(density).viscosity(viscosity).canSwim(false).canDrown(true)
                .canExtinguish(false).supportsBoating(false).fallDistanceModifier(0.6F).motionScale(0.004)));
        DeferredHolder<Fluid, FlowingFluid>[] src = new DeferredHolder[1], flow = new DeferredHolder[1];
        net.neoforged.neoforge.registries.DeferredBlock<net.minecraft.world.level.block.LiquidBlock>[] block = new net.neoforged.neoforge.registries.DeferredBlock[1];
        net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.BucketItem>[] bucket = new net.neoforged.neoforge.registries.DeferredItem[1];
        java.util.function.Supplier<BaseFlowingFluid.Properties> props = () -> new BaseFlowingFluid.Properties(type, src[0], flow[0])
                .block(block[0]).bucket(bucket[0]).levelDecreasePerBlock(levelDecrease).tickRate(tickRate).slopeFindDistance(3);
        DeferredHolder<Fluid, FlowingFluid> source = FLUIDS.register(id, () -> new BaseFlowingFluid.Source(props.get()));
        DeferredHolder<Fluid, FlowingFluid> flowing = FLUIDS.register("flowing_" + id, () -> new BaseFlowingFluid.Flowing(props.get()));
        src[0] = source;
        flow[0] = flowing;
        block[0] = ModBlocks.BLOCKS.registerBlock(id, p -> new net.minecraft.world.level.block.LiquidBlock(source.get(), p),
                () -> net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK)
                        .replaceable().noCollision().strength(100F).pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                        .noLootTable().liquid());
        bucket[0] = ModItems.ITEMS.registerItem(id + "_bucket", p -> new net.minecraft.world.item.BucketItem(source.get(), p),
                p -> p.craftRemainder(net.minecraft.world.item.Items.BUCKET).stacksTo(1));
        return new Liquid(type, source, flowing, block[0], bucket[0], tint);
    }

    // Reactor coolants and their hot sides (fission/ReactorCore.Coolant): pumped round a closed loop through a Heat Exchanger.
    public static final Liquid HEAVY_WATER = liquid("heavy_water", 1100, 1100, 0xFF5E9CD8, 1, 5);
    public static final Liquid HOT_HEAVY_WATER = liquid("hot_heavy_water", 1050, 900, 0xFFA8D8F4, 1, 5);
    public static final Liquid LIQUID_SODIUM = liquid("liquid_sodium", 930, 700, 0xFFC4CAD2, 1, 5);
    public static final Liquid HOT_SODIUM = liquid("hot_sodium", 880, 600, 0xFFF2B456, 1, 5);
    public static final Liquid MOLTEN_SALT = liquid("molten_salt", 1940, 1800, 0xFFDCEEE6, 1, 15);
    public static final Liquid HOT_MOLTEN_SALT = liquid("hot_molten_salt", 1900, 1500, 0xFFFF6A2E, 1, 15);

    public static final Liquid[] LIQUIDS = {CRUDE_OIL, DIESEL, JET_FUEL, SULFURIC_ACID, HEAVY_WATER, HOT_HEAVY_WATER, LIQUID_SODIUM, HOT_SODIUM,
            MOLTEN_SALT, HOT_MOLTEN_SALT};

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        FLUIDS.register(bus);
    }

    private ModFluids() {}
}
