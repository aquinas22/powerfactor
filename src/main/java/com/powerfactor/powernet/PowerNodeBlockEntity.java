package com.powerfactor.powernet;

import com.powerfactor.registry.ModPowerNet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * A device on a wireless power network: a Plug (takes power in from what touches it), a Point (gives power to
 * what touches it), a Cell (stores it) or the Controller (runs the network and charges its members wirelessly).
 * Each has a name, a priority, a transfer limit (or none), surge (always served first) and can keep its own
 * chunk loaded.
 */
public class PowerNodeBlockEntity extends BlockEntity implements MenuProvider {
    public enum Kind {
        PLUG(800_000, 0), POINT(800_000, 0), CELL(200_000, 4_000_000L), DENSE_CELL(2_000_000, 64_000_000L),
        QUANTUM_CELL(20_000_000, 1_000_000_000L), CONTROLLER(0, 0);

        public final int defaultLimit;
        public final long capacity;

        Kind(int defaultLimit, long capacity) {
            this.defaultLimit = defaultLimit;
            this.capacity = capacity;
        }

        public boolean cell() {
            return capacity > 0;
        }
    }

    private final Kind kind;
    int network = -1;
    String customName = "";
    int priority;
    int limit;
    boolean surge, unlimited, chunkLoad;
    long energy;
    /** Plugs: power received this tick, waiting for the network. */
    int buffered;
    /** Last tick's flow through this device (FE/t), for the screen and the glow. */
    int flow;
    boolean forced;
    // Client copy of the network's colour and whether it's running, for the renderer.
    int clientColor = 0xFF5A6A78;
    boolean clientActive;

    public PowerNodeBlockEntity(BlockPos pos, BlockState state) {
        super(ModPowerNet.NODE_BE.get(), pos, state);
        this.kind = state.getBlock() instanceof PowerNodeBlock b ? b.kind() : Kind.PLUG;
        this.limit = kind.defaultLimit;
    }

    public Kind kind() {
        return kind;
    }

    public int network() {
        return network;
    }

    public int effectiveLimit() {
        return unlimited ? Integer.MAX_VALUE : limit;
    }

    public long energy() {
        return energy;
    }

    public int flow() {
        return flow;
    }

    public int clientColor() {
        return clientColor;
    }

    public boolean clientActive() {
        return clientActive;
    }

    public String customName() {
        return customName;
    }

    public int priority() {
        return priority;
    }

    public int limit() {
        return limit;
    }

    public boolean surge() {
        return surge;
    }

    public boolean unlimited() {
        return unlimited;
    }

    public boolean chunkLoad() {
        return chunkLoad;
    }

    public Component displayName() {
        return customName.isEmpty() ? getBlockState().getBlock().getName() : Component.literal(customName);
    }

    private final net.neoforged.neoforge.transfer.transaction.SnapshotJournal<Integer> journal = new net.neoforged.neoforge.transfer.transaction.SnapshotJournal<>() {
        @Override
        protected Integer createSnapshot() {
            return buffered;
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            buffered = snapshot;
        }
    };

    /** The Plug's face to the world: anything may push power in, up to the limit each tick, while the network runs. */
    public final EnergyHandler plugHandler = new EnergyHandler() {
        @Override
        public long getAmountAsLong() {
            return buffered;
        }

        @Override
        public long getCapacityAsLong() {
            return effectiveLimit();
        }

        @Override
        public int insert(int amount, TransactionContext tx) {
            if (kind != Kind.PLUG || !(level instanceof ServerLevel s) || !PowerNets.running(s.getServer(), network)) return 0;
            int take = Math.max(0, Math.min(amount, effectiveLimit() - buffered));
            if (take > 0) {
                journal.updateSnapshots(tx);
                buffered += take;
            }
            return take;
        }

        @Override
        public int extract(int amount, TransactionContext tx) {
            return 0;
        }
    };

    GlobalPos global() {
        return GlobalPos.of(level.dimension(), worldPosition);
    }

    /** Joins a network (or none with -1). */
    public void setNetwork(int id) {
        if (level instanceof ServerLevel s) {
            PowerNets.leave(s.getServer(), network, global());
            network = id;
            PowerNets.join(s.getServer(), network, global());
            syncClient();
        }
        setChanged();
    }

    public void applySettings(String name, int priority, int limit, boolean surge, boolean unlimited, boolean chunkLoad) {
        this.customName = name.length() > 32 ? name.substring(0, 32) : name;
        this.priority = Math.clamp(priority, -10_000, 10_000);
        this.limit = Math.max(0, limit);
        this.surge = surge;
        this.unlimited = unlimited;
        this.chunkLoad = chunkLoad;
        setChanged();
        if (level instanceof ServerLevel s) PowerNets.updateChunk(s, this);
    }

    public void syncClient() {
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel s) {
            PowerNets.join(s.getServer(), network, global());
            PowerNets.updateChunk(s, this);
        }
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel s) PowerNets.leave(s.getServer(), network, global());
        super.setRemoved();
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel s && forced) {
            chunkLoad = false;
            PowerNets.updateChunk(s, this);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        network = input.getIntOr("network", -1);
        customName = input.getStringOr("name", "");
        priority = input.getIntOr("priority", 0);
        limit = input.getIntOr("limit", kind.defaultLimit);
        surge = input.getBooleanOr("surge", false);
        unlimited = input.getBooleanOr("unlimited", false);
        chunkLoad = input.getBooleanOr("chunk_load", false);
        energy = input.getLongOr("energy", 0L);
        clientColor = input.getIntOr("client_color", clientColor);
        clientActive = input.getBooleanOr("client_active", false);
        flow = input.getIntOr("flow", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("network", network);
        output.putString("name", customName);
        output.putInt("priority", priority);
        output.putInt("limit", limit);
        output.putBoolean("surge", surge);
        output.putBoolean("unlimited", unlimited);
        output.putBoolean("chunk_load", chunkLoad);
        output.putLong("energy", energy);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        PowerNet net = level instanceof ServerLevel s ? PowerNets.get(s.getServer()).net(network) : null;
        tag.putInt("client_color", net == null ? 0xFF5A6A78 : 0xFF000000 | net.color);
        tag.putBoolean("client_active", net != null && PowerNets.running(((ServerLevel) level).getServer(), network));
        tag.putInt("flow", flow);
        tag.putLong("energy", energy);
        tag.putInt("network", network);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return displayName();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new PowerNetMenu(id, inventory, worldPosition);
    }
}
