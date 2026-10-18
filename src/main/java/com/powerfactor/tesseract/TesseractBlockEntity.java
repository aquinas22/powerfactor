package com.powerfactor.tesseract;

import com.powerfactor.registry.ModTesseract;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
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
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * Tesseract: a fold in space shared by every tesseract on the same frequency, in any dimension. Each
 * channel (items, fluids, energy) is Off, Send or Receive. Whatever is pushed into a sending tesseract
 * is handed, in the same transaction, to the blocks touching the receiving ones, so if nothing can take
 * it, nothing moves. A tesseract never feeds another tesseract, so frequencies can't loop.
 */
public class TesseractBlockEntity extends BlockEntity implements MenuProvider {
    public enum Channel { ITEMS, FLUIDS, ENERGY }

    public static final int OFF = 0, SEND = 1, RECEIVE = 2, MAX_FREQUENCY = 9999;

    private final int[] modes = new int[3];
    private int frequency;
    private @Nullable UUID owner;
    private boolean shared;
    private int roundRobin;
    private boolean joined;
    private TesseractNetwork.Key joinedKey;
    /** Client view: modes and whether anything is linked, for the hypercube's colours. */
    private int clientModes;
    private boolean clientLinked;

    private final ResourceHandler<ItemResource> items = new Proxy<>(Channel.ITEMS, Capabilities.Item.BLOCK);
    private final ResourceHandler<FluidResource> fluids = new Proxy<>(Channel.FLUIDS, Capabilities.Fluid.BLOCK);
    private final EnergyHandler energy = new EnergyProxy();

    public TesseractBlockEntity(BlockPos pos, BlockState state) {
        super(ModTesseract.TESSERACT_BE.get(), pos, state);
    }

    public ResourceHandler<ItemResource> items() {
        return items;
    }

    public ResourceHandler<FluidResource> fluids() {
        return fluids;
    }

    public EnergyHandler energy() {
        return energy;
    }

    public int mode(Channel c) {
        return modes[c.ordinal()];
    }

    public void cycle(Channel c) {
        modes[c.ordinal()] = (modes[c.ordinal()] + 1) % 3;
        changed();
    }

    public void setMode(Channel c, int mode) {
        modes[c.ordinal()] = mode;
        changed();
    }

    public int frequency() {
        return frequency;
    }

    public void setFrequency(int f) {
        rejoin(() -> frequency = Math.clamp(f, 0, MAX_FREQUENCY));
    }

    public boolean shared() {
        return shared;
    }

    public void toggleShared() {
        rejoin(() -> shared = !shared);
    }

    public void setOwner(@Nullable UUID owner) {
        rejoin(() -> this.owner = owner);
    }

    public TesseractNetwork.Key key() {
        return new TesseractNetwork.Key(shared ? null : owner, frequency);
    }

    public int linked() {
        return level instanceof ServerLevel s ? Math.max(0, TesseractNetwork.count(s.getServer(), key()) - 1) : 0;
    }

    private void rejoin(Runnable change) {
        if (level instanceof ServerLevel s && joined) TesseractNetwork.leave(s.getServer(), joinedKey, here());
        change.run();
        joined = false;
        changed();
    }

