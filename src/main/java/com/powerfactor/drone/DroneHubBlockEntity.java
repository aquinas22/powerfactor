package com.powerfactor.drone;

import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.registry.ModDrone;
import com.powerfactor.scada.ScadaReading;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import org.jspecify.annotations.Nullable;

/**
 * Drone Hub. Its AI surveys the ground around it a slice at a time, clusters the ore it finds into veins,
 * scores each vein (what it's worth, how big, how far, how dangerous to reach), and sends its drones to the
 * best ones, writing every decision to a log the console shows. Drones bring their haul back here.
 */
public class DroneHubBlockEntity extends MachineBlockEntity implements com.powerfactor.upgrade.Upgradeable {
    public static final int RADIUS = 24, BAY = 4, OUT = 9, CAPACITY = 4_000_000, MAX_INPUT = 40_000, SCAN_PER_TICK = 6_000, LOG = 40;
    public static final int CHARGE_RATE = 2_000;
    private static final TagKey<Block> ORES = TagKey.create(net.minecraft.core.registries.Registries.BLOCK, net.minecraft.resources.Identifier.parse("c:ores"));

    /** A vein the AI found: its blocks, ore, value score, and the drone working it (or -1). */
    public record Vein(int id, List<BlockPos> blocks, BlockState ore, double score, int value, boolean hazard) {}

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CAPACITY, MAX_INPUT, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    /** 0..3 drone bay, 4.. output. */
    private final SimpleContainer slots = new SimpleContainer(BAY + OUT) {
        @Override
        public boolean canPlaceItem(int index, ItemStack stack) {
            return index < BAY && stack.is(ModDrone.DRONE_ITEM.get());
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return stack.is(ModDrone.DRONE_ITEM.get()) ? 1 : super.getMaxStackSize(stack);   // one drone per bay
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(DroneHubBlockEntity.this, player);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            DroneHubBlockEntity.this.setChanged();
        }
    };
    private final ResourceHandler<ItemResource> items = new net.neoforged.neoforge.transfer.DelegatingResourceHandler<>(VanillaContainerWrapper.of(slots)) {
        @Override
        public int extract(int index, ItemResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
            return index >= BAY ? super.extract(index, resource, amount, tx) : 0;
        }
    };
    private final com.powerfactor.upgrade.UpgradeInventory upgrades = new com.powerfactor.upgrade.UpgradeInventory(
            com.powerfactor.upgrade.UpgradeInventory.CONSOLE, this::setChanged, p -> Container.stillValidBlockEntity(this, p));

    // ---------------------------------------------------------------- the AI's state
    private int scanCursor = -1;   // -1 idle, else index into the survey volume
    private final Set<BlockPos> found = new HashSet<>();
    private final List<Vein> veins = new ArrayList<>();
    private final Map<Integer, UUID> assigned = new HashMap<>();   // vein id → drone
    private final Map<UUID, Integer> drones = new HashMap<>();     // deployed drone → bay slot
    private int nextVein, totalMined, surveys;
    /** Survey reach (blocks out from the hub); tests narrow it to their arena. */
    private int radius = RADIUS;
    private long nextSurvey;
    private final Deque<CompoundTag> log = new ArrayDeque<>();

    public DroneHubBlockEntity(BlockPos pos, BlockState state) {
        super(ModDrone.HUB_BE.get(), pos, state);
    }

    public SimpleEnergyHandler getEnergy() {
        return energy;
    }

    public ResourceHandler<ItemResource> getItems() {
        return items;
    }

    public SimpleContainer slots() {
        return slots;
    }

    public com.powerfactor.upgrade.UpgradeInventory upgrades() {
        return upgrades;
    }

    @Override
    protected Container inventory() {
        return slots;
    }

    public static boolean isOre(BlockState s) {
        return s.is(ORES);
    }

    /** What an ore is worth to the AI. */
    public static int value(BlockState s) {
        var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        if (key.contains("ancient_debris") || key.contains("adamantium") || key.contains("cortanium") || key.contains("qubit") || key.contains("faradaenium")) return 12;
        if (key.contains("diamond") || key.contains("emerald")) return 10;
        if (key.contains("uranium") || key.contains("gold") || key.contains("platinum") || key.contains("silver")) return 6;
        if (key.contains("redstone") || key.contains("lapis") || key.contains("iron")) return 4;
        if (key.contains("quartz") || key.contains("tin") || key.contains("lead") || key.contains("zinc") || key.contains("bauxite")) return 3;
        if (key.contains("copper")) return 2;
        return key.contains("coal") ? 1 : 3;
    }

    // ---------------------------------------------------------------- log

    private void log(String key, int color, Object... args) {
        CompoundTag t = new CompoundTag();
        t.putString("k", key);
        t.putInt("c", color);
        ListTag a = new ListTag();
        for (Object o : args) a.add(StringTag.valueOf(String.valueOf(o)));
        t.put("a", a);
        log.addLast(t);
        while (log.size() > LOG) log.removeFirst();
    }

    public List<CompoundTag> logLines() {
        return List.copyOf(log);
    }

