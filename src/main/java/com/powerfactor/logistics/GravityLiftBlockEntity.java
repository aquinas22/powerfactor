package com.powerfactor.logistics;

import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.registry.ModQuantum;
import com.powerfactor.scada.ScadaReading;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import org.jspecify.annotations.Nullable;

/**
 * Gravity Lift: projects a column of field straight up until the first solid block (up to 24 high).
 * Creatures and items in the column float upward; a sneaking player sinks gently instead, and nobody
 * takes fall damage inside it. Costs FE for every entity carried.
 */
public class GravityLiftBlockEntity extends MachineBlockEntity {
    public static final int CAPACITY = 200_000, MAX_INPUT = 2_000, PER_ENTITY = 20, MAX_HEIGHT = 24;
    public static final double RISE = 0.42, SINK = -0.12;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CAPACITY, MAX_INPUT, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private int height, syncedHeight = -1;
    private int clientHeight;
    private boolean clientOn;

    public GravityLiftBlockEntity(BlockPos pos, BlockState state) {
        super(ModQuantum.GRAVITY_LIFT_BE.get(), pos, state);
    }

    public SimpleEnergyHandler getEnergy() {
        return energy;
    }

    /** Clear column height above the pad. */
    static int column(Level level, BlockPos pos) {
        int h = 0;
        while (h < MAX_HEIGHT && !level.getBlockState(pos.above(h + 1)).isCollisionShapeFullBlock(level, pos.above(h + 1))) h++;
        return h;
    }

    AABB field(int h) {
        return new AABB(worldPosition.getX() + 0.1, worldPosition.getY() + 0.25, worldPosition.getZ() + 0.1,
                worldPosition.getX() + 0.9, worldPosition.getY() + 1 + h, worldPosition.getZ() + 0.9);
    }

    /** The lift's push on one entity; the same rule runs on the server (everyone but players) and client (the local player). */
    static void push(Entity e) {
        Vec3 v = e.getDeltaMovement();
        boolean sink = e instanceof Player p && p.isShiftKeyDown();
        double vy = sink ? Math.max(v.y * 0.8, SINK) : v.y + (RISE - v.y) * 0.35;
        // Gently centre the rider in the column.
        e.setDeltaMovement(v.x * 0.85, vy, v.z * 0.85);
        e.resetFallDistance();
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        height = level.getGameTime() % 10 == 0 || syncedHeight < 0 ? column(level, pos) : height;
        boolean on = enabled(level, pos) && energy.getAmountAsInt() >= PER_ENTITY && height > 0;
        if (on) {
            List<Entity> in = level.getEntities((Entity) null, field(height), e -> e instanceof LivingEntity || e instanceof ItemEntity);
            for (Entity e : in) {
                if (energy.getAmountAsInt() < PER_ENTITY) break;
                if (e instanceof Player p && p.getAbilities().flying) continue;
                energy.set(energy.getAmountAsInt() - PER_ENTITY);
                if (e instanceof Player) {
                    e.resetFallDistance();     // the player's own client moves them (see clientTick)
                } else {
                    push(e);
                    e.hurtMarked = true;
                }
            }
        }
        setRunning(level, pos, state, on);
        int sync = on ? height : 0;
        if (sync != syncedHeight) {
            syncedHeight = sync;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    /** Set by the client: pushes the local player when they stand in a lift's column (players move themselves). */
    public static java.util.function.Consumer<GravityLiftBlockEntity> localPlayerHook = be -> {};

    @Override
    public void clientTick() {
        super.clientTick();
        if (clientOn && level != null) localPlayerHook.accept(this);
    }

    /** Applies the lift to {@code player} if they are inside this lift's active column. */
    public void carry(Player player) {
        if (!player.getAbilities().flying && player.getBoundingBox().intersects(field(clientHeight))) push(player);
    }

    public int clientHeight() {
        return clientHeight;
    }

    public boolean clientOn() {
        return clientOn;
    }

    @Override
    public boolean hasMenu() {
        return false;
    }

    @Override
    public Component describe() {
        return Component.translatable("gui.powerfactor.gravity_lift.status", height, String.format("%,d", energy.getAmountAsInt()));
    }

    @Override
    public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), isRunning() ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF, 0, 0,
                describe(), true, remoteEnabled(), false, 0, 0);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return null;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("height", Math.max(0, syncedHeight));
        return tag;
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        clientHeight = input.getIntOr("height", 0);
        clientOn = clientHeight > 0;
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ValueInput input) {
        handleUpdateTag(input);
    }
}
