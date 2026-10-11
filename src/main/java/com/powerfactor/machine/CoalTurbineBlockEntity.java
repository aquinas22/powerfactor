package com.powerfactor.machine;

import com.powerfactor.PowerFactorConfig;
import com.powerfactor.api.Bus;
import com.powerfactor.api.GridGenerator;
import com.powerfactor.power.BusSolver;
import com.powerfactor.api.ReactiveLoad;
import com.powerfactor.grid.GridManager;
import com.powerfactor.grid.PowerGrid;
import com.powerfactor.grid.WireBlock;
import com.powerfactor.power.PowerTriangle;
import com.powerfactor.power.TurbineModel;
import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.registry.ModTags;
import com.powerfactor.scada.ScadaReading;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import org.jspecify.annotations.Nullable;

public class CoalTurbineBlockEntity extends MachineBlockEntity implements GridGenerator, Bus, com.powerfactor.api.MachineAudio.Looping {
    /** Used until the server config loads (e.g. a client-side instance). */
    private static final TurbineModel.Spec FALLBACK_SPEC = new TurbineModel.Spec(200, 250, 10, 200, 600);
    /** Degrees the rendered rotor turns per tick at 3600 RPM; real speed would just be a blur. */
    public static final float RENDER_DEGREES_PER_TICK_AT_SYNC = 54F;

