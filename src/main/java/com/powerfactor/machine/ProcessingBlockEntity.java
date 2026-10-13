package com.powerfactor.machine;

import com.powerfactor.api.Bus;
import com.powerfactor.api.ReactiveLoad;
import com.powerfactor.grid.BusLocator;
import com.powerfactor.power.UpgradeEffects;
import com.powerfactor.registry.ModSounds;
import com.powerfactor.scada.ScadaReading;
import com.powerfactor.upgrade.UpgradeInventory;
import com.powerfactor.upgrade.UpgradeType;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * A grid-powered machine that turns inputs into outputs: the crusher, the electric furnace, and
 * whatever comes next. Owns the inventory, FE buffer, upgrade slots, auto-eject, and the electrical
 * behavior of its {@link Drive}; subclasses only say what a job is.
 */
public abstract class ProcessingBlockEntity extends MachineBlockEntity implements ReactiveLoad, com.powerfactor.api.MachineAudio.Looping {
    public enum WorkState { NO_RECIPE, READY, OUTPUT_FULL, NEEDS_FLUID, DISABLED, NO_ENERGY, OUTPUT_UNAVAILABLE }
    /** Matching recipe and its current requirements; blocked work keeps its paid progress. */
    public record Job(net.minecraft.resources.Identifier id, int duration, Component description, WorkState state, Runnable complete) {}

    private static final int EJECT_INTERVAL = 10;

    protected final Inv items;
    private final int[] inputSlots, outputSlots;
    private final Buffer energy;
    private final int baseCapacity, baseFePerTick;
    protected final UpgradeInventory upgrades;
    protected final Drive drive;
    private final ResourceHandler<ItemResource>[] sidedItems;

    private double progress;
    private net.minecraft.resources.@Nullable Identifier activeRecipe;
    private WorkState workState = WorkState.NO_RECIPE;
    private double visualSpeed = 1, lastSyncedSpeed = -1;
    private int duration;
    private int lastFe;
    private boolean stalled, autoEject, autoInput;
    private final SideConfig sides = new SideConfig();
    private double volts = 1.0, hertz = 60.0;
    private @Nullable Component jobDescription;

