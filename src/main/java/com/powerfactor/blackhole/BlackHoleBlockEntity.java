package com.powerfactor.blackhole;

import com.powerfactor.machine.MachineBlock;
import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.multiblock.Buildable;
import com.powerfactor.multiblock.MultiblockPart;
import com.powerfactor.multiblock.Structure;
import com.powerfactor.multiblock.StructureAssembly;
import com.powerfactor.registry.ModBlackHole;
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
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * The Black Hole Power Plant: a micro black hole held in a 5x5x5 containment sphere. Ignite it with a
 * Singularity and a 500M FE spin-up; then feed matter through the Accretion Injectors (any item; Exotic
 * Matter and Singularities are dense) and draw power from the Hawking Collectors. Small holes blaze and
 * evaporate fast; big ones are dim and cost a fortune to contain. Starve one and it bursts; let the
 * containment field fail and it escapes.
 */
public class BlackHoleBlockEntity extends MachineBlockEntity implements Buildable {
    public static final int CAPACITY = 2_000_000_000, MAX_IO = 50_000_000, IGNITION = 500_000_000, IGNITION_RATE = 5_000_000, FEED = 9;
    public static final double SEED_MASS = 1000;
    public static final int STATE_DORMANT = 0, STATE_IGNITING = 1, STATE_RUNNING = 2;
    public static final Structure STRUCTURE = new Structure.Builder()
            .key('S', ModBlackHole.CASING).key('K', ModBlackHole.COIL).key('G', ModBlackHole.GLASS).key('I', ModBlackHole.INJECTOR)
            .key('H', ModBlackHole.COLLECTOR)
            .layer(0, "SSCSS", "SKKKS", "SKKKS", "SKKKS", "SSSSS")
            .layer(1, "SGIGS", "G...G", "I...I", "G...G", "SGIGS")
            .layer(2, "SGGGS", "G...G", "H...H", "G...G", "SGHGS")   // the front centre is a window onto the hole
            .layer(3, "SGGGS", "G...G", "G...G", "G...G", "SGGGS")
            .layer(4, "SSSSS", "SKKKS", "SKKKS", "SKKKS", "SSSSS").build();

