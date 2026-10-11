package com.powerfactor.generator;

import com.powerfactor.machine.MachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/** A generator with an extract-only FE buffer that it pushes into every neighbour each tick. */
public abstract class PushingGenerator extends MachineBlockEntity {
    protected final SimpleEnergyHandler buffer;
    protected int lastFe;

    protected PushingGenerator(BlockEntityType<?> type, BlockPos pos, BlockState state, int capacity) {
        super(type, pos, state);
        buffer = new SimpleEnergyHandler(capacity, 0, Integer.MAX_VALUE) {
            @Override
            protected void onEnergyChanged(int previousAmount) {
                setChanged();
            }
        };
    }

    protected void generate(int fe) {
        int room = buffer.getCapacityAsInt() - buffer.getAmountAsInt();
        lastFe = Math.min(fe, room);
        buffer.set(buffer.getAmountAsInt() + lastFe);
    }

    protected void push(ServerLevel level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            if (buffer.getAmountAsInt() == 0) {
                return;
            }
            EnergyHandler target = level.getCapability(Capabilities.Energy.BLOCK, pos.relative(d), d.getOpposite());
            if (target != null && !(level.getBlockEntity(pos.relative(d)) instanceof PushingGenerator)) {
                EnergyHandlerUtil.move(buffer, target, buffer.getAmountAsInt(), null);
            }
        }
    }

    public EnergyHandler getEnergy() {
        return buffer;
    }

    @Override
    public boolean hasMenu() {
        return false;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        buffer.deserialize(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        buffer.serialize(output);
    }
}
