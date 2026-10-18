package com.powerfactor.mob;

import com.powerfactor.machine.MachineBlock;
import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.registry.ModMob;
import com.powerfactor.scada.ScadaReading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import org.jspecify.annotations.Nullable;

/**
 * Spawner Cage: reproduces the mob imprinted on the Soul Snare in its slot, in front of it, every ten
 * seconds. Each spawn costs 2,000 FE plus 400 per point of the mob's health. It stops while eight of
 * that mob crowd the space in front. Speed and Efficiency cards apply.
 */
public class SpawnerCageBlockEntity extends MachineBlockEntity implements com.powerfactor.upgrade.Upgradeable {
    public static final int CAPACITY = 2_000_000, MAX_INPUT = 20_000, INTERVAL = 200, CROWD = 8;
    public static final int DATA_PROGRESS = 0, DATA_ENERGY = 1, DATA_SPAWNED = 2, DATA_STATE = 3, DATA_COST = 4, DATA_COUNT = 5;
    public static final int STATE_IDLE = 0, STATE_RUNNING = 1, STATE_NO_POWER = 2, STATE_CROWDED = 3;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CAPACITY, MAX_INPUT, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private final SimpleContainer slots = new SimpleContainer(1) {
        @Override
        public boolean canPlaceItem(int index, ItemStack stack) {
            return stack.getItem() instanceof SoulSnareItem && SoulSnareItem.soul(stack) != null;
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(SpawnerCageBlockEntity.this, player);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            SpawnerCageBlockEntity.this.setChanged();
            if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    };
    private final com.powerfactor.upgrade.UpgradeInventory upgrades = new com.powerfactor.upgrade.UpgradeInventory(
            com.powerfactor.upgrade.UpgradeInventory.CONSOLE, this::setChanged, p -> Container.stillValidBlockEntity(this, p));
    private float work;
    private int spawned, state, cost;
    private String clientSoul = "";
    private float clientProgress;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case DATA_PROGRESS -> (int) (1000 * work / INTERVAL);
                case DATA_ENERGY -> energy.getAmountAsInt() / 100;
                case DATA_SPAWNED -> spawned & 0x7FFF;
                case DATA_STATE -> state;
                case DATA_COST -> cost;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public SpawnerCageBlockEntity(BlockPos pos, BlockState state) {
        super(ModMob.SPAWNER_CAGE_BE.get(), pos, state);
    }

    public SimpleEnergyHandler getEnergy() {
        return energy;
    }

    private final net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> items =
            net.neoforged.neoforge.transfer.item.VanillaContainerWrapper.of(slots);

    public net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> getItems() {
        return items;
    }

    public SimpleContainer slots() {
        return slots;
    }

    public com.powerfactor.upgrade.UpgradeInventory upgrades() {
        return upgrades;
    }

    @Override
    protected Container inventory() {
        return slots;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && !level.isClientSide()) net.minecraft.world.Containers.dropContents(level, pos, upgrades);
    }

    public @Nullable EntityType<?> soul() {
        return SoulSnareItem.soul(slots.getItem(0));
    }

    private BlockPos spawnAt() {
        return worldPosition.relative(getBlockState().getValue(MachineBlock.FACING), 2);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState st) {
        EntityType<?> type = soul();
        var fx = upgrades.effects();
        if (type == null) {
            state = STATE_IDLE;
            work = 0;
            cost = 0;
            setRunning(level, pos, st, false);
            return;
        }
        BlockPos at = spawnAt();
        int crowd = level.getEntities(type, new AABB(at).inflate(4, 2, 4), e -> e.isAlive()).size();
        if (cost == 0 || level.getGameTime() % 40 == 0) cost = (int) ((2_000 + 400 * health(level, type)) * fx.energyPerOperation());
        state = crowd >= CROWD ? STATE_CROWDED : energy.getAmountAsInt() < cost ? STATE_NO_POWER : STATE_RUNNING;
        boolean on = state == STATE_RUNNING && enabled(level, pos);
        if (on) {
            work += (float) fx.speedMultiplier();
            if (work >= INTERVAL && spawn(level, type, at)) {
                work = 0;
                energy.set(energy.getAmountAsInt() - cost);
                spawned++;
            }
        }
        setRunning(level, pos, st, on);
        if (level.getGameTime() % 10 == 0) level.sendBlockUpdated(pos, st, st, Block.UPDATE_CLIENTS);
    }

    private static float health(ServerLevel level, EntityType<?> type) {
        var e = type.create(level, EntitySpawnReason.SPAWNER);
        float h = e instanceof LivingEntity l ? l.getMaxHealth() : 20;
        if (e != null) e.discard();
        return h;
    }

    private boolean spawn(ServerLevel level, EntityType<?> type, BlockPos at) {
        var e = type.create(level, EntitySpawnReason.SPAWNER);
        if (e == null) return false;
        e.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
        if (!level.noCollision(e)) {
            e.discard();
            return false;
        }
        if (e instanceof Mob mob) mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.SPAWNER, null);
        level.addFreshEntity(e);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 30, 0.3, 0.5, 0.3, 0.02);
        level.playSound(null, at, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 0.8F, 0.7F);
        return true;
    }

    public String clientSoul() {
        return clientSoul;
    }

    public float clientProgress() {
        return clientProgress;
    }

    @Override
    public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), isRunning() ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF, 0, 0,
                Component.translatable("gui.powerfactor.cage.scada", spawned), true, remoteEnabled(), false, 0, 0);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new SpawnerCageMenu(id, inventory, this, slots, data);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        slots.clearContent();
        ContainerHelper.loadAllItems(input, slots.getItems());
        upgrades.load(input);
        spawned = input.getIntOr("spawned", 0);
        work = input.getFloatOr("work", 0);
        clientSoul = input.getStringOr("soul_id", "");
        clientProgress = input.getFloatOr("progress", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        ContainerHelper.saveAllItems(output, slots.getItems());
        upgrades.save(output);
        output.putInt("spawned", spawned);
        output.putFloat("work", work);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        EntityType<?> t = soul();
        tag.putString("soul_id", t == null ? "" : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(t).toString());
        tag.putFloat("progress", work / INTERVAL);
        return tag;
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        clientSoul = input.getStringOr("soul_id", "");
        clientProgress = input.getFloatOr("progress", 0);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ValueInput input) {
        handleUpdateTag(input);
    }
}
