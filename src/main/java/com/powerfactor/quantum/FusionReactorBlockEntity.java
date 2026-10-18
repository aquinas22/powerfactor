package com.powerfactor.quantum;

import com.powerfactor.device.HydrogenTanks;
import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.multiblock.CasingBlock;
import com.powerfactor.multiblock.CasingBlockEntity;
import com.powerfactor.multiblock.Structure;
import com.powerfactor.registry.ModFluids;
import com.powerfactor.registry.ModQuantum;
import com.powerfactor.scada.ScadaReading;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Fusion Reactor: a 5x5x3 tokamak. Hydrogen is piped into any casing; a 50M FE ignition pulse heats
 * the plasma past ignition, after which it burns hydrogen and pays out up to 250,000 FE/t. Starve it
 * of fuel and the plasma cools and quenches; it must be ignited again.
 */
public class FusionReactorBlockEntity extends MachineBlockEntity implements com.powerfactor.multiblock.Buildable {
    public static final Structure STRUCTURE = new Structure.Builder()
            .key('S', ModQuantum.FUSION_CASING)
            .key('M', ModQuantum.FUSION_MAGNET)
            .key('G', ModQuantum.FUSION_GLASS)
            .layer(-1, "SSSSS", "SSSSS", "SSSSS", "SSSSS", "SSSSS")
            .layer(0, "MMCMM", "M...M", "M...M", "M...M", "MMMMM")
            .layer(1, "SSSSS", "SGGGS", "SGGGS", "SGGGS", "SSSSS")
            .build();

