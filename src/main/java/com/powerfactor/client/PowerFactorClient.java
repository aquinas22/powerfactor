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
        com.powerfactor.fission.ReactorPayloads.clientHandler = com.powerfactor.client.fission.ReactorScreen::accept;
        com.powerfactor.blackhole.BlackHolePayloads.clientHandler = com.powerfactor.client.blackhole.BlackHoleScreen::accept;
        com.powerfactor.drone.DronePayloads.clientHandler = com.powerfactor.client.drone.DroneHubScreen::accept;
        com.powerfactor.storage.GridPayloads.syncHandler = com.powerfactor.client.storage.ClientGrid::receive;
        com.powerfactor.weapon.FxPayload.handler = com.powerfactor.client.fx.WorldFx::receive;
        com.powerfactor.logistics.GravityLiftBlockEntity.localPlayerHook = lift -> {
            var me = net.minecraft.client.Minecraft.getInstance().player;
            if (me != null) lift.carry(me);
        };
        ScadaPayloads.displayHandler = ScadaDisplayRenderer::receive;
        com.powerfactor.item.ToolPayloads.meterHandler = ClampMeterHud::onReading;
        com.powerfactor.item.ToolPayloads.jetpackHandler = JetpackClient::onSync;
        com.powerfactor.movement.MovementPayloads.wingHandler = MovementClient::wingSync;
        com.powerfactor.client.fission.GeigerClient.init();
        com.powerfactor.logistics.PipeTraffic.clientHandler = PipeTrafficClient::receive;
        com.powerfactor.radio.RadioPayload.clientHandler = RadioHud::receive;
        com.powerfactor.api.MachineAudio.handler = MachineSoundLoop::update;
        com.powerfactor.item.HandbookItem.open = () -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(new HandbookScreen());
        com.powerfactor.item.ArcSaberItem.openScreen = hand -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(new SaberScreen(hand));
    }

    /** Names the GUI icon under the mouse on every PowerFactor screen. Runs before the screen draws, using the icon hit-boxes
     * Skin.icon recorded last frame, so the tooltip joins this frame's deferred tooltip pass. */
    @SubscribeEvent
    public static void iconTooltips(net.neoforged.neoforge.client.event.ScreenEvent.Render.Pre event) {
        if (event.getScreen().getClass().getName().startsWith("com.powerfactor.")) {
            Skin.iconTooltips(event.getGuiGraphics(), net.minecraft.client.Minecraft.getInstance().font, event.getMouseX(), event.getMouseY());
        }
    }

    @SubscribeEvent
    static void onItemExtensions(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        event.registerItem(new PowerArmorModels(), com.powerfactor.registry.ModItems.POWER_HELMET.get(),
                com.powerfactor.registry.ModItems.POWER_CHESTPLATE.get(), com.powerfactor.registry.ModItems.POWER_LEGGINGS.get(),
                com.powerfactor.registry.ModItems.POWER_BOOTS.get());
    }

    @SubscribeEvent
    static void onSpecialModels(net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent event) {
        event.register(com.powerfactor.PowerFactor.id("pocket_void"), com.powerfactor.client.pocketvoid.PocketVoidRenderer.Unbaked.MAP_CODEC);
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    static void onRecipes(net.neoforged.neoforge.client.event.RecipesReceivedEvent event) {
        com.powerfactor.compat.jei.ClientRecipes.map = event.getRecipeMap();
    }

    @SubscribeEvent
    static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        com.powerfactor.storage.TerminalMenu.clientGuiHeight = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight();
        SaberHum.clientTick();
        ChainsawSound.clientTick();
        JetpackClient.clientTick();
        MovementClient.tick();
        ElevatorClient.tick();
        com.powerfactor.client.fission.GeigerClient.tick();
        RoverClient.tick();
        HoverBikeClient.tick();
        PowerArmorClient.tick();
        AgentWorkbenchClient.tick();
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
        event.register(HudPreferences.TOGGLE);
        event.register(MovementClient.GRAPPLE); event.register(MovementClient.GLIDE);
        event.register(VeinClient.KEY);
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
    static void onMovementFov(net.neoforged.neoforge.client.event.ComputeFovModifierEvent event) {
        if(event.getPlayer()==net.minecraft.client.Minecraft.getInstance().player)
            event.setNewFovModifier(event.getNewFovModifier()*(1+MovementClient.flightFov()*event.getFovScale()));
    }

    @SubscribeEvent
    static void onGuiLayers(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) {
        event.registerAbove(net.neoforged.neoforge.client.gui.VanillaGuiLayers.HOTBAR, com.powerfactor.PowerFactor.id("power_armor"), PowerArmorClient::render);
        event.registerAbove(net.neoforged.neoforge.client.gui.VanillaGuiLayers.HOTBAR, com.powerfactor.PowerFactor.id("movement"), MovementClient::render);
        event.registerAbove(net.neoforged.neoforge.client.gui.VanillaGuiLayers.HOTBAR, com.powerfactor.PowerFactor.id("rover"), RoverClient::render);
        event.registerAbove(net.neoforged.neoforge.client.gui.VanillaGuiLayers.HOTBAR, com.powerfactor.PowerFactor.id("hover_bike"), HoverBikeClient::render);
        event.registerAbove(net.neoforged.neoforge.client.gui.VanillaGuiLayers.HOTBAR, com.powerfactor.PowerFactor.id("mech"), HoverBikeClient::renderMech);
        event.registerAbove(net.neoforged.neoforge.client.gui.VanillaGuiLayers.HOTBAR, com.powerfactor.PowerFactor.id("jetpack"),
                JetpackClient::renderHud);
        event.registerAbove(net.neoforged.neoforge.client.gui.VanillaGuiLayers.CROSSHAIR, com.powerfactor.PowerFactor.id("clamp_meter"),
                ClampMeterHud::render);
        event.registerAbove(net.neoforged.neoforge.client.gui.VanillaGuiLayers.HOTBAR, com.powerfactor.PowerFactor.id("radio"), RadioHud::render);
        event.registerAbove(net.neoforged.neoforge.client.gui.VanillaGuiLayers.HOTBAR, com.powerfactor.PowerFactor.id("geiger"),
                com.powerfactor.client.fission.GeigerClient::render);
        event.registerAbove(net.neoforged.neoforge.client.gui.VanillaGuiLayers.CROSSHAIR, com.powerfactor.PowerFactor.id("weapon_reticle"),
                com.powerfactor.client.weapon.WeaponClient::renderHud);
        event.registerAbove(net.neoforged.neoforge.client.gui.VanillaGuiLayers.CROSSHAIR, com.powerfactor.PowerFactor.id("vein_miner"), VeinClient::renderHud);
    }

    @SubscribeEvent
    static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        PowerArmorModels.invalidate();
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
        event.registerLayerDefinition(PowerArmorModel.LAYER, PowerArmorModel::createLayer);
        event.registerLayerDefinition(PowerArmorModel.BABY_LAYER, PowerArmorModel::createBabyLayer);
        event.registerLayerDefinition(WingsuitLayer.LAYER, WingsuitLayer::createLayer);
        event.registerLayerDefinition(GrappleHookRenderer.LAYER, GrappleHookRenderer::createLayer);
        event.registerLayerDefinition(GrappleWristLayer.LAYER, GrappleWristLayer::createLayer);
        event.registerLayerDefinition(RoverRenderer.LAYER, RoverRenderer::createLayer);
        event.registerLayerDefinition(PumpjackRenderer.LAYER, PumpjackRenderer::createLayer);
        event.registerLayerDefinition(WorkshopRenderer.LAYER, WorkshopRenderer::createLayer);
        event.registerLayerDefinition(SteamPlantRenderer.LAYER, SteamPlantRenderer::createLayer);
        event.registerLayerDefinition(LaserEngraverRenderer.LAYER, LaserEngraverRenderer::createLayer);
        event.registerLayerDefinition(RobotArmRenderer.LAYER, RobotArmRenderer::createLayer);
        event.registerLayerDefinition(HoverBikeRenderer.LAYER, HoverBikeRenderer::createLayer);
        event.registerLayerDefinition(MechRenderer.LAYER, MechRenderer::createLayer);
        event.registerLayerDefinition(com.powerfactor.client.quantum.OrbitalCollectorRenderer.LAYER, com.powerfactor.client.quantum.OrbitalCollectorRenderer::createLayer);
        event.registerLayerDefinition(com.powerfactor.client.weapon.MeteorRenderer.LAYER, com.powerfactor.client.weapon.MeteorRenderer::createLayer);
        event.registerLayerDefinition(com.powerfactor.client.defense.TurretRenderer.PLASMA, com.powerfactor.client.defense.TurretRenderer::plasmaLayer);
        event.registerLayerDefinition(com.powerfactor.client.defense.TurretRenderer.RAIL, com.powerfactor.client.defense.TurretRenderer::railLayer);
        event.registerLayerDefinition(TurbineRotorRenderer.LAYER, TurbineRotorRenderer::createLayer);
        event.registerLayerDefinition(EnderCoreRenderer.LAYER, EnderCoreRenderer::createLayer);
        event.registerLayerDefinition(WindTurbineRenderer.LAYER, WindTurbineRenderer::createLayer);
        event.registerLayerDefinition(CrusherJawRenderer.LAYER, CrusherJawRenderer::createLayer);
        event.registerLayerDefinition(SpinningRenderers.HYDRO, SpinningRenderers::hydroLayer);
        event.registerLayerDefinition(SpinningRenderers.CONDENSER, SpinningRenderers::condenserLayer);
        event.registerLayerDefinition(PowerConnections.LAYER, PowerConnections::createLayer);
        event.registerLayerDefinition(IndustrialRenderer.LAYER, IndustrialRenderer::createLayer);
        event.registerLayerDefinition(InstrumentRenderer.LAYER, InstrumentRenderer::createLayer);
    }

    @SubscribeEvent
    static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(com.powerfactor.registry.ModEntities.GRAPPLE_HOOK.get(), GrappleHookRenderer::new);
        event.registerEntityRenderer(com.powerfactor.registry.ModDrone.DRONE.get(), com.powerfactor.client.drone.MiningDroneRenderer::new);
        event.registerEntityRenderer(com.powerfactor.registry.ModQuantum.PLASMA_BOLT.get(), com.powerfactor.client.weapon.WeaponRenderers.Plasma::new);
        event.registerEntityRenderer(com.powerfactor.registry.ModQuantum.METEOR.get(), com.powerfactor.client.weapon.MeteorRenderer::new);
        event.registerEntityRenderer(com.powerfactor.registry.ModQuantum.SINGULARITY_ENTITY.get(), com.powerfactor.client.weapon.WeaponRenderers.Singularity::new);
        event.registerEntityRenderer(com.powerfactor.registry.ModEntities.ELECTRIC_ROVER.get(), RoverRenderer::new);
        event.registerEntityRenderer(com.powerfactor.registry.ModEntities.HOVER_BIKE.get(), HoverBikeRenderer::new);
        event.registerEntityRenderer(com.powerfactor.registry.ModEntities.MECH.get(), MechRenderer::new);
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModQuantum.STARFORGE_BE.get(), com.powerfactor.client.quantum.StarforgeRenderer::new);
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModQuantum.FUSION_REACTOR_BE.get(), com.powerfactor.client.quantum.FusionRenderer::new);
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModQuantum.ORBITAL_COLLECTOR_BE.get(), PowerConnections.wrap(com.powerfactor.client.quantum.OrbitalCollectorRenderer::new));
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModQuantum.SPACE_ELEVATOR_BE.get(), PowerConnections.wrap(com.powerfactor.client.quantum.SpaceElevatorRenderer::new));
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModQuantum.GRAVITY_LIFT_BE.get(), PowerConnections.wrap(GravityLiftRenderer::new));
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModQuantum.ROBOT_ARM_BE.get(), PowerConnections.wrap(RobotArmRenderer::new));
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModQuantum.TELEPORTER_BE.get(), PowerConnections.wrap(com.powerfactor.client.quantum.TeleporterRenderer::new));
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModLaser.LASER_NODE_BE.get(), LaserBeamRenderer::new);
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModDefense.SHIELD_GENERATOR_BE.get(), PowerConnections.wrap(com.powerfactor.client.defense.ShieldRenderer::new));
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModDefense.PLASMA_TURRET_BE.get(), PowerConnections.wrap(com.powerfactor.client.defense.TurretRenderer::new));
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModDefense.RAIL_TURRET_BE.get(), PowerConnections.wrap(com.powerfactor.client.defense.TurretRenderer::new));
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModQuantum.COLLIDER_BE.get(), PowerConnections.wrap(com.powerfactor.client.quantum.QuantumRenderers.Collider::new));
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModQuantum.ZERO_POINT_BE.get(), PowerConnections.wrap(com.powerfactor.client.quantum.QuantumRenderers.ZeroPoint::new));
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModQuantum.QUARRY_BE.get(), PowerConnections.wrap(com.powerfactor.client.quantum.QuarryRenderer::new));
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModQuantum.QUANTUM_QUARRY_BE.get(), PowerConnections.wrap(com.powerfactor.client.quantum.QuarryRenderer::new));
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModStorage.CONTROLLER_BE.get(), com.powerfactor.client.storage.StorageRenderers.Controller::new);
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModStorage.DRIVE_BAY_BE.get(), com.powerfactor.client.storage.StorageRenderers.Drive::new);
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModStorage.PROCESSOR_BE.get(), com.powerfactor.client.storage.StorageRenderers.Processor::new);
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModStorage.QUANTUM_PROCESSOR_BE.get(), com.powerfactor.client.storage.StorageRenderers.Processor::new);
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModStorage.ASSEMBLER_BE.get(), com.powerfactor.client.storage.StorageRenderers.Assembler::new);
        event.registerBlockEntityRenderer(ModBlockEntities.STEAM_BOILER.get(), PowerConnections.wrap(SteamPlantRenderer::boiler));
        event.registerBlockEntityRenderer(ModBlockEntities.STEAM_TURBINE.get(), PowerConnections.wrap(SteamPlantRenderer::turbine));
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModFission.HP_TURBINE_BE.get(), PowerConnections.wrap(SteamPlantRenderer::highPressure));
        event.registerBlockEntityRenderer(ModBlockEntities.COOLING_TOWER.get(), PowerConnections.wrap(CoolingTowerRenderer::new));
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModPowerNet.NODE_BE.get(), com.powerfactor.client.powernet.PowerNodeRenderer::new);
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
        event.registerBlockEntityRenderer(ModBlockEntities.SOLAR_PANEL.get(), AmbientMachineRenderer.of(AmbientMachineRenderer.Kind.SOLAR));
        event.registerBlockEntityRenderer(ModBlockEntities.CHARGING_PAD.get(), AmbientMachineRenderer.of(AmbientMachineRenderer.Kind.CHARGING_PAD));
        event.registerBlockEntityRenderer(ModBlockEntities.GROW_LAMP.get(), AmbientMachineRenderer.of(AmbientMachineRenderer.Kind.GROW_LAMP));
        event.registerBlockEntityRenderer(ModBlockEntities.RADIO_TOWER.get(), AmbientMachineRenderer.of(AmbientMachineRenderer.Kind.RADIO));
        event.registerBlockEntityRenderer(ModBlockEntities.BATTERY_BANK.get(), PowerConnections.wrap(InstrumentRenderer::new));
        event.registerBlockEntityRenderer(ModBlockEntities.POWER_METER.get(), PowerConnections.wrap(InstrumentRenderer::new));
        event.registerBlockEntityRenderer(ModBlockEntities.GEOTHERMAL_GENERATOR.get(), AmbientMachineRenderer.of(AmbientMachineRenderer.Kind.GEOTHERMAL));
        event.registerBlockEntityRenderer(ModBlockEntities.THERMOELECTRIC_GENERATOR.get(), AmbientMachineRenderer.of(AmbientMachineRenderer.Kind.THERMOELECTRIC));
        event.registerBlockEntityRenderer(ModBlockEntities.TRANSFORMER.get(), AmbientMachineRenderer.of(AmbientMachineRenderer.Kind.TRANSFORMER));
        event.registerBlockEntityRenderer(ModBlockEntities.CAPACITOR_BANK.get(), AmbientMachineRenderer.of(AmbientMachineRenderer.Kind.CAPACITOR));
        event.registerBlockEntityRenderer(ModBlockEntities.AUTO_CAPACITOR_BANK.get(), AmbientMachineRenderer.of(AmbientMachineRenderer.Kind.CAPACITOR));
        event.registerBlockEntityRenderer(ModBlockEntities.CASING.get(), PowerConnections.only());
        event.registerBlockEntityRenderer(ModBlockEntities.PYLON.get(), PylonRenderer::new);
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModUtility.CHUNK_LOADER_BE.get(), ChunkLoaderRenderer::new);
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModTesseract.TESSERACT_BE.get(), com.powerfactor.client.tesseract.TesseractRenderer::new);
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModFarm.FARM_GANTRY_BE.get(), PowerConnections.wrap(com.powerfactor.client.farm.FarmGantryRenderer::new));
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModMob.SPAWNER_CAGE_BE.get(), PowerConnections.wrap(com.powerfactor.client.mob.SpawnerCageRenderer::new));
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModMob.MOB_GRINDER_BE.get(), PowerConnections.wrap(com.powerfactor.client.mob.MobGrinderRenderer::new));
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModPocket.POCKET_BE.get(), com.powerfactor.client.pocket.PocketRenderer::new);
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModFission.REACTOR_CONTROLLER_BE.get(), com.powerfactor.client.fission.ReactorRenderer::new);
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModBlackHole.CONTROLLER_BE.get(), com.powerfactor.client.blackhole.BlackHoleRenderer::new);
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModDrone.HUB_BE.get(), PowerConnections.wrap(com.powerfactor.client.drone.DroneHubRenderer::new));
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModFission.CENTRIFUGE_BE.get(), PowerConnections.wrap(com.powerfactor.client.fission.CentrifugeRenderer::new));
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModCharging.WIRELESS_CHARGER_BE.get(), PowerConnections.wrap(com.powerfactor.client.charging.ChargerRenderer::new));
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModCharging.DIMENSIONAL_CHARGER_BE.get(), PowerConnections.wrap(com.powerfactor.client.charging.ChargerRenderer::new));
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModTime.TIME_FIELD_BE.get(),
                PowerConnections.wrap(com.powerfactor.client.dilation.TimeFieldRenderer::new));
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModWash.WASH_PLANT_BE.get(), PowerConnections.wrap(com.powerfactor.client.wash.WashPlantRenderer::new));
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModChem.CHEMICAL_REACTOR_BE.get(), PowerConnections.wrap(com.powerfactor.client.chem.ChemRenderers.Reactor::new));
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModChem.ORE_DISSOLVER_BE.get(), PowerConnections.wrap(com.powerfactor.client.chem.ChemRenderers.Dissolver::new));
        event.registerBlockEntityRenderer(com.powerfactor.registry.ModQuantum.LOGIC_CONTROLLER_BE.get(), AmbientMachineRenderer.of(AmbientMachineRenderer.Kind.LOGIC));
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
        event.register(com.powerfactor.registry.ModVoid.MENU.get(), com.powerfactor.client.pocketvoid.PocketVoidScreen::new);
        event.register(com.powerfactor.registry.ModPowerNet.MENU.get(), com.powerfactor.client.powernet.PowerNetScreen::new);
        event.register(ModMenus.PIPE_ROUTING.get(), PipeScreen::new);
        event.register(com.powerfactor.registry.ModQuantum.QUARRY_MENU.get(), com.powerfactor.client.quantum.QuarryScreen::new);
        event.register(com.powerfactor.registry.ModQuantum.STARFORGE_MENU.get(), com.powerfactor.client.quantum.StarforgeScreen::new);
        event.register(com.powerfactor.registry.ModQuantum.COLLIDER_MENU.get(), com.powerfactor.client.quantum.QuantumScreens.Collider::new);
        event.register(com.powerfactor.registry.ModQuantum.ZERO_POINT_MENU.get(), com.powerfactor.client.quantum.QuantumScreens.ZeroPoint::new);
        event.register(com.powerfactor.registry.ModQuantum.ORBITAL_COLLECTOR_MENU.get(), com.powerfactor.client.quantum.OrbitalCollectorScreen::new);
        event.register(com.powerfactor.registry.ModQuantum.SPACE_ELEVATOR_MENU.get(), com.powerfactor.client.quantum.SpaceElevatorScreen::new);
        event.register(com.powerfactor.registry.ModQuantum.LOGIC_CONTROLLER_MENU.get(), LogicControllerScreen::new);
        event.register(com.powerfactor.registry.ModQuantum.FUSION_MENU.get(), com.powerfactor.client.quantum.FusionScreen::new);
        event.register(com.powerfactor.registry.ModLaser.LASER_NODE_MENU.get(), LaserNodeScreen::new);
        event.register(com.powerfactor.registry.ModChem.CHEMICAL_REACTOR_MENU.get(), com.powerfactor.client.chem.ChemScreens.Reactor::new);
        event.register(com.powerfactor.registry.ModWash.WASH_PLANT_MENU.get(), com.powerfactor.client.wash.WashPlantScreen::new);
        event.register(com.powerfactor.registry.ModTime.TIME_FIELD_MENU.get(), com.powerfactor.client.dilation.TimeFieldScreen::new);
        event.register(com.powerfactor.registry.ModTesseract.TESSERACT_MENU.get(), com.powerfactor.client.tesseract.TesseractScreen::new);
        event.register(com.powerfactor.registry.ModFarm.FARM_GANTRY_MENU.get(), com.powerfactor.client.farm.FarmGantryScreen::new);
        event.register(com.powerfactor.registry.ModMob.SPAWNER_CAGE_MENU.get(), com.powerfactor.client.mob.SpawnerCageScreen::new);
        event.register(com.powerfactor.registry.ModMob.MOB_GRINDER_MENU.get(), com.powerfactor.client.mob.MobGrinderScreen::new);
        event.register(com.powerfactor.registry.ModFission.REACTOR_MENU.get(), com.powerfactor.client.fission.ReactorScreen::new);
        event.register(com.powerfactor.registry.ModBlackHole.MENU.get(), com.powerfactor.client.blackhole.BlackHoleScreen::new);
        event.register(com.powerfactor.registry.ModDrone.HUB_MENU.get(), com.powerfactor.client.drone.DroneHubScreen::new);
        event.register(com.powerfactor.registry.ModFission.CENTRIFUGE_MENU.get(), com.powerfactor.client.fission.CentrifugeScreen::new);
        event.register(com.powerfactor.registry.ModChem.ORE_DISSOLVER_MENU.get(), com.powerfactor.client.chem.ChemScreens.Dissolver::new);
        event.register(com.powerfactor.registry.ModDefense.SHIELD_MENU.get(), com.powerfactor.client.defense.ShieldScreen::new);
        event.register(com.powerfactor.registry.ModDefense.TURRET_MENU.get(), com.powerfactor.client.defense.TurretScreen::new);
        event.register(com.powerfactor.registry.ModStorage.TERMINAL_MENU.get(), com.powerfactor.client.storage.TerminalScreen::new);
        event.register(com.powerfactor.registry.ModStorage.FLUID_TERMINAL_MENU.get(), com.powerfactor.client.storage.FluidTerminalScreen::new);
        event.register(com.powerfactor.registry.ModStorage.DRIVE_BAY_MENU.get(), com.powerfactor.client.storage.StorageScreens.DriveBay::new);
        event.register(com.powerfactor.registry.ModStorage.IO_PORT_MENU.get(), com.powerfactor.client.storage.StorageScreens.IoPort::new);
        event.register(com.powerfactor.registry.ModStorage.PATTERN_RACK_MENU.get(), com.powerfactor.client.storage.StorageScreens.PatternRack::new);
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
