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
            .displayItems((params, output) -> {
                var quantum = new java.util.HashSet<Object>();
                ModStorage.TAB.forEach(i -> quantum.add(i.get()));
                ModItems.ITEMS.getEntries().forEach(holder -> {
                    if (!quantum.contains(holder.get())) output.accept(holder.get());
                });
            })
            .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> QUANTUM = TABS.register("quantum", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.powerfactor.quantum"))
            .icon(() -> new ItemStack(ModStorage.QUANTUM_CRAFTING_PROCESSOR.get()))
            .withTabsBefore(POWERFACTOR.getKey())
            .displayItems((params, output) -> ModStorage.TAB.forEach(i -> output.accept(i.get())))
            .build());

    private ModTabs() {}
}
