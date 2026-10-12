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

    /** Modules installed in an electric drill. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.minecraft.world.item.component.ItemContainerContents>> DRILL_MODULES =
            COMPONENTS.registerComponentType("drill_modules", b -> b.persistent(net.minecraft.world.item.component.ItemContainerContents.CODEC)
                    .networkSynchronized(net.minecraft.world.item.component.ItemContainerContents.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.powerfactor.item.SaberSettings>> SABER =
            COMPONENTS.registerComponentType("saber", b -> b.persistent(com.powerfactor.item.SaberSettings.CODEC)
                    .networkSynchronized(com.powerfactor.item.SaberSettings.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.minecraft.util.Unit>> SABER_LIT =
            COMPONENTS.registerComponentType("saber_lit", b -> b.persistent(net.minecraft.util.Unit.CODEC)
                    .networkSynchronized(net.minecraft.util.Unit.STREAM_CODEC));

    /** First pylon picked with a conductor spool. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.minecraft.core.BlockPos>> LINK_START =
            COMPONENTS.registerComponentType("link_start", b -> b.persistent(net.minecraft.core.BlockPos.CODEC)
                    .networkSynchronized(net.minecraft.core.BlockPos.STREAM_CODEC));

    /** Fluid carried by a tank item. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.neoforged.neoforge.fluids.SimpleFluidContent>> FLUID =
            COMPONENTS.registerComponentType("fluid", b -> b.persistent(net.neoforged.neoforge.fluids.SimpleFluidContent.CODEC)
                    .networkSynchronized(net.neoforged.neoforge.fluids.SimpleFluidContent.STREAM_CODEC));

    /** Jetpack hover mode switched on. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> JETPACK_HOVER =
            COMPONENTS.registerComponentType("jetpack_hover", b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    /** Walkie-talkie channel (1-16) and power switch. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> RADIO_CHANNEL =
            COMPONENTS.registerComponentType("radio_channel", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> RADIO_ON =
            COMPONENTS.registerComponentType("radio_on", b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    private ModComponents() {}
}
