package com.powerfactor.steam;

import com.powerfactor.api.MachineAudio;
import com.powerfactor.animation.RotaryMotion;
import com.powerfactor.device.HydrogenTanks;
import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.registry.ModFluids;
import com.powerfactor.registry.ModSounds;
import com.powerfactor.scada.ScadaReading;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import org.jspecify.annotations.Nullable;

/** Powered fan condenser: hot water in, the same amount of cold water out; its roof must vent to the sky. */
public final class CoolingTowerBlockEntity extends MachineBlockEntity implements MachineAudio.Looping {
    public static final int TANK = 16000, BUFFER = 8000, HOT = 0, COLD = 1;
    private final HydrogenTanks tanks = new HydrogenTanks(TANK, new net.minecraft.world.level.material.Fluid[] {ModFluids.HOT_WATER.get(), Fluids.WATER},
            new boolean[] {false, true}, this::setChanged);
    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(BUFFER, 400, 0) {
        @Override protected void onEnergyChanged(int previous) { setChanged(); }
    };
    private final RotaryMotion fan = new RotaryMotion();
    private int lastFe, lastWater;
    private boolean blocked;
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case ThermalMenu.DATA_FE -> lastFe;
                case ThermalMenu.DATA_TANK -> tanks.amount(HOT);
                case ThermalMenu.DATA_TANK2 -> tanks.amount(COLD);
                case ThermalMenu.DATA_STORED -> energy.getAmountAsInt() / 10;
                case ThermalMenu.DATA_FLAGS -> (lastWater > 0 ? 1 : 0) | (blocked ? 4 : 0);
                case ThermalMenu.DATA_REDSTONE -> redstoneMode().ordinal();
                case ThermalMenu.DATA_RATE -> lastWater;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {}
        @Override public int getCount() { return ThermalMenu.DATA_COUNT; }
    };

    public CoolingTowerBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.COOLING_TOWER.get(), pos, state); }
    public HydrogenTanks getTanks() { return tanks; }
    public EnergyHandler getEnergy() { return energy; }
    @Override public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        blocked = !level.canSeeSky(pos.above(2)) || level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                pos.getX(), pos.getZ()) > pos.getY() + 2;
        boolean rain = level.isRainingAt(pos.above(2));
        int cost = rain ? 1 : 2;
        lastWater = enabled(level, pos) && !blocked ? Math.min(Math.min(tanks.amount(HOT), TANK - tanks.amount(COLD)),
                Math.min(rain ? 32 : 16, energy.getAmountAsInt() / cost)) : 0;
        lastFe = lastWater * cost;
        if (lastWater > 0) {
            energy.set(energy.getAmountAsInt() - lastFe);
            tanks.add(HOT, -lastWater);
            tanks.add(COLD, lastWater);
            if (level.getGameTime() % 10 == 0) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 2.15, pos.getZ() + 0.5,
                        2, 0.2, 0.07, 0.2, 0.01);
            }
        }
        setRunning(level, pos, state, lastWater > 0);
    }
    @Override public int comparatorSignal() { return tanks.amount(COLD) * 15 / TANK; }
    @Override public boolean soundActive() { return isRunning(); }
    @Override public SoundEvent loopSound() { return ModSounds.COOLING_TOWER_RUN.get(); }
    @Override public float loopVolume() { return 0.5F; }
    @Override public void clientTick() { super.clientTick(); fan.tick(isRunning() ? 0.6 : 0, 0.08, 0.03); }
    public float fanPhase(float partial) { return fan.phase(partial); }
    @Override public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), blocked ? ScadaReading.Status.ALARM : lastWater > 0 ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF,
                lastFe, 0, Component.translatable(blocked ? "scada.powerfactor.cooling.blocked" : "scada.powerfactor.cooling", lastWater, tanks.amount(COLD)),
                true, remoteEnabled(), false, 1, 60);
    }
    @Override protected void loadAdditional(ValueInput input) { super.loadAdditional(input); energy.deserialize(input); tanks.deserialize(input.childOrEmpty("tanks")); }
    @Override protected void saveAdditional(ValueOutput output) { super.saveAdditional(output); energy.serialize(output); tanks.serialize(output.child("tanks")); }
    @Override public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ThermalMenu(ThermalMenu.Device.COOLING_TOWER, id, inventory, worldPosition, data, this);
    }
}
