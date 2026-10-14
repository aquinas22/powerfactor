package com.powerfactor.quantum;

import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.registry.ModQuantum;
import com.powerfactor.scada.ScadaReading;
import com.powerfactor.weapon.FxPayload;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import org.jspecify.annotations.Nullable;

/**
 * Quantum Teleporter pad. Two pads entangled with an Entangled Qubit Pair form a link; a player who
 * stands still on a linked, powered pad for 1.5 s is sent to the partner (across dimensions, too).
 * Arrivals must step off before the pad will send them back, so links never ping-pong.
 */
public class TeleporterBlockEntity extends MachineBlockEntity {
    public static final int CAPACITY = 2_000_000, MAX_INPUT = 40_000, CHARGE_TICKS = 30;
    public static final int BASE_COST = 5_000, PER_BLOCK = 20, CROSS_DIMENSION = 500_000;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CAPACITY, MAX_INPUT, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private @Nullable GlobalPos partner;
    private final Map<UUID, Integer> charging = new HashMap<>();
    /** Players who just arrived; they must leave the pad before it will send them anywhere. */
    private final Set<UUID> arrived = new HashSet<>();
    private int ticks, lastSyncedCharge = -1;
    private float charge;
    // Client view.
    private float clientCharge;
    private boolean clientLinked;
    private long clientFlash = Long.MIN_VALUE / 2;
    private int flashes, clientFlashes;

    public TeleporterBlockEntity(BlockPos pos, BlockState state) {
        super(ModQuantum.TELEPORTER_BE.get(), pos, state);
    }

    public SimpleEnergyHandler getEnergy() {
        return energy;
    }

    public @Nullable GlobalPos partner() {
        return partner;
    }

