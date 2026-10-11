package com.powerfactor.registry;

import com.mojang.serialization.Codec;
import com.powerfactor.PowerFactor;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PowerFactor.MODID);

    /** FE stored in a tool. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ENERGY = COMPONENTS.registerComponentType("energy",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Fluid carried by a tank item. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.neoforged.neoforge.fluids.SimpleFluidContent>> FLUID =
            COMPONENTS.registerComponentType("fluid", b -> b.persistent(net.neoforged.neoforge.fluids.SimpleFluidContent.CODEC)
                    .networkSynchronized(net.neoforged.neoforge.fluids.SimpleFluidContent.STREAM_CODEC));

    private ModComponents() {}
}
