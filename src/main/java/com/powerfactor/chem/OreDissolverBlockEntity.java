package com.powerfactor.chem;

import com.powerfactor.api.ReactiveLoad;
import com.powerfactor.device.HydrogenTanks;
import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.registry.ModChem;
import com.powerfactor.registry.ModFluids;
import com.powerfactor.scada.ScadaReading;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import org.jspecify.annotations.Nullable;

/**
 * Ore Dissolver: leaches raw ore in hot sulfuric acid and grows the metal back out as crystals. One raw
 * ore (or ore block) and 250 mB acid make four crystals; with a Quantum Catalyst in the catalyst slot,
 * five. Crystals crush to dust: 4x (5x) ore output, against 2x crushing and 3x washing.
 */
public class OreDissolverBlockEntity extends MachineBlockEntity implements com.powerfactor.upgrade.Upgradeable, ReactiveLoad, com.powerfactor.api.MachineAudio.Looping {
    public static final int CAPACITY = 400_000, MAX_INPUT = 4_000, FE_PER_TICK = 160, TIME = 200, TANK = 8_000, ACID_PER = 250;
    public static final int SLOT_ORE = 0, SLOT_CATALYST = 1, SLOT_OUT = 2;
    public static final int DATA_PROGRESS = 0, DATA_ACID = 1, DATA_ENERGY = 2, DATA_STATE = 3, DATA_METAL = 4, DATA_COUNT = 5;
    public static final int STATE_IDLE = 0, STATE_RUNNING = 1, STATE_NO_POWER = 2, STATE_FULL = 3, STATE_NO_ACID = 4;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CAPACITY, MAX_INPUT, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private final HydrogenTanks tank = new HydrogenTanks(TANK, new net.minecraft.world.level.material.Fluid[] {ModFluids.SULFURIC_ACID.source().get()},
            new boolean[] {false}, this::setChanged);
    private final SimpleContainer slots = new SimpleContainer(3) {
        @Override
        public boolean canPlaceItem(int index, ItemStack stack) {
            return index == SLOT_ORE ? OreMetal.of(stack) != null : index == SLOT_CATALYST && stack.is(ModChem.QUANTUM_CATALYST.get());
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(OreDissolverBlockEntity.this, player);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            OreDissolverBlockEntity.this.setChanged();
        }
    };
    /** Automation: ore and catalyst go in, crystals come out. */
    private final ResourceHandler<ItemResource> items = new net.neoforged.neoforge.transfer.DelegatingResourceHandler<>(VanillaContainerWrapper.of(slots)) {
        @Override
        public int insert(int index, ItemResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
            return index == SLOT_OUT ? 0 : super.insert(index, resource, amount, tx);
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
            return index == SLOT_OUT ? super.extract(index, resource, amount, tx) : 0;
        }
    };
    private int progress, state, metal = -1, synced = -1;
    private int clientMetal = -1, clientProgress, clientAcid;
    private boolean clientCatalyst;

    private final com.powerfactor.upgrade.UpgradeInventory upgrades = new com.powerfactor.upgrade.UpgradeInventory(
            java.util.EnumSet.of(com.powerfactor.upgrade.UpgradeType.SPEED, com.powerfactor.upgrade.UpgradeType.EFFICIENCY), this::setChanged,
            p -> Container.stillValidBlockEntity(this, p));
    /** Fractional progress: Speed cards add more than one tick of work per tick. */
    private float work;

