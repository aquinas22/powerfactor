package com.powerfactor.generator;

import com.powerfactor.api.MachineAudio;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.power.WindModel;
import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.registry.ModSounds;
import com.powerfactor.scada.ScadaReading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;

/**
 * Utility-scale wind turbine nacelle, mounted on a {@link WindTowerBlock} tower. The rotor sweeps a
 * 13-block disc in front of the nacelle, which must be clear. Wind speed depends on the weather, hub
 * height and gusts; {@link WindModel} turns it into power, rotor speed and blade pitch the way a real
 * turbine's controller does (cut-in, rated, storm cut-out). Power runs down the tower: cable it from
 * the nacelle or from the tower's base. Inverter-fed, so FE only, no EE support.
 */
public class WindTurbineBlockEntity extends PushingGenerator implements MachineAudio.Looping {
    public static final int PEAK = 160;
    /** Blade length, in blocks from the hub centre. */
    public static final double ROTOR_RADIUS = 6.5;
    /** Hub centre above the nacelle block's floor, and ahead of its centre, in blocks. */
    public static final double HUB_HEIGHT = 1.6, HUB_FORWARD = 1.15;

    private final WindModel model = new WindModel();
    private double gust = 1.0, gustTarget = 1.0;
    private int gustTimer;
    private double wind;
    private boolean blocked;
    private int towerHeight;
    private BlockPos towerBase;

    // Synced to clients.
    private float syncedWind;
    private WindModel.Mode syncedMode = WindModel.Mode.LOW_WIND;
    private boolean syncedBlocked;

    // Client animation.
    private float rpm, angle, prevAngle;
    private float pitch = (float) WindModel.FEATHER, prevPitch = pitch;
    private float anemometer, prevAnemometer;
    private WindModel.Mode heardMode = WindModel.Mode.LOW_WIND;

