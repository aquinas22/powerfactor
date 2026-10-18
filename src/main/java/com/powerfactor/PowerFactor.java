package com.powerfactor;

import com.mojang.logging.LogUtils;
import com.powerfactor.gametest.PowerFactorGameTests;
import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.registry.ModBlocks;
import com.powerfactor.registry.ModComponents;
import com.powerfactor.registry.ModItems;
import com.powerfactor.registry.ModConditions;
import com.powerfactor.registry.ModMaterials;
import com.powerfactor.registry.ModMenus;
import com.powerfactor.registry.ModRecipes;
import com.powerfactor.registry.ModSounds;
import com.powerfactor.registry.ModTabs;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(PowerFactor.MODID)
public class PowerFactor {
    public static final String MODID = "powerfactor";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PowerFactor(IEventBus modBus, ModContainer container) {
        modBus.addListener((net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent e) ->
                e.register(com.powerfactor.util.ChunkLoaderBlockEntity.TICKETS));
        modBus.addListener((net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent e) ->
                e.register(com.powerfactor.pocket.PocketRooms.TICKETS));
        modBus.addListener((net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent e) ->
                e.register(com.powerfactor.upgrade.ChunkCard.TICKETS));
        modBus.addListener((net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent e) ->
                e.register(com.powerfactor.powernet.PowerNets.TICKETS));
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e) ->
                e.enqueueWork(com.powerfactor.fission.FissionCherenkov::register));
        ModMaterials.register();
        com.powerfactor.registry.ModStorage.init();
        com.powerfactor.registry.ModQuantum.init();
        com.powerfactor.registry.ModDefense.init();
        com.powerfactor.registry.ModLaser.init();
        com.powerfactor.registry.ModChem.init();
        com.powerfactor.registry.ModWash.init();
        com.powerfactor.registry.ModUtility.init();
        com.powerfactor.registry.ModTime.init();
        com.powerfactor.registry.ModTesseract.init();
        com.powerfactor.registry.ModCharging.init();
        com.powerfactor.registry.ModFarm.init();
        com.powerfactor.registry.ModMob.init();
        com.powerfactor.registry.ModPocket.init();
        com.powerfactor.registry.ModFission.init();
        com.powerfactor.registry.ModBlackHole.init();
        com.powerfactor.registry.ModDrone.init();
        com.powerfactor.registry.ModVoid.init();
        com.powerfactor.registry.ModPowerNet.init();
        com.powerfactor.registry.ModRedstone.init();
        ModConditions.CONDITIONS.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, PowerFactorCommonConfig.SPEC);
        ModComponents.COMPONENTS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        com.powerfactor.registry.ModFluids.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        com.powerfactor.registry.ModEntities.ENTITIES.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModMenus.MENUS.register(modBus);
        com.powerfactor.registry.ModWorldgen.FEATURES.register(modBus);
        ModRecipes.register(modBus);
        ModTabs.TABS.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, PowerFactorConfig.SPEC);
        PowerFactorGameTests.register(modBus);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