    public void link(@Nullable GlobalPos other) {
        partner = other;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** FE to send one player to the partner. */
    public long cost() {
        if (partner == null || level == null) return 0;
        if (!partner.dimension().equals(level.dimension())) return CROSS_DIMENSION;
        return BASE_COST + (long) (PER_BLOCK * Math.sqrt(partner.pos().distSqr(worldPosition)));
    }

    private AABB pad() {
        return new AABB(worldPosition.getX(), worldPosition.getY() + 0.2, worldPosition.getZ(), worldPosition.getX() + 1, worldPosition.getY() + 2,
                worldPosition.getZ() + 1);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        ticks++;
        List<ServerPlayer> on = level.getEntitiesOfClass(ServerPlayer.class, pad(), p -> p.isAlive() && !p.isSpectator());
        Set<UUID> present = new HashSet<>();
        for (ServerPlayer p : on) present.add(p.getUUID());
        arrived.retainAll(present);
        charging.keySet().retainAll(present);
        boolean ready = partner != null && enabled(level, pos) && energy.getAmountAsInt() >= cost();
        float best = 0;
        for (ServerPlayer p : on) {
            if (!ready || arrived.contains(p.getUUID())) continue;
            // Standing still: horizontal speed near zero.
            boolean still = p.getDeltaMovement().horizontalDistanceSqr() < 0.003;
            int c = still ? charging.getOrDefault(p.getUUID(), 0) + 1 : Math.max(0, charging.getOrDefault(p.getUUID(), 0) - 2);
            charging.put(p.getUUID(), c);
            best = Math.max(best, (float) c / CHARGE_TICKS);
            if (c == 1) level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.7F, 1.6F);
            if (c % 6 == 0) {
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 6, 0.3, 0.6, 0.3, 0.02);
            }
            if (c >= CHARGE_TICKS) {
                charging.remove(p.getUUID());
                send(level, p);
            }
        }
        charge = best;
        setRunning(level, pos, state, partner != null && energy.getAmountAsInt() >= cost());
        int sync = Math.round(charge * 20);
        if (sync != lastSyncedCharge || ticks % 40 == 0) {
            lastSyncedCharge = sync;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private void send(ServerLevel level, ServerPlayer player) {
        if (partner == null) return;
        ServerLevel dest = level.getServer().getLevel(partner.dimension());
        if (dest == null) return;
        // Load the far pad's chunk and confirm it is still there and still linked back to us.
        dest.getChunk(partner.pos());
        if (!(dest.getBlockEntity(partner.pos()) instanceof TeleporterBlockEntity far) || far.partner == null
                || !far.partner.equals(GlobalPos.of(level.dimension(), worldPosition))) {
            player.sendOverlayMessage(Component.translatable("gui.powerfactor.teleporter.broken").withStyle(ChatFormatting.RED));
            link(null);
            return;
        }
        long cost = cost();
        if (energy.getAmountAsInt() < cost) return;
        energy.set((int) (energy.getAmountAsInt() - cost));
        Vec3 from = player.position();
        BlockPos to = partner.pos();
        far.arrived.add(player.getUUID());
        flashes++;
        far.flashes++;
        FxPayload.send(level, new FxPayload(FxPayload.SHOCKWAVE, Vec3.atBottomCenterOf(worldPosition).add(0, 0.3, 0), from, 0xFFB18CFF, 1.2F, -1));
        level.playSound(null, worldPosition, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1F, 0.8F);
        player.teleportTo(dest, to.getX() + 0.5, to.getY() + 0.25, to.getZ() + 0.5, Set.<Relative>of(), player.getYRot(), player.getXRot(), false);
        dest.playSound(null, to, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1F, 1.2F);
        dest.sendParticles(ParticleTypes.END_ROD, to.getX() + 0.5, to.getY() + 1, to.getZ() + 0.5, 40, 0.3, 0.8, 0.3, 0.08);
        FxPayload.send(dest, new FxPayload(FxPayload.SHOCKWAVE, Vec3.atBottomCenterOf(to).add(0, 0.3, 0), Vec3.atBottomCenterOf(to), 0xFF6FE3FF, 1.2F, -1));
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        dest.sendBlockUpdated(to, far.getBlockState(), far.getBlockState(), Block.UPDATE_CLIENTS);
        far.setChanged();
    }

    // ------------------------------------------------------------------ client view

    public float clientCharge() {
        return clientCharge;
    }

    public boolean clientLinked() {
        return clientLinked;
    }

    public long clientFlash() {
        return clientFlash;
    }

    @Override
    public boolean hasMenu() {
        return false;
    }

    @Override
    public Component describe() {
        if (partner == null) return Component.translatable("gui.powerfactor.teleporter.unlinked").withStyle(ChatFormatting.GOLD);
        String where = partner.dimension().equals(level == null ? null : level.dimension())
                ? partner.pos().toShortString() : partner.pos().toShortString() + " · " + partner.dimension().identifier().getPath();
        return Component.translatable("gui.powerfactor.teleporter.status", where, String.format("%,d", cost()),
                String.format("%,d", energy.getAmountAsInt())).withStyle(energy.getAmountAsInt() >= cost() ? ChatFormatting.AQUA : ChatFormatting.RED);
    }

    @Override
    public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), partner != null ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF, 0, 0,
                describe(), true, remoteEnabled(), true, 1F, 60F);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return null;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        partner = input.getString("partner_dim").flatMap(d -> input.getLong("partner_pos").map(p -> GlobalPos.of(
                ResourceKey.create(Registries.DIMENSION, Identifier.parse(d)), BlockPos.of(p)))).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        if (partner != null) {
            output.putString("partner_dim", partner.dimension().identifier().toString());
            output.putLong("partner_pos", partner.pos().asLong());
        }
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putFloat("charge", charge);
        tag.putBoolean("linked", partner != null);
        tag.putInt("flashes", flashes);
        return tag;
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        clientCharge = input.getFloatOr("charge", 0);
        clientLinked = input.getBooleanOr("linked", false);
        int f = input.getIntOr("flashes", 0);
        if (f != clientFlashes && level != null) clientFlash = level.getGameTime();
        clientFlashes = f;
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ValueInput input) {
        handleUpdateTag(input);
    }
}
