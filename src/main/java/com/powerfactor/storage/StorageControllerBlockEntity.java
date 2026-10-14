package com.powerfactor.storage;

import com.powerfactor.api.ReactiveLoad;
import com.powerfactor.registry.ModStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import org.jspecify.annotations.Nullable;

/**
 * The heart of a storage network. Takes FE from the grid, discovers the network through data cables,
 * and keeps everything online while its buffer covers the network's draw. Two controllers on one
 * network conflict and both go offline, as in AE2.
 */
public class StorageControllerBlockEntity extends NodeBlockEntity implements ReactiveLoad {
    public static final int CAPACITY = 200_000, MAX_INPUT = 20_000;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CAPACITY, MAX_INPUT, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private @Nullable StorageNetwork own;
    private boolean rescan = true;
    private int lastDraw;
    /** Extra FE billed this tick by crafting and quarries, beyond idle draw. */
    private int extraDraw;

    public StorageControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModStorage.CONTROLLER_BE.get(), pos, state);
    }

    void serverTick(ServerLevel level) {
        if (own == null || !own.valid()) {
            own = new StorageNetwork(level, this);
            StorageNetworks.add(level, this);
            attach(own);
            rescan = true;
        }
        if (rescan) {
            rescan = false;
            own.scan();
        }
        int need = own.draw() + extraDraw;
        extraDraw = 0;
        boolean powered = energy.getAmountAsInt() >= need;
        if (powered) {
            energy.set(energy.getAmountAsInt() - need);
            lastDraw = need;
        } else {
            lastDraw = 0;
        }
        own.tick(powered);
    }

    void invalidateIfTouching(BlockPos pos) {
        if (own == null || pos.equals(worldPosition) || own.touches(pos)) rescan = true;
    }

    /** Bill extra FE against this tick's budget; returns false (and bills nothing) if it isn't there. */
    public boolean spend(int fe) {
        int have = energy.getAmountAsInt();
        int reserved = own == null ? 0 : own.draw() + extraDraw;
        if (have - reserved < fe) return false;
        extraDraw += fe;
        return true;
    }

    private final com.powerfactor.storage.fluid.NetworkFluidHandler fluids = new com.powerfactor.storage.fluid.NetworkFluidHandler(this::ownNetwork);

    /** The network's fluid cells as one fluid handler: pipes can push fluid in and pull it out here. */
    public com.powerfactor.storage.fluid.NetworkFluidHandler getFluids() {
        return fluids;
    }

    public SimpleEnergyHandler getEnergy() {
        return energy;
    }

    public int stored() {
        return energy.getAmountAsInt();
    }

    public int lastDraw() {
        return lastDraw;
    }

    public @Nullable StorageNetwork ownNetwork() {
        return own;
    }

    @Override
    public double eeDemand() {
        // Switch-mode supplies with active PFC: almost no reactive draw.
        return lastDraw * 0.05;
    }

    @Override
    public int idleDraw() {
        return 0;
    }

    @Override
    public void setRemoved() {
        if (own != null) own.dissolve();
        if (level instanceof ServerLevel server) StorageNetworks.remove(server, this);
        super.setRemoved();
    }

    public Component status() {
        if (own == null) return Component.translatable("gui.powerfactor.storage.starting");
        if (own.conflict()) return Component.translatable("gui.powerfactor.storage.conflict");
        if (!own.online()) return Component.translatable("gui.powerfactor.storage.no_power");
        return Component.translatable("gui.powerfactor.storage.online");
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
