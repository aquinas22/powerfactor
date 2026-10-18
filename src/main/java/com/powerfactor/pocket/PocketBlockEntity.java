package com.powerfactor.pocket;

import com.powerfactor.registry.ModPocket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jspecify.annotations.Nullable;

/**
 * The door to a pocket room. It remembers its room (the room travels with the block when it's broken),
 * keeps the room loaded while it is, and passes capabilities through to whatever the room's Pocket Port
 * faces inside.
 */
public class PocketBlockEntity extends BlockEntity {
    private int room, tier = 1;
    private boolean forced;

    public PocketBlockEntity(BlockPos pos, BlockState state) {
        super(ModPocket.POCKET_BE.get(), pos, state);
    }

    public int room() {
        return room;
    }

    public int tier() {
        return tier;
    }

    /** The room behind this door, made (and built) on first use. */
    public PocketData.Room ensureRoom(ServerLevel level) {
        PocketData data = PocketData.get(level.getServer());
        PocketData.Room r = room > 0 ? data.room(room).orElse(null) : null;
        if (r == null) {
            r = data.create(tier);
            room = r.id;
            ServerLevel pocket = PocketRooms.level(level.getServer());
            if (pocket != null) PocketRooms.build(pocket, r.id, r.tier, 0);
            setChanged();
            sync();
        }
        if (!level.dimension().identifier().toString().equals(r.doorDim) || r.doorPos != worldPosition.asLong()) {
            r.doorDim = level.dimension().identifier().toString();
            r.doorPos = worldPosition.asLong();
            data.changed();
        }
        if (!forced) force(level, true);
        return r;
    }

    /** One size bigger; false at the largest size. */
    public boolean grow(ServerLevel level) {
        PocketData.Room r = ensureRoom(level);
        if (r.tier >= PocketRooms.MAX_TIER) return false;
        ServerLevel pocket = PocketRooms.level(level.getServer());
        if (pocket == null) return false;
        force(level, false);
        PocketRooms.build(pocket, r.id, r.tier + 1, r.tier);
        r.tier++;
        tier = r.tier;
        PocketData.get(level.getServer()).changed();
        force(level, true);
        setChanged();
        sync();
        return true;
    }

    private void force(ServerLevel level, boolean add) {
        ServerLevel pocket = PocketRooms.level(level.getServer());
        if (pocket == null || room == 0) return;
        PocketRooms.forceRoom(pocket, room, tier, add);
        forced = add;
    }

    public void serverTick(ServerLevel level) {
        if (room > 0 && !forced) ensureRoom(level);
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel s && forced) force(s, false);
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if (level instanceof ServerLevel s && forced) force(s, false);
        super.onChunkUnloaded();
    }

    /** The capability of whatever the room's port faces, seen from the port's side. */
    public <T> @Nullable T passThrough(BlockCapability<T, @Nullable Direction> cap) {
        if (!(level instanceof ServerLevel s) || room == 0) return null;
        PocketData.Room r = PocketData.get(s.getServer()).room(room).orElse(null);
        ServerLevel pocket = PocketRooms.level(s.getServer());
        if (r == null || pocket == null || r.port().isEmpty()) return null;
        BlockPos port = r.port().get();
        if (!pocket.isLoaded(port) || !pocket.getBlockState(port).is(ModPocket.POCKET_PORT.get())) return null;
        Direction f = r.portFacing();
        return pocket.getCapability(cap, port.relative(f), f.getOpposite());
    }

    private void sync() {
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        room = input.getIntOr("room", 0);
        tier = Math.clamp(input.getIntOr("tier", 1), 1, PocketRooms.MAX_TIER);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("room", room);
        output.putInt("tier", tier);
    }

    // The room rides on the dropped item as custom data, and comes back when it's placed.
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        CompoundTag t = new CompoundTag();
        t.putInt("pocket_room", room);
        t.putInt("pocket_tier", tier);
        components.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        CompoundTag t = components.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        room = t.getIntOr("pocket_room", 0);
        tier = Math.clamp(t.getIntOr("pocket_tier", 1), 1, PocketRooms.MAX_TIER);
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("room", room);
        tag.putInt("tier", tier);
        return tag;
    }
}
