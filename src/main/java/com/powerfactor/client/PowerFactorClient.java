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
        com.powerfactor.item.ToolPayloads.meterHandler = ClampMeterHud::onReading;
        com.powerfactor.item.ToolPayloads.jetpackHandler = JetpackClient::onSync;
        com.powerfactor.movement.MovementPayloads.wingHandler = MovementClient::wingSync;
        com.powerfactor.logistics.PipeTraffic.clientHandler = PipeTrafficClient::receive;
        com.powerfactor.radio.RadioPayload.clientHandler = RadioHud::receive;
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
        ChainsawSound.clientTick();
        JetpackClient.clientTick();
        MovementClient.tick();
        RoverClient.tick();
        PowerArmorClient.tick();
        ClampMeterHud.clientTick();
        PipeTrafficClient.clientTick();
    }

    @SubscribeEvent
    static void onFluidModels(net.neoforged.neoforge.client.event.RegisterFluidModelsEvent event) {
        for (var fluid : java.util.List.of(com.powerfactor.registry.ModFluids.STEAM, com.powerfactor.registry.ModFluids.FLOWING_STEAM,
                com.powerfactor.registry.ModFluids.HOT_WATER, com.powerfactor.registry.ModFluids.FLOWING_HOT_WATER)) {
            boolean steam = fluid == com.powerfactor.registry.ModFluids.STEAM || fluid == com.powerfactor.registry.ModFluids.FLOWING_STEAM;
            event.register(new net.minecraft.client.renderer.block.FluidModel.Unbaked(
                    new net.minecraft.client.resources.model.sprite.Material(com.powerfactor.PowerFactor.id(steam ? "block/steam_still" : "block/hot_water_still")),
                    new net.minecraft.client.resources.model.sprite.Material(com.powerfactor.PowerFactor.id(steam ? "block/steam_still" : "block/hot_water_still")), null,
                    (net.neoforged.neoforge.client.fluid.FluidTintSource) null), fluid);
        }
        event.register(new net.minecraft.client.renderer.block.FluidModel.Unbaked(
                new net.minecraft.client.resources.model.sprite.Material(com.powerfactor.PowerFactor.id("block/hydrogen_still")),
                new net.minecraft.client.resources.model.sprite.Material(com.powerfactor.PowerFactor.id("block/hydrogen_flow")), null,
                (net.neoforged.neoforge.client.fluid.FluidTintSource) null), com.powerfactor.registry.ModFluids.HYDROGEN,
                com.powerfactor.registry.ModFluids.FLOWING_HYDROGEN);
    }

    @SubscribeEvent
    static void onPictureInPicture(net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent event) {
        event.register(MachineView.State.class, MachineView.Renderer::new);
        event.register(SideConfigView.State.class, SideConfigView.Renderer::new);
    }

    @SubscribeEvent
    static void onLiquidModels(net.neoforged.neoforge.client.event.RegisterFluidModelsEvent event) {
        for (var liquid : com.powerfactor.registry.ModFluids.LIQUIDS) {
            int tint = liquid.tint();
            event.register(new net.minecraft.client.renderer.block.FluidModel.Unbaked(
                    new net.minecraft.client.resources.model.sprite.Material(net.minecraft.resources.Identifier.withDefaultNamespace("block/water_still")),
                    new net.minecraft.client.resources.model.sprite.Material(net.minecraft.resources.Identifier.withDefaultNamespace("block/water_flow")),
                    new net.minecraft.client.resources.model.sprite.Material(net.minecraft.resources.Identifier.withDefaultNamespace("block/water_overlay")),
                    (net.neoforged.neoforge.client.fluid.FluidTintSource) state -> tint), liquid.source(), liquid.flowing());
        }
    }

    @SubscribeEvent
    static void onKeys(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) {
        event.registerCategory(JetpackClient.CATEGORY);
        event.register(JetpackClient.HOVER_KEY);
        event.register(PowerArmorClient.SETTINGS);
        event.register(MovementClient.GRAPPLE); event.register(MovementClient.GLIDE);
    }

    @SubscribeEvent
    static void onRenderStates(net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent event) {
        event.registerAvatarEntityModifier(new net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier() {
            @Override public <T extends net.minecraft.world.entity.Avatar & net.minecraft.client.entity.ClientAvatarEntity> void accept(T avatar,
                    net.minecraft.client.renderer.entity.state.AvatarRenderState state) {
                if (com.powerfactor.item.WingsuitItem.worn(avatar.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST))) {
                    float open = MovementClient.wingOpen(state.id, state.partialTick);
                    state.setRenderData(WingsuitPose.OPEN, open);
                    if (open > 0.02F) state.leftArmPose = state.rightArmPose = WingsuitPose.POSE.getValue();
                } else state.setRenderData(WingsuitPose.OPEN, null);
            }
        });
    }

    @SubscribeEvent
    static void onCamera(net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles event) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null && player.isFallFlying() && com.powerfactor.item.WingsuitItem.worn(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST))) {
            event.setRoll(event.getRoll() + MovementClient.bank((float) event.getPartialTick()) * 0.35F);
        }
    }

    @SubscribeEvent
    static void onGuiLayers(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) {
        event.registerAbove(net.neoforged.neoforge.client.gui.VanillaGuiLayers.HOTBAR, com.powerfactor.PowerFactor.id("power_armor"), PowerArmorClient::render);
        event.registerAbove(net.neoforged.neoforge.client.gui.VanillaGuiLayers.HOTBAR, com.powerfactor.PowerFactor.id("movement"), MovementClient::render);
        event.registerAbove(net.neoforged.neoforge.client.gui.VanillaGuiLayers.HOTBAR, com.powerfactor.PowerFactor.id("rover"), RoverClient::render);
        event.registerAbove(net.neoforged.neoforge.client.gui.VanillaGuiLayers.HOTBAR, com.powerfactor.PowerFactor.id("jetpack"),
                JetpackClient::renderHud);
        event.registerAbove(net.neoforged.neoforge.client.gui.VanillaGuiLayers.CROSSHAIR, com.powerfactor.PowerFactor.id("clamp_meter"),
                ClampMeterHud::render);
        event.registerAbove(net.neoforged.neoforge.client.gui.VanillaGuiLayers.HOTBAR, com.powerfactor.PowerFactor.id("radio"), RadioHud::render);
    }

    @SubscribeEvent
    static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            net.minecraft.client.renderer.entity.player.AvatarRenderer<net.minecraft.client.player.AbstractClientPlayer> renderer =
                    event.getPlayerRenderer(skin);
            if (renderer != null) {
                renderer.addLayer(new JetpackLayer(renderer, event.getEntityModels()));
                renderer.addLayer(new PowerArmorLayer(renderer, event.getEntityModels()));
                renderer.addLayer(new WingsuitLayer(renderer, event.getEntityModels()));
                renderer.addLayer(new GrappleWristLayer(renderer, event.getEntityModels()));
            }
        }
    }

    @SubscribeEvent
    static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(EngineRenderer.LAYER, EngineRenderer::createLayer);
        event.registerLayerDefinition(JetpackLayer.LAYER, JetpackLayer::createLayer);
        event.registerLayerDefinition(PowerArmorLayer.LAYER, PowerArmorLayer::createLayer);
        event.registerLayerDefinition(WingsuitLayer.LAYER, WingsuitLayer::createLayer);
        event.registerLayerDefinition(GrappleHookRenderer.LAYER, GrappleHookRenderer::createLayer);
        event.registerLayerDefinition(GrappleWristLayer.LAYER, GrappleWristLayer::createLayer);
        event.registerLayerDefinition(RoverRenderer.LAYER, RoverRenderer::createLayer);
        event.registerLayerDefinition(PumpjackRenderer.LAYER, PumpjackRenderer::createLayer);
        event.registerLayerDefinition(WorkshopRenderer.LAYER, WorkshopRenderer::createLayer);
        event.registerLayerDefinition(SteamPlantRenderer.LAYER, SteamPlantRenderer::createLayer);
        event.registerLayerDefinition(LaserEngraverRenderer.LAYER, LaserEngraverRenderer::createLayer);
        event.registerLayerDefinition(TurbineRotorRenderer.LAYER, TurbineRotorRenderer::createLayer);
        event.registerLayerDefinition(EnderCoreRenderer.LAYER, EnderCoreRenderer::createLayer);
        event.registerLayerDefinition(WindTurbineRenderer.LAYER, WindTurbineRenderer::createLayer);
        event.registerLayerDefinition(CrusherJawRenderer.LAYER, CrusherJawRenderer::createLayer);
        event.registerLayerDefinition(SpinningRenderers.HYDRO, SpinningRenderers::hydroLayer);
        event.registerLayerDefinition(SpinningRenderers.CONDENSER, SpinningRenderers::condenserLayer);
        event.registerLayerDefinition(PowerConnections.LAYER, PowerConnections::createLayer);
        event.registerLayerDefinition(IndustrialRenderer.LAYER, IndustrialRenderer::createLayer);
    }

    @SubscribeEvent
    static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(com.powerfactor.registry.ModEntities.GRAPPLE_HOOK.get(), GrappleHookRenderer::new);
        event.registerEntityRenderer(com.powerfactor.registry.ModEntities.ELECTRIC_ROVER.get(), RoverRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.STEAM_BOILER.get(), PowerConnections.wrap(SteamPlantRenderer::boiler));
        event.registerBlockEntityRenderer(ModBlockEntities.STEAM_TURBINE.get(), PowerConnections.wrap(SteamPlantRenderer::turbine));
        event.registerBlockEntityRenderer(ModBlockEntities.COOLING_TOWER.get(), PowerConnections.wrap(CoolingTowerRenderer::new));
        event.registerBlockEntityRenderer(ModBlockEntities.RAIN_COLLECTOR.get(), PowerConnections.wrap(RainCollectorRenderer::new));
        event.registerBlockEntityRenderer(ModBlockEntities.ENGINE.get(), PowerConnections.wrap(EngineRenderer::new));
        event.registerBlockEntityRenderer(ModBlockEntities.COAL_TURBINE.get(), PowerConnections.wrap(TurbineRotorRenderer::new));
        event.registerBlockEntityRenderer(ModBlockEntities.ELECTRIC_FURNACE.get(), PowerConnections.wrap(IndustrialRenderer::new));
        event.registerBlockEntityRenderer(ModBlockEntities.CHARGER.get(), PowerConnections.wrap(IndustrialRenderer::new));
        event.registerBlockEntityRenderer(ModBlockEntities.FLUID_TANK.get(), FluidTankRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SCADA_DISPLAY.get(), ScadaDisplayRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ENDER_LINK.get(), PowerConnections.wrap(EnderCoreRenderer::new));
        event.registerBlockEntityRenderer(ModBlockEntities.WIND_TURBINE.get(), PowerConnections.wrap(WindTurbineRenderer::new));
        event.registerBlockEntityRenderer(ModBlockEntities.HYDRO_TURBINE.get(), PowerConnections.wrap(SpinningRenderers::hydro));
        event.registerBlockEntityRenderer(ModBlockEntities.SYNCHRONOUS_CONDENSER.get(), PowerConnections.wrap(SpinningRenderers::condenser));
        event.registerBlockEntityRenderer(ModBlockEntities.DIESEL_GENERATOR.get(), PowerConnections.wrap(IndustrialRenderer::new));
        event.registerBlockEntityRenderer(ModBlockEntities.ELECTRIC_PUMP.get(), PowerConnections.wrap(IndustrialRenderer::new));
        event.registerBlockEntityRenderer(ModBlockEntities.FUEL_CELL.get(), PowerConnections.wrap(IndustrialRenderer::new));
        event.registerBlockEntityRenderer(ModBlockEntities.ELECTROLYZER.get(), PowerConnections.wrap(IndustrialRenderer::new));
        event.registerBlockEntityRenderer(ModBlockEntities.SOLAR_PANEL.get(), PowerConnections.only());
        event.registerBlockEntityRenderer(ModBlockEntities.CHARGING_PAD.get(), PowerConnections.only());
        event.registerBlockEntityRenderer(ModBlockEntities.GROW_LAMP.get(), PowerConnections.only());
        event.registerBlockEntityRenderer(ModBlockEntities.RADIO_TOWER.get(), PowerConnections.only());
        event.registerBlockEntityRenderer(ModBlockEntities.BATTERY_BANK.get(), PowerConnections.only());
        event.registerBlockEntityRenderer(ModBlockEntities.POWER_METER.get(), PowerConnections.only());
        event.registerBlockEntityRenderer(ModBlockEntities.GEOTHERMAL_GENERATOR.get(), PowerConnections.only());
        event.registerBlockEntityRenderer(ModBlockEntities.THERMOELECTRIC_GENERATOR.get(), PowerConnections.only());
        event.registerBlockEntityRenderer(ModBlockEntities.TRANSFORMER.get(), PowerConnections.only());
        event.registerBlockEntityRenderer(ModBlockEntities.CAPACITOR_BANK.get(), PowerConnections.only());
        event.registerBlockEntityRenderer(ModBlockEntities.AUTO_CAPACITOR_BANK.get(), PowerConnections.only());
        event.registerBlockEntityRenderer(ModBlockEntities.CASING.get(), PowerConnections.only());
        event.registerBlockEntityRenderer(ModBlockEntities.PYLON.get(), PylonRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CRUSHER.get(), PowerConnections.wrap(CrusherJawRenderer::new));
        event.registerBlockEntityRenderer(ModBlockEntities.TESLA_COIL.get(), PowerConnections.wrap(TeslaCoilRenderer::new));
        event.registerBlockEntityRenderer(ModBlockEntities.UPGRADE_TABLE.get(), UpgradeTableRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.PIPE.get(), PipeRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.PUMPJACK.get(), PowerConnections.wrap(PumpjackRenderer::new));
        for (var machine : com.powerfactor.machine.RecipeMachine.values()) {
            if (machine.animatedWorkshop()) {
                event.registerBlockEntityRenderer(ModBlockEntities.RECIPE_MACHINES.get(machine).get(), PowerConnections.wrap(WorkshopRenderer::new));
            } else if (machine != com.powerfactor.machine.RecipeMachine.LASER_ENGRAVER) {
                event.registerBlockEntityRenderer(ModBlockEntities.RECIPE_MACHINES.get(machine).get(), PowerConnections.wrap(IndustrialRenderer::new));
            }
        }
        event.registerBlockEntityRenderer(ModBlockEntities.REFINERY.get(), PowerConnections.wrap(RefineryRenderer::new));
        event.registerBlockEntityRenderer(ModBlockEntities.RECIPE_MACHINES.get(com.powerfactor.machine.RecipeMachine.LASER_ENGRAVER).get(),
                PowerConnections.wrap(LaserEngraverRenderer::new));
    }

    @SubscribeEvent
    static void onMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.STEAM_BOILER.get(), SteamBoilerScreen::new);
        for (var device : com.powerfactor.steam.ThermalMenu.Device.values()) event.register(ModMenus.THERMAL.get(device).get(), ThermalScreen::new);
        event.register(ModMenus.COAL_TURBINE.get(), CoalTurbineScreen::new);
        event.register(ModMenus.CRUSHER.get(), (CrusherMenu m, Inventory inv, Component t) ->
                new ProcessingScreen<>(m, inv, t, null, 93, 39, 25, true, com.powerfactor.registry.ModBlocks.CRUSHER.get()));
        event.register(ModMenus.ELECTRIC_FURNACE.get(), (ElectricFurnaceMenu m, Inventory inv, Component t) ->
                new ProcessingScreen<>(m, inv, t, null, 93, 39, 16, false,
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
        event.register(ModMenus.ELECTROLYZER.get(), GeneratorScreens.Electrolyzer::new);
        event.register(ModMenus.FUEL_CELL.get(), GeneratorScreens.FuelCell::new);
        event.register(ModMenus.UPGRADE_TABLE.get(), UpgradeTableScreen::new);
        event.register(ModMenus.PUMPJACK.get(), GeneratorScreens.Pumpjack::new);
        event.register(ModMenus.REFINERY.get(), GeneratorScreens.Refinery::new);
    }
}