    public com.powerfactor.upgrade.UpgradeInventory upgrades() {
        return upgrades;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && !level.isClientSide()) net.minecraft.world.Containers.dropContents(level, pos, upgrades);
    }

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case DATA_PROGRESS -> progress;
                case DATA_ACID -> tank.amount(0);
                case DATA_ENERGY -> energy.getAmountAsInt() / 100;
                case DATA_STATE -> state;
                case DATA_METAL -> metal + 1;
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

    public OreDissolverBlockEntity(BlockPos pos, BlockState state) {
        super(ModChem.ORE_DISSOLVER_BE.get(), pos, state);
    }

    public SimpleEnergyHandler getEnergy() {
        return energy;
    }

    public HydrogenTanks getTank() {
        return tank;
    }

    public ResourceHandler<ItemResource> getItems() {
        return items;
    }

    public SimpleContainer slots() {
        return slots;
    }

    @Override
    protected Container inventory() {
        return slots;
    }

    public int crystalYield() {
        return slots.getItem(SLOT_CATALYST).is(ModChem.QUANTUM_CATALYST.get()) ? 5 : 4;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState st) {
        OreMetal m = OreMetal.of(slots.getItem(SLOT_ORE));
        metal = m == null ? -1 : m.ordinal();
        boolean room = false;
        ItemStack product = ItemStack.EMPTY;
        if (m != null) {
            product = new ItemStack(ModChem.CRYSTALS.get(m).get(), crystalYield());
            ItemStack out = slots.getItem(SLOT_OUT);
            room = out.isEmpty() || ItemStack.isSameItemSameComponents(out, product) && out.getCount() + product.getCount() <= out.getMaxStackSize();
        }
        var fx = upgrades.effects();
        int fe = (int) Math.ceil(FE_PER_TICK * fx.powerMultiplier());
        state = m == null ? STATE_IDLE : tank.amount(0) < ACID_PER ? STATE_NO_ACID : !room ? STATE_FULL
                : energy.getAmountAsInt() < fe ? STATE_NO_POWER : STATE_RUNNING;
        boolean on = state == STATE_RUNNING && enabled(level, pos);
        if (m == null) progress = 0;
        if (m == null) work = 0;
        if (on) {
            energy.set(energy.getAmountAsInt() - fe);
            work += (float) fx.speedMultiplier();
            progress = (int) work;
            if (work >= TIME) {
                work = 0;
                progress = 0;
                slots.getItem(SLOT_ORE).shrink(1);
                tank.add(0, -ACID_PER);
                ItemStack catalyst = slots.getItem(SLOT_CATALYST);
                if (!catalyst.isEmpty()) {
                    catalyst.setDamageValue(catalyst.getDamageValue() + 1);
                    if (catalyst.getDamageValue() >= catalyst.getMaxDamage()) slots.setItem(SLOT_CATALYST, ItemStack.EMPTY);
                }
                ItemStack out = slots.getItem(SLOT_OUT);
                if (out.isEmpty()) slots.setItem(SLOT_OUT, product);
                else out.grow(product.getCount());
            }
        }
        setRunning(level, pos, st, on);
        int sync = ((metal + 1) * 32 + progress * 31 / TIME) * 4 + (slots.getItem(SLOT_CATALYST).isEmpty() ? 0 : 2) + (tank.amount(0) > 0 ? 1 : 0);
        if (sync != synced && (level.getGameTime() % 4 == 0 || metal != clientMetalSent)) {
            synced = sync;
            clientMetalSent = metal;
            level.sendBlockUpdated(pos, st, st, Block.UPDATE_CLIENTS);
        }
    }

    private int clientMetalSent = -2;

    @Override
    public boolean soundActive() {
        return isRunning();
    }

    @Override
    public net.minecraft.sounds.SoundEvent loopSound() {
        return com.powerfactor.registry.ModSounds.ORE_DISSOLVER_RUN.get();
    }

    @Override
    public double eeDemand() {
        return isRunning() ? FE_PER_TICK * 0.5 : 0;
    }

    public @Nullable OreMetal clientMetal() {
        return clientMetal < 0 ? null : OreMetal.values()[clientMetal];
    }

    public float clientProgress() {
        return clientProgress / (float) TIME;
    }

    public float clientAcid() {
        return clientAcid / (float) TANK;
    }

    public boolean clientCatalyst() {
        return clientCatalyst;
    }

    @Override
    public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), isRunning() ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF,
                isRunning() ? FE_PER_TICK : 0, 0, Component.translatable("gui.powerfactor.dissolver.scada", tank.amount(0)), true, remoteEnabled(), false, 0, 0);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new OreDissolverMenu(id, inventory, this, slots, data);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        tank.deserialize(input.childOrEmpty("tank"));
        slots.clearContent();
        ContainerHelper.loadAllItems(input, slots.getItems());
        progress = input.getIntOr("progress", 0);
        work = progress;
        upgrades.load(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        tank.serialize(output.child("tank"));
        ContainerHelper.saveAllItems(output, slots.getItems());
        output.putInt("progress", progress);
        upgrades.save(output);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("metal", metal);
        tag.putInt("progress", progress);
        tag.putInt("acid", tank.amount(0));
        tag.putBoolean("catalyst", !slots.getItem(SLOT_CATALYST).isEmpty());
        return tag;
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        clientMetal = input.getIntOr("metal", -1);
        clientProgress = input.getIntOr("progress", 0);
        clientAcid = input.getIntOr("acid", 0);
        clientCatalyst = input.getBooleanOr("catalyst", false);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ValueInput input) {
        handleUpdateTag(input);
    }
}