    protected final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case ProcessingMenu.DATA_PROGRESS -> (int) progress;
                case ProcessingMenu.DATA_DURATION -> duration;
                case ProcessingMenu.DATA_FE -> lastFe;
                case ProcessingMenu.DATA_EE -> (int) Math.round(drive.ee());
                case ProcessingMenu.DATA_STORED_LO -> energy.getAmountAsInt() & 0xFFFF;
                case ProcessingMenu.DATA_STORED_HI -> energy.getAmountAsInt() >>> 16;
                case ProcessingMenu.DATA_CAPACITY_LO -> energy.getCapacityAsInt() & 0xFFFF;
                case ProcessingMenu.DATA_CAPACITY_HI -> energy.getCapacityAsInt() >>> 16;
                case ProcessingMenu.DATA_VOLTS -> (int) Math.round(volts * 1000);
                case ProcessingMenu.DATA_HERTZ -> (int) Math.round(hertz * 100);
                case ProcessingMenu.DATA_STATE -> drive.state().ordinal() | (stalled ? 4 : 0);
                case ProcessingMenu.DATA_SPEED -> (int) Math.round(drive.speed() * 1000);
                case ProcessingMenu.DATA_FLAGS -> (autoEject ? 1 : 0) | redstoneMode().ordinal() << 1 | (autoInput ? 8 : 0);
                case ProcessingMenu.DATA_SIDES -> sides.pack();
                case ProcessingMenu.DATA_TANK -> tankAmount();
                case ProcessingMenu.DATA_WORK_STATE -> workState.ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return ProcessingMenu.DATA_COUNT;
        }
    };

    @SuppressWarnings("unchecked")
    protected ProcessingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int[] inputSlots, int[] outputSlots,
            int baseCapacity, int baseFePerTick, Drive drive, Set<UpgradeType> supportedUpgrades) {
        super(type, pos, state);
        this.inputSlots = inputSlots;
        this.outputSlots = outputSlots;
        this.items = new Inv(inputSlots.length + outputSlots.length);
        this.baseCapacity = baseCapacity;
        this.baseFePerTick = baseFePerTick;
        this.energy = new Buffer(baseCapacity);
        this.drive = drive;
        this.upgrades = new UpgradeInventory(supportedUpgrades, this::onUpgradesChanged, p -> Container.stillValidBlockEntity(this, p));
        this.sidedItems = new ResourceHandler[7];
        for (Direction d : Direction.values()) {
            sidedItems[d.ordinal()] = new WorldlyContainerWrapper(items, d);
        }
        sidedItems[6] = new WorldlyContainerWrapper(items, null);
    }

    /** Matching inputs, including a blocked output or missing fluid; null when no recipe matches. */
    protected abstract @Nullable Job findJob(ServerLevel level);

    protected abstract boolean isValidInput(int slot, ItemStack stack);

    /** Fluid in the machine's tank, mB, for the GUI. */
    protected int tankAmount() {
        return 0;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        Bus bus = BusLocator.find(level, pos);
        volts = bus.volts();
        hertz = bus.hertz();
        UpgradeEffects up = upgrades.effects();
        lastFe = 0;
        stalled = false;

        Job job = findJob(level);
        if (job == null) {
            if (progress != 0 || activeRecipe != null) setChanged();
            progress = 0;
            activeRecipe = null;
            duration = 0;
            jobDescription = null;
        } else {
            if (activeRecipe != null && (!activeRecipe.equals(job.id()) || duration > 0 && duration != job.duration())) {
                progress = 0;
                setChanged();
            }
            if (!job.id().equals(activeRecipe)) setChanged();
            activeRecipe = job.id();
            duration = job.duration();
            jobDescription = job.description();
        }
        boolean enabled = enabled(level, pos);
        boolean ready = job != null && job.state() == WorkState.READY;
        workState = !enabled ? WorkState.DISABLED : job == null ? WorkState.NO_RECIPE : job.state();
        if (ready && enabled && energy.getAmountAsInt() <= 3) workState = WorkState.NO_ENERGY;
        if (drive.tick(volts, hertz, ready, enabled, energy.getAmountAsInt() > 3, up)) {
            level.playSound(null, pos, ModSounds.MOTOR_TRIP.get(), SoundSource.BLOCKS, 0.7F, 1.0F);
        }

        if (drive.state() == Drive.State.RUNNING && ready) {
            double speed = drive.speed();
            // FE per operation is fixed, so the draw per tick scales with how fast it's going.
            int cost = (int) Math.ceil(baseFePerTick * up.energyPerOperation() * speed);
            if (speed > 0 && energy.getAmountAsInt() >= cost) {
                energy.set(energy.getAmountAsInt() - cost);
                lastFe = cost;
                progress += speed;
                if (progress >= duration) {
                    progress = 0;
                    job.complete().run();
                    items.setChanged();
                }
            } else {
                stalled = true;
                workState = WorkState.NO_ENERGY;
            }
            setChanged();
        } else {
            int idle = Math.min(drive.idleFe(), energy.getAmountAsInt());
            energy.set(energy.getAmountAsInt() - idle);
            lastFe = idle;
        }
        boolean working = ready && drive.state() == Drive.State.RUNNING && !stalled;
        visualSpeed = working ? drive.speed() : 0;
        setRunning(level, pos, state, working);
        boolean stoppedChanged = (visualSpeed == 0) != (lastSyncedSpeed == 0);
        if (stoppedChanged || level.getGameTime() % 4 == 0 && Math.abs(visualSpeed - lastSyncedSpeed) >= 0.05) {
            lastSyncedSpeed = visualSpeed;
            level.sendBlockUpdated(pos, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }

        if (level.getGameTime() % EJECT_INTERVAL == 0) {
            if (autoEject) {
                eject(level, pos);
            }
            if (autoInput) {
                pull(level, pos);
            }
        }
    }

    private Direction facing() {
        return getBlockState().getValue(MachineBlock.FACING);
    }

    /** Push finished items out of every output face into whatever's there. */
    private void eject(ServerLevel level, BlockPos pos) {
        for (int slot : outputSlots) {
            ItemStack stack = items.getItem(slot);
            for (Direction d : Direction.values()) {
                if (stack.isEmpty()) {
                    break;
                }
                if (!sides.mode(facing(), d).output()) {
                    continue;
                }
                var target = level.getCapability(Capabilities.Item.BLOCK, pos.relative(d), d.getOpposite());
                if (target == null) {
                    continue;
                }
                try (Transaction tx = Transaction.openRoot()) {
                    int moved = target.insert(ItemResource.of(stack), stack.getCount(), tx);
                    if (moved > 0) {
                        tx.commit();
                        stack.shrink(moved);
                        items.setChanged();
                    }
                }
            }
        }
    }

    /** Pull one stack's worth into the inputs from every input face. */
    private void pull(ServerLevel level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            if (!sides.mode(facing(), d).input()) {
                continue;
            }
            var source = level.getCapability(Capabilities.Item.BLOCK, pos.relative(d), d.getOpposite());
            if (source == null) {
                continue;
            }
            for (int i = 0; i < source.size(); i++) {
                ItemResource resource = source.getResource(i);
                if (resource.isEmpty()) {
                    continue;
                }
                ItemStack probe = resource.toStack();
                for (int slot : inputSlots) {
                    if (!isValidInput(slot, probe) || !fits(probe.copyWithCount(1), slot)) {
                        continue;
                    }
                    ItemStack existing = items.getItem(slot);
                    int room = existing.isEmpty() ? probe.getMaxStackSize() : existing.getMaxStackSize() - existing.getCount();
                    try (Transaction tx = Transaction.openRoot()) {
                        int got = source.extract(i, resource, Math.min(room, 16), tx);
                        if (got > 0) {
                            tx.commit();
                            merge(resource.toStack(got), slot);
                            items.setChanged();
                            return;
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ helpers for subclasses

    protected boolean fits(ItemStack stack, int slot) {
        if (stack.isEmpty()) {
            return true;
        }
        ItemStack existing = items.getItem(slot);
        int limit = Math.min(items.getMaxStackSize(), stack.getMaxStackSize());
        return stack.getCount() <= limit && (existing.isEmpty()
                || ItemStack.isSameItemSameComponents(existing, stack) && (long) existing.getCount() + stack.getCount() <= limit);
    }

    protected record PlannedOutput(int slot, ItemStack stack) {}

    /** Reserve the whole possible batch, including outputs sharing one physical slot. */
    protected boolean fitsOutputs(java.util.List<PlannedOutput> outputs) {
        var reserved = new java.util.HashMap<Integer, ItemStack>();
        for (var output : outputs) {
            ItemStack stack = output.stack();
            if (stack.isEmpty()) continue;
            ItemStack existing = reserved.getOrDefault(output.slot(), items.getItem(output.slot()));
            if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, stack)) return false;
            long total = (long) existing.getCount() + stack.getCount();
            if (total > Math.min(items.getMaxStackSize(), stack.getMaxStackSize())) return false;
            reserved.put(output.slot(), stack.copyWithCount((int) total));
        }
        return true;
    }

    protected void merge(ItemStack stack, int slot) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack existing = items.getItem(slot);
        if (existing.isEmpty()) {
            items.setItem(slot, stack);
        } else {
            existing.grow(stack.getCount());
        }
    }

    private void onUpgradesChanged() {
        energy.setCapacity((int) (baseCapacity * upgrades.effects().bufferMultiplier()));
        setChanged();
    }

    // ------------------------------------------------------------------ accessors

    @Override
    public double eeDemand() {
        return drive.ee();
    }

    @Override
    public boolean soundActive() {
        return isRunning();
    }

    public Drive drive() {
        return drive;
    }

    public WorkState workState() { return workState; }
    public double workProgress() { return progress; }

    protected double animationSpeed() { return isRunning() ? visualSpeed : 0; }
    @Override protected double animationRate() { return 0.35 * animationSpeed(); }

    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
    @Override public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider lookup) {
        var tag = saveCustomOnly(lookup); tag.putDouble("visual_speed", animationSpeed()); return tag;
    }

    public void setSlot(int index, ItemStack stack) {
        items.setItem(index, stack);
    }

    public ItemStack slot(int index) {
        return items.getItem(index);
    }

    public UpgradeInventory upgrades() {
        return upgrades;
    }

    public boolean autoEject() {
        return autoEject;
    }

    public void toggleAutoEject() {
        autoEject = !autoEject;
        setChanged();
    }

    public void toggleAutoInput() {
        autoInput = !autoInput;
        setChanged();
    }

    public void cycleSide(SideConfig.Face face, int step) {
        sides.cycle(face, step);
        setChanged();
        if (level != null) {
            invalidateCapabilities();
        }
    }

    public SideConfig.Mode resourceMode(@Nullable Direction side) {
        return side == null ? SideConfig.Mode.BOTH : sides.mode(facing(), side);
    }

    public EnergyHandler getEnergy() {
        return energy;
    }

    public ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        return sidedItems[side == null ? 6 : side.ordinal()];
    }

    @Override
    public int comparatorSignal() {
        return EnergyHandlerUtil.getRedstoneSignalFromEnergyHandler(energy);
    }

    @Override
    protected Container inventory() {
        return items;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            net.minecraft.world.Containers.dropContents(level, pos, upgrades);
        }
    }

    @Override
    public ScadaReading scadaReading() {
        Drive.State s = drive.state();
        ScadaReading.Status status = switch (s) {
            case TRIPPED -> ScadaReading.Status.ALARM;
            case RUNNING -> stalled || drive.speed() < 0.9 ? ScadaReading.Status.WARNING : ScadaReading.Status.RUNNING;
            case IDLE -> ScadaReading.Status.WARNING;
            case OFF -> ScadaReading.Status.OFF;
        };
        Component detail = switch (s) {
            case TRIPPED -> Component.translatable("scada.powerfactor.motor.tripped", String.format("%.2f", volts));
            case IDLE -> Component.translatable("scada.powerfactor.motor.idle");
            case RUNNING -> stalled || jobDescription == null ? Component.translatable("scada.powerfactor.no_power")
                    : Component.translatable("scada.powerfactor.working", jobDescription,
                            duration == 0 ? 0 : (int) (100 * progress / duration), (int) Math.round(drive.speed() * 100));
            case OFF -> Component.translatable("scada.powerfactor.idle");
        };
        if (s != Drive.State.TRIPPED && (workState == WorkState.OUTPUT_FULL || workState == WorkState.OUTPUT_UNAVAILABLE
                || workState == WorkState.NEEDS_FLUID || workState == WorkState.NO_ENERGY)) {
            status = ScadaReading.Status.WARNING;
            detail = Component.translatable("gui.powerfactor.work." + workState.name().toLowerCase(java.util.Locale.ROOT));
        } else if (s != Drive.State.TRIPPED && workState == WorkState.DISABLED) {
            status = ScadaReading.Status.OFF;
            detail = Component.translatable("gui.powerfactor.work.disabled");
        }
        return new ScadaReading(worldPosition, getDisplayName(), status, lastFe, (int) Math.round(drive.ee()), detail, true, remoteEnabled(),
                false, (float) volts, (float) hertz);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, items.getItems());
        upgrades.load(input);
        energy.setCapacity((int) (baseCapacity * upgrades.effects().bufferMultiplier()));
        energy.deserialize(input);
        progress = input.getDoubleOr("progress", 0);
        duration = input.getIntOr("recipe_duration", 0);
        activeRecipe = input.getString("active_recipe").map(net.minecraft.resources.Identifier::tryParse).orElse(null);
        visualSpeed = input.getDoubleOr("visual_speed", 1);
        autoEject = input.getBooleanOr("auto_eject", false);
        autoInput = input.getBooleanOr("auto_input", false);
        sides.load(input);
        drive.load(input.getIntOr("drive_state", 0), input.getIntOr("drive_timer", 0));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items.getItems());
        upgrades.save(output);
        energy.serialize(output);
        output.putDouble("progress", progress);
        output.putInt("recipe_duration", duration);
        if (activeRecipe != null) output.putString("active_recipe", activeRecipe.toString());
        output.putBoolean("auto_eject", autoEject);
        output.putBoolean("auto_input", autoInput);
        sides.save(output);
        output.putInt("drive_state", drive.state().ordinal());
        output.putInt("drive_timer", drive.timer());
    }

    private static final class Buffer extends SimpleEnergyHandler {
        Buffer(int capacity) {
            super(capacity, Math.max(400, capacity / 10), 0);
        }

        void setCapacity(int capacity) {
            this.capacity = capacity;
            this.maxInsert = Math.max(400, capacity / 10);
            if (energy > capacity) {
                energy = capacity;
            }
        }
    }

    /** Hoppers and pipes put into inputs and take from outputs; down only takes. */
    protected final class Inv extends SimpleContainer implements WorldlyContainer {
        private final int[] all;

        Inv(int size) {
            super(size);
            all = new int[size];
            for (int i = 0; i < size; i++) {
                all[i] = i;
            }
        }

        private boolean isInput(int slot) {
            for (int s : inputSlots) {
                if (s == slot) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public int[] getSlotsForFace(Direction side) {
            SideConfig.Mode mode = sides.mode(facing(), side);
            return switch (mode) {
                case NONE -> new int[0];
                case INPUT -> inputSlots;
                case OUTPUT -> outputSlots;
                case BOTH -> all;
            };
        }

        @Override
        public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
            return isInput(slot) && isValidInput(slot, stack) && (side == null || sides.mode(facing(), side).input());
        }

        @Override
        public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
            return !isInput(slot) && sides.mode(facing(), side).output();
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return isInput(slot);
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(ProcessingBlockEntity.this, player);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            ProcessingBlockEntity.this.setChanged();
        }
    }
}
