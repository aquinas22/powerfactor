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

    /** Vein mining, fitted to any digging tool in an anvil with a Vein Mining Matrix. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.minecraft.util.Unit>> VEIN_MINER =
            COMPONENTS.registerComponentType("vein_miner", b -> b.persistent(net.minecraft.util.Unit.CODEC)
                    .networkSynchronized(net.minecraft.util.Unit.STREAM_CODEC));

    /** First pylon picked with a conductor spool. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.minecraft.core.BlockPos>> LINK_START =
            COMPONENTS.registerComponentType("link_start", b -> b.persistent(net.minecraft.core.BlockPos.CODEC)
                    .networkSynchronized(net.minecraft.core.BlockPos.STREAM_CODEC));

    /** Fluid carried by a tank item. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.neoforged.neoforge.fluids.SimpleFluidContent>> FLUID =
            COMPONENTS.registerComponentType("fluid", b -> b.persistent(net.neoforged.neoforge.fluids.SimpleFluidContent.CODEC)
                    .networkSynchronized(net.neoforged.neoforge.fluids.SimpleFluidContent.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.powerfactor.logistics.PipeSettings>> PIPE_SETTINGS =
            COMPONENTS.registerComponentType("pipe_settings", b -> b.persistent(com.powerfactor.logistics.PipeSettings.CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(com.powerfactor.logistics.PipeSettings.CODEC)));

    /** Jetpack hover mode switched on. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> JETPACK_HOVER =
            COMPONENTS.registerComponentType("jetpack_hover", b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> ARMOR_ASSIST =
            COMPONENTS.registerComponentType("armor_assist", b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> ARMOR_VISOR =
            COMPONENTS.registerComponentType("armor_visor", b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> WINGSUIT_OPEN =
            COMPONENTS.registerComponentType("wingsuit_open", b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> VEHICLE_HEALTH =
            COMPONENTS.registerComponentType("vehicle_health", b -> b.persistent(Codec.floatRange(0, 80)).networkSynchronized(ByteBufCodecs.FLOAT));

    /** Walkie-talkie channel (1-16) and power switch. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> RADIO_CHANNEL =
            COMPONENTS.registerComponentType("radio_channel", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> RADIO_ON =
            COMPONENTS.registerComponentType("radio_on", b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    /** Items stored on a storage cell. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.powerfactor.storage.CellContents>> CELL_CONTENTS =
            COMPONENTS.registerComponentType("cell_contents", b -> b.persistent(com.powerfactor.storage.CellContents.CODEC)
                    .networkSynchronized(com.powerfactor.storage.CellContents.STREAM_CODEC));
    /** Fluids stored on a fluid cell. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.powerfactor.storage.fluid.FluidCellContents>> FLUID_CELL_CONTENTS =
            COMPONENTS.registerComponentType("fluid_cell_contents", b -> b.persistent(com.powerfactor.storage.fluid.FluidCellContents.CODEC)
                    .networkSynchronized(com.powerfactor.storage.fluid.FluidCellContents.STREAM_CODEC));
    /** An encoded crafting or processing pattern. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.powerfactor.storage.craft.PatternData>> PATTERN =
            COMPONENTS.registerComponentType("pattern", b -> b.persistent(com.powerfactor.storage.craft.PatternData.CODEC)
                    .networkSynchronized(com.powerfactor.storage.craft.PatternData.STREAM_CODEC));
    /** The Storage Controller a wireless terminal is linked to. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.minecraft.core.GlobalPos>> LINKED_CONTROLLER =
            COMPONENTS.registerComponentType("linked_controller", b -> b.persistent(net.minecraft.core.GlobalPos.CODEC)
                    .networkSynchronized(net.minecraft.core.GlobalPos.STREAM_CODEC));

    /** Plasma rifle coil heat, 0-100. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> WEAPON_HEAT =
            COMPONENTS.registerComponentType("weapon_heat", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    /** Selected mode of a multi-mode tool (the Quantum Disassembler). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TOOL_MODE =
            COMPONENTS.registerComponentType("tool_mode", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    private ModComponents() {}
}
