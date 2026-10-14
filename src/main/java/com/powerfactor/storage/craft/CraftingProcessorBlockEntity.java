package com.powerfactor.storage.craft;

import com.powerfactor.registry.ModStorage;
import com.powerfactor.storage.NodeBlockEntity;
import com.powerfactor.storage.StorageNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Runs crafting jobs for the network. The standard processor runs one job and dispatches two
 * executions a tick; the quantum processor superposes four jobs at sixteen dispatches a tick.
 */
public class CraftingProcessorBlockEntity extends NodeBlockEntity {
    public static final int FE_PER_DISPATCH = 16;

    private final boolean quantum;
    private final List<CraftingJob> jobs = new ArrayList<>();
    /** Synced to clients for the core animation: 0 idle, else running jobs. */
    private int busy;

    public CraftingProcessorBlockEntity(boolean quantum, BlockPos pos, BlockState state) {
        super(quantum ? ModStorage.QUANTUM_PROCESSOR_BE.get() : ModStorage.PROCESSOR_BE.get(), pos, state);
        this.quantum = quantum;
    }

    public boolean quantum() {
        return quantum;
    }

    public int maxJobs() {
        return quantum ? 4 : 1;
    }

    public int parallel() {
        return quantum ? 16 : 2;
    }

    public boolean hasRoom() {
        return jobs.size() < maxJobs();
    }

    public List<CraftingJob> jobs() {
        return jobs;
    }

    public void start(CraftingJob job) {
        jobs.add(job);
        setChanged();
    }

    @Override
    public int idleDraw() {
        return quantum ? 16 : 4;
    }

    @Override
    public void networkTick(ServerLevel level, StorageNetwork network) {
        for (CraftingJob job : List.copyOf(jobs)) {
            int budget = parallel();
            // Each dispatch costs FE from the controller; an underpowered network crafts slower.
            while (budget > 0 && !network.controller().spend(FE_PER_DISPATCH * budget)) budget /= 2;
            job.tick(level, network, budget);
            if (job.state() == CraftingJob.State.DONE) {
                jobs.remove(job);
                announce(level, job);
            }
        }
        int nowBusy = jobs.size();
        if (nowBusy != busy) {
            busy = nowBusy;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        if (!jobs.isEmpty()) setChanged();
    }

    private void announce(ServerLevel level, CraftingJob job) {
        var player = level.getServer().getPlayerList().getPlayerByName(job.requester());
        if (player != null) {
            player.sendOverlayMessage(Component.translatable("gui.powerfactor.craft.done", String.format("%,d", job.amount()),
                    job.target().getHoverName()));
        }
    }

    public void cancel(int index, StorageNetwork network) {
        if (index >= 0 && index < jobs.size()) {
            jobs.get(index).cancel(network);
            if (jobs.get(index).state() == CraftingJob.State.DONE) jobs.remove(index);
            setChanged();
        }
    }

    public int busy() {
        return busy;
    }

    @Override
    public void dropContents(ServerLevel level, BlockPos pos) {
        // Broken mid-job: everything the jobs held drops (or returns to a live network).
        StorageNetwork n = onlineNetwork();
        for (CraftingJob job : jobs) {
            for (var e : job.buffer().entrySet()) {
                long left = e.getValue() - (n == null ? 0 : n.insert(e.getKey(), e.getValue(), false));
                while (left > 0) {
                    int c = (int) Math.min(left, e.getKey().getMaxStackSize());
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, e.getKey().toStack(c));
                    left -= c;
                }
            }
        }
        jobs.clear();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        jobs.clear();
        input.read("jobs", CraftingJob.CODEC.listOf()).ifPresent(jobs::addAll);
        busy = input.getIntOr("busy", jobs.size());
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("jobs", CraftingJob.CODEC.listOf(), List.copyOf(jobs));
        output.putInt("busy", jobs.size());
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("busy", busy);
        return tag;
    }
}
