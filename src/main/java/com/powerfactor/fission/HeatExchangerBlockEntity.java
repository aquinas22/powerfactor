package com.powerfactor.fission;

import com.powerfactor.api.MachineAudio;
import com.powerfactor.device.HydrogenTanks;
import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.registry.ModFission;
import com.powerfactor.registry.ModFluids;
import com.powerfactor.registry.ModSounds;
import com.powerfactor.scada.ScadaReading;
import com.powerfactor.steam.ThermalMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

/**
 * The steam generator of a loop-cooled reactor: hot heavy water, sodium or molten salt in one side, water in
 * the other. Heat crosses the tube bundle, raising steam (20 FE a mB, 10 mB from each mB of water) and
 * sending the same coolant back out cold for the reactor. Up to 100,000 FE/t of heat per exchanger.
 */
public final class HeatExchangerBlockEntity extends MachineBlockEntity implements MachineAudio.Looping {
    public static final int TANK = 32_000, MAX_HEAT = 100_000, HOT = 0, WATER = 1, COLD = 2, STEAM = 3;
    private final Tanks tanks = new Tanks(this::setChanged);
    private int lastHot, lastSteam, lastHeat;
    private ReactorCore.@Nullable Coolant lastCoolant;

    /** Hot coolant and water in; cold coolant (the same kind) and steam out. */
    public static final class Tanks extends HydrogenTanks {
        Tanks(Runnable changed) {
            super(TANK, new Fluid[] {ModFluids.HOT_SODIUM.source().get(), Fluids.WATER, ModFluids.LIQUID_SODIUM.source().get(), ModFluids.STEAM.get()},
                    new boolean[] {false, false, true, true}, changed);
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            Fluid f = resource.getFluid();
            return switch (index) {
                case HOT -> ReactorTanks.fromHot(f) != null;
                case COLD -> ReactorTanks.coolant(f) != null && ReactorTanks.coolant(f).loop;
                default -> super.isValid(index, resource);
            };
        }

        ReactorCore.@Nullable Coolant hotCoolant() {
            FluidResource r = getResource(HOT);
            return r.isEmpty() ? null : ReactorTanks.fromHot(r.getFluid());
        }

        int room(int index, Fluid fluid) {
            FluidResource r = getResource(index);
            if (!r.isEmpty() && !r.getFluid().isSame(fluid)) return 0;
            return TANK - amount(index);
        }

        void fill(int index, Fluid fluid, int amount) {
            if (amount > 0) set(index, FluidResource.of(fluid), amount(index) + amount);
        }
    }

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case ThermalMenu.DATA_FE -> lastHeat / 100;
                case ThermalMenu.DATA_EE -> lastCoolant == null ? 0 : lastCoolant.ordinal() + 1;
                case ThermalMenu.DATA_TANK -> tanks.amount(HOT);
                case ThermalMenu.DATA_TANK2 -> tanks.amount(COLD);
                case ThermalMenu.DATA_TANK3 -> tanks.amount(WATER);
                case ThermalMenu.DATA_RPM -> tanks.amount(STEAM);
                case ThermalMenu.DATA_FLAGS -> lastSteam > 0 ? 1 : 0;
                case ThermalMenu.DATA_REDSTONE -> redstoneMode().ordinal();
                case ThermalMenu.DATA_RATE -> lastSteam;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return ThermalMenu.DATA_COUNT;
        }
    };

    public HeatExchangerBlockEntity(BlockPos pos, BlockState state) {
        super(ModFission.HEAT_EXCHANGER_BE.get(), pos, state);
    }

    public Tanks getTanks() {
        return tanks;
    }

    public int lastSteam() {
        return lastSteam;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        var c = tanks.hotCoolant();
        lastCoolant = c;
        lastHot = lastSteam = lastHeat = 0;
        if (c != null && enabled(level, pos)) {
            Fluid cold = ReactorTanks.cold(c);
            // How much hot coolant can cross: limited by itself, the heat limit, room for it cold, water to boil and room for steam.
            int x = Math.min(tanks.amount(HOT), MAX_HEAT / c.heatPerMb);
            x = Math.min(x, tanks.room(COLD, cold));
            x = Math.min(x, tanks.amount(WATER) * 10 * 20 / c.heatPerMb);
            x = Math.min(x, tanks.room(STEAM, ModFluids.STEAM.get()) * 20 / c.heatPerMb);
            if (x > 0) {
                int steam = x * c.heatPerMb / 20;
                int water = Math.max(1, (steam + 9) / 10);
                tanks.add(HOT, -x);
                tanks.fill(COLD, cold, x);
                tanks.add(WATER, -Math.min(water, tanks.amount(WATER)));
                tanks.fill(STEAM, ModFluids.STEAM.get(), steam);
                lastHot = x;
                lastSteam = steam;
                lastHeat = x * c.heatPerMb;
            }
        }
        setRunning(level, pos, state, lastSteam > 0);
    }

    @Override
    public int comparatorSignal() {
        return tanks.amount(STEAM) * 15 / TANK;
    }

    @Override
    public boolean soundActive() {
        return isRunning();
    }

    @Override
    public SoundEvent loopSound() {
        return ModSounds.COOLING_TOWER_RUN.get();
    }

    @Override
    public float loopVolume() {
        return 0.35F;
    }

    @Override
    public ScadaReading scadaReading() {
        var base = new ScadaReading(worldPosition, getDisplayName(), lastSteam > 0 ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF, 0, 0,
                Component.translatable("scada.powerfactor.heat_exchanger", lastSteam, lastHot), true, remoteEnabled(), false, 0, 0);
        var points = java.util.List.of(
                new ScadaReading.Point(Component.translatable("gui.powerfactor.heat_exchanger.heat"), lastHeat / 1000F, "kFE/t", 0, MAX_HEAT / 1000F, 0),
                new ScadaReading.Point(Component.translatable("gui.powerfactor.fission.point.steam"), lastSteam, "mB/t", 0, MAX_HEAT / 20F, 0),
                new ScadaReading.Point(Component.translatable("gui.powerfactor.heat_exchanger.hot"), tanks.amount(HOT), "mB", 0, TANK,
                        tanks.amount(HOT) > TANK * 0.9 ? 1 : 0),
                new ScadaReading.Point(Component.translatable("block.minecraft.water"), tanks.amount(WATER), "mB", 0, TANK,
                        lastHot > 0 && tanks.amount(WATER) < 1000 ? 2 : 0));
        return base.with(points, java.util.List.of());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tanks.deserialize(input.childOrEmpty("tanks"));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tanks.serialize(output.child("tanks"));
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ThermalMenu(ThermalMenu.Device.HEAT_EXCHANGER, id, inventory, worldPosition, data, this);
    }
}
