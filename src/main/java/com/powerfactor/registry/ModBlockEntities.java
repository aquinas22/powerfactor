package com.powerfactor.registry;

import com.powerfactor.PowerFactor;
import com.powerfactor.logistics.FluidTankBlockEntity;
import com.powerfactor.logistics.PipeBlockEntity;
import com.powerfactor.logistics.PumpBlockEntity;
import com.powerfactor.engine.EngineBlockEntity;
import com.powerfactor.machine.AutoCapacitorBankBlockEntity;
import com.powerfactor.machine.CapacitorBankBlockEntity;
import com.powerfactor.machine.ChargerBlockEntity;
import com.powerfactor.machine.CoalTurbineBlockEntity;
import com.powerfactor.machine.CrusherBlockEntity;
import com.powerfactor.machine.RecipeMachine;
import com.powerfactor.machine.RecipeMachineBlockEntity;
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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidTankBlockEntity>> FLUID_TANK = BLOCK_ENTITIES.register("fluid_tank",
            () -> new BlockEntityType<>(FluidTankBlockEntity::new, ModBlocks.FLUID_TANK.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PumpBlockEntity>> ELECTRIC_PUMP = BLOCK_ENTITIES.register("electric_pump",
            () -> new BlockEntityType<>(PumpBlockEntity::new, ModBlocks.ELECTRIC_PUMP.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PipeBlockEntity>> PIPE = BLOCK_ENTITIES.register("pipe",
            () -> new BlockEntityType<>(PipeBlockEntity::new, ModBlocks.FLUID_PIPE.get(), ModBlocks.ITEM_PIPE.get()));

    public static final java.util.Map<RecipeMachine, DeferredHolder<BlockEntityType<?>, BlockEntityType<RecipeMachineBlockEntity>>> RECIPE_MACHINES =
            new java.util.EnumMap<>(RecipeMachine.class);

    static {
        for (RecipeMachine m : RecipeMachine.values()) {
            RECIPE_MACHINES.put(m, BLOCK_ENTITIES.register(m.id,
                    () -> new BlockEntityType<>((pos, state) -> new RecipeMachineBlockEntity(m, pos, state), ModBlocks.RECIPE_MACHINES.get(m).get())));
        }
    }

    private ModBlockEntities() {}
}