    private static String oreName(BlockState s) {
        return s.getBlock().getName().getString();
    }

    private static String at(BlockPos p) {
        return p.getX() + ", " + p.getY() + ", " + p.getZ();
    }

    // ---------------------------------------------------------------- tick

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState st) {
        if (!enabled(level, pos)) {
            setRunning(level, pos, st, false);
            return;
        }
        if (scanCursor < 0 && level.getGameTime() >= nextSurvey) {
            scanCursor = 0;
            found.clear();
            surveys++;
            log("gui.powerfactor.drone.log.survey_start", 0xFF6FE3FF, surveys, radius * 2 + 1);
        }
        if (scanCursor >= 0) survey(level, pos);
        prune(level);
        dispatch(level, pos);
        setRunning(level, pos, st, !drones.isEmpty() || scanCursor >= 0);
        if (level.getGameTime() % 5 == 0) DronePayloads.broadcast(level, this);
    }

    private int minY() {
        return Math.max(level.getMinY(), worldPosition.getY() - 72);
    }

    private int volume() {
        int side = radius * 2 + 1;
        return side * side * (worldPosition.getY() + 8 - minY() + 1);
    }

    public void setRadius(int r) {
        radius = Math.clamp(r, 2, RADIUS);
    }

    public float surveyProgress() {
        return scanCursor < 0 ? 1 : scanCursor / (float) volume();
    }

    /** A slice of the survey per tick, top down. */
    private void survey(ServerLevel level, BlockPos pos) {
        int side = radius * 2 + 1, total = volume(), top = pos.getY() + 8;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int n = 0; n < SCAN_PER_TICK && scanCursor < total; n++, scanCursor++) {
            int layer = scanCursor / (side * side), rem = scanCursor % (side * side);
            m.set(pos.getX() - radius + rem % side, top - layer, pos.getZ() - radius + rem / side);
            if (!level.isLoaded(m)) continue;
            if (isOre(level.getBlockState(m))) found.add(m.immutable());
        }
        if (scanCursor >= total) {
            scanCursor = -1;
            nextSurvey = level.getGameTime() + 2400;
            cluster(level, pos);
        }
    }

    /** Groups what the survey found into veins and scores them. */
    private void cluster(ServerLevel level, BlockPos pos) {
        Set<BlockPos> claimedBlocks = new HashSet<>();
        for (Vein v : veins) if (assigned.containsKey(v.id())) claimedBlocks.addAll(v.blocks());
        veins.removeIf(v -> !assigned.containsKey(v.id()));
        Set<BlockPos> left = new HashSet<>(found);
        left.removeAll(claimedBlocks);
        int made = 0, hazards = 0;
        while (!left.isEmpty()) {
            BlockPos seed = left.iterator().next();
            BlockState ore = level.getBlockState(seed);
            List<BlockPos> blocks = new ArrayList<>();
            Deque<BlockPos> q = new ArrayDeque<>(List.of(seed));
            left.remove(seed);
            boolean hazard = false;
            while (!q.isEmpty()) {
                BlockPos p = q.poll();
                blocks.add(p);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            BlockPos n = p.offset(dx, dy, dz);
                            if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) == 1 && level.getFluidState(n).is(net.minecraft.tags.FluidTags.LAVA)) hazard = true;
                            if (left.remove(n)) q.add(n);   // diagonal neighbours count, as for vein mining
                        }
                    }
                }
            }
            blocks.sort(Comparator.comparingDouble(b -> b.distSqr(pos)));
            double dist = Math.sqrt(blocks.getFirst().distSqr(pos));
            int value = value(ore);
            double score = value * Math.sqrt(blocks.size()) * 10 / (4 + dist) * (hazard ? 0.25 : 1);
            veins.add(new Vein(nextVein++, blocks, ore, score, value, hazard));
            made++;
            if (hazard) hazards++;
        }
        veins.sort(Comparator.comparingDouble(Vein::score).reversed());
        log("gui.powerfactor.drone.log.survey_done", 0xFF6FE3FF, found.size(), made);
        if (!veins.isEmpty()) {
            Vein best = veins.getFirst();
            log("gui.powerfactor.drone.log.best", 0xFFFFD84A, oreName(best.ore()), best.blocks().size(), String.format("%.1f", best.score()));
        }
        if (hazards > 0) log("gui.powerfactor.drone.log.hazards", 0xFFFF8A3A, hazards);
    }

    /** Sends any idle drone to the best unclaimed vein. */
    private void dispatch(ServerLevel level, BlockPos pos) {
        if (level.getGameTime() % 10 != 0) return;
        for (int slot = 0; slot < BAY; slot++) {
            if (!slots.getItem(slot).is(ModDrone.DRONE_ITEM.get()) || drones.containsValue(slot)) continue;
            if (energy.getAmountAsInt() < MiningDroneEntity.BATTERY) {
                if (level.getGameTime() % 200 == 0) log("gui.powerfactor.drone.log.no_power", 0xFFFF4A3A);
                return;
            }
            if (outputFull()) {
                if (level.getGameTime() % 200 == 0) log("gui.powerfactor.drone.log.full", 0xFFFF4A3A);
                return;
            }
            Vein pick = null;
            for (Vein v : veins) {
                if (assigned.containsKey(v.id())) continue;
                if (v.hazard() && veins.stream().anyMatch(o -> !o.hazard() && !assigned.containsKey(o.id()))) continue;
                pick = v;
                break;
            }
            if (pick == null) return;
            energy.set(energy.getAmountAsInt() - MiningDroneEntity.BATTERY);
            double speed = 0.3 * upgrades.effects().speedMultiplier();
            var d = MiningDroneEntity.launch(level, pos, slot, pick.blocks(), pick.id(), speed);
            drones.put(d.getUUID(), slot);
            assigned.put(pick.id(), d.getUUID());
            double eta = Math.sqrt(pick.blocks().getFirst().distSqr(pos)) / (speed * 20) + 1;
            log("gui.powerfactor.drone.log.assign", 0xFF7CFF8A, slot + 1, oreName(pick.ore()), pick.blocks().size(), at(pick.blocks().getFirst()),
                    String.format("%.1f", pick.score()), String.format("%.0f", eta));
            if (pick.hazard()) log("gui.powerfactor.drone.log.risk", 0xFFFF8A3A, slot + 1);
        }
    }

    /** Forgets drones that are gone (killed, or their chunk lost) so their bay slot frees up. */
    private void prune(ServerLevel level) {
        if (level.getGameTime() % 100 != 0) return;
        drones.keySet().removeIf(id -> {
            if (level.getEntity(id) instanceof MiningDroneEntity) return false;
            assigned.values().remove(id);
            return true;
        });
    }

    private boolean outputFull() {
        for (int i = BAY; i < BAY + OUT; i++) if (slots.getItem(i).isEmpty()) return false;
        return true;
    }

    void droneMined(MiningDroneEntity d, BlockState ore) {
        totalMined++;
        found.remove(d.target());
    }

    void droneReturning(MiningDroneEntity d) {
        log("gui.powerfactor.drone.log.returning", 0xFF8FA6B5, d.index() + 1, d.mined(), d.battery() / 300);
    }

    /** A drone landed: unload its cargo, free its vein if worked out, recharge happens on the next launch. */
    void dock(MiningDroneEntity d) {
        for (int i = 0; i < MiningDroneEntity.CARGO; i++) {
            ItemStack s = d.cargo().getItem(i);
            for (int o = BAY; o < BAY + OUT && !s.isEmpty(); o++) {
                ItemStack t = slots.getItem(o);
                if (t.isEmpty()) {
                    slots.setItem(o, s.copy());
                    s.setCount(0);
                } else if (ItemStack.isSameItemSameComponents(t, s)) {
                    int move = Math.min(s.getCount(), t.getMaxStackSize() - t.getCount());
                    t.grow(move);
                    s.shrink(move);
                }
            }
            if (!s.isEmpty() && level != null) net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1,
                    worldPosition.getZ() + 0.5, s);
        }
        slots.setChanged();
        drones.remove(d.getUUID());
        Integer vein = null;
        for (var e : assigned.entrySet()) if (e.getValue().equals(d.getUUID())) vein = e.getKey();
        if (vein != null) {
            int id = vein;
            Vein v = veins.stream().filter(x -> x.id() == id).findFirst().orElse(null);
            boolean done = v == null || v.blocks().stream().noneMatch(b -> isOre(level.getBlockState(b)));
            if (done) {
                veins.removeIf(x -> x.id() == id);
                assigned.remove(id);
                log("gui.powerfactor.drone.log.vein_done", 0xFF7CFF8A, d.index() + 1, d.mined());
            } else {
                assigned.remove(id);   // unfinished: back in the pool for the next drone
                log("gui.powerfactor.drone.log.unfinished", 0xFFFFD84A, d.index() + 1);
            }
        }
        level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.BEEHIVE_ENTER, net.minecraft.sounds.SoundSource.BLOCKS, 1F, 1.6F);
    }

    public List<Vein> veins() {
        return List.copyOf(veins);
    }

    public Map<Integer, UUID> assigned() {
        return assigned;
    }

    public Set<UUID> droneIds() {
        return drones.keySet();
    }

    public int totalMined() {
        return totalMined;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && !level.isClientSide()) net.minecraft.world.Containers.dropContents(level, pos, upgrades);
    }

    @Override
    public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), isRunning() ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF, 0, 0,
                Component.translatable("gui.powerfactor.drone.scada", drones.size(), veins.size(), totalMined), true, remoteEnabled(), false, 0, 0);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new DroneHubMenu(id, inventory, this, slots);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        slots.clearContent();
        ContainerHelper.loadAllItems(input, slots.getItems());
        upgrades.load(input);
        totalMined = input.getIntOr("mined", 0);
        surveys = input.getIntOr("surveys", 0);
        // Veins and assignments are re-derived by the next survey; deployed drones re-dock on their own.
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        ContainerHelper.saveAllItems(output, slots.getItems());
        upgrades.save(output);
        output.putInt("mined", totalMined);
        output.putInt("surveys", surveys);
    }
}
