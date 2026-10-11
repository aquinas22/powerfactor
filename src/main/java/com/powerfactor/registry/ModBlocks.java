package com.powerfactor.registry;

import com.powerfactor.PowerFactor;
import com.powerfactor.engine.EngineBlock;
import com.powerfactor.engine.EngineKind;
import com.powerfactor.grid.WireBlock;
import com.powerfactor.machine.AutoCapacitorBankBlock;
import com.powerfactor.machine.CapacitorBankBlock;
import com.powerfactor.machine.ChargerBlock;
import com.powerfactor.machine.CoalTurbineBlock;
import com.powerfactor.machine.CrusherBlock;
import com.powerfactor.machine.ElectricFurnaceBlock;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.scada.FiberCableBlock;
import com.powerfactor.scada.ScadaTerminalBlock;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PowerFactor.MODID);

    // ---------------------------------------------------------------- materials
    public static final DeferredBlock<DropExperienceBlock> ZINC_ORE = BLOCKS.registerBlock("zinc_ore",
            p -> new DropExperienceBlock(ConstantInt.of(0), p),
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops().strength(3.0F, 3.0F));
    public static final DeferredBlock<DropExperienceBlock> DEEPSLATE_ZINC_ORE = BLOCKS.registerBlock("deepslate_zinc_ore",
            p -> new DropExperienceBlock(ConstantInt.of(0), p),
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops().strength(4.5F, 3.0F).sound(SoundType.DEEPSLATE));
    public static final DeferredBlock<Block> RAW_ZINC_BLOCK = BLOCKS.registerSimpleBlock("raw_zinc_block",
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops().strength(5.0F, 6.0F));
    public static final DeferredBlock<Block> ZINC_BLOCK = BLOCKS.registerSimpleBlock("zinc_block", ModBlocks::metal);
    public static final DeferredBlock<Block> BRASS_BLOCK = BLOCKS.registerSimpleBlock("brass_block", ModBlocks::metal);

    // ---------------------------------------------------------------- machines
    public static final DeferredBlock<CoalTurbineBlock> COAL_TURBINE = BLOCKS.registerBlock("coal_turbine", CoalTurbineBlock::new,
            () -> machine().noOcclusion().lightLevel(s -> s.getValue(MachineBlock.RUNNING) ? 9 : 0));
    public static final DeferredBlock<CrusherBlock> CRUSHER = BLOCKS.registerBlock("crusher", CrusherBlock::new, ModBlocks::machine);
    public static final DeferredBlock<ElectricFurnaceBlock> ELECTRIC_FURNACE = BLOCKS.registerBlock("electric_furnace", ElectricFurnaceBlock::new,
            () -> machine().lightLevel(s -> s.getValue(MachineBlock.RUNNING) ? 11 : 0));
    public static final DeferredBlock<ChargerBlock> CHARGER = BLOCKS.registerBlock("charger", ChargerBlock::new,
            () -> machine().lightLevel(s -> s.getValue(MachineBlock.RUNNING) ? 6 : 0));
    public static final DeferredBlock<CapacitorBankBlock> CAPACITOR_BANK = BLOCKS.registerBlock("capacitor_bank", CapacitorBankBlock::new,
            () -> machine().noOcclusion().lightLevel(s -> s.getValue(MachineBlock.RUNNING) ? 6 : 0));
    public static final DeferredBlock<AutoCapacitorBankBlock> AUTO_CAPACITOR_BANK = BLOCKS.registerBlock("auto_capacitor_bank", AutoCapacitorBankBlock::new,
            () -> machine().noOcclusion().lightLevel(s -> s.getValue(AutoCapacitorBankBlock.STEPS) > 0 ? 3 + s.getValue(AutoCapacitorBankBlock.STEPS) : 0));

    // ---------------------------------------------------------------- BuildCraft-style engines
    public static final DeferredBlock<EngineBlock> REDSTONE_ENGINE = BLOCKS.registerBlock("redstone_engine",
            p -> new EngineBlock(EngineKind.REDSTONE, p),
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredBlock<EngineBlock> STIRLING_ENGINE = BLOCKS.registerBlock("stirling_engine",
            p -> new EngineBlock(EngineKind.STIRLING, p),
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.0F).sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion());

    // ---------------------------------------------------------------- wiring and SCADA
    public static final DeferredBlock<WireBlock> COPPER_WIRE = BLOCKS.registerBlock("copper_wire",
            p -> new WireBlock(p, 512),
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(0.5F).sound(SoundType.COPPER).noOcclusion());
    public static final DeferredBlock<FiberCableBlock> FIBER_OPTIC_CABLE = BLOCKS.registerBlock("fiber_optic_cable", FiberCableBlock::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.5F).sound(SoundType.WOOL).noOcclusion());
    public static final DeferredBlock<ScadaTerminalBlock> SCADA_TERMINAL = BLOCKS.registerBlock("scada_terminal", ScadaTerminalBlock::new,
            () -> machine().lightLevel(s -> 7));

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                .requiresCorrectToolForDrops().strength(5.0F, 6.0F).sound(SoundType.METAL);
    }

    private static BlockBehaviour.Properties machine() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops();
    }

    private ModBlocks() {}
}
