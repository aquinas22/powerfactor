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

        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.CHARGER.get(), (be, side) -> be.getEnergy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.CHARGER.get(), (be, side) -> be.getItemHandler());

        // Wires accept FE into their grid from any side.
        event.registerBlock(Capabilities.Energy.BLOCK, (level, pos, state, be, side) -> {
            if (level instanceof ServerLevel server) {
                var grid = GridManager.get(server).gridAt(pos);
                return grid == null ? null : grid.handler();
            }
            return null;
        }, ModBlocks.COPPER_WIRE.get());

        // The drill charges from any mod's charger.
        event.registerItem(Capabilities.Energy.ITEM,
                (stack, access) -> new ItemAccessEnergyHandler(access, ModComponents.ENERGY.get(), DrillItem.capacity(), 5_000),
                ModItems.ELECTRIC_DRILL.get());
    }

    @SubscribeEvent
    static void onPayloads(RegisterPayloadHandlersEvent event) {
        ScadaPayloads.register(event);
    }

    /** A flat drill is barely better than a fist. */
    @SubscribeEvent
    static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        var stack = event.getEntity().getMainHandItem();
        if (stack.getItem() instanceof DrillItem && !DrillItem.powered(stack)) {
            event.setNewSpeed(Math.min(event.getNewSpeed(), DrillItem.UNPOWERED_SPEED));
        }
    }

    private CommonEvents() {}
}
