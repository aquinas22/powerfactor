package com.powerfactor.engine;

import com.powerfactor.PowerFactorConfig;
import com.powerfactor.api.ScadaDevice;
import com.powerfactor.power.EngineHeat;
import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.scada.ScadaReading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import org.jspecify.annotations.Nullable;

public class EngineBlockEntity extends BlockEntity implements MenuProvider, ScadaDevice {
    public static final int MAX_HEAT = EngineHeat.MAX;
    public static final int STAGE_BLUE = 0, STAGE_GREEN = 1, STAGE_YELLOW = 2, STAGE_RED = 3;
    /** Piston stroke progress per tick at each heat stage. */
    private static final float[] STROKE_SPEED = {0.02F, 0.035F, 0.05F, 0.07F};
    private static final int BUFFER = 2_000;

    private final EngineHeat heat = new EngineHeat();
    private final SimpleEnergyHandler buffer = new SimpleEnergyHandler(BUFFER, 0, Integer.MAX_VALUE) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private final SimpleContainer fuel = new SimpleContainer(1) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return level != null && stack.getBurnTime(null, level.fuelValues()) > 0;
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(EngineBlockEntity.this, player);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            EngineBlockEntity.this.setChanged();
        }
    };
    private final ResourceHandler<ItemResource> fuelHandler = VanillaContainerWrapper.of(fuel);
    private int burnTime, burnTotal;
    private boolean remoteEnabled = true;
    private int lastFe;

    // Synced to clients for the piston animation.
    private boolean running;
    private int stage;
    // Client-only.
    private float stroke, prevStroke;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case EngineMenu.DATA_BURN_PERMILLE -> burnTotal == 0 ? 0 : 1000 * burnTime / burnTotal;
                case EngineMenu.DATA_HEAT -> heat.heat();
                case EngineMenu.DATA_STORED -> buffer.getAmountAsInt();
                case EngineMenu.DATA_FE -> lastFe;
                case EngineMenu.DATA_FLAGS -> (running ? 1 : 0) | (heat.seized() ? 2 : 0);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return EngineMenu.DATA_COUNT;
        }
    };

    public EngineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENGINE.get(), pos, state);
    }

    public EngineKind kind() {
        return getBlockState().getBlock() instanceof EngineBlock engine ? engine.kind() : EngineKind.REDSTONE;
    }

    public Direction facing() {
        return getBlockState().getValueOrElse(EngineBlock.FACING, Direction.UP);
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        EngineKind kind = kind();
        boolean powered = remoteEnabled && level.hasNeighborSignal(pos);
        boolean full = buffer.getAmountAsInt() >= buffer.getCapacityAsInt();

        if (kind.burnsFuel && burnTime <= 0 && powered && !full && !heat.seized()) {
            ItemStack stack = fuel.getItem(0);
            int ticks = stack.isEmpty() ? 0 : stack.getBurnTime(null, level.fuelValues());
            if (ticks > 0) {
                burnTime = burnTotal = ticks;
                ItemStackTemplate remainder = stack.getCraftingRemainder();
                stack.shrink(1);
                if (stack.isEmpty()) {
                    fuel.setItem(0, remainder != null ? remainder.create() : ItemStack.EMPTY);
                }
                fuel.setChanged();
            }
        }
        boolean fueled = !kind.burnsFuel || burnTime > 0;

        double fill = (double) buffer.getAmountAsInt() / buffer.getCapacityAsInt();
        boolean wasSeized = heat.seized();
        boolean producing = heat.tick(powered && fueled, fill, kind.heatCap);
        if (heat.seized() && !wasSeized) {
            level.playSound(null, pos, com.powerfactor.registry.ModSounds.ENGINE_SEIZE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        lastFe = 0;
        if (producing) {
            lastFe = Math.min(kind.fePerTick(), buffer.getCapacityAsInt() - buffer.getAmountAsInt());
            buffer.set(buffer.getAmountAsInt() + lastFe);
            if (kind.burnsFuel) {
                burnTime--;
            }
        }
        if (heat.seized() && heat.heat() >= MAX_HEAT - 3 && kind.burnsFuel && PowerFactorConfig.ENGINES_EXPLODE.get()) {
            level.removeBlock(pos, false);
            level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3.0F, Level.ExplosionInteraction.BLOCK);
            return;
        }

        // Output only through the plate face.
        Direction out = facing();
        EnergyHandler target = level.getCapability(Capabilities.Energy.BLOCK, pos.relative(out), out.getOpposite());
        if (target != null && buffer.getAmountAsInt() > 0) {
            EnergyHandlerUtil.move(buffer, target, buffer.getAmountAsInt(), null);
        }

        if (producing != running || heat.stage() != stage) {
            running = producing;
            stage = heat.stage();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
        if (producing || heat.heat() > 0) {
            setChanged();
        }
    }

    public void clientTick() {
        prevStroke = stroke;
        if (running || stroke > 0) {
            stroke += running ? STROKE_SPEED[Mth.clamp(stage, 0, 3)] : 0.05F;
            if (stroke >= 1F) {
                stroke -= 1F;
                prevStroke -= 1F;
                if (running && level != null) {
                    level.playLocalSound(worldPosition, com.powerfactor.registry.ModSounds.ENGINE_CHUFF.get(), SoundSource.BLOCKS,
                            0.35F, 0.85F + stage * 0.1F, false);
                }
                if (!running) {
                    stroke = prevStroke = 0;
                }
            }
        }
    }

    /** Plate extension 0..1, a triangle wave over one stroke. */
    public float plateOffset(float partialTick) {
        float p = Mth.lerp(partialTick, prevStroke, stroke);
        return p < 0.5F ? p * 2F : 2F - p * 2F;
    }

    public int heatStage() {
        return stage;
    }

    public EnergyHandler getEnergy() {
        return buffer;
    }

    public @Nullable ResourceHandler<ItemResource> getFuelHandler() {
        return kind().burnsFuel ? fuelHandler : null;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) {
            Containers.dropContents(level, pos, fuel);
        }
    }

    @Override
    public ScadaReading scadaReading() {
        String[] stages = {"blue", "green", "yellow", "red"};
        ScadaReading.Status status = heat.seized() ? ScadaReading.Status.ALARM
                : stage == STAGE_RED ? ScadaReading.Status.WARNING
                : running ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF;
        Component detail = heat.seized() ? Component.translatable("scada.powerfactor.engine.seized")
                : Component.translatable("scada.powerfactor.engine", Component.translatable("scada.powerfactor.heat." + stages[stage]), heat.heat() / 10);
        return new ScadaReading(worldPosition, getDisplayName(), status, lastFe, 0, detail, true, remoteEnabled, true, 1F, 60F);
    }

    @Override
    public boolean remoteEnabled() {
        return remoteEnabled;
    }

    @Override
    public void setRemoteEnabled(boolean enabled) {
        remoteEnabled = enabled;
        setChanged();
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return kind().burnsFuel ? new EngineMenu(containerId, inventory, fuel, data) : null;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, fuel.getItems());
        buffer.deserialize(input);
        heat.load(input.getIntOr("heat", 0), input.getBooleanOr("seized", false));
        burnTime = input.getIntOr("burn_time", 0);
        burnTotal = input.getIntOr("burn_total", 0);
        remoteEnabled = input.getBooleanOr("remote_enabled", true);
        running = input.getBooleanOr("running", false);
        stage = input.getIntOr("stage", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, fuel.getItems());
        buffer.serialize(output);
        output.putInt("heat", heat.heat());
        output.putBoolean("seized", heat.seized());
        output.putInt("burn_time", burnTime);
        output.putInt("burn_total", burnTotal);
        output.putBoolean("remote_enabled", remoteEnabled);
        output.putBoolean("running", running);
        output.putInt("stage", stage);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }
}