    private final SimpleContainer fuel = new SimpleContainer(1) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return isFuel(stack);
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(CoalTurbineBlockEntity.this, player);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            CoalTurbineBlockEntity.this.setChanged();
        }
    };
    private final ResourceHandler<ItemResource> fuelHandler = VanillaContainerWrapper.of(fuel);

    /** Extract-only from outside; the turbine fills it directly. */
    private final SimpleEnergyHandler buffer = new SimpleEnergyHandler(
            PowerFactorConfig.SPEC.isLoaded() ? PowerFactorConfig.TURBINE_BUFFER.getAsInt() : 20_000, 0, Integer.MAX_VALUE) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };

    private TurbineModel model = new TurbineModel(FALLBACK_SPEC);
    private final Map<Direction, BlockCapabilityCache<EnergyHandler, @Nullable Direction>> outputs = new EnumMap<>(Direction.class);
    private int syncedRpm = -1;

    // Client-side rotor animation.
    private int clientRpm;
    private float rotorAngle;
    private float prevRotorAngle;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case CoalTurbineMenu.DATA_RPM -> model.rpm();
                case CoalTurbineMenu.DATA_FLAGS -> (model.breakerClosed() ? CoalTurbineMenu.FLAG_BREAKER : 0)
                        | (isRunning() ? CoalTurbineMenu.FLAG_STEAM : 0);
                case CoalTurbineMenu.DATA_FUEL_PERMILLE -> model.fuelEnergyMax() <= 0 ? 0 : (int) (1000 * model.fuelEnergy() / model.fuelEnergyMax());
                case CoalTurbineMenu.DATA_FE -> (int) model.lastOutput().fe();
                case CoalTurbineMenu.DATA_EE -> (int) Math.round(model.lastOutput().ee());
                case CoalTurbineMenu.DATA_STORED_LO -> buffer.getAmountAsInt() & 0xFFFF;
                case CoalTurbineMenu.DATA_STORED_HI -> buffer.getAmountAsInt() >>> 16;
                case CoalTurbineMenu.DATA_CAPACITY_LO -> buffer.getCapacityAsInt() & 0xFFFF;
                case CoalTurbineMenu.DATA_CAPACITY_HI -> buffer.getCapacityAsInt() >>> 16;
                case CoalTurbineMenu.DATA_VOLTS -> (int) Math.round(state().volts() * 1000);
                case CoalTurbineMenu.DATA_FLAG_OVERLOAD -> model.overloaded() ? 1 : 0;
                case CoalTurbineMenu.DATA_REDSTONE -> redstoneMode().ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return CoalTurbineMenu.DATA_COUNT;
        }
    };

    public CoalTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COAL_TURBINE.get(), pos, state);
    }

    public static boolean isFuel(ItemStack stack) {
        return stack.is(ModTags.TURBINE_FUELS);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        refreshSpec();
        boolean enabled = enabled(level, pos);

        if (enabled && model.needsFuel()) {
            ItemStack stack = fuel.getItem(0);
            int burnTicks = isFuel(stack) ? stack.getBurnTime(null, level.fuelValues()) : 0;
            if (burnTicks > 0) {
                model.addFuel((long) burnTicks * PowerFactorConfig.TURBINE_FE_PER_BURN_TICK.getAsInt());
                ItemStackTemplate remainder = stack.getCraftingRemainder();
                stack.shrink(1);
                if (stack.isEmpty()) {
                    fuel.setItem(0, remainder != null ? remainder.create() : ItemStack.EMPTY);
                }
                fuel.setChanged();
            }
        }

        int stored = buffer.getAmountAsInt();
        double rpmBefore = model.rawRpm();
        boolean breakerBefore = model.breakerClosed();
        int generated = model.tick(enabled, buffer.getCapacityAsInt() - stored, eeDemand(level, pos));
        if (generated > 0) {
            buffer.set(stored + generated);
        }
        pushToNeighbors(level, pos, state.getValue(MachineBlock.FACING));

        if (model.breakerClosed() != breakerBefore) {
            level.playSound(null, pos, model.breakerClosed() ? com.powerfactor.registry.ModSounds.TURBINE_BREAKER_CLOSE.get()
                    : com.powerfactor.registry.ModSounds.TURBINE_BREAKER_OPEN.get(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        boolean running = enabled && model.fuelEnergy() > 0;
        setRunning(level, pos, state, running);
        if (model.rawRpm() != rpmBefore || running) {
            setChanged();
        }
        // Clients animate the rotor themselves; tell them when the speed has moved noticeably.
        if (Math.abs(model.rpm() - syncedRpm) >= 90 || (model.rpm() == 0) != (syncedRpm == 0)) {
            syncedRpm = model.rpm();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    /**
     * EE this turbine must supply: its share of each adjacent wire grid's net reactive flow, plus
     * any motors or capacitors bolted directly onto it. Negative means it must absorb EE because
     * the grid is over-corrected.
     */
    private double eeDemand(ServerLevel level, BlockPos pos) {
        GridManager grids = GridManager.get(level);
        Set<PowerGrid> counted = new HashSet<>();
        double ee = 0;
        for (Direction d : Direction.values()) {
            BlockPos other = pos.relative(d);
            if (level.getBlockState(other).getBlock() instanceof WireBlock) {
                PowerGrid grid = grids.gridAt(other);
                if (grid != null && counted.add(grid)) {
                    ee += grid.eeShareFor(this);
                }
            } else if (level.getBlockEntity(other) instanceof ReactiveLoad load) {
                ee += load.eeDemand();
            }
        }
        return ee;
    }

    private void pushToNeighbors(ServerLevel level, BlockPos pos, Direction front) {
        for (Direction dir : Direction.values()) {
            if (dir == front || buffer.getAmountAsInt() == 0) {
                continue;
            }
            EnergyHandler target = outputs.computeIfAbsent(dir,
                    d -> BlockCapabilityCache.create(Capabilities.Energy.BLOCK, level, pos.relative(d), d.getOpposite())).getCapability();
            if (target != null) {
                EnergyHandlerUtil.move(buffer, target, buffer.getAmountAsInt(), null);
            }
        }
    }

    /** Picks up config changes without losing turbine state. */
    private void refreshSpec() {
        if (!PowerFactorConfig.SPEC.isLoaded()) {
            return;
        }
        TurbineModel.Spec spec = PowerFactorConfig.turbineSpec();
        if (!spec.equals(model.spec())) {
            TurbineModel next = new TurbineModel(spec);
            next.load(model.rawRpm(), model.breakerClosed(), model.fuelEnergy(), model.fuelEnergyMax());
            model = next;
        }
    }

    @Override
    public boolean soundActive() {
        return clientRpm > 150;
    }

    @Override
    public net.minecraft.sounds.SoundEvent loopSound() {
        return com.powerfactor.registry.ModSounds.TURBINE_RUN.get();
    }

    /** The whine rises with the rotor: half pitch at standstill, full at 3600 RPM. */
    @Override
    public float loopPitch() {
        return 0.5F + 0.5F * clientRpm / TurbineModel.SYNC_RPM;
    }

    @Override
    public float loopVolume() {
        return 0.25F + 0.3F * clientRpm / TurbineModel.SYNC_RPM;
    }

    @Override
    public void clientTick() {
        super.clientTick();
        prevRotorAngle = rotorAngle;
        rotorAngle += RENDER_DEGREES_PER_TICK_AT_SYNC * clientRpm / TurbineModel.SYNC_RPM;
        if (rotorAngle >= 360F) {
            rotorAngle -= 360F;
            prevRotorAngle -= 360F;
        }
    }

    /** Interpolated rotor angle in degrees, for the renderer. */
    public float rotorAngle(float partialTick) {
        return prevRotorAngle + (rotorAngle - prevRotorAngle) * partialTick;
    }

    @Override
    public double ratedCe() {
        return model.spec().ratedCe();
    }

    @Override
    public double hertz() {
        return model.breakerClosed() ? model.hertz() : 0;
    }

    @Override
    public double eeCapacity() {
        if (!model.breakerClosed()) {
            return 0;
        }
        double ce = model.spec().ratedCe(), fe = model.lastOutput().fe();
        return Math.sqrt(Math.max(0, ce * ce - fe * fe));
    }

    /**
     * Terminal voltage for machines bolted straight onto the turbine: the generator covers all of
     * their EE itself, so only its own loading (and overload) sags it.
     */
    @Override
    public BusSolver.Result state() {
        if (!model.breakerClosed()) {
            return new BusSolver.Result(0, 0, 0, 0, 0, 0);
        }
        PowerTriangle out = model.lastOutput();
        BusSolver.Result r = BusSolver.solve(model.spec().ratedCe(), out.fe(), Math.max(0, out.ee()), Math.max(0, -out.ee()), Double.MAX_VALUE, 1.0);
        double volts = model.overloaded() ? r.volts() - 0.08 : r.volts();
        return new BusSolver.Result(volts, r.current(), r.fe(), r.netEe(), r.generatorEe(), r.lossFe());
    }

    public int rpm() {
        return model.rpm();
    }

    public PowerTriangle lastOutput() {
        return model.lastOutput();
    }

    @Override
    public int comparatorSignal() {
        return EnergyHandlerUtil.getRedstoneSignalFromEnergyHandler(buffer);
    }

    public EnergyHandler getEnergy() {
        return buffer;
    }

    public ResourceHandler<ItemResource> getFuelHandler() {
        return fuelHandler;
    }

    @Override
    protected Container inventory() {
        return fuel;
    }

    @Override
    public ScadaReading scadaReading() {
        PowerTriangle out = model.lastOutput();
        ScadaReading.Status status = model.breakerClosed() ? ScadaReading.Status.RUNNING
                : model.rpm() > 0 ? ScadaReading.Status.WARNING : ScadaReading.Status.OFF;
        int fuelPct = model.fuelEnergyMax() <= 0 ? 0 : (int) (100 * model.fuelEnergy() / model.fuelEnergyMax());
        Component detail = Component.translatable("scada.powerfactor.turbine", String.format("%.1f", model.hertz()),
                fuel.getItem(0).getCount(), fuelPct);
        return new ScadaReading(worldPosition, getDisplayName(), status, (int) out.fe(), (int) Math.round(out.ee()), detail, true, remoteEnabled(), true,
                (float) state().volts(), (float) hertz());
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        outputs.clear();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, fuel.getItems());
        buffer.deserialize(input);
        model.load(input.getDoubleOr("rpm", 0), input.getBooleanOr("breaker_closed", false),
                input.getLongOr("fuel_energy", 0), input.getLongOr("fuel_energy_max", 0));
        clientRpm = model.rpm();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, fuel.getItems());
        buffer.serialize(output);
        output.putDouble("rpm", model.rawRpm());
        output.putBoolean("breaker_closed", model.breakerClosed());
        output.putLong("fuel_energy", model.fuelEnergy());
        output.putLong("fuel_energy_max", model.fuelEnergyMax());
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new CoalTurbineMenu(containerId, inventory, fuel, data, this);
    }
}
