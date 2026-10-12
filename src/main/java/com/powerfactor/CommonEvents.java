package com.powerfactor;

import com.powerfactor.item.DrillItem;
import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.registry.ModBlocks;
import com.powerfactor.registry.ModComponents;
import com.powerfactor.registry.ModItems;
import com.powerfactor.grid.GridManager;
import com.powerfactor.scada.ScadaPayloads;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.transfer.energy.ItemAccessEnergyHandler;

@EventBusSubscriber(modid = PowerFactor.MODID)
public final class CommonEvents {
    @SubscribeEvent
    static void onCapabilities(RegisterCapabilitiesEvent event) {
        // Any mod's cables can pull FE from the turbine; hoppers and pipes can feed it coal.
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.COAL_TURBINE.get(), (be, side) -> be.getEnergy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.COAL_TURBINE.get(), (be, side) -> be.getFuelHandler());

        // Engines: extract from anywhere (pipes/cables can pull), push only from the plate face.
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.ENGINE.get(), (be, side) -> be.getEnergy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.ENGINE.get(), (be, side) -> be.getFuelHandler());

        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.CRUSHER.get(), (be, side) -> be.getEnergy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.CRUSHER.get(), (be, side) -> be.getItemHandler(side));

        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.ELECTRIC_FURNACE.get(), (be, side) -> be.getEnergy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.ELECTRIC_FURNACE.get(), (be, side) -> be.getItemHandler(side));

        for (var type : ModBlockEntities.RECIPE_MACHINES.values()) {
            event.registerBlockEntity(Capabilities.Energy.BLOCK, type.get(), (be, side) -> be.getEnergy());
            event.registerBlockEntity(Capabilities.Item.BLOCK, type.get(), (be, side) -> be.getItemHandler(side));
            event.registerBlockEntity(Capabilities.Fluid.BLOCK, type.get(), (be, side) -> be.getTank());
        }

        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.CHARGER.get(), (be, side) -> be.getEnergy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.CHARGER.get(), (be, side) -> be.getItemHandler());

        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.FLUID_TANK.get(), (be, side) -> be.getTank());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.ELECTRIC_PUMP.get(), (be, side) -> be.getTank());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.ELECTRIC_PUMP.get(), (be, side) -> be.getEnergy());

        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.ENDER_LINK.get(), (be, side) -> be.getEnergy());

        for (var type : java.util.List.of(ModBlockEntities.SOLAR_PANEL.get(), ModBlockEntities.WIND_TURBINE.get(), ModBlockEntities.HYDRO_TURBINE.get())) {
            event.registerBlockEntity(Capabilities.Energy.BLOCK, type, (be, side) -> be.getEnergy());
        }
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.DIESEL_GENERATOR.get(), (be, side) -> be.getEnergy());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.DIESEL_GENERATOR.get(), (be, side) -> be.getTank());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.SYNCHRONOUS_CONDENSER.get(), (be, side) -> be.getEnergy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.BATTERY_BANK.get(), (be, side) -> be.getEnergy());

        // Wires accept FE into their grid from any side.
        event.registerBlock(Capabilities.Energy.BLOCK, (level, pos, state, be, side) -> {
            if (level instanceof ServerLevel server) {
                var grid = GridManager.get(server).gridAt(pos);
                return grid == null ? null : grid.handler();
            }
            return null;
        }, ModBlocks.COPPER_WIRE.get(), ModBlocks.INSULATED_COPPER_CABLE.get(), ModBlocks.ALUMINUM_CABLE.get(), ModBlocks.HV_PYLON.get(),
                ModBlocks.CIRCUIT_BREAKER.get());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.TRANSFORMER.get(), (be, side) -> be.primaryHandler(side));

        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.TESLA_COIL.get(), (be, side) -> be.getEnergy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.RADIO_TOWER.get(), (be, side) -> be.getEnergy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.PUMPJACK.get(), (be, side) -> be.getEnergy());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.PUMPJACK.get(), (be, side) -> be.getTank());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.REFINERY.get(), (be, side) -> be.getEnergy());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.REFINERY.get(), (be, side) -> be.getTanks());
        // Casings of a formed structure pass energy and fluids through to their controller.
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.CASING.get(), (be, side) ->
                be.controller() instanceof com.powerfactor.oil.RefineryBlockEntity r ? r.getEnergy() : null);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.CASING.get(), (be, side) ->
                be.controller() instanceof com.powerfactor.oil.RefineryBlockEntity r ? r.getTanks() : null);
        event.registerItem(Capabilities.Energy.ITEM,
                (stack, access) -> new ItemAccessEnergyHandler(access, ModComponents.ENERGY.get(), com.powerfactor.radio.WalkieTalkieItem.CAPACITY, 2_000),
                ModItems.WALKIE_TALKIE.get());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.GROW_LAMP.get(), (be, side) -> be.getEnergy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.CHARGING_PAD.get(), (be, side) -> be.getEnergy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.ELECTROLYZER.get(), (be, side) -> be.getEnergy());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.ELECTROLYZER.get(), (be, side) -> be.getTanks());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.FUEL_CELL.get(), (be, side) -> be.getEnergy());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.FUEL_CELL.get(), (be, side) -> be.getTank());

        event.registerItem(Capabilities.Energy.ITEM,
                (stack, access) -> new ItemAccessEnergyHandler(access, ModComponents.ENERGY.get(), com.powerfactor.item.ArcGunItem.capacity(stack), 5_000),
                ModItems.ARC_GUN.get());

        event.registerItem(Capabilities.Energy.ITEM,
                (stack, access) -> new ItemAccessEnergyHandler(access, ModComponents.ENERGY.get(), com.powerfactor.item.ArcSaberItem.capacity(stack), 5_000),
                ModItems.ARC_SABER.get());

        event.registerItem(Capabilities.Energy.ITEM,
                (stack, access) -> new ItemAccessEnergyHandler(access, ModComponents.ENERGY.get(), ModItems.ELECTRIC_CHAINSAW.get().capacity(stack), 5_000),
                ModItems.ELECTRIC_CHAINSAW.get());
        event.registerItem(Capabilities.Energy.ITEM,
                (stack, access) -> new ItemAccessEnergyHandler(access, ModComponents.ENERGY.get(), ModItems.ELECTRIC_JETPACK.get().capacity(stack), 8_000),
                ModItems.ELECTRIC_JETPACK.get());
        event.registerItem(Capabilities.Energy.ITEM,
                (stack, access) -> new ItemAccessEnergyHandler(access, ModComponents.ENERGY.get(), com.powerfactor.item.ClampMeterItem.CAPACITY, 2_000),
                ModItems.CLAMP_METER.get());

        // The drill charges from any mod's charger.
        event.registerItem(Capabilities.Energy.ITEM,
                (stack, access) -> new ItemAccessEnergyHandler(access, ModComponents.ENERGY.get(), DrillItem.capacity(stack), 5_000),
                ModItems.ELECTRIC_DRILL.get());
    }

    /** Our recipe types aren't synced by vanilla; JEI on a dedicated-server client needs them. */
    @SubscribeEvent
    static void onDatapackSync(net.neoforged.neoforge.event.OnDatapackSyncEvent event) {
        event.sendRecipes(com.powerfactor.registry.ModRecipes.CRUSHING.get());
        for (var kind : com.powerfactor.recipe.MachineRecipeKind.values()) {
            event.sendRecipes(kind.type());
        }
    }

    @SubscribeEvent
    static void onPayloads(RegisterPayloadHandlersEvent event) {
        ScadaPayloads.register(event);
        com.powerfactor.item.ToolPayloads.register(event);
    }

    /** An unlit saber is just a metal tube. */
    @SubscribeEvent
    static void onIncomingDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker
                && event.getSource().getDirectEntity() == attacker
                && attacker.getMainHandItem().getItem() instanceof com.powerfactor.item.ArcSaberItem
                && !com.powerfactor.item.ArcSaberItem.lit(attacker.getMainHandItem())) {
            event.setAmount(Math.min(event.getAmount(), 2F));
        }
    }

    /** A flat drill is barely better than a fist; Speed modules add 50% each. */
    @SubscribeEvent
    static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        var stack = event.getEntity().getMainHandItem();
        if (stack.getItem() instanceof DrillItem) {
            if (!DrillItem.powered(stack)) {
                event.setNewSpeed(Math.min(event.getNewSpeed(), DrillItem.UNPOWERED_SPEED));
            } else {
                event.setNewSpeed(event.getNewSpeed() * (1 + 0.5F * DrillItem.modules(stack, com.powerfactor.item.DrillModule.Type.SPEED)));
            }
        }
    }

    private static final ThreadLocal<Boolean> AREA_MINING = ThreadLocal.withInitial(() -> false);

    /** Area module: also break the 3x3 face around the block, paying FE for each. */
    @SubscribeEvent
    static void onBreak(net.neoforged.neoforge.event.level.block.BreakBlockEvent event) {
        if (AREA_MINING.get() || !(event.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player) || player.isShiftKeyDown()) {
            return;
        }
        var stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof DrillItem) || DrillItem.modules(stack, com.powerfactor.item.DrillModule.Type.AREA) == 0) {
            return;
        }
        var hit = player.pick(player.blockInteractionRange(), 0, false);
        if (!(hit instanceof net.minecraft.world.phys.BlockHitResult bhr) || !bhr.getBlockPos().equals(event.getPos())) {
            return;
        }
        net.minecraft.core.Direction.Axis axis = bhr.getDirection().getAxis();
        AREA_MINING.set(true);
        try {
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    if (a == 0 && b == 0) {
                        continue;
                    }
                    var pos = switch (axis) {
                        case X -> event.getPos().offset(0, a, b);
                        case Y -> event.getPos().offset(a, 0, b);
                        case Z -> event.getPos().offset(a, b, 0);
                    };
                    var state = player.level().getBlockState(pos);
                    if (!DrillItem.powered(stack) || state.isAir() || state.getDestroySpeed(player.level(), pos) < 0
                            || !stack.isCorrectToolForDrops(state)) {
                        continue;
                    }
                    player.gameMode.destroyBlock(pos);
                }
            }
        } finally {
            AREA_MINING.set(false);
        }
    }

    private CommonEvents() {}
}
