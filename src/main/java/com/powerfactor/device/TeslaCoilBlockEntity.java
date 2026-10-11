package com.powerfactor.device;

import com.powerfactor.api.MachineAudio;
import com.powerfactor.api.ReactiveLoad;
import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.registry.ModSounds;
import com.powerfactor.scada.ScadaReading;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/**
 * Tesla coil: charges its capacitor from the grid, then throws lightning at the nearest hostile mob
 * it can see, arcing on to a second. Every strike is a sharp reactive pulse on the grid; a big coil
 * makes the lights flicker.
 */
public class TeslaCoilBlockEntity extends com.powerfactor.machine.MachineBlockEntity implements ReactiveLoad, MachineAudio.Looping {
    public static final int CAPACITY = 30_000, INTAKE = 600, STRIKE = 2_500, RANGE = 14, COOLDOWN = 12;
    public static final float DAMAGE = 9F, CHAIN_DAMAGE = 5F;
    /** Height of the toroid above the coil's base, where the arcs leave. */
    public static final double TOP = 1.65;
    public static final int EVENT_STRIKE = 1, EVENT_CHAIN = 2;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CAPACITY, INTAKE, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private int previous, lastIn, cooldown, pulse, strikes;

    // Client: recent bolts, for the renderer. {fromEntity (-1 = coil top), toEntity, age}
    private final List<int[]> bolts = new ArrayList<>();
    private int lastTarget = -1;

    public TeslaCoilBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TESLA_COIL.get(), pos, state);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        int now = energy.getAmountAsInt();
        lastIn = Math.max(0, now - previous);
        if (pulse > 0) {
            pulse--;
        }
        if (cooldown > 0) {
            cooldown--;
        } else if (now >= STRIKE && enabled(level, pos) && level.getGameTime() % 4 == 0) {
            strike(level, pos);
        }
        previous = energy.getAmountAsInt();
        setRunning(level, pos, state, energy.getAmountAsInt() >= STRIKE && enabled(level, pos));
    }

    private void strike(ServerLevel level, BlockPos pos) {
        Vec3 top = Vec3.atBottomCenterOf(pos).add(0, TOP, 0);
        LivingEntity target = nearest(level, top, new AABB(pos).inflate(RANGE), null);
        if (target == null) {
            return;
        }
        energy.set(energy.getAmountAsInt() - STRIKE);
        cooldown = COOLDOWN;
        pulse = 4;
        strikes++;
        zap(level, target, DAMAGE);
        level.blockEvent(pos, getBlockState().getBlock(), EVENT_STRIKE, target.getId());
        level.playSound(null, top.x, top.y, top.z, ModSounds.TESLA_ZAP.get(), SoundSource.BLOCKS, 2.0F, 0.9F + level.getRandom().nextFloat() * 0.2F);
        LivingEntity second = nearest(level, target.getEyePosition(), target.getBoundingBox().inflate(5), target);
        if (second != null) {
            zap(level, second, CHAIN_DAMAGE);
            level.blockEvent(pos, getBlockState().getBlock(), EVENT_CHAIN, second.getId());
        }
    }

    private static void zap(ServerLevel level, LivingEntity target, float damage) {
        target.hurtServer(level, level.damageSources().lightningBolt(), damage);
        target.igniteForSeconds(2);
    }

    private static LivingEntity nearest(ServerLevel level, Vec3 from, AABB box, Entity except) {
        return level.getEntitiesOfClass(LivingEntity.class, box, e -> e instanceof Enemy && e.isAlive() && e != except
                        && e.distanceToSqr(from) < (RANGE + 1) * (RANGE + 1) && visible(level, from, e))
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(from))).orElse(null);
    }

    private static boolean visible(ServerLevel level, Vec3 from, Entity e) {
        Vec3 to = e.getBoundingBox().getCenter();
        // Start just clear of the coil's own toroid, or the ray hits the coil itself.
        from = from.add(to.subtract(from).normalize().scale(1.0));
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()))
                .getType() == HitResult.Type.MISS;
    }

    // ------------------------------------------------------------------ client

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == EVENT_STRIKE) {
            bolts.add(new int[] {-1, param, 0});
            lastTarget = param;
            return true;
        }
        if (id == EVENT_CHAIN) {
            bolts.add(new int[] {lastTarget, param, 0});
            return true;
        }
        return super.triggerEvent(id, param);
    }

    @Override
    public void clientTick() {
        super.clientTick();
        bolts.removeIf(b -> ++b[2] > 6);
    }

    public List<int[]> bolts() {
        return bolts;
    }

    public boolean charged() {
        return isRunning();
    }

    // ------------------------------------------------------------------ grid, audio, SCADA

    /** The resonant charging supply draws a little EE; each strike is a big reactive pulse. */
    @Override
    public double eeDemand() {
        return pulse > 0 ? 320 : lastIn > 0 ? 0.4 * lastIn : 0;
    }

    public EnergyHandler getEnergy() {
        return energy;
    }

    @Override
    public boolean soundActive() {
        return charged();
    }

    @Override
    public SoundEvent loopSound() {
        return ModSounds.TESLA_HUM.get();
    }

    @Override
    public float loopVolume() {
        return 0.4F;
    }

    @Override
    public boolean hasMenu() {
        return false;
    }

    @Override
    public int comparatorSignal() {
        return (int) (15L * energy.getAmountAsInt() / CAPACITY);
    }

    @Override
    public ScadaReading scadaReading() {
        int pct = (int) (100L * energy.getAmountAsInt() / CAPACITY);
        return new ScadaReading(worldPosition, getDisplayName(), energy.getAmountAsInt() >= STRIKE ? ScadaReading.Status.RUNNING
                : ScadaReading.Status.OFF, lastIn, (int) Math.round(eeDemand()),
                Component.translatable("scada.powerfactor.tesla_coil", pct, strikes), true, remoteEnabled(), false, 1F, 60F);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        previous = energy.getAmountAsInt();
        strikes = input.getIntOr("strikes", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        output.putInt("strikes", strikes);
    }
}
