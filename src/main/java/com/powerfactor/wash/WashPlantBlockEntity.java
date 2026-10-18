package com.powerfactor.wash;

import com.powerfactor.api.ReactiveLoad;
import com.powerfactor.chem.OreMetal;
import com.powerfactor.device.HydrogenTanks;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.multiblock.CasingBlock;
import com.powerfactor.multiblock.CasingBlockEntity;
import com.powerfactor.multiblock.Structure;
import com.powerfactor.registry.ModWash;
import com.powerfactor.scada.ScadaReading;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import org.jspecify.annotations.Nullable;

/**
 * Wash Plant: a placer-mine wash line. Ore goes into the feed hopper, tumbles through the rotating
 * trommel under spray bars, the fines wash down a six-block riffled sluice where the heavy concentrate
 * settles, and the shaker table (this controller) separates it. One raw ore becomes four concentrate
 * (each smelts to an ingot) with placer byproducts; gravel and sand pan out gold. Costs 1,000 mB of
 * water and two seconds at 400 FE/t per load.
 */
public class WashPlantBlockEntity extends MachineBlockEntity implements com.powerfactor.upgrade.Upgradeable, ReactiveLoad, com.powerfactor.api.MachineAudio.Looping, com.powerfactor.multiblock.Buildable {
    public static final int CAPACITY = 1_000_000, MAX_INPUT = 8_000, FE_PER_TICK = 400, TIME = 40, TANK = 32_000, WATER_PER = 1_000;
    public static final int INPUTS = 4, OUTPUTS = 6, YIELD = 4;
    public static final int DATA_PROGRESS = 0, DATA_WATER = 1, DATA_ENERGY = 2, DATA_STATE = 3, DATA_METAL = 4, DATA_FORMED = 5, DATA_WASHED = 6,
            DATA_COUNT = 7;
    public static final int STATE_IDLE = 0, STATE_RUNNING = 1, STATE_NO_POWER = 2, STATE_FULL = 3, STATE_NO_WATER = 4, STATE_UNFORMED = 5;

    /**
     * Facing north (the shaker table at the front): the sluice runs six blocks back on legs, the trommel
     * sits on the back half of it, and the feed hopper tops a frame column at the far end.
     */
    public static final Structure STRUCTURE = new Structure.Builder()
            .key('F', ModWash.WASH_FRAME)
            .key('S', ModWash.SLUICE_BOX)
            .key('T', ModWash.TROMMEL)
            .key('H', ModWash.FEED_HOPPER)
            .layer(0, ".C.", ".S.", ".S.", ".S.", "FSF", ".S.", "FSF", ".F.")
            .layer(1, "...", "...", "...", "...", ".T.", ".T.", ".T.", ".F.")
            .layer(2, "...", "...", "...", "...", "...", "...", "...", ".H.")
            .build();

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CAPACITY, MAX_INPUT, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private final HydrogenTanks water = new HydrogenTanks(TANK, new net.minecraft.world.level.material.Fluid[] {Fluids.WATER}, new boolean[] {false},
            this::setChanged);
    private final SimpleContainer slots = new SimpleContainer(INPUTS + OUTPUTS) {
        @Override
        public boolean canPlaceItem(int index, ItemStack stack) {
            return index < INPUTS && washable(stack);
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(WashPlantBlockEntity.this, player);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            WashPlantBlockEntity.this.setChanged();
        }
    };
    private final ResourceHandler<ItemResource> items = new net.neoforged.neoforge.transfer.DelegatingResourceHandler<>(VanillaContainerWrapper.of(slots)) {
        @Override
        public int insert(int index, ItemResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
            return index < INPUTS ? super.insert(index, resource, amount, tx) : 0;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
            return index >= INPUTS ? super.extract(index, resource, amount, tx) : 0;
        }
    };
    private final Set<BlockPos> linked = new HashSet<>();
    private int progress, state, metal = -1, synced = -1;
    private long washed;
    private boolean formed;
    /** Client view: running, the metal being washed (-1 gravel), and loads washed (drives the riffle glitter). */
    private boolean clientRunning, clientFormed;
    private int clientMetal = -1;
    private long clientWashed;

    private final com.powerfactor.upgrade.UpgradeInventory upgrades = new com.powerfactor.upgrade.UpgradeInventory(
            java.util.EnumSet.of(com.powerfactor.upgrade.UpgradeType.SPEED, com.powerfactor.upgrade.UpgradeType.EFFICIENCY), this::setChanged,
            p -> Container.stillValidBlockEntity(this, p));
    /** Fractional progress: Speed cards add more than one tick of work per tick. */
    private float work;

