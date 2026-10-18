package com.powerfactor.charging;

import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.registry.ModCharging;
import com.powerfactor.registry.ModUtility;
import com.powerfactor.scada.ScadaReading;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.PlayerInventoryWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Wireless Charger: charges the FE gear (armour, tools, anything with an energy store) of every player in
 * range, 16 blocks plus 8 per Range Upgrade. The Dimensional variant instead charges the players bound to
 * it (right-click to bind or unbind) wherever they are, in any dimension, losing a quarter in transit.
 */
public class WirelessChargerBlockEntity extends MachineBlockEntity {
    private final boolean dimensional;
    private final int capacity, rate, perItem;
    private final SimpleEnergyHandler energy;
    private final Set<UUID> bound = new LinkedHashSet<>();
    private int cards, delivered, cursor, synced = -1;
    /** Entity ids charged this second, for the arcs the renderer draws. */
    private final List<Integer> charged = new ArrayList<>();
    private List<Integer> clientCharged = List.of();
    private boolean clientActive;

    public WirelessChargerBlockEntity(BlockPos pos, BlockState state) {
        this(pos, state, state.is(ModCharging.DIMENSIONAL_CHARGER.get()));
    }

    private WirelessChargerBlockEntity(BlockPos pos, BlockState state, boolean dimensional) {
        super(dimensional ? ModCharging.DIMENSIONAL_CHARGER_BE.get() : ModCharging.WIRELESS_CHARGER_BE.get(), pos, state);
        this.dimensional = dimensional;
        this.capacity = dimensional ? 50_000_000 : 5_000_000;
        this.rate = dimensional ? 40_000 : 8_000;
        this.perItem = dimensional ? 10_000 : 2_000;
        this.energy = new SimpleEnergyHandler(capacity, rate * 2, 0) {
            @Override
            protected void onEnergyChanged(int previousAmount) {
                setChanged();
            }
        };
    }

    public boolean dimensional() {
        return dimensional;
    }

    public SimpleEnergyHandler getEnergy() {
        return energy;
    }

    public int range() {
        return 16 + 8 * cards;
    }

    public int cards() {
        return cards;
    }

    public boolean addCard() {
        if (dimensional || cards >= 3) return false;
        cards++;
        setChanged();
        return true;
    }

    /** Dimensional charger: bind or unbind a player. Returns true if now bound. */
    public boolean toggleBound(UUID player) {
        boolean now = !bound.remove(player);
        if (now) bound.add(player);
        setChanged();
        return now;
    }

    public int boundCount() {
        return bound.size();
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getGameTime() % 20 == 0) charged.clear();
        delivered = 0;
        if (!enabled(level, pos) || energy.getAmountAsInt() <= 0) {
            setRunning(level, pos, state, false);
            return;
        }
        List<ServerPlayer> players = new ArrayList<>();
        if (dimensional) {
            for (UUID id : bound) {
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
                if (p != null && !p.isSpectator()) players.add(p);
            }
        } else {
            double r = range();
            for (ServerPlayer p : level.players()) if (!p.isSpectator() && p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) <= r * r) players.add(p);
        }
        record Target(ServerPlayer player, EnergyHandler handler) {}
        List<Target> targets = new ArrayList<>();
        for (ServerPlayer p : players) {
            ResourceHandler<ItemResource> inv = PlayerInventoryWrapper.of(p);
            for (int i = 0; i < inv.size(); i++) {
                EnergyHandler h = ItemAccess.forHandlerIndex(inv, i).getCapability(Capabilities.Energy.ITEM);
                if (h != null && h.getAmountAsLong() < h.getCapacityAsLong()) targets.add(new Target(p, h));
            }
        }
        // A quarter is lost crossing between worlds: the dimensional charger spends 4 FE for every 3 it delivers.
        double loss = dimensional ? 4.0 / 3.0 : 1.0;
        int budget = (int) Math.min(rate, energy.getAmountAsInt() / loss);
        for (int k = 0; k < targets.size() && budget > 0; k++) {
            Target t = targets.get((k + cursor) % targets.size());
            int want = Math.min(perItem, budget);
            try (Transaction tx = Transaction.openRoot()) {
                int got = t.handler().insert(want, tx);
                if (got > 0) {
                    tx.commit();
                    budget -= got;
                    delivered += got;
                    if (!charged.contains(t.player().getId())) charged.add(t.player().getId());
                }
            }
        }
        if (!targets.isEmpty()) cursor = (cursor + 1) % targets.size();
        energy.set(energy.getAmountAsInt() - (int) Math.ceil(delivered * loss));
        setRunning(level, pos, state, delivered > 0);
        int sync = charged.hashCode() * 31 + (delivered > 0 ? 1 : 0);
        if (sync != synced && level.getGameTime() % 5 == 0) {
            synced = sync;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public List<Integer> clientCharged() {
        return clientCharged;
    }

    public boolean clientActive() {
        return clientActive;
    }

    @Override
    public boolean hasMenu() {
        return false;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return null;
    }

    @Override
    public Component describe() {
        return dimensional ? Component.translatable("gui.powerfactor.charger.dimensional.status", bound.size(), String.format("%,d", energy.getAmountAsInt()))
                : Component.translatable("gui.powerfactor.charger.wireless.status", range(), String.format("%,d", energy.getAmountAsInt()));
    }

    @Override
    public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), isRunning() ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF, delivered, 0,
                describe(), true, remoteEnabled(), false, 0, 0);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel s && cards > 0) {
            net.minecraft.world.Containers.dropItemStack(s, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5,
                    new net.minecraft.world.item.ItemStack(ModUtility.RANGE_UPGRADE.get(), cards));
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        cards = Math.clamp(input.getIntOr("cards", 0), 0, 3);
        bound.clear();
        input.getString("bound").ifPresent(sv -> {
            for (String part : sv.split(",")) if (!part.isBlank()) bound.add(UUID.fromString(part));
        });
        clientActive = input.getBooleanOr("active", false);
        clientCharged = input.getIntArray("charged").map(a -> java.util.Arrays.stream(a).boxed().toList()).orElse(List.of());
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        output.putInt("cards", cards);
        output.putString("bound", String.join(",", bound.stream().map(UUID::toString).toList()));
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putBoolean("active", delivered > 0 || !charged.isEmpty());
        tag.putIntArray("charged", charged.stream().mapToInt(Integer::intValue).toArray());
        return tag;
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        clientActive = input.getBooleanOr("active", false);
        clientCharged = input.getIntArray("charged").map(a -> java.util.Arrays.stream(a).boxed().toList()).orElse(List.of());
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ValueInput input) {
        handleUpdateTag(input);
    }
}
