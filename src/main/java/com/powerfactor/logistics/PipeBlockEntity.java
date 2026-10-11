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
    private int disabledMask;
    private int rotation;

    public PipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PIPE.get(), pos, state);
    }

    public boolean disabled(Direction side) {
        return (disabledMask & 1 << side.ordinal()) != 0;
    }

    public void setDisabled(Direction side, boolean disabled) {
        disabledMask = disabled ? disabledMask | 1 << side.ordinal() : disabledMask & ~(1 << side.ordinal());
        setChanged();
    }

    /** A place pipes deliver to: the block, the face it's reached through, and the pipe next to it. */
    record Endpoint(BlockPos pos, Direction side, BlockPos pipe) {}

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
            Object source = level.getCapability(kind.capability, sourcePos, d.getOpposite());
            if (source instanceof ResourceHandler<?> handler) {
                pull(level, kind, handler, sourcePos, pos, targets);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Resource> void pull(ServerLevel level, PipeKind kind, ResourceHandler<?> raw, BlockPos sourcePos, BlockPos start,
            List<Endpoint> targets) {
        ResourceHandler<T> source = (ResourceHandler<T>) raw;
        int budget = kind.batch;
        for (int i = 0; i < source.size() && budget > 0; i++) {
            T resource = source.getResource(i);
            if (resource.isEmpty()) {
                continue;
            }
            for (int t = 0; t < targets.size() && budget > 0; t++) {
                Endpoint e = targets.get((t + rotation) % targets.size());
                if (e.pos().equals(sourcePos)) {
                    continue;
                }
                Object targetRaw = level.getCapability(kind.capability, e.pos(), e.side());
                if (!(targetRaw instanceof ResourceHandler<?> th)) {
                    continue;
                }
                ResourceHandler<T> target = (ResourceHandler<T>) th;
                try (Transaction tx = Transaction.openRoot()) {
                    int accepted;
                    try (Transaction simulate = Transaction.open(tx)) {
                        accepted = target.insert(resource, budget, simulate);
                    }
                    if (accepted <= 0) {
                        continue;
                    }
                    int got = source.extract(i, resource, accepted, tx);
                    if (got > 0 && target.insert(resource, got, tx) == got) {
                        tx.commit();
                        budget -= got;
                        show(level, sourcePos, start, e, resource, got);
                    }
                }
            }
        }
        rotation++;
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
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingChunk(level, net.minecraft.world.level.ChunkPos.containing(start),
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
        while (!queue.isEmpty() && seen.size() < MAX_PIPES) {
            BlockPos p = queue.poll();
            BlockState s = level.getBlockState(p);
            if (s.getBlock() != pipe) {
                continue;
            }
            for (Direction d : Direction.values()) {
                PipeSide side = s.getValue(PipeBlock.SIDES.get(d));
                BlockPos next = p.relative(d);
                if (side == PipeSide.PIPE && level.isLoaded(next) && seen.add(next)) {
                    parents.put(next, p);
                    queue.add(next);
                } else if (side == PipeSide.INSERT) {
                    out.add(new Endpoint(next, d.getOpposite(), p));
                }
            }
        }
        return out;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        disabledMask = input.getIntOr("disabled", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("disabled", disabledMask);
    }
}