    public WindTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WIND_TURBINE.get(), pos, state, 8_000);
    }

    // ------------------------------------------------------------------ server

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (towerBase == null || level.getGameTime() % 100 == 0) {
            surveyTower(level, pos);
            blocked = !clear(level, pos, state.getValue(MachineBlock.FACING));
        }
        if (--gustTimer <= 0) {
            var r = level.getRandom();
            gustTimer = 60 + r.nextInt(160);
            gustTarget = level.isThundering() ? 0.7 + r.nextDouble() * 1.25 : 0.55 + r.nextDouble() * 0.9;
        }
        gust += (gustTarget - gust) * 0.02;
        wind = meanWind(level) * heightFactor(pos) * gust;

        WindModel.Mode mode = model.update(wind, enabled(level, pos) && !blocked, 1);
        generate(mode == WindModel.Mode.RUNNING ? (int) Math.round(PEAK * WindModel.powerFraction(wind)) : 0);
        push(level, pos);
        setRunning(level, pos, state, lastFe > 0);

        if (mode != syncedMode || blocked != syncedBlocked || Math.abs(wind - syncedWind) > 0.4) {
            syncedMode = mode;
            syncedBlocked = blocked;
            syncedWind = (float) wind;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private static double meanWind(Level level) {
        return level.isThundering() ? 15.0 : level.isRaining() ? 11.0 : 8.5;
    }

    /** Wind picks up with height above the ground (the 1/7th power law) and, a little, with altitude. */
    private double heightFactor(BlockPos pos) {
        double hub = Math.max(2, towerHeight + HUB_HEIGHT);
        return Math.pow(hub / 12.0, 1 / 7.0) * Mth.clamp(1 + (pos.getY() - 63) / 250.0, 0.85, 1.3);
    }

    private void surveyTower(Level level, BlockPos pos) {
        BlockPos p = pos.below();
        int h = 0;
        while (h < 64 && level.getBlockState(p).getBlock() instanceof WindTowerBlock) {
            p = p.below();
            h++;
        }
        towerHeight = h;
        towerBase = p.above();
    }

    /** Every block the blades sweep, a disc of radius {@link #ROTOR_RADIUS} one block ahead, must be air. */
    private static boolean clear(ServerLevel level, BlockPos pos, Direction facing) {
        BlockPos plane = pos.relative(facing);
        Direction side = facing.getClockWise();
        int r = (int) Math.ceil(ROTOR_RADIUS);
        for (int a = -r; a <= r; a++) {
            for (int b = -r - 1; b <= r + 1; b++) {
                double dy = b + 0.5 - HUB_HEIGHT;
                if (a * a + dy * dy > (ROTOR_RADIUS + 0.3) * (ROTOR_RADIUS + 0.3)) {
                    continue;
                }
                if (!level.getBlockState(plane.relative(side, a).above(b)).isAir()) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Push into whatever touches the nacelle, then into whatever touches the foot of the tower. */
    @Override
    protected void push(ServerLevel level, BlockPos pos) {
        super.push(level, pos);
        if (towerBase == null || towerBase.equals(pos) || buffer.getAmountAsInt() == 0) {
            return;
        }
        for (Direction d : Direction.values()) {
            if (buffer.getAmountAsInt() == 0) {
                return;
            }
            BlockPos at = towerBase.relative(d);
            if (level.getBlockState(at).getBlock() instanceof WindTowerBlock || at.equals(pos)) {
                continue;
            }
            EnergyHandler target = level.getCapability(Capabilities.Energy.BLOCK, at, d.getOpposite());
            if (target != null && !(level.getBlockEntity(at) instanceof PushingGenerator)) {
                EnergyHandlerUtil.move(buffer, target, buffer.getAmountAsInt(), null);
            }
        }
    }

    // ------------------------------------------------------------------ client

    @Override
    public void clientTick() {
        super.clientTick();
        Level level = getLevel();
        prevAngle = angle;
        prevPitch = pitch;
        prevAnemometer = anemometer;

        float targetRpm = syncedMode == WindModel.Mode.RUNNING ? (float) WindModel.rpm(syncedWind) : 0;
        // A big rotor has a lot of inertia: slow to spin up, quicker when the brake is on.
        float rate = targetRpm > rpm ? 0.004F : syncedMode == WindModel.Mode.RUNNING ? 0.006F : 0.012F;
        rpm += (targetRpm - rpm) * rate + Math.signum(targetRpm - rpm) * 0.002F;
        if (Math.abs(targetRpm - rpm) < 0.003F) {
            rpm = targetRpm;
        }
        // In light air an idling rotor still turns over slowly.
        float idle = syncedMode == WindModel.Mode.LOW_WIND ? 0.4F * syncedWind / (float) WindModel.CUT_IN : 0;
        float spin = Math.max(rpm, idle);

        float targetPitch = (float) WindModel.pitch(syncedMode, syncedWind);
        pitch += Mth.clamp(targetPitch - pitch, -0.45F, 0.45F);   // pitch drives: about 9 degrees a second

        // degrees per tick = rpm * 360 / 60 / 20
        angle += spin * 0.3F;
        anemometer += syncedWind * 2.2F;
        if (angle >= 360F) {
            angle -= 360F;
            prevAngle -= 360F;
        }
        if (anemometer >= 360F) {
            anemometer -= 360F;
            prevAnemometer -= 360F;
        }

        if (level != null) {
            // Each blade sweeping down past the tower gives the deep 'whoomph' you hear under a real one.
            if (spin > 3F && Math.floor((prevAngle + 60F) / 120F) != Math.floor((angle + 60F) / 120F)) {
                float load = spin / (float) WindModel.MAX_RPM;
                Vec3 at = hubPosition(0).add(0, -ROTOR_RADIUS * 0.6, 0);
                level.playLocalSound(at.x, at.y, at.z, ModSounds.WIND_TURBINE_SWOOSH.get(), SoundSource.BLOCKS,
                        0.5F + 1.6F * load * load, 0.85F + 0.2F * load + level.getRandom().nextFloat() * 0.06F, false);
            }
            if (heardMode == WindModel.Mode.RUNNING && (syncedMode == WindModel.Mode.CUT_OUT || syncedMode == WindModel.Mode.STOPPED)
                    && rpm > 4) {
                Vec3 at = hubPosition(0);
                level.playLocalSound(at.x, at.y, at.z, ModSounds.WIND_TURBINE_BRAKE.get(), SoundSource.BLOCKS, 2.0F, 1.0F, false);
            }
            heardMode = syncedMode;
        }
    }

    /** World position of the hub centre (ignoring the small yaw offset). */
    public Vec3 hubPosition(float partial) {
        Direction facing = getBlockState().getValueOrElse(MachineBlock.FACING, Direction.NORTH);
        return Vec3.atBottomCenterOf(worldPosition).add(0, HUB_HEIGHT, 0)
                .add(facing.getStepX() * HUB_FORWARD, 0, facing.getStepZ() * HUB_FORWARD);
    }

    public float rotorAngle(float partial) {
        return prevAngle + (angle - prevAngle) * partial;
    }

    public float bladePitch(float partial) {
        return prevPitch + (pitch - prevPitch) * partial;
    }

    public float anemometerAngle(float partial) {
        return prevAnemometer + (anemometer - prevAnemometer) * partial;
    }

    /** The nacelle hunts a few degrees either side of its heading as the wind veers. */
    public float yawOffset(float partial) {
        if (getLevel() == null) {
            return 0;
        }
        double t = getLevel().getGameTime() + partial;
        long seed = worldPosition.asLong();
        return (float) (6 * Math.sin(t / 1300.0 + (seed & 63)) + 2.5 * Math.sin(t / 377.0 + ((seed >> 6) & 63)));
    }

    public float rpm() {
        return rpm;
    }

    public float syncedWind() {
        return syncedWind;
    }

    public WindModel.Mode mode() {
        return syncedMode;
    }

    // ------------------------------------------------------------------ audio, SCADA

    @Override
    public boolean soundActive() {
        return rpm > 1F;
    }

    @Override
    public SoundEvent loopSound() {
        return ModSounds.WIND_TURBINE_RUN.get();
    }

    @Override
    public float loopPitch() {
        return 0.75F + 0.35F * rpm / (float) WindModel.MAX_RPM;
    }

    @Override
    public float loopVolume() {
        return 0.15F + 0.45F * rpm / (float) WindModel.MAX_RPM;
    }

    @Override
    public ScadaReading scadaReading() {
        WindModel.Mode mode = model.mode();
        ScadaReading.Status status = blocked || mode == WindModel.Mode.CUT_OUT ? ScadaReading.Status.WARNING
                : mode == WindModel.Mode.RUNNING ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF;
        String ms = String.format("%.1f", wind);
        Component detail = blocked ? Component.translatable("scada.powerfactor.wind.blocked")
                : switch (mode) {
                    case RUNNING -> Component.translatable("scada.powerfactor.wind", ms, Math.round(WindModel.rpm(wind)),
                            Math.round(WindModel.pitch(mode, wind)));
                    case LOW_WIND -> Component.translatable("scada.powerfactor.wind.low", ms);
                    case CUT_OUT -> Component.translatable("scada.powerfactor.wind.cut_out", ms);
                    case STOPPED -> Component.translatable("scada.powerfactor.wind.stopped");
                };
        return new ScadaReading(worldPosition, getDisplayName(), status, lastFe, 0, detail, true, remoteEnabled(), true, 1F, 0F);
    }

    // ------------------------------------------------------------------ persistence and sync

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        syncedWind = input.getFloatOr("wind", 0);
        syncedMode = WindModel.Mode.values()[Math.clamp(input.getIntOr("mode", 1), 0, WindModel.Mode.values().length - 1)];
        syncedBlocked = input.getBooleanOr("blocked", false);
        model.setMode(syncedMode);
        if (getLevel() == null || !getLevel().isClientSide()) {
            return;
        }
        // First sync: start already turning rather than spinning up from rest on every chunk load.
        if (heardMode != syncedMode && rpm == 0 && syncedMode == WindModel.Mode.RUNNING) {
            rpm = (float) WindModel.rpm(syncedWind);
            pitch = prevPitch = (float) WindModel.pitch(syncedMode, syncedWind);
            heardMode = syncedMode;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putFloat("wind", syncedWind);
        output.putInt("mode", syncedMode.ordinal());
        output.putBoolean("blocked", syncedBlocked);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("wind", syncedWind);
        tag.putInt("mode", syncedMode.ordinal());
        tag.putBoolean("blocked", syncedBlocked);
        return tag;
    }
}