    public static final int CAPACITY = 100_000_000, MAX_IO = 1_000_000, TANK = 32_000;
    public static final long IGNITION_ENERGY = 50_000_000L;
    public static final int RATED = 250_000, BURN = 8;
    public static final int STATE_COLD = 0, STATE_IGNITING = 1, STATE_BURNING = 2;
    public static final int DATA_STATE = 0, DATA_TEMP = 1, DATA_OUT_HI = 2, DATA_OUT_LO = 3, DATA_FUEL = 4, DATA_ENERGY = 5, DATA_FORMED = 6,
            DATA_COUNT = 7;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CAPACITY, MAX_IO, MAX_IO) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private final HydrogenTanks tank = new HydrogenTanks(TANK, new net.minecraft.world.level.material.Fluid[] {ModFluids.HYDROGEN.get()},
            new boolean[] {false}, this::setChanged);
    private final Set<BlockPos> linked = new HashSet<>();
    private int state, out, ticks;
    /** Plasma temperature, 0..1 (1 = 150 MK). */
    private float temp;
    private long injected;
    private float clientTemp;
    private int clientState;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case DATA_STATE -> state;
                case DATA_TEMP -> Math.round(temp * 1000);
                case DATA_OUT_HI -> out >>> 16;
                case DATA_OUT_LO -> out & 0xFFFF;
                case DATA_FUEL -> tank.amount(0);
                case DATA_ENERGY -> energy.getAmountAsInt() / 10_000;
                case DATA_FORMED -> formed() ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public FusionReactorBlockEntity(BlockPos pos, BlockState state) {
        super(ModQuantum.FUSION_REACTOR_BE.get(), pos, state);
    }

    public SimpleEnergyHandler getEnergy() {
        return energy;
    }

    public HydrogenTanks getTank() {
        return tank;
    }

    @Override
    public Structure buildStructure() {
        return STRUCTURE;
    }

    private Direction facingDir() {
        return getBlockState().getValue(com.powerfactor.machine.MachineBlock.FACING);
    }

    public boolean formed() {
        return getBlockState().getValue(CasingBlock.FORMED);
    }

    /** Centre of the plasma chamber, relative to the controller block's origin (for the renderer). */
    public Vec3 chamberOffset() {
        BlockPos c = STRUCTURE.worldPos(BlockPos.ZERO, facingDir(), new Structure.Part(new BlockPos(0, 0, 2), ModQuantum.FUSION_CASING));
        return new Vec3(c.getX() + 0.5, 0.5, c.getZ() + 0.5);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState bs) {
        ticks++;
        if (ticks % 20 == 0) checkStructure(level, pos, bs);
        boolean run = formed() && enabled(level, pos);
        out = 0;
        if (!run) {
            cool(level, pos, 0.01F);
        } else if (state == STATE_BURNING) {
            int need = Math.max(1, Math.round(BURN * temp));
            if (tank.amount(0) >= need) {
                tank.add(0, -need);
                temp = Math.min(1F, temp + 0.002F);
                out = Math.round(RATED * temp * temp);
                energy.set((int) Math.min(CAPACITY, (long) energy.getAmountAsInt() + out));
            } else {
                cool(level, pos, 0.004F);
            }
        } else if (tank.amount(0) >= 1000) {
            // Ignition: pour energy into the plasma until it lights.
            int draw = Math.min(MAX_IO, energy.getAmountAsInt());
            if (draw > 0) {
                if (state == STATE_COLD) level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1F, 0.5F);
                state = STATE_IGNITING;
                energy.set(energy.getAmountAsInt() - draw);
                injected += draw;
                temp = Math.max(temp, 0.6F * Math.min(1F, (float) injected / IGNITION_ENERGY));
                if (injected >= IGNITION_ENERGY) {
                    state = STATE_BURNING;
                    injected = 0;
                    level.playSound(null, pos, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 1.2F, 0.6F);
                    level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5F, 0.5F);
                    Vec3 c = Vec3.atLowerCornerOf(pos).add(chamberOffset());
                    level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 80, 1.0, 0.3, 1.0, 0.25);
                    com.powerfactor.weapon.FxPayload.send(level, new com.powerfactor.weapon.FxPayload(com.powerfactor.weapon.FxPayload.SHOCKWAVE, c, c, 0xFFB18CFF, 2.5F, -1));
                }
            } else if (state == STATE_IGNITING) {
                cool(level, pos, 0.002F);
            }
        } else {
            cool(level, pos, 0.004F);
        }
        if (state == STATE_BURNING) push(level, pos);
        setRunning(level, pos, bs, state == STATE_BURNING);
        if (state == STATE_BURNING && ticks % 60 == 0) level.playSound(null, pos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 1F, 0.5F + temp * 0.4F);
        if (ticks % 5 == 0) level.sendBlockUpdated(pos, bs, bs, Block.UPDATE_CLIENTS);
    }

    private void cool(ServerLevel level, BlockPos pos, float rate) {
        temp = Math.max(0, temp - rate);
        if (state == STATE_BURNING && temp < 0.5F) {
            state = STATE_COLD;
            injected = 0;
            level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.2F, 0.5F);
        }
        if (state == STATE_IGNITING && temp <= 0) {
            state = STATE_COLD;
            injected = 0;
        }
    }

    private void push(ServerLevel level, BlockPos pos) {
        List<EnergyHandler> targets = new ArrayList<>();
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (linked.contains(n)) continue;
            EnergyHandler h = level.getCapability(Capabilities.Energy.BLOCK, n, d.getOpposite());
            if (h != null) targets.add(h);
        }
        for (int i = 0; i < targets.size() && energy.getAmountAsInt() > 0; i++) {
            int share = Math.max(1, energy.getAmountAsInt() / (targets.size() - i));
            try (Transaction tx = Transaction.openRoot()) {
                int moved = targets.get(i).insert(Math.min(share, MAX_IO), tx);
                tx.commit();
                energy.set(energy.getAmountAsInt() - moved);
            }
        }
    }

    private void checkStructure(ServerLevel level, BlockPos pos, BlockState bs) {
        if (!STRUCTURE.loaded(level, pos, facingDir())) return;
        boolean now = STRUCTURE.complete(level, pos, facingDir());
        Set<BlockPos> parts = new HashSet<>();
        if (now) for (var p : STRUCTURE.parts()) parts.add(STRUCTURE.worldPos(pos, facingDir(), p));
        for (BlockPos p : linked) {
            if (!parts.contains(p) && level.getBlockEntity(p) instanceof CasingBlockEntity c && pos.equals(c.controllerPos())) c.link(null);
        }
        for (BlockPos p : parts) if (level.getBlockEntity(p) instanceof CasingBlockEntity c) c.link(pos);
        linked.clear();
        linked.addAll(parts);
        if (bs.getValue(CasingBlock.FORMED) != now) {
            level.playSound(null, pos, now ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1F, 0.7F);
            level.setBlock(pos, bs.setValue(CasingBlock.FORMED, now), Block.UPDATE_ALL);
            if (!now) {
                state = STATE_COLD;
                temp = 0;
                injected = 0;
            }
        }
    }

    /** Only a real removal frees the casings: setRemoved also runs when the chunk unloads, and must not touch other blocks then. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && !level.isClientSide()) CasingBlockEntity.release(level, linked, worldPosition);
    }

    public float clientTemp() {
        return clientTemp;
    }

    public int clientState() {
        return clientState;
    }

    @Override
    protected double animationRate() {
        return 0.2 + clientTemp * 2;
    }

    @Override
    public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), state == STATE_BURNING ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF,
                -out, 0, Component.translatable("scada.powerfactor.fusion", Math.round(temp * 150), tank.amount(0)), true, remoteEnabled(), true,
                1F, 60F);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new FusionMenu(containerId, inventory, this, data);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        tank.deserialize(input.childOrEmpty("tank"));
        state = input.getIntOr("plasma_state", STATE_COLD);
        temp = input.getFloatOr("temp", 0);
        injected = input.getLongOr("injected", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        tank.serialize(output.child("tank"));
        output.putInt("plasma_state", state);
        output.putFloat("temp", temp);
        output.putLong("injected", injected);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putFloat("temp", temp);
        tag.putInt("plasma_state", state);
        return tag;
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        clientTemp = input.getFloatOr("temp", 0);
        clientState = input.getIntOr("plasma_state", 0);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ValueInput input) {
        handleUpdateTag(input);
    }
}
