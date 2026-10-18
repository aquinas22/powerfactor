package com.powerfactor.laser;

import com.powerfactor.registry.ModLaser;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * A Laser Node. It reaches into the block it is attached to and, through every node it is linked to
 * (directly or along a chain of links), moves items, fluids and energy: each channel is Off, Send or
 * Receive. Senders push to receivers in priority order; items can be filtered. Everything moves in
 * one transaction per hop, so nothing is ever lost or duplicated.
 */
public class LaserNodeBlockEntity extends BlockEntity implements MenuProvider {
    public enum Channel { ITEMS, FLUIDS, ENERGY }

    public static final int OFF = 0, SEND = 1, RECEIVE = 2;
    public static final int RANGE = 24, MAX_LINKS = 8;
    public static final int ITEMS_PER_OP = 16, FLUID_PER_OP = 1000, ENERGY_PER_TICK = 100_000, PERIOD = 5;
    public static final int FILTER_SLOTS = 6;

    private final Set<BlockPos> links = new LinkedHashSet<>();
    private final int[] modes = new int[3];
    private final SimpleContainer filter = new SimpleContainer(FILTER_SLOTS);
    private boolean deny;
    private int priority;
    /** Bit per channel: moved something recently (drives the beam colours). */
    private int activity, syncedActivity = -1, ticks;
    private int clientActivity;
    private final List<BlockPos> clientLinks = new ArrayList<>();

    public LaserNodeBlockEntity(BlockPos pos, BlockState state) {
        super(ModLaser.LASER_NODE_BE.get(), pos, state);
    }

    public Direction facing() {
        return getBlockState().getValue(LaserNodeBlock.FACING);
    }

    public BlockPos target() {
        return worldPosition.relative(facing());
    }

    public int mode(Channel c) {
        return modes[c.ordinal()];
    }

    public void cycle(Channel c) {
        modes[c.ordinal()] = (modes[c.ordinal()] + 1) % 3;
        changed();
    }

    public void setMode(Channel c, int mode) {
        modes[c.ordinal()] = Math.clamp(mode, OFF, RECEIVE);
        changed();
    }

    public SimpleContainer filter() {
        return filter;
    }

    public boolean deny() {
        return deny;
    }

    public void toggleDeny() {
        deny = !deny;
        changed();
    }

    public int priority() {
        return priority;
    }

    public void addPriority(int d) {
        priority = Math.clamp(priority + d, -99, 99);
        changed();
    }

