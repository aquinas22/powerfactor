package com.powerfactor.oil;

import com.powerfactor.api.MachineAudio;
import com.powerfactor.api.ReactiveLoad;
import com.powerfactor.device.HydrogenTanks;
import com.powerfactor.generator.DieselMenu;
import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.registry.ModFluids;
import com.powerfactor.registry.ModSounds;
import com.powerfactor.scada.ScadaReading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import org.jspecify.annotations.Nullable;

/**
 * Fractional distillation column: heats crude oil and splits it into diesel (heavier, drawn off
 * low) and jet fuel (kerosene, drawn off higher). Pipe crude in, pipe the fuels out.
 */
public class RefineryBlockEntity extends com.powerfactor.machine.MachineBlockEntity implements ReactiveLoad, MachineAudio.Looping {
    public static final int FE_PER_TICK = 100, CRUDE_PER_TICK = 10, DIESEL_PER_TICK = 5, JET_PER_TICK = 4, TANK = 16_000;
    public static final int CRUDE = 0, DIESEL = 1, JET = 2;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(20_000, 500, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private final HydrogenTanks tanks = new HydrogenTanks(TANK, new net.minecraft.world.level.material.Fluid[] {ModFluids.CRUDE_OIL.source().get(),
            ModFluids.DIESEL.source().get(), ModFluids.JET_FUEL.source().get()}, new boolean[] {false, true, true}, this::setChanged);
    private int lastFe;
    private boolean formed;
    private final java.util.Set<BlockPos> linked = new java.util.HashSet<>();

    /** 3x3 casing base with this controller front-centre, and a three-block distillation column in the middle. */
    public static final com.powerfactor.multiblock.Structure STRUCTURE = new com.powerfactor.multiblock.Structure.Builder()
            .key('B', com.powerfactor.registry.ModBlocks.REFINERY_CASING)
            .key('T', com.powerfactor.registry.ModBlocks.DISTILLATION_COLUMN)
            .layer(0, "BCB", "BBB", "BBB")
            .layer(1, "...", ".T.", "...")
            .layer(2, "...", ".T.", "...")
            .layer(3, "...", ".T.", "...")
            .build();

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case DieselMenu.DATA_FE -> lastFe;
                case DieselMenu.DATA_EE -> (int) Math.round(eeDemand());
                case DieselMenu.DATA_TANK -> tanks.amount(CRUDE);
                case DieselMenu.DATA_TANK2 -> tanks.amount(DIESEL);
                case DieselMenu.DATA_TANK3 -> tanks.amount(JET);
                case DieselMenu.DATA_STORED -> energy.getAmountAsInt() / 10;
                case DieselMenu.DATA_FLAGS -> (lastFe > 0 ? 1 : 0) | (formed ? 2 : 0);
                case DieselMenu.DATA_REDSTONE -> redstoneMode().ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return DieselMenu.DATA_COUNT;
        }
    };

    public RefineryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REFINERY.get(), pos, state);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        lastFe = 0;
        if (level.getGameTime() % 20 == pos.asLong() % 20 || linked.isEmpty() == formed) {
            checkStructure(level, pos, state);
        }
        boolean run = formed && enabled(level, pos) && energy.getAmountAsInt() >= FE_PER_TICK && tanks.amount(CRUDE) >= CRUDE_PER_TICK
                && tanks.amount(DIESEL) + DIESEL_PER_TICK <= TANK && tanks.amount(JET) + JET_PER_TICK <= TANK;
        if (run) {
            energy.set(energy.getAmountAsInt() - FE_PER_TICK);
            lastFe = FE_PER_TICK;
            tanks.add(CRUDE, -CRUDE_PER_TICK);
            tanks.add(DIESEL, DIESEL_PER_TICK);
            tanks.add(JET, JET_PER_TICK);
            if (level.getGameTime() % 6 == 0) {
                BlockPos top = pos.offset(new BlockPos(0, 4, 1).rotate(com.powerfactor.multiblock.Structure.rotation(facing())));
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, top.getX() + 0.5, top.getY() + 0.1, top.getZ() + 0.5, 1, 0.05, 0, 0.05, 0.01);
            }
        }
        setRunning(level, pos, state, run);
    }

    private net.minecraft.core.Direction facing() {
        return getBlockState().getValueOrElse(com.powerfactor.machine.MachineBlock.FACING, net.minecraft.core.Direction.NORTH);
    }

    public boolean formed() {
        return formed;
    }

    /** Form or break the structure, linking or releasing its casings. */
    private void checkStructure(ServerLevel level, BlockPos pos, BlockState state) {
        boolean now = STRUCTURE.complete(level, pos, facing());
        java.util.Set<BlockPos> parts = new java.util.HashSet<>();
        if (now) {
            for (var p : STRUCTURE.parts()) {
                parts.add(STRUCTURE.worldPos(pos, facing(), p));
            }
        }
        for (BlockPos p : linked) {
            if (!parts.contains(p) && level.getBlockEntity(p) instanceof com.powerfactor.multiblock.CasingBlockEntity c && pos.equals(c.controllerPos())) {
                c.link(null);
            }
        }
        for (BlockPos p : parts) {
            if (level.getBlockEntity(p) instanceof com.powerfactor.multiblock.CasingBlockEntity c) {
                c.link(pos);
            }
        }
        linked.clear();
        linked.addAll(parts);
        if (now != formed) {
            formed = now;
            level.playSound(null, pos, now ? com.powerfactor.registry.ModSounds.UPGRADE_INSTALL.get() : com.powerfactor.registry.ModSounds.UPGRADE_REMOVE.get(),
                    net.minecraft.sounds.SoundSource.BLOCKS, 1F, now ? 0.8F : 1F);
        }
        if (state.getValue(com.powerfactor.multiblock.CasingBlock.FORMED) != now) {
            level.setBlock(pos, state.setValue(com.powerfactor.multiblock.CasingBlock.FORMED, now), net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide()) {
            for (BlockPos p : linked) {
                if (level.getBlockEntity(p) instanceof com.powerfactor.multiblock.CasingBlockEntity c && worldPosition.equals(c.controllerPos())) {
                    c.link(null);
                }
            }
        }
    }

    /** Resistive heaters and a pump motor. */
    @Override
    public double eeDemand() {
        return lastFe > 0 ? 40 : 0;
    }

    public EnergyHandler getEnergy() {
        return energy;
    }

    public HydrogenTanks getTanks() {
        return tanks;
    }

    @Override
    public boolean soundActive() {
        return isRunning();
    }

    @Override
    public SoundEvent loopSound() {
        return ModSounds.REFINERY_RUN.get();
    }

    @Override
    public float loopVolume() {
        return 0.45F;
    }

    @Override
    public ScadaReading scadaReading() {
        if (!formed) {
            return new ScadaReading(worldPosition, getDisplayName(), ScadaReading.Status.WARNING, 0, 0,
                    Component.translatable("scada.powerfactor.multiblock.incomplete"), true, remoteEnabled(), false, 1F, 60F);
        }
        return new ScadaReading(worldPosition, getDisplayName(), lastFe > 0 ? ScadaReading.Status.RUNNING
                : tanks.amount(CRUDE) < CRUDE_PER_TICK ? ScadaReading.Status.WARNING : ScadaReading.Status.OFF, lastFe, (int) eeDemand(),
                Component.translatable("scada.powerfactor.refinery", tanks.amount(CRUDE), tanks.amount(DIESEL), tanks.amount(JET)), true,
                remoteEnabled(), false, 1F, 60F);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        tanks.deserialize(input.childOrEmpty("tanks"));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        tanks.serialize(output.child("tanks"));
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new DieselMenu.Refinery(id, inventory, worldPosition, data, this);
    }
}
