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
        ScadaPayloads.displayHandler = ScadaDisplayRenderer::receive;
        com.powerfactor.api.MachineAudio.handler = MachineSoundLoop::update;
        com.powerfactor.item.HandbookItem.open = () -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(new HandbookScreen());
        com.powerfactor.item.ArcSaberItem.openScreen = hand -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(new SaberScreen(hand));
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    static void onRecipes(net.neoforged.neoforge.client.event.RecipesReceivedEvent event) {
        com.powerfactor.compat.jei.ClientRecipes.map = event.getRecipeMap();
    }

    @SubscribeEvent
    static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        SaberHum.clientTick();
    }

    @SubscribeEvent
    static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(EngineRenderer.LAYER, EngineRenderer::createLayer);
        event.registerLayerDefinition(TurbineRotorRenderer.LAYER, TurbineRotorRenderer::createLayer);
        event.registerLayerDefinition(EnderCoreRenderer.LAYER, EnderCoreRenderer::createLayer);
        event.registerLayerDefinition(WindTurbineRenderer.LAYER, WindTurbineRenderer::createLayer);
        event.registerLayerDefinition(CrusherJawRenderer.LAYER, CrusherJawRenderer::createLayer);
        event.registerLayerDefinition(SpinningRenderers.HYDRO, SpinningRenderers::hydroLayer);
        event.registerLayerDefinition(SpinningRenderers.CONDENSER, SpinningRenderers::condenserLayer);
    }

    @SubscribeEvent
    static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.ENGINE.get(), EngineRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.COAL_TURBINE.get(), TurbineRotorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.FLUID_TANK.get(), FluidTankRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SCADA_DISPLAY.get(), ScadaDisplayRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ENDER_LINK.get(), EnderCoreRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.WIND_TURBINE.get(), WindTurbineRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.HYDRO_TURBINE.get(), SpinningRenderers::hydro);
        event.registerBlockEntityRenderer(ModBlockEntities.SYNCHRONOUS_CONDENSER.get(), SpinningRenderers::condenser);
        event.registerBlockEntityRenderer(ModBlockEntities.PYLON.get(), PylonRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CRUSHER.get(), CrusherJawRenderer::new);
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
        event.register(ModMenus.DRILL.get(), DrillScreen::new);
        event.register(ModMenus.ENDER_LINK.get(), EnderLinkScreen::new);
        event.register(ModMenus.DIESEL_GENERATOR.get(), GeneratorScreens.Diesel::new);
        event.register(ModMenus.BATTERY_BANK.get(), GeneratorScreens.Battery::new);
        event.register(ModMenus.POWER_METER.get(), GeneratorScreens.Meter::new);
    }
}
