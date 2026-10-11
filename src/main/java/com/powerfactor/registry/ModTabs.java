package com.powerfactor.registry;

import com.powerfactor.PowerFactor;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PowerFactor.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> POWERFACTOR = TABS.register("powerfactor", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.powerfactor"))
            .icon(() -> new ItemStack(ModItems.COAL_TURBINE.get()))
            .displayItems((params, output) -> ModItems.ITEMS.getEntries().forEach(holder -> output.accept(holder.get())))
            .build());

    private ModTabs() {}
}
