package com.powerfactor.logistics;

import com.powerfactor.registry.ModBlockEntities;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Holds which arms a player has disabled, and (for pipes with an extract arm) does the moving:
 * every 10 ticks it walks the connected pipes, then pulls a batch from each extract face and
 * shares it round-robin among the insert faces.
 */
public class PipeBlockEntity extends BlockEntity {
    private static final int INTERVAL = 10, MAX_PIPES = 1024;
    private PipeSettings settings = PipeSettings.DEFAULT;
    private final PipeFilter[] filters = new PipeFilter[6];
    private int rotation;

    public PipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PIPE.get(), pos, state);
    }

    public boolean disabled(Direction side) {
        return (settings.disabledMask() & 1 << side.ordinal()) != 0;
    }

    public void setDisabled(Direction side, boolean disabled) {
        settings = settings.withDisabled(side, disabled);
        setChanged();
    }

    public PipeSettings settings() { return settings; }
    public PipeKind kind() { return ((PipeBlock) getBlockState().getBlock()).kind(); }
    public PipeSettings.Face face(Direction side) { return settings.face(side); }
    public void setFace(Direction side, PipeSettings.Face filter) {
        settings = settings.withFace(side, filter);
        filters[side.ordinal()] = null;
        setChanged();
    }
    public boolean setSample(Direction side, int index, net.minecraft.world.item.ItemStack stack) {
        if (index < 0 || index >= PipeSettings.SAMPLES) return false;
        if (!stack.isEmpty() && (PipeFilter.sample(stack, kind()).isEmpty() || !kind().accepts(PipeFilter.sample(stack, kind())))) return false;
        setFace(side, face(side).withSample(index, stack));
        return true;
    }
    public boolean accepts(Direction side, Resource resource) {
        if (disabled(side)) return false;
        PipeFilter filter = filters[side.ordinal()];
        if (filter == null) filters[side.ordinal()] = filter = new PipeFilter(face(side), kind());
        return filter.accepts(resource);
    }

    /** A place pipes deliver to: the block, the face it's reached through, and the pipe next to it. */
    record Endpoint(BlockPos pos, Direction side, BlockPos pipe, int priority) {}

    private final java.util.Map<BlockPos, BlockPos> parents = new java.util.HashMap<>();

    void serverTick(ServerLevel level, BlockPos pos, BlockState state, PipeKind kind) {
        if ((level.getGameTime() + pos.asLong()) % INTERVAL != 0) {
            return;
        }
        List<Endpoint> targets = network(level, pos, state.getBlock());
        if (targets.isEmpty()) {
            return;
        }
        for (Direction d : Direction.values()) {
            if (state.getValue(PipeBlock.SIDES.get(d)) != PipeSide.EXTRACT) {
                continue;
            }
            BlockPos sourcePos = pos.relative(d);
            if (disabled(d) || !level.isLoaded(sourcePos)) continue;
            Object source = level.getCapability(kind.capability, sourcePos, d.getOpposite());
            if (source instanceof ResourceHandler<?> handler) {
                pull(level, kind, handler, sourcePos, pos, d, targets);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Resource> void pull(ServerLevel level, PipeKind kind, ResourceHandler<?> raw, BlockPos sourcePos, BlockPos start, Direction sourceSide,
            List<Endpoint> targets) {
        ResourceHandler<T> source = (ResourceHandler<T>) raw;
        int budget = kind.batch;
        for (int i = 0; i < source.size() && budget > 0; i++) {
            T resource = source.getResource(i);
            if (resource.isEmpty() || !kind.accepts(resource) || !accepts(sourceSide, resource)) {
                continue;
            }
            // Rotate peers inside each priority tier; higher-priority destinations remain first.
            for (int first = 0; first < targets.size() && budget > 0;) {
                int end = first + 1;
                while (end < targets.size() && targets.get(end).priority() == targets.get(first).priority()) end++;
                for (int t = 0; t < end - first && budget > 0; t++) {
                    Endpoint e = targets.get(first + Math.floorMod(t + rotation, end - first));
                    if (e.pos().equals(sourcePos)) continue;
                    if (!level.isLoaded(e.pos()) || !(level.getBlockEntity(e.pipe()) instanceof PipeBlockEntity destination)
                            || !destination.accepts(e.side().getOpposite(), resource)) continue;
                    Object targetRaw = level.getCapability(kind.capability, e.pos(), e.side());
                    if (!(targetRaw instanceof ResourceHandler<?> th)) continue;
                    ResourceHandler<T> target = (ResourceHandler<T>) th;
                    if (target == source) continue;
                    int moved = move(source, i, target, resource, budget);
                    if (moved > 0) {
                        budget -= moved;
                        show(level, sourcePos, start, e, resource, moved);
                    }
                }
                first = end;
            }
        }
        rotation = (rotation + 1) & Integer.MAX_VALUE;
    }

    private static <T extends Resource> int move(ResourceHandler<T> source, int slot, ResourceHandler<T> target, T resource, int budget) {
        try (Transaction tx = Transaction.openRoot()) {
            int accepted;
            try (Transaction simulate = Transaction.open(tx)) {
                accepted = Math.clamp(target.insert(resource, budget, simulate), 0, budget);
            }
            if (accepted <= 0) return 0;
            int got = source.extract(slot, resource, accepted, tx);
            if (got <= 0 || got > accepted || target.insert(resource, got, tx) != got) return 0;
            tx.commit();
            return got;
        }
    }

    /** Tell nearby players what just went through, so the glass pipes can show it travelling. */
    private void show(ServerLevel level, BlockPos sourcePos, BlockPos start, Endpoint e, Resource resource, int amount) {
        List<BlockPos> path = new ArrayList<>();
        path.add(e.pos());
        for (BlockPos p = e.pipe(); p != null; p = parents.get(p)) {
            path.add(p);
            if (p.equals(start)) {
                break;
            }
        }
        path.add(sourcePos);
        java.util.Collections.reverse(path);
        net.minecraft.world.item.ItemStack item = resource instanceof net.neoforged.neoforge.transfer.item.ItemResource ir
                ? ir.toStack(Math.min(amount, 64)) : net.minecraft.world.item.ItemStack.EMPTY;
        net.minecraft.resources.Identifier fluid = resource instanceof net.neoforged.neoforge.transfer.fluid.FluidResource fr
                ? net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fr.getFluid()) : net.minecraft.resources.Identifier.withDefaultNamespace("empty");
        com.powerfactor.api.ClientEffects.send(level, net.minecraft.world.level.ChunkPos.containing(start),
                new PipeTraffic(path, item, fluid));
    }

    /** Insert endpoints reachable through pipes of the same block. */
    private List<Endpoint> network(ServerLevel level, BlockPos start, net.minecraft.world.level.block.Block pipe) {
        List<Endpoint> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        parents.clear();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            BlockState s = level.getBlockState(p);
            if (s.getBlock() != pipe) {
                continue;
            }
            for (Direction d : Direction.values()) {
                PipeSide side = s.getValue(PipeBlock.SIDES.get(d));
                BlockPos next = p.relative(d);
                if (!level.isLoaded(next) || level.getBlockEntity(p) instanceof PipeBlockEntity owner && owner.disabled(d)) continue;
                if (side == PipeSide.PIPE && seen.size() < MAX_PIPES && level.getBlockState(next).getBlock() == pipe
                        && level.getBlockState(next).getValue(PipeBlock.SIDES.get(d.getOpposite())) == PipeSide.PIPE
                        && !(level.getBlockEntity(next) instanceof PipeBlockEntity neighbour && neighbour.disabled(d.getOpposite())) && seen.add(next)) {
                    parents.put(next, p);
                    queue.add(next);
                } else if (side == PipeSide.INSERT) {
                    int priority = level.getBlockEntity(p) instanceof PipeBlockEntity owner ? owner.face(d).priority() : 0;
                    out.add(new Endpoint(next, d.getOpposite(), p, priority));
                }
            }
        }
        out.sort(java.util.Comparator.comparingInt(Endpoint::priority).reversed());
        return out;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        settings = input.read("routing", PipeSettings.CODEC).orElseGet(() ->
                new PipeSettings(input.getIntOr("disabled", 0) & 63, PipeSettings.DEFAULT.faces()));
        java.util.Arrays.fill(filters, null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("routing", PipeSettings.CODEC, settings);
    }

    @Override protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!settings.equals(PipeSettings.DEFAULT)) components.set(com.powerfactor.registry.ModComponents.PIPE_SETTINGS.get(), settings);
    }
    @Override protected void applyImplicitComponents(net.minecraft.core.component.DataComponentGetter components) {
        super.applyImplicitComponents(components);
        var saved = components.get(com.powerfactor.registry.ModComponents.PIPE_SETTINGS.get());
        if (saved != null) { settings = saved; java.util.Arrays.fill(filters, null); }
    }
}