    public com.powerfactor.upgrade.UpgradeInventory upgrades() {
        return upgrades;
    }

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case DATA_PROGRESS -> progress;
                case DATA_WATER -> water.amount(0);
                case DATA_ENERGY -> energy.getAmountAsInt() / 100;
                case DATA_STATE -> state;
                case DATA_METAL -> metal + 1;
                case DATA_FORMED -> formed ? 1 : 0;
                case DATA_WASHED -> (int) (washed & 0x7FFF);
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public WashPlantBlockEntity(BlockPos pos, BlockState state) {
        super(ModWash.WASH_PLANT_BE.get(), pos, state);
    }

    public static boolean washable(ItemStack stack) {
        return OreMetal.of(stack) != null || stack.is(Items.GRAVEL) || stack.is(Items.SAND) || stack.is(Items.RED_SAND);
    }

    public SimpleEnergyHandler getEnergy() {
        return energy;
    }

    public HydrogenTanks getWater() {
        return water;
    }

    public ResourceHandler<ItemResource> getItems() {
        return items;
    }

    public SimpleContainer slots() {
        return slots;
    }

    @Override
    public Structure buildStructure() {
        return STRUCTURE;
    }

    public Direction facingDir() {
        return getBlockState().getValue(MachineBlock.FACING);
    }

    private int nextInput() {
        for (int i = 0; i < INPUTS; i++) if (!slots.getItem(i).isEmpty()) return i;
        return -1;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState st) {
        if (level.getGameTime() % 20 == 0) checkStructure(level, pos, st);
        int in = nextInput();
        OreMetal m = in < 0 ? null : OreMetal.of(slots.getItem(in));
        metal = m == null ? -1 : m.ordinal();
        boolean room = in >= 0 && hasRoom(m);
        var fx = upgrades.effects();
        int fe = (int) Math.ceil(FE_PER_TICK * fx.powerMultiplier());
        state = !formed ? STATE_UNFORMED : in < 0 ? STATE_IDLE : water.amount(0) < WATER_PER ? STATE_NO_WATER : !room ? STATE_FULL
                : energy.getAmountAsInt() < fe ? STATE_NO_POWER : STATE_RUNNING;
        boolean on = state == STATE_RUNNING && enabled(level, pos);
        if (in < 0) progress = 0;
        if (in < 0) work = 0;
        if (on) {
            energy.set(energy.getAmountAsInt() - fe);
            work += (float) fx.speedMultiplier();
            progress = (int) work;
            if (work >= TIME) {
                work = 0;
                progress = 0;
                wash(level.getRandom(), in, m);
                if (washed % 4 == 0) level.playSound(null, pos, SoundEvents.GRAVEL_HIT, SoundSource.BLOCKS, 0.6F, 0.8F);
            }
            if (level.getGameTime() % 30 == 0) level.playSound(null, pos, SoundEvents.WATER_AMBIENT, SoundSource.BLOCKS, 0.25F, 1.4F);
        }
        setRunning(level, pos, st, on);
        int sync = (on ? 1 : 0) | (formed ? 2 : 0) | (metal + 1) << 2 | (int) (washed & 0xFF) << 6;
        if (sync != synced) {
            synced = sync;
            level.sendBlockUpdated(pos, st, st, Block.UPDATE_CLIENTS);
        }
    }

    /** Outputs must take a full load (the concentrate and the most byproducts one load can make). */
    private boolean hasRoom(@Nullable OreMetal m) {
        int free = 0;
        Item main = m == null ? Items.GOLD_NUGGET : ModWash.CONCENTRATES.get(m).get();
        int room = 0;
        for (int i = INPUTS; i < INPUTS + OUTPUTS; i++) {
            ItemStack s = slots.getItem(i);
            if (s.isEmpty()) free++;
            else if (s.is(main)) room += s.getMaxStackSize() - s.getCount();
        }
        return free >= 2 || free >= 1 && room >= YIELD;
    }

    private void wash(RandomSource r, int in, @Nullable OreMetal m) {
        slots.getItem(in).shrink(1);
        water.add(0, -WATER_PER);
        washed++;
        if (m != null) {
            merge(new ItemStack(ModWash.CONCENTRATES.get(m).get(), YIELD));
            // Placer byproducts: the heavies a sluice catches alongside the ore.
            if (r.nextFloat() < 0.20F) merge(new ItemStack(Items.GOLD_NUGGET, 1 + r.nextInt(3)));
            if (r.nextFloat() < 0.08F) merge(new ItemStack(Items.FLINT));
            if (r.nextFloat() < 0.03F) merge(new ItemStack(Items.LAPIS_LAZULI));
            if (r.nextFloat() < 0.006F) merge(new ItemStack(Items.DIAMOND));
            if (r.nextFloat() < 0.004F) merge(new ItemStack(Items.EMERALD));
        } else {
            // Gravel and sand: colour in the pan.
            if (r.nextFloat() < 0.30F) merge(new ItemStack(Items.GOLD_NUGGET, 1 + r.nextInt(2)));
            if (r.nextFloat() < 0.10F) merge(new ItemStack(Items.IRON_NUGGET, 1 + r.nextInt(3)));
            if (r.nextFloat() < 0.15F) merge(new ItemStack(Items.FLINT));
            if (r.nextFloat() < 0.002F) merge(new ItemStack(Items.DIAMOND));
        }
    }

