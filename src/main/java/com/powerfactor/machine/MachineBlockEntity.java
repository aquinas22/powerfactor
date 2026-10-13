package com.powerfactor.machine;

import com.powerfactor.api.ScadaDevice;
import com.powerfactor.scada.ScadaReading;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Shared machine plumbing: SCADA remote enable, redstone shutdown, the RUNNING block state,
 * dropping inventory when broken, and a GUI title.
 */
public abstract class MachineBlockEntity extends BlockEntity implements MenuProvider, ScadaDevice {
    /** When a redstone signal lets the machine run. */
    public enum RedstoneMode {
        /** Runs unless powered (the default: a lever is an off switch). */
        LOW,
        /** Runs only while powered. */
        HIGH,
        IGNORE;

        public RedstoneMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private boolean remoteEnabled = true;
    private RedstoneMode redstoneMode = RedstoneMode.LOW;
    private final com.powerfactor.animation.RotaryMotion industrialMotion = new com.powerfactor.animation.RotaryMotion();

    protected MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public abstract void serverTick(ServerLevel level, BlockPos pos, BlockState state);

    /** Client-side animation and sound. */
    public void clientTick() {
        com.powerfactor.api.MachineAudio.tick(this);
        industrialMotion.tick(animationRate(), 0.06, 0.08);
    }
    protected double animationRate() { return isRunning() ? 0.35 : 0; }

    public float industrialPhase(float partial) { return industrialMotion.phase(partial); }

    /** Inventory to drop when broken, if any. */
    protected @Nullable Container inventory() {
        return null;
    }

    public int comparatorSignal() {
        return 0;
    }

    /** Allowed to run: SCADA hasn't stopped it and redstone agrees. */
    protected boolean enabled(ServerLevel level, BlockPos pos) {
        if (!remoteEnabled) {
            return false;
        }
        return switch (redstoneMode) {
            case LOW -> !level.hasNeighborSignal(pos);
            case HIGH -> level.hasNeighborSignal(pos);
            case IGNORE -> true;
        };
    }

    public RedstoneMode redstoneMode() {
        return redstoneMode;
    }

    public void cycleRedstoneMode() {
        setRedstoneMode(redstoneMode.next());
    }

    public void setRedstoneMode(RedstoneMode mode) {
        redstoneMode = mode;
        setChanged();
    }

    /** Shared direct selection for the three redstone icon buttons. */
    public boolean redstoneButton(int id) {
        if (id == ProcessingMenu.BUTTON_REDSTONE) { cycleRedstoneMode(); return true; }
        int mode = id - ProcessingMenu.BUTTON_REDSTONE_SET;
        if (mode < 0 || mode >= RedstoneMode.values().length) return false;
        setRedstoneMode(RedstoneMode.values()[mode]); return true;
    }

    protected void setRunning(ServerLevel level, BlockPos pos, BlockState state, boolean running) {
        if (state.getValue(MachineBlock.RUNNING) != running) {
            level.setBlock(pos, state.setValue(MachineBlock.RUNNING, running), Block.UPDATE_ALL);
        }
    }

    protected boolean isRunning() {
        return getBlockState().getValue(MachineBlock.RUNNING);
    }

    @Override
    public boolean remoteEnabled() {
        return remoteEnabled;
    }

    @Override
    public void setRemoteEnabled(boolean enabled) {
        if (remoteEnabled != enabled) {
            remoteEnabled = enabled;
            setChanged();
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        Container inv = inventory();
        if (inv != null && level != null) {
            Containers.dropContents(level, pos, inv);
        }
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return null;
    }

    /** False for machines that answer right-clicks with {@link #describe()} instead of a GUI. */
    public boolean hasMenu() {
        return true;
    }

    public Component describe() {
        ScadaReading r = scadaReading();
        return Component.empty().append(r.name()).append(": ").append(Component.literal(r.fe() + " FE/t  " + r.ee() + " EE/t  ")).append(r.detail());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        remoteEnabled = input.getBooleanOr("remote_enabled", true);
        redstoneMode = RedstoneMode.values()[Math.clamp(input.getIntOr("redstone_mode", 0), 0, 2)];
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("remote_enabled", remoteEnabled);
        output.putInt("redstone_mode", redstoneMode.ordinal());
    }
}