    private final StructureAssembly assembly = new StructureAssembly();
    private final BlackHoleCore core = new BlackHoleCore();
    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CAPACITY, MAX_IO, MAX_IO) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private final SimpleContainer feed = new SimpleContainer(FEED) {
        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(BlackHoleBlockEntity.this, player);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            BlackHoleBlockEntity.this.setChanged();
        }
    };
    private final ResourceHandler<ItemResource> items = new net.neoforged.neoforge.transfer.DelegatingResourceHandler<>(VanillaContainerWrapper.of(feed)) {
        @Override
        public int extract(int index, ItemResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
            return 0;   // matter only goes in
        }
    };
    private int state;
    /** Mass the auto-feeder holds; 0 = feed manually at {@link #feedRate}. */
    private double targetMass = SEED_MASS;
    private boolean autoFeed = true;
    /** Manual feed rate, items per second. */
    private int feedRate = 2;
    private double feedDebt, lastFeed, feedAvg, ignition;
    private int outputCursor;
    private float clientMass, clientHawking, clientFeed;
    private int clientState;

    public BlackHoleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlackHole.CONTROLLER_BE.get(), pos, state);
    }

    @Override
    public Structure buildStructure() {
        return STRUCTURE;
    }

    public boolean formed() {
        return assembly.formed();
    }

    public SimpleEnergyHandler getEnergy() {
        return energy;
    }

    public ResourceHandler<ItemResource> getItems() {
        return items;
    }

    public BlackHoleCore core() {
        return core;
    }

    public SimpleContainer feed() {
        return feed;
    }

    @Override
    protected Container inventory() {
        return feed;
    }

    public int state() {
        return state;
    }

    public double targetMass() {
        return targetMass;
    }

    public boolean autoFeed() {
        return autoFeed;
    }

    public int feedRate() {
        return feedRate;
    }

    /** Mass units per tick, smoothed over a few seconds. */
    public double lastFeed() {
        return feedAvg;
    }

    public double ignition() {
        return ignition;
    }

    /** The centre of the containment sphere, where the hole hangs. */
    public Vec3 centre() {
        Direction f = getBlockState().getValue(MachineBlock.FACING);
        // The structure runs from the controller (row 0) back 4 rows; the middle of the 3x3x3 hollow is row 2, layer 2.
        BlockPos c = worldPosition.relative(f.getOpposite(), 2).above(2);
        return Vec3.atCenterOf(c);
    }

    /** Mass of an item fed in: Exotic Matter and Singularities are far denser than ordinary matter. */
    public static double massOf(ItemStack s) {
        if (s.is(ModQuantum.SINGULARITY.get())) return 1000;
        if (s.is(ModQuantum.EXOTIC_MATTER.get())) return 100;
        return 1;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState st) {
        assembly.tick(level, pos, st.getValue(MachineBlock.FACING), STRUCTURE);
        if (!formed()) {
            if (state == STATE_RUNNING && core.mass > 0) escape(level);   // the cage is broken
            setRunning(level, pos, st, false);
            return;
        }
        switch (state) {
            case STATE_IGNITING -> {
                int draw = Math.min(IGNITION_RATE, energy.getAmountAsInt());
                energy.set(energy.getAmountAsInt() - draw);
                ignition += draw;
                if (level.getGameTime() % 4 == 0) {
                    Vec3 c = centre();
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 12, 0.6, 0.6, 0.6, 0.2);
                }
                if (ignition >= IGNITION) {
                    state = STATE_RUNNING;
                    core.mass = SEED_MASS;
                    core.burst = core.escaped = false;
                    Vec3 c = centre();
                    level.playSound(null, BlockPos.containing(c), SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 2F, 0.5F);
                    level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 80, 0.3, 0.3, 0.3, 0.4);
                }
            }
            case STATE_RUNNING -> {
                double fed = feedMatter();
                double out = core.tick(fed, energy.getAmountAsInt() + core.hawking + fed * BlackHoleCore.C2 * BlackHoleCore.ACCRETION_EFFICIENCY);
                long net = (long) energy.getAmountAsInt() + (long) out - (long) core.containment;
                energy.set((int) Math.clamp(net, 0, CAPACITY));
                if (core.burst) burst(level);
                else if (core.escaped) escape(level);
            }
            default -> {}
        }
        push(level, pos);
        setRunning(level, pos, st, state != STATE_DORMANT);
        if (level.getGameTime() % 5 == 0) {
            level.sendBlockUpdated(pos, st, st, Block.UPDATE_CLIENTS);
            BlackHolePayloads.broadcast(level, this);
        }
    }

    /** Takes this tick's matter from the injectors: enough to hold the target mass on auto, or the manual rate. */
    private double feedMatter() {
        double want;
        if (autoFeed) {
            // Replace what evaporates, and close a fraction of the gap to the target each second.
            want = core.holdFeed() + Math.max(-core.holdFeed(), (targetMass - core.mass) * 0.002);
        } else {
            want = feedRate / 20.0;
        }
        feedDebt += Math.max(0, want);
        double fed = 0;
        for (int i = 0; i < FEED && feedDebt >= 1e-9; i++) {
            ItemStack s = feed.getItem(i);
            while (!s.isEmpty() && feedDebt > 0) {
                double m = massOf(s);
                if (m > feedDebt + 1 && fed > 0) break;   // don't overshoot with a dense item while lighter matter is coming
                s.shrink(1);
                fed += m;
                feedDebt -= m;
            }
        }
        feedDebt = Math.min(feedDebt, 5);   // an empty hopper doesn't bank a backlog
        lastFeed = fed;
        // Matter arrives an item at a time; the readouts show a smoothed rate.
        feedAvg += (fed - feedAvg) * 0.02;
        if (fed > 0) feed.setChanged();
        return fed;
    }

    /** Starved: the last of the hole radiates away in one flash. */
    private void burst(ServerLevel level) {
        Vec3 c = centre();
        state = STATE_DORMANT;
        core.mass = 0;
        ignition = 0;
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 2, 0.5, 0.5, 0.5, 0);
        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 200, 1, 1, 1, 0.6);
        level.explode(null, c.x, c.y, c.z, 6, false, com.powerfactor.PowerFactorConfig.reactorExplosions() ? Level.ExplosionInteraction.BLOCK
                : Level.ExplosionInteraction.NONE);
        level.playSound(null, BlockPos.containing(c), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 6F, 0.4F);
    }

    /** Containment lost: the hole breaks loose as a wandering singularity. */
    private void escape(ServerLevel level) {
        Vec3 c = centre();
        state = STATE_DORMANT;
        core.mass = 0;
        ignition = 0;
        var e = new com.powerfactor.weapon.SingularityEntity(ModQuantum.SINGULARITY_ENTITY.get(), level);
        e.setPos(c.x, c.y, c.z);
        e.setDeltaMovement(0, 0.02, 0);
        level.addFreshEntity(e);
        level.playSound(null, BlockPos.containing(c), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 4F, 0.3F);
    }

    private void push(ServerLevel level, BlockPos pos) {
        if (energy.getAmountAsInt() == 0) return;
        Set<BlockPos> ports = new HashSet<>(assembly.ports());
        ports.add(pos);
        List<EnergyHandler> targets = new ArrayList<>();
        var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<EnergyHandler, Boolean>());
        for (BlockPos port : ports) {
            if (port != pos && !level.getBlockState(port).is(ModBlackHole.COLLECTOR.get())) continue;   // power leaves by the collectors
            for (Direction side : Direction.values()) {
                BlockPos at = port.relative(side);
                if (ports.contains(at) || !level.hasChunkAt(at)) continue;
                var entity = level.getBlockEntity(at);
                if (entity instanceof MultiblockPart part) entity = part.controller();
                if (entity == this) continue;
                var target = level.getCapability(Capabilities.Energy.BLOCK, at, side.getOpposite());
                if (target != null && target != energy && seen.add(target)) targets.add(target);
            }
        }
        // Keep a reserve for the containment field: never send away the next ten seconds of it.
        int reserve = (int) Math.min(CAPACITY, core.containment * 200);
        for (int i = 0; i < targets.size() && energy.getAmountAsInt() > reserve; i++) {
            int share = Math.max(1, (energy.getAmountAsInt() - reserve) / (targets.size() - i));
            try (Transaction tx = Transaction.openRoot()) {
                int moved = targets.get((i + outputCursor) % targets.size()).insert(Math.min(share, MAX_IO), tx);
                tx.commit();
                energy.set(energy.getAmountAsInt() - moved);
            }
        }
        outputCursor++;
    }

    // ---------------------------------------------------------------- controls

    public boolean ignite(ServerLevel level) {
        if (!formed() || state != STATE_DORMANT) return false;
        for (int i = 0; i < FEED; i++) {
            if (feed.getItem(i).is(ModQuantum.SINGULARITY.get())) {
                feed.getItem(i).shrink(1);
                state = STATE_IGNITING;
                ignition = 0;
                level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 2F, 0.4F);
                return true;
            }
        }
        return false;
    }

    public void setTargetMass(double m) {
        targetMass = Math.clamp(m, BlackHoleCore.MIN_MASS * 2, 20_000);
        setChanged();
    }

    public void setAutoFeed(boolean on) {
        autoFeed = on;
        setChanged();
    }

    public void setFeedRate(int perSecond) {
        feedRate = Math.clamp(perSecond, 0, 200);
        setChanged();
    }

    // ---------------------------------------------------------------- client, SCADA, persistence

    /** 1.2M, 340k: server-safe (Skin is client-only). */
    static String compact(double v) {
        double a = Math.abs(v);
        return a >= 1e9 ? String.format("%.1fG", v / 1e9) : a >= 1e6 ? String.format("%.1fM", v / 1e6) : a >= 1e3 ? String.format("%.0fk", v / 1e3)
                : String.valueOf(Math.round(v));
    }

    public float clientMass() {
        return clientMass;
    }

    public float clientHawking() {
        return clientHawking;
    }

    public float clientFeed() {
        return clientFeed;
    }

    public int clientState() {
        return clientState;
    }

    @Override
    public ScadaReading scadaReading() {
        var status = state == STATE_RUNNING ? (core.mass < 200 || energy.getAmountAsInt() < core.containment * 100 ? ScadaReading.Status.WARNING
                : ScadaReading.Status.RUNNING) : ScadaReading.Status.OFF;
        int fe = (int) Math.min(Integer.MAX_VALUE, core.net);
        var base = new ScadaReading(worldPosition, getDisplayName(), status, state == STATE_RUNNING ? fe : 0, 0,
                Component.translatable("gui.powerfactor.black_hole.scada", (int) core.mass, compact(core.net)), true, true, true, 0, 0);
        return base.with(List.of(
                new ScadaReading.Point(Component.translatable("gui.powerfactor.black_hole.point.mass"), (float) core.mass, "", 0, 5000,
                        core.mass < 200 ? 2 : core.mass < 400 ? 1 : 0),
                new ScadaReading.Point(Component.translatable("gui.powerfactor.black_hole.point.hawking"), (float) core.hawking, "FE/t", 0, 5_000_000, 0),
                new ScadaReading.Point(Component.translatable("gui.powerfactor.black_hole.point.accretion"), (float) (feedAvg * BlackHoleCore.C2
                        * BlackHoleCore.ACCRETION_EFFICIENCY), "FE/t", 0, 5_000_000, 0),
                new ScadaReading.Point(Component.translatable("gui.powerfactor.black_hole.point.containment"), (float) core.containment, "FE/t", 0, 1_000_000,
                        energy.getAmountAsInt() < core.containment * 100 ? 2 : 0),
                new ScadaReading.Point(Component.translatable("gui.powerfactor.black_hole.point.stored"), energy.getAmountAsInt(), "FE", 0, CAPACITY, 0)),
                List.of(ScadaReading.Control.toggle("auto", Component.translatable("gui.powerfactor.black_hole.auto"), autoFeed),
                        ScadaReading.Control.setpoint("mass", Component.translatable("gui.powerfactor.black_hole.target"), (float) targetMass, 100, 20_000, 100)));
    }

    @Override
    public void scadaControl(String id, float value) {
        switch (id) {
            case "auto" -> setAutoFeed(value > 0);
            case "mass" -> setTargetMass(value);
            default -> {}
        }
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new BlackHoleMenu(id, inventory, this, feed);
    }

    /** Only a real removal frees the casings: setRemoved also runs when the chunk unloads. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel s) assembly.release(s, worldPosition);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        feed.clearContent();
        ContainerHelper.loadAllItems(input, feed.getItems());
        state = input.getIntOr("bh_state", 0);
        core.mass = input.getDoubleOr("mass", 0);
        ignition = input.getDoubleOr("ignition", 0);
        targetMass = input.getDoubleOr("target_mass", SEED_MASS);
        autoFeed = input.getBooleanOr("auto_feed", true);
        feedRate = input.getIntOr("feed_rate", 2);
        clientMass = (float) core.mass;
        clientState = state;
        clientHawking = input.getFloatOr("hawking", 0);
        clientFeed = input.getFloatOr("fed", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        ContainerHelper.saveAllItems(output, feed.getItems());
        output.putInt("bh_state", state);
        output.putDouble("mass", core.mass);
        output.putDouble("ignition", ignition);
        output.putDouble("target_mass", targetMass);
        output.putBoolean("auto_feed", autoFeed);
        output.putInt("feed_rate", feedRate);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("bh_state", state);
        tag.putDouble("mass", core.mass);
        tag.putFloat("hawking", (float) core.hawking);
        tag.putFloat("fed", (float) feedAvg);
        tag.putDouble("ignition", ignition);
        return tag;
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        clientState = input.getIntOr("bh_state", 0);
        clientMass = (float) input.getDoubleOr("mass", 0);
        clientHawking = input.getFloatOr("hawking", 0);
        clientFeed = clientFeed * 0.7F + input.getFloatOr("fed", 0) * 0.3F;
        ignition = input.getDoubleOr("ignition", 0);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ValueInput input) {
        handleUpdateTag(input);
    }
}
