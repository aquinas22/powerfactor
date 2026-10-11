package com.powerfactor.client;

import com.powerfactor.PowerFactor;
import com.powerfactor.machine.CrusherMenu;
import com.powerfactor.machine.ElectricFurnaceMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.registry.ModMenus;
import com.powerfactor.scada.ScadaPayloads;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@Mod(value = PowerFactor.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = PowerFactor.MODID, value = Dist.CLIENT)
public final class PowerFactorClient {
    public PowerFactorClient() {
        ScadaPayloads.clientHandler = ScadaTerminalScreen::receive;
        com.powerfactor.api.MachineAudio.handler = MachineSoundLoop::update;
    }

    @SubscribeEvent
    static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(EngineRenderer.LAYER, EngineRenderer::createLayer);
        event.registerLayerDefinition(TurbineRotorRenderer.LAYER, TurbineRotorRenderer::createLayer);
    }

    @SubscribeEvent
    static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.ENGINE.get(), EngineRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.COAL_TURBINE.get(), TurbineRotorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.FLUID_TANK.get(), FluidTankRenderer::new);
    }

    @SubscribeEvent
    static void onMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.COAL_TURBINE.get(), CoalTurbineScreen::new);
        event.register(ModMenus.CRUSHER.get(), (CrusherMenu m, Inventory inv, Component t) ->
                new ProcessingScreen<>(m, inv, t, PowerFactor.id("textures/gui/crusher.png"), 70, 34, 25, true,
                        com.powerfactor.registry.ModBlocks.CRUSHER.get()));
        event.register(ModMenus.ELECTRIC_FURNACE.get(), (ElectricFurnaceMenu m, Inventory inv, Component t) ->
                new ProcessingScreen<>(m, inv, t, PowerFactor.id("textures/gui/electric_furnace.png"), 79, 34, 16, false,
                        com.powerfactor.registry.ModBlocks.ELECTRIC_FURNACE.get()));
        event.register(ModMenus.ENGINE.get(), EngineScreen::new);
        event.register(ModMenus.CHARGER.get(), ChargerScreen::new);
        event.register(ModMenus.SCADA_TERMINAL.get(), ScadaTerminalScreen::new);
        for (var m : com.powerfactor.machine.RecipeMachine.values()) {
            event.register(ModMenus.RECIPE_MACHINES.get(m).get(), (com.powerfactor.machine.RecipeMachineMenu menu, Inventory inv, Component t) ->
                    new RecipeMachineScreen(menu, inv, t));
        }
        event.register(ModMenus.AUTO_CAPACITOR_BANK.get(), AutoCapacitorScreen::new);
        event.register(ModMenus.ELECTRIC_PUMP.get(), PumpScreen::new);
    }
}
