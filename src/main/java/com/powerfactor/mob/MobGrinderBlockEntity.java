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
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.item.ItemEntity;
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
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import org.jspecify.annotations.Nullable;

/**
 * Mob Grinder: blades that cut down mobs in the 5x3x5 space in front of it, as a player would (so player-
 * only drops and experience fall), then gather the drops into its inventory and the experience into its
 * store. Named mobs, pets and players are spared. Right-click with an empty hand to draw out the experience.
 */
public class MobGrinderBlockEntity extends MachineBlockEntity {
    public static final int CAPACITY = 500_000, MAX_INPUT = 10_000, FE_PER_HIT = 300, OUT = 9, DAMAGE = 10, PERIOD = 10;
    public static final int DATA_ENERGY = 0, DATA_XP_HI = 1, DATA_XP_LO = 2, DATA_KILLS = 3, DATA_ON = 4, DATA_COUNT = 5;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CAPACITY, MAX_INPUT, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private final SimpleContainer slots = new SimpleContainer(OUT) {
        @Override
        public boolean canPlaceItem(int index, ItemStack stack) {
            return false;
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(MobGrinderBlockEntity.this, player);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            MobGrinderBlockEntity.this.setChanged();
        }
    };
    private final ResourceHandler<ItemResource> items = VanillaContainerWrapper.of(slots);
    private int xp, kills;
    private boolean cutting;
    private boolean clientCutting;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case DATA_ENERGY -> energy.getAmountAsInt() / 100;
                case DATA_XP_HI -> xp >>> 16;
                case DATA_XP_LO -> xp & 0xFFFF;
                case DATA_KILLS -> kills & 0x7FFF;
                case DATA_ON -> cutting ? 1 : 0;
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

    public MobGrinderBlockEntity(BlockPos pos, BlockState state) {
        super(ModMob.MOB_GRINDER_BE.get(), pos, state);
    }

    public SimpleEnergyHandler getEnergy() {
        return energy;
    }

    public ResourceHandler<ItemResource> getItems() {
        return items;
    }

    public SimpleContainer slots() {
        return slots;
    }

    @Override
    protected Container inventory() {
        return slots;
    }

    public int xp() {
        return xp;
    }

    /** Hands the stored experience to a player. */
    public void drawXp(Player player) {
        if (xp <= 0) return;
        player.giveExperiencePoints(xp);
        xp = 0;
        setChanged();
        level.playSound(null, worldPosition, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.2F);
    }

    public AABB zone() {
        Direction f = getBlockState().getValue(MachineBlock.FACING);
        BlockPos c = worldPosition.relative(f, 3);
        return new AABB(c).inflate(2, 0, 2).expandTowards(0, 2, 0);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState st) {
        if (level.getGameTime() % PERIOD == 0 && enabled(level, pos)) {
            boolean hit = false;
            var fake = FakePlayerFactory.getMinecraft(level);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, zone(), e -> e.isAlive() && !(e instanceof Player)
                    && !e.hasCustomName() && !(e instanceof OwnableEntity o && o.getOwnerReference() != null))) {
                if (energy.getAmountAsInt() < FE_PER_HIT) break;
                energy.set(energy.getAmountAsInt() - FE_PER_HIT);
                hit = true;
                e.hurtServer(level, level.damageSources().playerAttack(fake), DAMAGE);
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 1, 0, 0, 0, 0);
                if (!e.isAlive()) kills++;
            }
            if (hit) {
                lastCut = level.getGameTime();
                level.playSound(null, pos, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.BLOCKS, 0.6F, 0.8F);
            }
        }
        // Gather the spoils: drops into the inventory, experience into the store.
        AABB catchBox = zone().inflate(1);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, catchBox, ItemEntity::isAlive)) {
            ItemStack rest = collect(item.getItem().copy());
            if (rest.isEmpty()) item.discard();
            else item.setItem(rest);
        }
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, catchBox, ExperienceOrb::isAlive)) {
            xp += orb.getValue();
            orb.discard();
        }
        // The blades keep spinning for a second after the last cut.
        boolean now = level.getGameTime() - lastCut < 20;
        setRunning(level, pos, st, now);
        if (now != cutting) {
            cutting = now;
            level.sendBlockUpdated(pos, st, st, Block.UPDATE_CLIENTS);
        }
    }

    private long lastCut = -100;

    private ItemStack collect(ItemStack stack) {
        for (int i = 0; i < OUT && !stack.isEmpty(); i++) {
            ItemStack s = slots.getItem(i);
            if (s.isEmpty()) {
                slots.setItem(i, stack.copy());
                return ItemStack.EMPTY;
            }
            if (ItemStack.isSameItemSameComponents(s, stack)) {
                int move = Math.min(stack.getCount(), s.getMaxStackSize() - s.getCount());
                s.grow(move);
                stack.shrink(move);
            }
        }
        slots.setChanged();
        return stack;
    }

    public boolean clientCutting() {
        return clientCutting;
    }

    @Override
    public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), isRunning() ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF, 0, 0,
                Component.translatable("gui.powerfactor.grinder.scada", kills, xp), true, remoteEnabled(), false, 0, 0);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MobGrinderMenu(id, inventory, this, slots, data);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        slots.clearContent();
        ContainerHelper.loadAllItems(input, slots.getItems());
        xp = input.getIntOr("xp", 0);
        kills = input.getIntOr("kills", 0);
        clientCutting = input.getBooleanOr("cutting", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        ContainerHelper.saveAllItems(output, slots.getItems());
        output.putInt("xp", xp);
        output.putInt("kills", kills);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putBoolean("cutting", cutting);
        return tag;
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        clientCutting = input.getBooleanOr("cutting", false);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ValueInput input) {
        handleUpdateTag(input);
    }
}
