package com.powerfactor.generator;

import com.powerfactor.api.GridGenerator;
import com.powerfactor.api.MachineAudio;
import com.powerfactor.grid.GridManager;
import com.powerfactor.grid.PowerGrid;
import com.powerfactor.grid.WireBlock;
import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.power.TurbineModel;
import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.registry.ModSounds;
import com.powerfactor.scada.ScadaReading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import org.jspecify.annotations.Nullable;

/**
 * Diesel genset: a synchronous generator on a reciprocating engine. Starts in two seconds instead
 * of ten, but has little inertia. Burns any fuel fluid other mods tag as diesel/fuel, or lava at a
 * worse rate. Pipe fuel into it.
 */
public class DieselGeneratorBlockEntity extends MachineBlockEntity implements GridGenerator, MachineAudio.Looping {
    public static final int TANK = 8_000;
    private static final TurbineModel.Spec SPEC = new TurbineModel.Spec(100, 125, 2, 40, 80);
    private static final java.util.List<TagKey<Fluid>> FUELS = java.util.stream.Stream.of("diesel", "biodiesel", "fuel", "fuels", "gasoline")
            .map(n -> TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath("c", n))).toList();

    private final TurbineModel engine = new TurbineModel(SPEC);
    private final SimpleEnergyHandler buffer = new SimpleEnergyHandler(10_000, 0, Integer.MAX_VALUE) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private final FluidStacksResourceHandler tank = new FluidStacksResourceHandler(1, TANK) {
        @Override
        public boolean isValid(int index, FluidResource resource) {
            return energyPerMb(resource.getFluid()) > 0;
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previous) {
            setChanged();
        }
    };

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case DieselMenu.DATA_FE -> (int) engine.lastOutput().fe();
                case DieselMenu.DATA_EE -> (int) Math.round(engine.lastOutput().ee());
                case DieselMenu.DATA_RPM -> engine.rpm();
                case DieselMenu.DATA_TANK -> tank.getAmountAsInt(0);
                case DieselMenu.DATA_FLAGS -> (engine.breakerClosed() ? 1 : 0) | (isLava() ? 2 : 0);
                case DieselMenu.DATA_REDSTONE -> redstoneMode().ordinal();
                case DieselMenu.DATA_STORED -> buffer.getAmountAsInt() / 10;
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

    public DieselGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DIESEL_GENERATOR.get(), pos, state);
    }

    private static int energyPerMb(Fluid fluid) {
        if (fluid.isSame(Fluids.LAVA)) {
            return 60;
        }
        for (TagKey<Fluid> tag : FUELS) {
            if (fluid.defaultFluidState().is(tag)) {
                return 240;
            }
        }
        return 0;
    }

    private boolean isLava() {
        return tank.getResource(0).getFluid().isSame(Fluids.LAVA);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        boolean enabled = enabled(level, pos);
        if (enabled && engine.needsFuel() && tank.getAmountAsInt(0) >= 10) {
            int per = energyPerMb(tank.getResource(0).getFluid());
            tank.set(0, tank.getResource(0), tank.getAmountAsInt(0) - 10);
            engine.addFuel(10L * per);
        }
        double ee = 0;
        for (Direction d : Direction.values()) {
            if (level.getBlockState(pos.relative(d)).getBlock() instanceof WireBlock) {
                PowerGrid grid = GridManager.get(level).gridAt(pos.relative(d));
                if (grid != null) {
                    ee = grid.eeShareFor(this);
                    break;
                }
            }
        }
        int stored = buffer.getAmountAsInt();
        int made = engine.tick(enabled, buffer.getCapacityAsInt() - stored, ee);
        if (made > 0) {
            buffer.set(stored + made);
        }
        for (Direction d : Direction.values()) {
            EnergyHandler target = level.getCapability(Capabilities.Energy.BLOCK, pos.relative(d), d.getOpposite());
            if (target != null && buffer.getAmountAsInt() > 0) {
                EnergyHandlerUtil.move(buffer, target, buffer.getAmountAsInt(), null);
            }
        }
        setRunning(level, pos, state, enabled && engine.fuelEnergy() > 0);
        setChanged();
    }

    @Override
    public double ratedCe() {
        return SPEC.ratedCe();
    }

    @Override
    public double hertz() {
        return engine.breakerClosed() ? engine.hertz() : 0;
    }

    @Override
    public double eeCapacity() {
        if (!engine.breakerClosed()) {
            return 0;
        }
        double fe = engine.lastOutput().fe();
        return Math.sqrt(Math.max(0, SPEC.ratedCe() * SPEC.ratedCe() - fe * fe));
    }

    public EnergyHandler getEnergy() {
        return buffer;
    }

    public FluidStacksResourceHandler getTank() {
        return tank;
    }

    @Override
    public boolean soundActive() {
        return isRunning();
    }

    @Override
    public SoundEvent loopSound() {
        return ModSounds.DIESEL_RUN.get();
    }

    @Override
    public ScadaReading scadaReading() {
        var out = engine.lastOutput();
        ScadaReading.Status status = engine.breakerClosed() ? ScadaReading.Status.RUNNING
                : engine.rpm() > 0 ? ScadaReading.Status.WARNING : ScadaReading.Status.OFF;
        return new ScadaReading(worldPosition, getDisplayName(), status, (int) out.fe(), (int) Math.round(out.ee()),
                Component.translatable("scada.powerfactor.diesel", String.format("%.1f", engine.hertz()), tank.getAmountAsInt(0)), true,
                remoteEnabled(), true, 1F, (float) hertz());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        buffer.deserialize(input);
        tank.deserialize(input.childOrEmpty("tank"));
        engine.load(input.getDoubleOr("rpm", 0), input.getBooleanOr("breaker", false), input.getLongOr("fuel", 0), input.getLongOr("fuel_max", 0));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        buffer.serialize(output);
        tank.serialize(output.child("tank"));
        output.putDouble("rpm", engine.rawRpm());
        output.putBoolean("breaker", engine.breakerClosed());
        output.putLong("fuel", engine.fuelEnergy());
        output.putLong("fuel_max", engine.fuelEnergyMax());
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new DieselMenu(id, inventory, worldPosition, data, this);
    }
}
