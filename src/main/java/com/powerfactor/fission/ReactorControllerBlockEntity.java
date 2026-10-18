package com.powerfactor.fission;

import com.powerfactor.machine.MachineBlock;
import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.registry.ModFission;
import com.powerfactor.scada.ScadaReading;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The fission reactor's controller: finds the reactor around it, loads fuel rods into its assemblies, moves
 * the control rods toward their setpoints, runs the core's physics, boils the pool's coolant into steam,
 * watches its alarms and interlocks, and (on AUTO) holds a target power with the rods.
 */
public class ReactorControllerBlockEntity extends MachineBlockEntity {
    // Up to an 18x18x18 reactor: a 16x16 interior, 16 assemblies tall. Slot order is unchanged for smaller reactors.
    public static final int TANK = 256_000, FUEL_SLOTS = 256 * 16, SPENT_SLOTS = 54, MAX_COLUMNS = 256;
    public static final double ROD_SPEED = 0.002, SCRAM_SPEED = 0.05;

    /** Alarms, in annunciator order. */
    public enum Alarm {
        HIGH_POWER, HIGH_TEMP, LOW_COOLANT, STEAM_FULL, FAST_PERIOD, HIGH_XENON, CORE_DAMAGE, FUEL_LOW, SPENT_FULL, SCRAMMED;

        public int bit() {
            return 1 << ordinal();
        }
    }

    private final ReactorCore core = new ReactorCore();
    private ReactorStructure.@Nullable Shape shape;
    /** Shell blocks currently showing as one connected reactor. */
    private java.util.Set<BlockPos> formedShell = java.util.Set.of();
    private Component problem = Component.empty();
    private @Nullable BlockPos problemAt;
    /** Per-column rod position and setpoint (0 out .. 1 fully in). */
    private double[] rods = new double[MAX_COLUMNS], setpoints = new double[MAX_COLUMNS];
    private double[] columnTemp = new double[MAX_COLUMNS], columnFlux = new double[MAX_COLUMNS];
    private boolean scrammed = true, auto;
    /** Why the last SCRAM happened (an interlock's alarm key, "operator", "scada", "adapter"), shown first-out on the console. */
    private String tripCause = "";
    private double targetPower = 0.5;   // fraction of rated
    private int alarms, acknowledged;
    // Interlock setpoints: SCRAM on these.
    private double tripTemp = 1100, tripPower = 1.2;
    private double pidIntegral;
    private final ReactorTanks tanks;
    private final SimpleContainer fuel = new SimpleContainer(FUEL_SLOTS) {
        @Override
        public boolean canPlaceItem(int i, ItemStack s) {
            return FuelRodItem.fresh(s);
        }
    };
    private final SimpleContainer spent = new SimpleContainer(SPENT_SLOTS);

