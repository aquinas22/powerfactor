package com.powerfactor;

import com.mojang.logging.LogUtils;
import com.powerfactor.gametest.PowerFactorGameTests;
import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.registry.ModBlocks;
import com.powerfactor.registry.ModComponents;
import com.powerfactor.registry.ModItems;
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
        ModComponents.COMPONENTS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModRecipes.register(modBus);
        ModTabs.TABS.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, PowerFactorConfig.SPEC);
        PowerFactorGameTests.register(modBus);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
