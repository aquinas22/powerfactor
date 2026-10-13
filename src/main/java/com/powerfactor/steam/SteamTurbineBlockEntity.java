package com.powerfactor.steam;

import com.powerfactor.api.Bus;
import com.powerfactor.api.GridGenerator;
import com.powerfactor.api.MachineAudio;
import com.powerfactor.api.ReactiveLoad;
import com.powerfactor.animation.RotaryMotion;
import com.powerfactor.device.HydrogenTanks;
import com.powerfactor.generator.PushingGenerator;
import com.powerfactor.grid.GridManager;
import com.powerfactor.grid.PowerGrid;
import com.powerfactor.grid.WireBlock;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.multiblock.Buildable;
import com.powerfactor.multiblock.MultiblockPart;
import com.powerfactor.multiblock.Structure;
import com.powerfactor.multiblock.StructureAssembly;
import com.powerfactor.power.BoilerModel;
import com.powerfactor.power.BusSolver;
import com.powerfactor.power.TurbineModel;
import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.registry.ModBlocks;
import com.powerfactor.registry.ModFluids;
import com.powerfactor.registry.ModSounds;
import com.powerfactor.scada.ScadaReading;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import org.jspecify.annotations.Nullable;

/** 3x2x5 turbine/alternator: steam energy is spent on FE and rotor loss, condensate returns as hot water. */
public final class SteamTurbineBlockEntity extends PushingGenerator implements GridGenerator, Bus, Buildable, MachineAudio.Looping {
    public static final int RATED_FE = 800, RATED_CE = 1000, TANK = 32000, BUFFER = 128000;
    public static final int STEAM = 0, CONDENSATE = 1, CHAMBER_FE = 3200;
    public static final Structure STRUCTURE = new Structure.Builder()
            .key('B', ModBlocks.TURBINE_CASING).key('R', ModBlocks.STEAM_ROTOR).key('M', ModBlocks.GENERATOR_MAGNET)
            .layer(0, "BCB", "BBB", "BBB", "BBB", "BBB")
            .layer(1, "BBB", "BRB", "BRB", "BMB", "BBB").build();
    private final StructureAssembly assembly = new StructureAssembly();
    private final TurbineModel turbine = new TurbineModel(new TurbineModel.Spec(RATED_FE, RATED_CE, 20, 200, 600));
    private final HydrogenTanks tanks = new HydrogenTanks(TANK,
            new net.minecraft.world.level.material.Fluid[] {ModFluids.STEAM.get(), ModFluids.HOT_WATER.get()}, new boolean[] {false, true}, this::setChanged);
    private long condensateEnergy;
    private boolean valveOpen;
    private int lastSteam, syncedRpm, clientRpm;
    private int outputCursor;
    private final RotaryMotion motion = new RotaryMotion();
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case ThermalMenu.DATA_FE -> lastFe;
                case ThermalMenu.DATA_EE -> (int) Math.round(turbine.lastOutput().ee());
                case ThermalMenu.DATA_RPM -> turbine.rpm();
                case ThermalMenu.DATA_TANK -> tanks.amount(STEAM);
                case ThermalMenu.DATA_TANK2 -> tanks.amount(CONDENSATE);
                case ThermalMenu.DATA_STORED -> buffer.getAmountAsInt() / 10;
                case ThermalMenu.DATA_FLAGS -> (online() ? 1 : 0) | (formed() ? 2 : 0) | (tanks.amount(CONDENSATE) > TANK - 5 ? 4 : 0);
                case ThermalMenu.DATA_REDSTONE -> redstoneMode().ordinal();
                case ThermalMenu.DATA_RATE -> lastSteam;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {}
        @Override public int getCount() { return ThermalMenu.DATA_COUNT; }
    };

    public SteamTurbineBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.STEAM_TURBINE.get(), pos, state, BUFFER); }
    @Override public Structure buildStructure() { return STRUCTURE; }
    public boolean formed() { return assembly.formed(); }
    public TurbineModel model() { return turbine; }
    public HydrogenTanks getTanks() { return tanks; }
    public long condensateRemainderFe() { return condensateEnergy; }
    private boolean online() { return formed() && valveOpen && turbine.breakerClosed(); }

    @Override public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        assembly.tick(level, pos, state.getValue(MachineBlock.FACING), STRUCTURE);
        valveOpen = formed() && enabled(level, pos) && tanks.amount(CONDENSATE) <= TANK - 5;
        if (valveOpen) {
            int steam = (int) Math.min(tanks.amount(STEAM), Math.max(0, CHAMBER_FE - turbine.fuelEnergy()) / BoilerModel.FE_PER_STEAM);
            if (steam > 0) {
                tanks.add(STEAM, -steam);
                turbine.addFuel((long) steam * BoilerModel.FE_PER_STEAM);
            }
        }
        long before = turbine.fuelEnergy();
        boolean breaker = turbine.breakerClosed();
        int generated = turbine.tick(valveOpen, buffer.getCapacityAsInt() - buffer.getAmountAsInt(), eeDemand(level, pos));
        generate(generated);
        long spent = before - turbine.fuelEnergy();
        lastSteam = (int) (spent / BoilerModel.FE_PER_STEAM);
        condensateEnergy += spent;
        int water = (int) (condensateEnergy / (BoilerModel.FE_PER_STEAM * BoilerModel.EXPANSION));
        if (water > 0) {
            tanks.add(CONDENSATE, water);
            condensateEnergy %= BoilerModel.FE_PER_STEAM * BoilerModel.EXPANSION;
        }
        push(level, pos);
        setRunning(level, pos, getBlockState(), formed() && turbine.rpm() > 0);
        if (breaker != turbine.breakerClosed()) {
            level.playSound(null, pos, turbine.breakerClosed() ? ModSounds.TURBINE_BREAKER_CLOSE.get() : ModSounds.TURBINE_BREAKER_OPEN.get(),
                    SoundSource.BLOCKS, 1F, 0.8F);
        }
        if (Math.abs(turbine.rpm() - syncedRpm) >= 90 || (turbine.rpm() == 0) != (syncedRpm == 0)) {
            syncedRpm = turbine.rpm();
            level.sendBlockUpdated(pos, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        if (spent > 0 || turbine.rpm() > 0) setChanged();
    }

    private double eeDemand(ServerLevel level, BlockPos pos) {
        Set<PowerGrid> grids = new HashSet<>();
        Set<Object> loads = new HashSet<>();
        double demand = 0;
        Set<BlockPos> ports = new HashSet<>(assembly.ports());
        ports.add(pos);
        for (BlockPos port : ports) {
            for (Direction side : Direction.values()) {
                BlockPos at = port.relative(side);
                if (ports.contains(at) || !level.hasChunkAt(at)) continue;
                if (level.getBlockState(at).getBlock() instanceof WireBlock) {
                    var grid = GridManager.get(level).gridAt(at);
                    if (grid != null && grids.add(grid)) demand += grid.eeShareFor(this);
                } else {
                    var entity = level.getBlockEntity(at);
                    if (entity instanceof MultiblockPart part) entity = part.controller();
                    if (entity instanceof ReactiveLoad load && loads.add(load)) demand += load.eeDemand();
                }
            }
        }
        return demand;
    }

    @Override protected void push(ServerLevel level, BlockPos pos) {
        if (buffer.getAmountAsInt() == 0) return;
        Set<BlockPos> ports = new HashSet<>(assembly.ports());
        ports.add(pos);
        var targets = new java.util.ArrayList<net.neoforged.neoforge.transfer.energy.EnergyHandler>();
        var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<net.neoforged.neoforge.transfer.energy.EnergyHandler, Boolean>());
        for (BlockPos port : ports) {
            for (Direction side : Direction.values()) {
                BlockPos at = port.relative(side);
                if (ports.contains(at) || !level.hasChunkAt(at)) continue;
                var entity = level.getBlockEntity(at);
                if (entity instanceof MultiblockPart part) entity = part.controller();
                if (entity instanceof PushingGenerator) continue;
                var target = level.getCapability(Capabilities.Energy.BLOCK, at, side.getOpposite());
                if (target != null && target != buffer && seen.add(target)) targets.add(target);
            }
        }
        com.powerfactor.machine.EnergyOut.distribute(buffer, targets, outputCursor++);
    }
    @Override public double ratedCe() { return online() ? RATED_CE : 0; }
    @Override public double hertz() { return online() ? turbine.hertz() : 0; }
    @Override public double eeCapacity() { return online() ? Math.sqrt(Math.max(0, (double) RATED_CE * RATED_CE - (double) lastFe * lastFe)) : 0; }
    @Override public BusSolver.Result state() {
        return online() ? BusSolver.solve(RATED_CE, 0, lastFe, turbine.lastOutput().ee(), 0, eeCapacity(), 1)
                : new BusSolver.Result(0, 0, 0, 0, 0, 0);
    }
    @Override public boolean hasMenu() { return true; }
    @Override public int comparatorSignal() { return online() ? 15 : 0; }
    @Override public boolean soundActive() { return isRunning(); }
    @Override public SoundEvent loopSound() { return ModSounds.STEAM_TURBINE_RUN.get(); }
    @Override public float loopVolume() { return 0.25F + 0.65F * (level != null && level.isClientSide() ? clientRpm : turbine.rpm()) / 3600F; }
    @Override public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), !formed() ? ScadaReading.Status.ALARM : online() ? ScadaReading.Status.RUNNING
                : tanks.amount(CONDENSATE) > TANK - 5 ? ScadaReading.Status.ALARM : ScadaReading.Status.WARNING,
                lastFe, (int) Math.round(turbine.lastOutput().ee()), Component.translatable(!formed() ? "scada.powerfactor.multiblock.incomplete"
                : "scada.powerfactor.steam_turbine", turbine.rpm(), tanks.amount(STEAM), tanks.amount(CONDENSATE)),
                true, remoteEnabled(), true, online() ? 1 : 0, (float) hertz());
    }
    @Override public void clientTick() { super.clientTick(); motion.tick(clientRpm / 3600.0 * 0.7, 0.1, 0.03); }
    public float rotorPhase(float partial) { return motion.phase(partial); }
    @Override public void setRemoved() {
        if (level instanceof ServerLevel server) assembly.release(server, worldPosition);
        super.setRemoved();
    }
    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tanks.deserialize(input.childOrEmpty("tanks"));
        turbine.load(input.getIntOr("rpm", 0), input.getBooleanOr("breaker", false), input.getLongOr("steam_energy", 0), input.getLongOr("steam_max", 0));
        condensateEnergy = Math.clamp(input.getLongOr("condensate_energy", 0), 0, 199);
        clientRpm = input.getIntOr("rpm", 0);
    }
    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tanks.serialize(output.child("tanks"));
        output.putInt("rpm", turbine.rpm());
        output.putBoolean("breaker", turbine.breakerClosed());
        output.putLong("steam_energy", turbine.fuelEnergy());
        output.putLong("steam_max", turbine.fuelEnergyMax());
        output.putLong("condensate_energy", condensateEnergy);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { var tag = new CompoundTag(); tag.putInt("rpm", turbine.rpm()); return tag; }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ThermalMenu(ThermalMenu.Device.STEAM_TURBINE, id, inventory, worldPosition, data, this);
    }
}
