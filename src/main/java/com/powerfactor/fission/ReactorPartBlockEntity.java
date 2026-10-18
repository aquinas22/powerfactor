package com.powerfactor.fission;

import com.powerfactor.registry.ModFission;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * A Reactor Port or Logic Adapter in a reactor's wall, knowing its controller.
 *
 * <p>Ports have a mode (use to switch; it shows as the ring's colour): COOLANT takes water in and lets
 * steam be pulled out; FUEL takes fresh rods in and lets spent rods be pulled out. Logic Adapters
 * have a mode too: which alarm lights their redstone output; a redstone signal into one SCRAMs the reactor.
 */
public class ReactorPartBlockEntity extends BlockEntity {
    public enum PortMode { COOLANT, FUEL }

    private @Nullable BlockPos controller;
    private int mode;

    public ReactorPartBlockEntity(BlockPos pos, BlockState state) {
        super(ModFission.REACTOR_PART_BE.get(), pos, state);
    }

    public void link(@Nullable BlockPos controller) {
        if (java.util.Objects.equals(this.controller, controller)) return;
        this.controller = controller;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    public @Nullable ReactorControllerBlockEntity controller() {
        if (controller == null || level == null || !level.isLoaded(controller)) return null;
        return level.getBlockEntity(controller) instanceof ReactorControllerBlockEntity r && r.shape() != null ? r : null;
    }

    public int mode() {
        return mode;
    }

    public void cycle(int count) {
        mode = (mode + 1) % count;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    public PortMode portMode() {
        return getBlockState().getValueOrElse(ReactorPartBlock.FUEL, false) ? PortMode.FUEL : PortMode.COOLANT;
    }

    public @Nullable ResourceHandler<FluidResource> fluids() {
        var r = controller();
        return r == null || portMode() != PortMode.COOLANT ? null : r.tanks();
    }

    public @Nullable ResourceHandler<ItemResource> items() {
        var r = controller();
        if (r == null || portMode() != PortMode.FUEL) return null;
        return r.fuelHandler();
    }

    /** Logic adapter: the alarm whose state it outputs (mode indexes {@link ReactorControllerBlockEntity.Alarm}). */
    public int signal() {
        var r = controller();
        if (r == null) return 0;
        var alarm = ReactorControllerBlockEntity.Alarm.values()[Math.clamp(mode, 0, ReactorControllerBlockEntity.Alarm.values().length - 1)];
        return (r.alarms() & alarm.bit()) != 0 ? 15 : 0;
    }

    /** Logic adapter: a redstone signal in SCRAMs the reactor. */
    public void redstoneIn(ServerLevel level, boolean powered) {
        var r = controller();
        if (r != null && powered) r.scram(level, "adapter");
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        long c = input.getLongOr("controller", Long.MIN_VALUE);
        controller = c == Long.MIN_VALUE ? null : BlockPos.of(c);
        mode = input.getIntOr("mode", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (controller != null) output.putLong("controller", controller.asLong());
        output.putInt("mode", mode);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
}