    private GlobalPos here() {
        return GlobalPos.of(level.dimension(), worldPosition);
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    public void serverTick(ServerLevel level) {
        if (!joined) {
            joinedKey = key();
            TesseractNetwork.join(level.getServer(), joinedKey, here());
            joined = true;
        }
        if (level.getGameTime() % 20 == 0) {
            boolean linked = linked() > 0;
            if (linked != clientLinkedSent) {
                clientLinkedSent = linked;
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    private boolean clientLinkedSent;

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel s && joined) TesseractNetwork.leave(s.getServer(), joinedKey, here());
        joined = false;
    }

    /** Receivers on this frequency for a channel, rotated so successive pushes spread across them. */
    private List<TesseractBlockEntity> receivers(Channel c) {
        if (!(level instanceof ServerLevel s) || mode(c) != SEND) return List.of();
        List<TesseractBlockEntity> out = TesseractNetwork.peers(s.getServer(), key(), here()).stream().filter(t -> t.mode(c) == RECEIVE).toList();
        if (out.size() < 2) return out;
        int k = Math.floorMod(roundRobin++, out.size());
        return java.util.stream.Stream.concat(out.subList(k, out.size()).stream(), out.subList(0, k).stream()).toList();
    }

    /** The handlers touching a receiving tesseract (never another tesseract). */
    private <H> List<H> outlets(BlockCapability<H, @Nullable Direction> cap) {
        List<H> out = new java.util.ArrayList<>();
        for (Direction d : Direction.values()) {
            BlockPos p = worldPosition.relative(d);
            if (level.getBlockEntity(p) instanceof TesseractBlockEntity) continue;
            H h = level.getCapability(cap, p, d.getOpposite());
            if (h != null) out.add(h);
        }
        return out;
    }

    private final class Proxy<R extends net.neoforged.neoforge.transfer.resource.Resource> implements ResourceHandler<R> {
        private final Channel channel;
        private final BlockCapability<ResourceHandler<R>, @Nullable Direction> cap;

        Proxy(Channel channel, BlockCapability<ResourceHandler<R>, @Nullable Direction> cap) {
            this.channel = channel;
            this.cap = cap;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        @SuppressWarnings("unchecked")
        public R getResource(int index) {
            return (R) (channel == Channel.ITEMS ? ItemResource.EMPTY : FluidResource.EMPTY);
        }

        @Override
        public long getAmountAsLong(int index) {
            return 0;
        }

        @Override
        public long getCapacityAsLong(int index, R resource) {
            return mode(channel) == SEND ? Integer.MAX_VALUE : 0;
        }

        @Override
        public boolean isValid(int index, R resource) {
            return mode(channel) == SEND;
        }

        @Override
        public int insert(int index, R resource, int amount, TransactionContext tx) {
            if (resource.isEmpty() || amount <= 0) return 0;
            int moved = 0;
            for (TesseractBlockEntity t : receivers(channel)) {
                for (ResourceHandler<R> h : t.outlets(cap)) {
                    moved += h.insert(resource, amount - moved, tx);
                    if (moved >= amount) return moved;
                }
            }
            return moved;
        }

        @Override
        public int extract(int index, R resource, int amount, TransactionContext tx) {
            return 0;
        }
    }

    private final class EnergyProxy implements EnergyHandler {
        @Override
        public long getAmountAsLong() {
            return 0;
        }

        @Override
        public long getCapacityAsLong() {
            return mode(Channel.ENERGY) == SEND ? Integer.MAX_VALUE : 0;
        }

        @Override
        public int insert(int amount, TransactionContext tx) {
            if (amount <= 0) return 0;
            int moved = 0;
            for (TesseractBlockEntity t : receivers(Channel.ENERGY)) {
                for (EnergyHandler h : t.outlets(Capabilities.Energy.BLOCK)) {
                    moved += h.insert(amount - moved, tx);
                    if (moved >= amount) return moved;
                }
            }
            return moved;
        }

        @Override
        public int extract(int amount, TransactionContext tx) {
            return 0;
        }
    }

    public int clientModes() {
        return clientModes;
    }

    public boolean clientLinked() {
        return clientLinked;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.powerfactor.tesseract");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new TesseractMenu(id, inventory, this);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.getIntArray("modes").ifPresent(a -> {
            for (int i = 0; i < Math.min(3, a.length); i++) modes[i] = Math.clamp(a[i], OFF, RECEIVE);
        });
        frequency = Math.clamp(input.getIntOr("frequency", 0), 0, MAX_FREQUENCY);
        shared = input.getBooleanOr("shared", false);
        owner = input.getString("owner").map(UUID::fromString).orElse(null);
        clientModes = input.getIntOr("client_modes", 0);
        clientLinked = input.getBooleanOr("linked", false);
        joined = false;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putIntArray("modes", modes.clone());
        output.putInt("frequency", frequency);
        output.putBoolean("shared", shared);
        if (owner != null) output.putString("owner", owner.toString());
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("client_modes", modes[0] | modes[1] << 2 | modes[2] << 4);
        tag.putBoolean("linked", clientLinkedSent);
        return tag;
    }
}