    public Set<BlockPos> links() {
        return links;
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    // ------------------------------------------------------------------ linking

    /** Why two nodes can't be linked, or null if they can. */
    public static @Nullable Component canLink(Level level, BlockPos a, BlockPos b) {
        if (a.equals(b)) return Component.translatable("gui.powerfactor.laser.same");
        if (!(level.getBlockEntity(a) instanceof LaserNodeBlockEntity na) || !(level.getBlockEntity(b) instanceof LaserNodeBlockEntity nb)) {
            return Component.translatable("gui.powerfactor.laser.not_node");
        }
        if (a.distSqr(b) > (double) RANGE * RANGE) return Component.translatable("gui.powerfactor.laser.too_far", RANGE);
        if (!na.links.contains(b) && (na.links.size() >= MAX_LINKS || nb.links.size() >= MAX_LINKS)) {
            return Component.translatable("gui.powerfactor.laser.full", MAX_LINKS);
        }
        if (!lineOfSight(level, a, b)) return Component.translatable("gui.powerfactor.laser.blocked");
        return null;
    }

    /** Clear air between the two lenses (the nodes themselves don't count). */
    public static boolean lineOfSight(Level level, BlockPos a, BlockPos b) {
        Vec3 from = Vec3.atCenterOf(a), to = Vec3.atCenterOf(b);
        Vec3 d = to.subtract(from).normalize();
        Vec3 start = from.add(d.scale(0.5)), end = to.subtract(d.scale(0.5));
        if (start.distanceToSqr(end) < 0.01) return true;
        return level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty())).getType()
                == HitResult.Type.MISS;
    }

    /** Links or (if already linked) unlinks two nodes. Returns true if they are now linked. */
    public static boolean toggleLink(Level level, BlockPos a, BlockPos b) {
        var na = (LaserNodeBlockEntity) level.getBlockEntity(a);
        var nb = (LaserNodeBlockEntity) level.getBlockEntity(b);
        if (na.links.remove(b)) {
            nb.links.remove(a);
            na.changed();
            nb.changed();
            return false;
        }
        na.links.add(b);
        nb.links.add(a);
        na.changed();
        nb.changed();
        return true;
    }

    /** Only when the node is actually broken (not on chunk unload): tell loaded partners the beam is gone. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && !level.isClientSide()) {
            for (BlockPos p : links) {
                if (level.isLoaded(p) && level.getBlockEntity(p) instanceof LaserNodeBlockEntity other && other.links.remove(worldPosition)) other.changed();
            }
        }
    }

    // ------------------------------------------------------------------ transport

    /** Every node reachable through links (including this one), never crossing unloaded chunks. */
    private List<LaserNodeBlockEntity> network(ServerLevel level) {
        List<LaserNodeBlockEntity> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(worldPosition);
        seen.add(worldPosition);
        while (!queue.isEmpty() && seen.size() < 256) {
            BlockPos p = queue.poll();
            if (!level.isLoaded(p) || !(level.getBlockEntity(p) instanceof LaserNodeBlockEntity n)) continue;
            out.add(n);
            for (BlockPos q : n.links) if (seen.add(q)) queue.add(q);
        }
        return out;
    }

    private @Nullable ResourceHandler<ItemResource> items(ServerLevel level) {
        return level.getCapability(Capabilities.Item.BLOCK, target(), facing().getOpposite());
    }

    private @Nullable ResourceHandler<FluidResource> fluids(ServerLevel level) {
        return level.getCapability(Capabilities.Fluid.BLOCK, target(), facing().getOpposite());
    }

    private @Nullable EnergyHandler energy(ServerLevel level) {
        return level.getCapability(Capabilities.Energy.BLOCK, target(), facing().getOpposite());
    }

    private boolean allows(ItemResource r) {
        boolean any = false, match = false;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            ItemStack f = filter.getItem(i);
            if (f.isEmpty()) continue;
            any = true;
            if (r.matches(f)) match = true;
        }
        return !any || match != deny;
    }

    public void serverTick(ServerLevel level) {
        ticks++;
        // Drop links whose partner has gone (e.g. broken while this chunk was unloaded).
        if (ticks % 40 == 0 && links.removeIf(p -> level.isLoaded(p) && !(level.getBlockEntity(p) instanceof LaserNodeBlockEntity))) changed();
        int act = 0;
        boolean sendsAny = modes[0] == SEND || modes[1] == SEND || modes[2] == SEND;
        if (sendsAny && !links.isEmpty()) {
            List<LaserNodeBlockEntity> graph = network(level);
            if (modes[Channel.ENERGY.ordinal()] == SEND && moveEnergy(level, graph)) act |= 4;
            if (ticks % PERIOD == 0) {
                if (modes[Channel.ITEMS.ordinal()] == SEND && moveItems(level, graph)) act |= 1;
                if (modes[Channel.FLUIDS.ordinal()] == SEND && moveFluids(level, graph)) act |= 2;
            } else {
                act |= activity & 3;     // items/fluids flicker on their own period; hold the beam lit between ops
            }
        }
        activity = act;
        if (ticks % 10 == 0 && activity != syncedActivity) {
            syncedActivity = activity;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private List<LaserNodeBlockEntity> receivers(List<LaserNodeBlockEntity> graph, Channel c) {
        List<LaserNodeBlockEntity> out = new ArrayList<>();
        for (LaserNodeBlockEntity n : graph) if (n != this && n.mode(c) == RECEIVE) out.add(n);
        out.sort(Comparator.comparingInt(LaserNodeBlockEntity::priority).reversed());
        return out;
    }

    private boolean moveItems(ServerLevel level, List<LaserNodeBlockEntity> graph) {
        var from = items(level);
        if (from == null) return false;
        for (LaserNodeBlockEntity to : receivers(graph, Channel.ITEMS)) {
            var dst = to.items(level);
            if (dst == null) continue;
            for (int i = 0; i < from.size(); i++) {
                ItemResource r = from.getResource(i);
                if (r.isEmpty() || from.getAmountAsLong(i) <= 0 || !allows(r) || !to.allows(r)) continue;
                try (Transaction tx = Transaction.openRoot()) {
                    int took = from.extract(i, r, ITEMS_PER_OP, tx);
                    if (took <= 0) continue;
                    int put = net.neoforged.neoforge.transfer.ResourceHandlerUtil.insertStacking(dst, r, took, tx);
                    if (put < took) continue;     // all or nothing: the transaction rolls back
                    tx.commit();
                    to.activity |= 1;
                    return true;
                }
            }
        }
        return false;
    }

    private boolean moveFluids(ServerLevel level, List<LaserNodeBlockEntity> graph) {
        var from = fluids(level);
        if (from == null) return false;
        for (LaserNodeBlockEntity to : receivers(graph, Channel.FLUIDS)) {
            var dst = to.fluids(level);
            if (dst == null) continue;
            int moved = net.neoforged.neoforge.transfer.ResourceHandlerUtil.move(from, dst, f -> true, FLUID_PER_OP, null);
            if (moved > 0) {
                to.activity |= 2;
                return true;
            }
        }
        return false;
    }

    private boolean moveEnergy(ServerLevel level, List<LaserNodeBlockEntity> graph) {
        var from = energy(level);
        if (from == null) return false;
        boolean any = false;
        int budget = ENERGY_PER_TICK;
        for (LaserNodeBlockEntity to : receivers(graph, Channel.ENERGY)) {
            var dst = to.energy(level);
            if (dst == null || budget <= 0) continue;
            // Ask how much would actually make the trip (simulated: never committed), then move exactly that.
            int can;
            try (Transaction probe = Transaction.openRoot()) {
                can = dst.insert(from.extract(budget, probe), probe);
            }
            if (can <= 0) continue;
            try (Transaction tx = Transaction.openRoot()) {
                int took = from.extract(can, tx);
                if (took <= 0 || dst.insert(took, tx) != took) continue;
                tx.commit();
                budget -= took;
                any = true;
                to.activity |= 4;
            }
        }
        return any;
    }

    // ------------------------------------------------------------------ client view

    public int serverActivity() {
        return activity;
    }

    public int clientActivity() {
        return clientActivity;
    }

    public List<BlockPos> clientLinks() {
        return clientLinks;
    }

    public int[] clientModes() {
        return modes;
    }

    // ------------------------------------------------------------------ menu & persistence

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.powerfactor.laser_node");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new LaserNodeMenu(containerId, inventory, this);
    }

    /** Links as x, y, z triples. */
    private int[] packLinks() {
        int[] a = new int[links.size() * 3];
        int i = 0;
        for (BlockPos p : links) {
            a[i++] = p.getX();
            a[i++] = p.getY();
            a[i++] = p.getZ();
        }
        return a;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        links.clear();
        input.getIntArray("links").ifPresent(a -> {
            for (int i = 0; i + 2 < a.length; i += 3) links.add(new BlockPos(a[i], a[i + 1], a[i + 2]));
        });
        input.getIntArray("modes").ifPresent(a -> {
            for (int i = 0; i < Math.min(3, a.length); i++) modes[i] = Math.clamp(a[i], OFF, RECEIVE);
        });
        filter.clearContent();
        net.minecraft.world.ContainerHelper.loadAllItems(input, filter.getItems());
        deny = input.getBooleanOr("deny", false);
        priority = input.getIntOr("priority", 0);
        clientActivity = input.getIntOr("activity", 0);
        clientLinks.clear();
        clientLinks.addAll(links);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putIntArray("links", packLinks());
        output.putIntArray("modes", modes.clone());
        net.minecraft.world.ContainerHelper.saveAllItems(output, filter.getItems());
        output.putBoolean("deny", deny);
        output.putInt("priority", priority);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putIntArray("links", packLinks());
        tag.putIntArray("modes", modes.clone());
        tag.putInt("activity", activity);
        return tag;
    }
}
