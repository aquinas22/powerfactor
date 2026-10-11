package com.powerfactor.registry;

import com.powerfactor.PowerFactor;
import com.powerfactor.engine.EngineBlockEntity;
import com.powerfactor.machine.AutoCapacitorBankBlockEntity;
import com.powerfactor.machine.CapacitorBankBlockEntity;
import com.powerfactor.machine.ChargerBlockEntity;
import com.powerfactor.machine.CoalTurbineBlockEntity;
import com.powerfactor.machine.CrusherBlockEntity;
import com.powerfactor.machine.ElectricFurnaceBlockEntity;
import com.powerfactor.scada.ScadaTerminalBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PowerFactor.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CoalTurbineBlockEntity>> COAL_TURBINE = BLOCK_ENTITIES.register("coal_turbine",
            () -> new BlockEntityType<>(CoalTurbineBlockEntity::new, ModBlocks.COAL_TURBINE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EngineBlockEntity>> ENGINE = BLOCK_ENTITIES.register("engine",
            () -> new BlockEntityType<>(EngineBlockEntity::new, ModBlocks.REDSTONE_ENGINE.get(), ModBlocks.STIRLING_ENGINE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrusherBlockEntity>> CRUSHER = BLOCK_ENTITIES.register("crusher",
            () -> new BlockEntityType<>(CrusherBlockEntity::new, ModBlocks.CRUSHER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectricFurnaceBlockEntity>> ELECTRIC_FURNACE = BLOCK_ENTITIES.register("electric_furnace",
            () -> new BlockEntityType<>(ElectricFurnaceBlockEntity::new, ModBlocks.ELECTRIC_FURNACE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChargerBlockEntity>> CHARGER = BLOCK_ENTITIES.register("charger",
            () -> new BlockEntityType<>(ChargerBlockEntity::new, ModBlocks.CHARGER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CapacitorBankBlockEntity>> CAPACITOR_BANK = BLOCK_ENTITIES.register("capacitor_bank",
            () -> new BlockEntityType<>(CapacitorBankBlockEntity::new, ModBlocks.CAPACITOR_BANK.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutoCapacitorBankBlockEntity>> AUTO_CAPACITOR_BANK = BLOCK_ENTITIES.register("auto_capacitor_bank",
            () -> new BlockEntityType<>(AutoCapacitorBankBlockEntity::new, ModBlocks.AUTO_CAPACITOR_BANK.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ScadaTerminalBlockEntity>> SCADA_TERMINAL = BLOCK_ENTITIES.register("scada_terminal",
            () -> new BlockEntityType<>(ScadaTerminalBlockEntity::new, ModBlocks.SCADA_TERMINAL.get()));

    private ModBlockEntities() {}
}