    private void merge(ItemStack stack) {
        for (int i = INPUTS; i < INPUTS + OUTPUTS && !stack.isEmpty(); i++) {
            ItemStack s = slots.getItem(i);
            if (ItemStack.isSameItemSameComponents(s, stack)) {
                int move = Math.min(stack.getCount(), s.getMaxStackSize() - s.getCount());
                s.grow(move);
                stack.shrink(move);
            }
        }
        for (int i = INPUTS; i < INPUTS + OUTPUTS && !stack.isEmpty(); i++) {
            if (slots.getItem(i).isEmpty()) {
                slots.setItem(i, stack.copy());
                stack.setCount(0);
            }
        }
        slots.setChanged();
    }

    private void checkStructure(ServerLevel level, BlockPos pos, BlockState state) {
        boolean now = STRUCTURE.complete(level, pos, facingDir());
        Set<BlockPos> parts = new HashSet<>();
        if (now) for (var p : STRUCTURE.parts()) parts.add(STRUCTURE.worldPos(pos, facingDir(), p));
        for (BlockPos p : linked) {
            if (!parts.contains(p) && level.getBlockEntity(p) instanceof CasingBlockEntity c && pos.equals(c.controllerPos())) c.link(null);
        }
        for (BlockPos p : parts) if (level.getBlockEntity(p) instanceof CasingBlockEntity c) c.link(pos);
        linked.clear();
        linked.addAll(parts);
        if (now != formed) {
            formed = now;
            level.playSound(null, pos, now ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1F, 1.2F);
        }
        if (state.getValue(CasingBlock.FORMED) != now) level.setBlock(pos, state.setValue(CasingBlock.FORMED, now), Block.UPDATE_ALL);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && !level.isClientSide()) {
            for (BlockPos p : linked) {
                if (level.getBlockEntity(p) instanceof CasingBlockEntity c && worldPosition.equals(c.controllerPos())) c.link(null);
            }
            net.minecraft.world.Containers.dropContents(level, pos, slots);
            net.minecraft.world.Containers.dropContents(level, pos, upgrades);
        }
    }

    @Override
    public boolean soundActive() {
        return clientRunning;
    }

    @Override
    public net.minecraft.sounds.SoundEvent loopSound() {
        return com.powerfactor.registry.ModSounds.WASH_PLANT_RUN.get();
    }

    @Override
    public double eeDemand() {
        return isRunning() ? FE_PER_TICK * 0.55 : 0;
    }

    public boolean clientRunning() {
        return clientRunning;
    }

    public boolean clientFormed() {
        return clientFormed;
    }

    public @Nullable OreMetal clientMetal() {
        return clientMetal < 0 ? null : OreMetal.values()[clientMetal];
    }

    public long clientWashed() {
        return clientWashed;
    }

    @Override
    public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), isRunning() ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF,
                isRunning() ? FE_PER_TICK : 0, 0, Component.translatable("gui.powerfactor.wash.scada", washed), true, remoteEnabled(), false, 0, 0);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new WashPlantMenu(id, inventory, this, slots, data);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        water.deserialize(input.childOrEmpty("water"));
        slots.clearContent();
        ContainerHelper.loadAllItems(input, slots.getItems());
        progress = input.getIntOr("progress", 0);
        work = progress;
        upgrades.load(input);
        washed = input.getLongOr("washed", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        water.serialize(output.child("water"));
        ContainerHelper.saveAllItems(output, slots.getItems());
        output.putInt("progress", progress);
        upgrades.save(output);
        output.putLong("washed", washed);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putBoolean("running", state == STATE_RUNNING);
        tag.putBoolean("formed", formed);
        tag.putInt("metal", metal);
        tag.putLong("washed", washed);
        return tag;
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        clientRunning = input.getBooleanOr("running", false);
        clientFormed = input.getBooleanOr("formed", false);
        clientMetal = input.getIntOr("metal", -1);
        clientWashed = input.getLongOr("washed", 0);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ValueInput input) {
        handleUpdateTag(input);
    }
}
