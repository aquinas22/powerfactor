package com.powerfactor.defense;

import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.registry.ModDefense;
import com.powerfactor.scada.ScadaReading;
import com.powerfactor.weapon.FxPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import org.jspecify.annotations.Nullable;

/**
 * Shield Generator: a dome of force around the block. Hostile mobs are shoved back out through the
 * surface and projectiles fired by hostiles are burned out of the air as they cross it. Upkeep grows
 * with the dome's area; every repulse costs extra.
 */
public class ShieldGeneratorBlockEntity extends MachineBlockEntity {
    public static final int CAPACITY = 2_000_000, MAX_INPUT = 40_000, HIT_COST = 2_000, MIN_R = 4, MAX_R = 16;
    public static final int DATA_RADIUS = 0, DATA_ENERGY = 1, DATA_ON = 2, DATA_HITS = 3, DATA_UPKEEP = 4, DATA_COUNT = 5;
    public static final int BUTTON_SMALLER = 0, BUTTON_BIGGER = 1;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CAPACITY, MAX_INPUT, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private int radius = 8, hits, synced = -1;
    private boolean on;
    private int clientRadius;
    private boolean clientOn;
    private long clientHit = Long.MIN_VALUE / 2;
    private int clientHits;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case DATA_RADIUS -> radius;
                case DATA_ENERGY -> energy.getAmountAsInt() / 1000;
                case DATA_ON -> on ? 1 : 0;
                case DATA_HITS -> hits;
                case DATA_UPKEEP -> upkeep();
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

    public ShieldGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModDefense.SHIELD_GENERATOR_BE.get(), pos, state);
    }

    public SimpleEnergyHandler getEnergy() {
        return energy;
    }

    /** FE/t to hold the dome up: proportional to its surface area. */
    public int upkeep() {
        return radius * radius * 3;
    }

    public void button(int id) {
        radius = Math.clamp(radius + (id == BUTTON_BIGGER ? 2 : -2), MIN_R, MAX_R);
        setChanged();
    }

    public void setRadius(int r) {
        radius = Math.clamp(r, MIN_R, MAX_R);
    }

    Vec3 centre() {
        return Vec3.atCenterOf(worldPosition);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        on = enabled(level, pos) && energy.getAmountAsInt() >= upkeep();
        if (on) {
            energy.set(energy.getAmountAsInt() - upkeep());
            Vec3 c = centre();
            AABB box = new AABB(pos).inflate(radius + 1);
            // Hostiles inside are pushed out along the radius, hard.
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e instanceof Enemy && e.isAlive())) {
                Vec3 d = e.position().add(0, e.getBbHeight() / 2, 0).subtract(c);
                double dist = d.length();
                if (dist >= radius || energy.getAmountAsInt() < HIT_COST) continue;
                Vec3 out = dist < 0.1 ? new Vec3(1, 0, 0) : d.scale(1 / dist);
                e.setDeltaMovement(out.x * 1.2, 0.35, out.z * 1.2);
                e.hurtMarked = true;
                repulse(level, c.add(out.scale(radius)));
            }
            // Hostile projectiles crossing the dome are burned away.
            for (Projectile p : level.getEntitiesOfClass(Projectile.class, box, p -> p.getOwner() instanceof Enemy)) {
                if (p.position().distanceTo(c) <= radius + 0.5 && energy.getAmountAsInt() >= HIT_COST) {
                    repulse(level, p.position());
                    level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY(), p.getZ(), 8, 0.1, 0.1, 0.1, 0.08);
                    p.discard();
                }
            }
        }
        setRunning(level, pos, state, on);
        int sync = (on ? radius : 0) * 10000 + hits % 10000;
        if (sync != synced) {
            synced = sync;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private void repulse(ServerLevel level, Vec3 at) {
        energy.set(energy.getAmountAsInt() - HIT_COST);
        hits++;
        FxPayload.send(level, new FxPayload(FxPayload.SHOCKWAVE, at, at, 0xFF6FE3FF, 0.6F, -1));
        level.playSound(null, BlockPos.containing(at), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 1F, 0.6F);
    }

    public int clientRadius() {
        return clientRadius;
    }

    public boolean clientOn() {
        return clientOn;
    }

    public long clientHit() {
        return clientHit;
    }

    public int hits() {
        return hits;
    }

    @Override
    public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), on ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF, on ? upkeep() : 0, 0,
                Component.translatable("gui.powerfactor.shield.scada", radius, hits), true, remoteEnabled(), false, 0, 0);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ShieldGeneratorMenu(containerId, inventory, this, data);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        radius = Math.clamp(input.getIntOr("radius", 8), MIN_R, MAX_R);
        hits = input.getIntOr("hits", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        output.putInt("radius", radius);
        output.putInt("hits", hits);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("radius", on ? radius : 0);
        tag.putInt("hits", hits);
        return tag;
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        clientRadius = input.getIntOr("radius", 0);
        clientOn = clientRadius > 0;
        int h = input.getIntOr("hits", 0);
        if (h != clientHits && level != null) clientHit = level.getGameTime();
        clientHits = h;
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ValueInput input) {
        handleUpdateTag(input);
    }
}
