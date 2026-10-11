package com.powerfactor.device;

import com.powerfactor.api.MachineAudio;
import com.powerfactor.api.ReactiveLoad;
import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.registry.ModSounds;
import com.powerfactor.scada.ScadaReading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.PlayerInventoryWrapper;

/**
 * Induction charging pad: stand on it and everything you carry that holds FE charges, armour and
 * all; items dropped on it charge too. A resonant inductive link, so it draws some EE while working.
 */
public class ChargingPadBlockEntity extends com.powerfactor.machine.MachineBlockEntity implements ReactiveLoad, MachineAudio.Looping {
    public static final int CAPACITY = 50_000, INTAKE = 4_000, RATE = 2_500, PER_ITEM = 1_000;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CAPACITY, INTAKE, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private int lastOut, items;

    public ChargingPadBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHARGING_PAD.get(), pos, state);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        lastOut = 0;
        items = 0;
        if (enabled(level, pos) && energy.getAmountAsInt() > 0) {
            for (Entity e : level.getEntities((Entity) null, new AABB(pos).setMaxY(pos.getY() + 1.2), e -> e instanceof Player || e instanceof ItemEntity)) {
                if (e instanceof Player player) {
                    ResourceHandler<ItemResource> inv = PlayerInventoryWrapper.of(player);
                    for (int i = 0; i < inv.size() && lastOut < RATE; i++) {
                        charge(ItemAccess.forHandlerIndex(inv, i).getCapability(Capabilities.Energy.ITEM));
                    }
                } else if (e instanceof ItemEntity item && lastOut < RATE) {
                    // Charge a copy, then put the charged stack back on the entity.
                    ItemAccess access = ItemAccess.forStack(item.getItem().copy());
                    if (charge(access.getCapability(Capabilities.Energy.ITEM))) {
                        item.setItem(access.getResource().toStack(access.getAmount()));
                    }
                }
            }
        }
        setRunning(level, pos, state, lastOut > 0);
        if (lastOut > 0 && level.getGameTime() % 4 == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 2, 0.3, 0.1, 0.3, 0.02);
        }
    }

    private boolean charge(EnergyHandler target) {
        if (target == null || target.getAmountAsLong() >= target.getCapacityAsLong()) {
            return false;
        }
        int moved = com.powerfactor.machine.EnergyOut.give(energy, target, Math.min(PER_ITEM, RATE - lastOut));
        lastOut += moved;
        if (moved > 0) {
            items++;
        }
        return moved > 0;
    }

    @Override
    public double eeDemand() {
        return lastOut > 0 ? 0.35 * Math.min(INTAKE, lastOut) : 0;
    }

    public EnergyHandler getEnergy() {
        return energy;
    }

    @Override
    public boolean hasMenu() {
        return false;
    }

    @Override
    public boolean soundActive() {
        return isRunning();
    }

    @Override
    public SoundEvent loopSound() {
        return ModSounds.INDUCTION_HUM.get();
    }

    @Override
    public float loopVolume() {
        return 0.35F;
    }

    @Override
    public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), lastOut > 0 ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF, lastOut,
                (int) Math.round(eeDemand()), Component.translatable(lastOut > 0 ? "scada.powerfactor.charging_pad" : "scada.powerfactor.charging_pad.idle",
                        items, lastOut), true, remoteEnabled(), false, 1F, 60F);
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
}