    public ReactorControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModFission.REACTOR_CONTROLLER_BE.get(), pos, state);
        tanks = new ReactorTanks(TANK, this::setChanged);
    }

    public ReactorCore core() {
        return core;
    }

    public ReactorTanks tanks() {
        return tanks;
    }

    public ReactorStructure.@Nullable Shape shape() {
        return shape;
    }

    @Override
    protected Container inventory() {
        return fuel;
    }

    // ---------------------------------------------------------------- tick

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState st) {
        if (level.getGameTime() % 40 == (pos.asLong() & 31)) rescan(level);
        if (shape == null) {
            core.tick(1, 1, 0, 0, 0);
            setRunning(level, pos, st, false);
            return;
        }
        int columns = shape.columns().size();
        // Rods travel toward their setpoints; a SCRAM drives them all home fast.
        if (auto && !scrammed) autopilot(columns);
        double meanRods = 0;
        for (int c = 0; c < columns; c++) {
            double target = scrammed ? 1 : setpoints[c];
            double speed = scrammed ? SCRAM_SPEED : ROD_SPEED;
            rods[c] += Math.clamp(target - rods[c], -speed, speed);
            meanRods += rods[c];
        }
        meanRods /= Math.max(1, columns);
        // Fuel: one rod per assembly block; reactivity from fresh fraction and moderation.
        int loaded = 0;
        double kSum = 0, mox = 0;
        for (int i = 0; i < assemblyCount(); i++) {
            ItemStack rod = fuel.getItem(i);
            if (!FuelRodItem.fresh(rod)) continue;
            loaded++;
            kSum += FuelRodItem.reactivity(rod);
            if (FuelRodItem.mox(rod)) mox++;
        }
        double moderation = moderation();
        double kInf = loaded == 0 ? 0 : (kSum / loaded) * moderation;
        // Water boils away (its steam is vented if the output backs up); a loop coolant can only flow while its hot side has room.
        var coolant = tanks.coolant();
        int available = Math.min(tanks.amount(ReactorTanks.COOLANT), 4000);
        if (coolant.loop) available = Math.min(available, tanks.outputRoom(coolant));
        double used = core.tick(kInf, meanRods, loaded, available, loaded == 0 ? 0 : mox / loaded, coolant);
        tanks.add(ReactorTanks.COOLANT, -(int) Math.ceil(used));
        tanks.addOutput(coolant, Math.min(tanks.outputRoom(coolant), (int) Math.round(core.steamMade)));
        burnFuel(loaded);
        spreadColumns(columns);
        checkAlarms(level, loaded);
        if (core.meltdown()) {
            ReactorMeltdown.melt(level, this, shape);
            return;
        }
        setRunning(level, pos, st, core.power > 1);
        if (level.getGameTime() % 10 == 0) glow(level, core.n);
        if (level.getGameTime() % 20 == 0) FissionCherenkov.update(level, pos, shape, core.n);
        if (level.getGameTime() % 5 == 0) {
            level.sendBlockUpdated(pos, st, st, Block.UPDATE_CLIENTS);
            ReactorPayloads.broadcast(level, this);
        }
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel s) FissionCherenkov.remove(s, worldPosition);
        super.setRemoved();
    }

    private void rescan(ServerLevel level) {
        // A reactor across a chunk border waits for all of itself to load, rather than tripping on the half it can't see.
        if (shape != null && !level.hasChunksAt(shape.min(), shape.max())) return;
        Direction inward = getBlockState().getValue(MachineBlock.FACING).getOpposite();
        var r = ReactorStructure.scan(level, worldPosition, inward);
        var old = shape;
        shape = r.shape();
        problem = r.problem() == null ? Component.empty() : Component.translatable(r.problem().key(), r.problem().args());
        problemAt = r.at();
        if (old != null && shape == null) {
            // A broken reactor is a tripped reactor; the console names what broke it.
            if (!scrammed) {
                scram(level, "structure");
                com.powerfactor.PowerFactor.LOGGER.info("Reactor at {} tripped: structure ({} at {})", worldPosition,
                        r.problem() == null ? "?" : r.problem().key(), r.at());
            }
            scrammed = true;
        }
        if (shape != null) ReactorPorts.link(level, shape, worldPosition);
        reshell(level, shape == null ? java.util.Set.of() : new java.util.HashSet<>(shape.shell()));
        wet = new int[shape == null ? 0 : shape.columns().size()];
        for (int c = 0; c < wet.length; c++) {
            var col = shape.columns().get(c);
            int water = 0;
            for (BlockPos p : col.assemblies()) {
                for (Direction d : Direction.Plane.HORIZONTAL) if (ReactorStructure.isWater(level.getBlockState(p.relative(d)))) water++;
            }
            wet[c] = Math.round(100F * water / Math.max(1, col.assemblies().size() * 4));
        }
        // Lights follow the shape: assemblies that left it go dark, the rest relight on the next tick.
        java.util.Set<BlockPos> now = new java.util.HashSet<>();
        if (shape != null) for (var col : shape.columns()) now.addAll(col.assemblies());
        for (BlockPos p : glowing) if (!now.contains(p)) setGlow(level, p, 0);
        glowing = java.util.List.copyOf(now);
        glowBand = -1;
        if (shape == null) glowing = java.util.List.of();
    }

    /** Per column, the share of its assembly faces in water (the pool that glows Cherenkov blue), in percent. */
    private int[] wet = new int[0];
    private java.util.List<BlockPos> glowing = java.util.List.of();
    private int glowBand = -1;

    /** Lights the fuel to suit the power, with a little hysteresis so it doesn't flicker at a threshold. */
    private void glow(ServerLevel level, double n) {
        int band = FuelAssemblyBlock.band(n);
        if (band == glowBand || glowBand >= 0 && FuelAssemblyBlock.band(band > glowBand ? n * 0.92 : n * 1.08) != band) return;
        glowBand = band;
        for (BlockPos p : glowing) setGlow(level, p, band);
    }

    private static void setGlow(ServerLevel level, BlockPos p, int band) {
        if (!level.hasChunkAt(p)) return;
        BlockState s = level.getBlockState(p);
        if (s.hasProperty(FuelAssemblyBlock.GLOW) && s.getValue(FuelAssemblyBlock.GLOW) != band) {
            level.setBlock(p, s.setValue(FuelAssemblyBlock.GLOW, band), Block.UPDATE_CLIENTS);
        }
    }

    /** Joins the shell into one connected casing while formed, and releases blocks that left it. */
    private void reshell(ServerLevel level, java.util.Set<BlockPos> now) {
        for (BlockPos p : formedShell) if (!now.contains(p) && level.hasChunkAt(p)) com.powerfactor.multiblock.Connected.setFormed(level, p, false);
        for (BlockPos p : now) if (!formedShell.contains(p)) com.powerfactor.multiblock.Connected.setFormed(level, p, true);
        formedShell = now;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel s) {
            reshell(s, java.util.Set.of());
            for (BlockPos p : glowing) setGlow(s, p, 0);
        }
    }

    /** Fuel assembly blocks across all columns. */
    public int assemblyCount() {
        if (shape == null) return 0;
        int n = 0;
        for (var c : shape.columns()) n += c.assemblies().size();
        return n;
    }

    /** Moderation from the pool and graphite: well-moderated fuel multiplies better, up to a point. */
    double moderation() {
        if (shape == null) return 0;
        int faces = 0, possible = 0;
        for (var c : shape.columns()) {
            faces += c.moderators();
            possible += c.assemblies().size() * 4;
        }
        double m = possible == 0 ? 0 : faces / (double) possible;
        // Bare fuel is subcritical; a fully moderated lattice reaches 1.10.
        return 0.88 + 0.22 * Math.sqrt(m);
    }

    private void burnFuel(int loaded) {
        if (loaded == 0 || core.power <= 0) return;
        double perRod = core.power / loaded * core.coolant.burn;
        for (int i = 0; i < assemblyCount(); i++) {
            ItemStack rod = fuel.getItem(i);
            if (!FuelRodItem.fresh(rod)) continue;
            if (FuelRodItem.burn(rod, perRod)) {
                fuel.setItem(i, ItemStack.EMPTY);
                ItemStack out = FuelRodItem.spent(rod);
                for (int s = 0; s < SPENT_SLOTS && !out.isEmpty(); s++) {
                    if (spent.getItem(s).isEmpty()) {
                        spent.setItem(s, out);
                        out = ItemStack.EMPTY;
                    }
                }
                if (!out.isEmpty()) fuel.setItem(i, out);   // nowhere to go: it sits spent in the core
            }
        }
    }

    /** The core's single temperature spread across the columns by rod position and moderation, for the map. */
    private void spreadColumns(int columns) {
        if (shape == null) return;
        double meanFactor = 0;
        double[] factor = new double[columns];
        for (int c = 0; c < columns; c++) {
            var col = shape.columns().get(c);
            factor[c] = (1 - 0.8 * rods[c]) * (0.6 + 0.4 * col.moderators() / Math.max(1.0, col.assemblies().size() * 4));
            meanFactor += factor[c];
        }
        meanFactor /= Math.max(1, columns);
        for (int c = 0; c < columns; c++) {
            double rel = meanFactor <= 0 ? 1 : factor[c] / meanFactor;
            columnFlux[c] = core.n * rel;
            columnTemp[c] = ReactorCore.AMBIENT + (core.fuelTemp - ReactorCore.AMBIENT) * rel;
        }
    }

    /**
     * AUTO: steer reactivity, not rods. The power error (in decades, so a cold start and a trim both make
     * sense) asks for a reactivity, kept well short of prompt critical, and the rods are nudged from where
     * they actually are toward the position that gives it.
     */
    private void autopilot(int columns) {
        double n = Math.max(core.n, 1e-7);
        double e = Math.log(Math.max(targetPower, 1e-4) / n);
        double want = Math.clamp(e * 0.0012, -0.004, 0.0015);
        // Rising through the power range it holds the period at five seconds or longer, short of the fast-period
        // alarm; down in the source range it climbs as fast as it likes.
        double period = core.period();
        if (want > 0 && core.n > 0.01 && period > 0 && period < 5) want = Math.min(want, core.reactivity * 0.8);
        double step = Math.clamp((core.reactivity - want) / ReactorCore.ROD_WORTH, -ROD_SPEED, ROD_SPEED);
        for (int c = 0; c < columns; c++) setpoints[c] = Math.clamp(rods[c] + step, 0, 1);
    }

    private void checkAlarms(ServerLevel level, int loaded) {
        int a = 0;
        double rel = core.n;
        if (rel > 1.05) a |= Alarm.HIGH_POWER.bit();
        if (core.fuelTemp > 1000) a |= Alarm.HIGH_TEMP.bit();
        if (tanks.amount(0) < 1000 && rel > 0.01) a |= Alarm.LOW_COOLANT.bit();
        if (tanks.amount(1) > TANK * 0.9) a |= Alarm.STEAM_FULL.bit();
        double period = core.period();
        if (rel > 0.01 && period > 0 && period < 4) a |= Alarm.FAST_PERIOD.bit();
        if (core.xenonWorth() > ReactorCore.XENON_WORTH * 1.3) a |= Alarm.HIGH_XENON.bit();
        if (core.damage > 0) a |= Alarm.CORE_DAMAGE.bit();
        if (loaded < assemblyCount() / 2) a |= Alarm.FUEL_LOW.bit();
        if (!spent.getItem(SPENT_SLOTS - 1).isEmpty()) a |= Alarm.SPENT_FULL.bit();
        if (scrammed) a |= Alarm.SCRAMMED.bit();
        // Interlocks trip the reactor on their own.
        if (!scrammed) {
            if (core.fuelTemp > tripTemp) scram(level, "high_temp");
            else if (rel > tripPower) scram(level, "high_power");
            else if (tanks.amount(0) <= 0 && rel > 0.01) scram(level, "low_coolant");
            else if (core.damage > 5) scram(level, "core_damage");
        }
        int fresh = a & ~alarms & ~Alarm.SCRAMMED.bit() & ~Alarm.FUEL_LOW.bit();
        if (fresh != 0) {
            acknowledged &= ~fresh;
            level.playSound(null, worldPosition, com.powerfactor.registry.ModSounds.REACTOR_ALARM.get(), SoundSource.BLOCKS, 1.5F, 1F);
        }
        alarms = a;
    }

    // ---------------------------------------------------------------- control (console, SCADA, logic adapters)

    public void scram(ServerLevel level, String why) {
        if (scrammed) return;
        scrammed = true;
        auto = false;
        tripCause = why;
        com.powerfactor.PowerFactor.LOGGER.info("Reactor at {} SCRAM: {} (power {}, fuel {} K, coolant {} mB, tick {})", worldPosition, why,
                String.format("%.3f", core.n), (int) core.fuelTemp, tanks.amount(ReactorTanks.COOLANT), level.getGameTime());
        level.playSound(null, worldPosition, com.powerfactor.registry.ModSounds.REACTOR_SCRAM.get(), SoundSource.BLOCKS, 2F, 1F);
    }

    /** Clears a SCRAM (only once nothing would trip it straight back). */
    /** Empties the coolant and output tanks to change coolant; only while shut down and cool. */
    public boolean purge() {
        if (!scrammed || core.fuelTemp > 450) return false;
        tanks.purge();
        setChanged();
        return true;
    }

    public boolean reset() {
        if (!scrammed || core.fuelTemp > tripTemp - 100 || core.damage > 5) return false;
        scrammed = false;
        tripCause = "";
        for (int c = 0; c < MAX_COLUMNS; c++) setpoints[c] = rods[c];
        return true;
    }

    public void setRods(int column, double value) {
        if (column < 0) {
            for (int c = 0; c < MAX_COLUMNS; c++) setpoints[c] = Math.clamp(value, 0, 1);
        } else if (column < MAX_COLUMNS) {
            setpoints[column] = Math.clamp(value, 0, 1);
        }
        auto = false;
        setChanged();
    }

    public void setAuto(boolean on) {
        auto = on && !scrammed;
        pidIntegral = 0;
        setChanged();
    }

    public void setTarget(double fraction) {
        targetPower = Math.clamp(fraction, 0, 1.1);
        setChanged();
    }

    public void acknowledge() {
        acknowledged = alarms;
    }

    public void setTrips(double temp, double power) {
        tripTemp = Math.clamp(temp, 600, 1200);
        tripPower = Math.clamp(power, 0.5, 1.5);
        setChanged();
    }

    public int alarms() {
        return alarms;
    }

    public int unacknowledged() {
        return alarms & ~acknowledged;
    }

    public boolean scrammed() {
        return scrammed;
    }

    public boolean auto() {
        return auto;
    }

    public double target() {
        return targetPower;
    }

    public double tripTemp() {
        return tripTemp;
    }

    public double tripPower() {
        return tripPower;
    }

    public double[] rods() {
        return rods;
    }

    public double[] setpoints() {
        return setpoints;
    }

    public double[] columnTemp() {
        return columnTemp;
    }

    public double[] columnFlux() {
        return columnFlux;
    }

    public Component problem() {
        return problem;
    }

    public String tripCause() {
        return tripCause;
    }

    /** For fuel ports: fresh rods go into the assemblies that exist; spent rods come out. */
    public net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> fuelHandler() {
        var in = new net.neoforged.neoforge.transfer.DelegatingResourceHandler<>(net.neoforged.neoforge.transfer.item.VanillaContainerWrapper.of(fuel)) {
            @Override
            public int insert(int index, net.neoforged.neoforge.transfer.item.ItemResource resource, int amount,
                    net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
                return index < assemblyCount() ? super.insert(index, resource, amount, tx) : 0;
            }

            @Override
            public int extract(int index, net.neoforged.neoforge.transfer.item.ItemResource resource, int amount,
                    net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
                return 0;
            }
        };
        var out = new net.neoforged.neoforge.transfer.DelegatingResourceHandler<>(net.neoforged.neoforge.transfer.item.VanillaContainerWrapper.of(spent)) {
            @Override
            public int insert(int index, net.neoforged.neoforge.transfer.item.ItemResource resource, int amount,
                    net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
                return 0;
            }
        };
        return new net.neoforged.neoforge.transfer.CombinedResourceHandler<>(in, out);
    }

    public SimpleContainer fuel() {
        return fuel;
    }

    public SimpleContainer spent() {
        return spent;
    }

    // ---------------------------------------------------------------- client view (for the renderer)

    /** Client: interior bounds (min x,y,z, max x,y,z) or empty when unformed; columns as x,z,top-y pairs; rod positions; power; water fraction. */
    public int[] clientBounds = new int[0], clientColumns = new int[0];
    public float[] clientRods = new float[0];
    public float clientPower, clientTemp;
    public boolean clientScrammed = true;
    /** Client: per column, percent of its faces in water; the coolant (ReactorCore.Coolant ordinal). */
    public int[] clientWet = new int[0];
    public int clientCoolant;

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        var tag = new net.minecraft.nbt.CompoundTag();
        if (shape != null) {
            tag.putIntArray("bounds", new int[] {shape.min().getX(), shape.min().getY(), shape.min().getZ(), shape.max().getX(), shape.max().getY(), shape.max().getZ()});
            int n = shape.columns().size();
            int[] cols = new int[n * 2];
            int[] rodsMilli = new int[n];
            for (int c = 0; c < n; c++) {
                cols[c * 2] = shape.columns().get(c).x();
                cols[c * 2 + 1] = shape.columns().get(c).z();
                rodsMilli[c] = (int) Math.round(rods[c] * 1000);
            }
            tag.putIntArray("cols", cols);
            tag.putIntArray("rods", rodsMilli);
            tag.putIntArray("wet", wet.length == n ? wet : new int[n]);
        }
        tag.putInt("coolant", core.coolant.ordinal());
        tag.putFloat("power", (float) core.n);
        tag.putFloat("temp", (float) core.fuelTemp);
        tag.putBoolean("scrammed", scrammed);
        return tag;
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        clientBounds = input.getIntArray("bounds").orElse(new int[0]);
        clientColumns = input.getIntArray("cols").orElse(new int[0]);
        int[] r = input.getIntArray("rods").orElse(new int[0]);
        clientRods = new float[r.length];
        for (int i = 0; i < r.length; i++) clientRods[i] = r[i] / 1000F;
        clientPower = input.getFloatOr("power", 0);
        clientTemp = input.getFloatOr("temp", 300);
        clientScrammed = input.getBooleanOr("scrammed", true);
        clientWet = input.getIntArray("wet").orElse(new int[0]);
        clientCoolant = input.getIntOr("coolant", 0);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ValueInput input) {
        handleUpdateTag(input);
    }

    @Override
    public ScadaReading scadaReading() {
        ScadaReading.Status st = core.damage > 0 || (alarms & ~Alarm.SCRAMMED.bit() & ~Alarm.FUEL_LOW.bit()) != 0 ? ScadaReading.Status.ALARM
                : scrammed ? ScadaReading.Status.OFF : ScadaReading.Status.RUNNING;
        var base = new ScadaReading(worldPosition, getDisplayName(), st, 0, 0,
                Component.translatable("gui.powerfactor.fission.scada", (int) Math.round(core.n * 100), (int) core.fuelTemp, (int) core.steamMade),
                true, !scrammed, false, 0, 0);
        double period = core.period();
        var points = java.util.List.of(
                new ScadaReading.Point(Component.translatable("gui.powerfactor.fission.point.power"), (float) (core.n * 100), "%", 0, 120,
                        core.n > tripPower ? 2 : core.n > 1.05 ? 1 : 0),
                new ScadaReading.Point(Component.translatable("gui.powerfactor.fission.point.temp"), (float) core.fuelTemp, "K", 300, 1300,
                        core.fuelTemp > tripTemp - 50 ? 2 : core.fuelTemp > 1000 ? 1 : 0),
                new ScadaReading.Point(Component.translatable("gui.powerfactor.fission.point.reactivity"), (float) (core.reactivity * 1e5), "pcm", -1000, 1000,
                        core.reactivity > ReactorCore.BETA * 0.5 ? 2 : 0),
                new ScadaReading.Point(Component.translatable("gui.powerfactor.fission.point.period"), (float) Math.clamp(period, -999, 999), "s", -999, 999,
                        period > 0 && period < 4 ? 2 : period > 0 && period < 15 ? 1 : 0),
                new ScadaReading.Point(Component.translatable("gui.powerfactor.fission.point.xenon"), (float) (core.xenonWorth() * 1e5), "pcm", 0,
                        (float) (ReactorCore.XENON_WORTH * 2e5), core.xenonWorth() > ReactorCore.XENON_WORTH * 1.3 ? 1 : 0),
                new ScadaReading.Point(Component.translatable(core.coolant.loop ? "gui.powerfactor.fission.point.hot" : "gui.powerfactor.fission.point.steam"),
                        (float) core.steamMade, "mB/t", 0,
                        Math.max(100, assemblyCount() * 250), 0),
                new ScadaReading.Point(Component.translatable("gui.powerfactor.fission.coolant." + tanks.coolant().name().toLowerCase(java.util.Locale.ROOT)),
                        tanks.amount(0), "mB", 0, TANK,
                        tanks.amount(0) < 1000 ? 2 : tanks.amount(0) < 8000 ? 1 : 0),
                new ScadaReading.Point(Component.translatable("gui.powerfactor.fission.point.rods"), (float) (meanRod() * 100), "%", 0, 100, 0),
                new ScadaReading.Point(Component.translatable("gui.powerfactor.fission.point.damage"), (float) core.damage, "%", 0, 100,
                        core.damage > 0 ? 2 : 0));
        var controls = java.util.List.of(
                ScadaReading.Control.button("scram", Component.translatable("gui.powerfactor.fission.scram"), true),
                ScadaReading.Control.button("reset", Component.translatable("gui.powerfactor.fission.reset"), false),
                ScadaReading.Control.button("ack", Component.translatable("gui.powerfactor.fission.ack"), false),
                ScadaReading.Control.toggle("auto", Component.translatable("gui.powerfactor.fission.control.auto"), auto),
                ScadaReading.Control.setpoint("target", Component.translatable("gui.powerfactor.fission.control.target"), (float) (targetPower * 100), 0, 110, 5),
                ScadaReading.Control.setpoint("rods", Component.translatable("gui.powerfactor.fission.control.rods"), (float) Math.round(meanSetpoint() * 100), 0,
                        100, 5),
                ScadaReading.Control.setpoint("trip_temp", Component.translatable("gui.powerfactor.fission.control.trip_temp"), (float) tripTemp, 600, 1200, 50),
                ScadaReading.Control.setpoint("trip_power", Component.translatable("gui.powerfactor.fission.control.trip_power"), (float) (tripPower * 100), 50, 150, 5),
                ScadaReading.Control.button("purge", Component.translatable("gui.powerfactor.fission.purge"), true));
        return base.with(points, controls);
    }

    private double meanRod() {
        int n = shape == null ? 0 : shape.columns().size();
        double m = 0;
        for (int c = 0; c < n; c++) m += rods[c];
        return n == 0 ? 1 : m / n;
    }

    private double meanSetpoint() {
        int n = shape == null ? 0 : shape.columns().size();
        double m = 0;
        for (int c = 0; c < n; c++) m += setpoints[c];
        return n == 0 ? 1 : m / n;
    }

    @Override
    public void scadaControl(String id, float value) {
        if (!(level instanceof ServerLevel s)) return;
        switch (id) {
            case "scram" -> scram(s, "scada");
            case "reset" -> reset();
            case "ack" -> acknowledge();
            case "auto" -> setAuto(value > 0);
            case "target" -> setTarget(value / 100.0);
            case "rods" -> setRods(-1, value / 100.0);
            case "trip_temp" -> setTrips(value, tripPower);
            case "trip_power" -> setTrips(tripTemp, value / 100.0);
            case "purge" -> purge();
            default -> {}
        }
    }

    @Override
    public void setRemoteEnabled(boolean on) {
        if (level instanceof ServerLevel s) {
            if (!on) scram(s, "scada");
            else reset();
        }
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ReactorMenu(id, inventory, this);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tanks.deserialize(input.childOrEmpty("tanks"));
        fuel.clearContent();
        input.child("fuel").ifPresent(c -> ContainerHelper.loadAllItems(c, fuel.getItems()));
        spent.clearContent();
        input.child("spent").ifPresent(c -> ContainerHelper.loadAllItems(c, spent.getItems()));
        scrammed = input.getBooleanOr("scrammed", true);
        tripCause = input.getStringOr("trip_cause", "");
        auto = input.getBooleanOr("auto", false);
        targetPower = input.getDoubleOr("target", 0.5);
        tripTemp = input.getDoubleOr("trip_temp", 1100);
        tripPower = input.getDoubleOr("trip_power", 1.2);
        core.n = input.getDoubleOr("n", 0);
        core.precursors = input.getDoubleOr("c", 0);
        core.iodine = input.getDoubleOr("iodine", 0);
        core.xenon = input.getDoubleOr("xenon", 0);
        core.fuelTemp = input.getDoubleOr("temp", ReactorCore.AMBIENT);
        core.damage = input.getDoubleOr("damage", 0);
        core.decayHeat = input.getDoubleOr("decay", 0);
        for (int c = 0; c < MAX_COLUMNS; c++) {
            rods[c] = input.getDoubleOr("rod" + c, 1);
            setpoints[c] = input.getDoubleOr("set" + c, 1);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tanks.serialize(output.child("tanks"));
        ContainerHelper.saveAllItems(output.child("fuel"), fuel.getItems());
        ContainerHelper.saveAllItems(output.child("spent"), spent.getItems());
        output.putBoolean("scrammed", scrammed);
        output.putString("trip_cause", tripCause);
        output.putBoolean("auto", auto);
        output.putDouble("target", targetPower);
        output.putDouble("trip_temp", tripTemp);
        output.putDouble("trip_power", tripPower);
        output.putDouble("n", core.n);
        output.putDouble("c", core.precursors);
        output.putDouble("iodine", core.iodine);
        output.putDouble("xenon", core.xenon);
        output.putDouble("temp", core.fuelTemp);
        output.putDouble("damage", core.damage);
        output.putDouble("decay", core.decayHeat);
        for (int c = 0; c < MAX_COLUMNS; c++) {
            output.putDouble("rod" + c, rods[c]);
            output.putDouble("set" + c, setpoints[c]);
        }
    }
}
