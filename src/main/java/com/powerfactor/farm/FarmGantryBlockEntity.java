package com.powerfactor.farm;

import com.powerfactor.device.HydrogenTanks;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.registry.ModFarm;
import com.powerfactor.scada.ScadaReading;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.StemBlock;
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
 * Farm Gantry: an overhead crane over the 9x9 field in front of it. The bridge rolls along the side rails
 * and the carriage along the bridge, visiting every square in a snake. At each one the tool head drops:
 * it tills dirt or grass into farmland and sows it, plants a seed into empty farmland, or cuts a
 * ripe crop (and melons and pumpkins off their stems), keeping a seed back to replant. With water in its
 * tank it irrigates every square it passes. 60 FE per job; moving costs 10 FE/t.
 */
public class FarmGantryBlockEntity extends MachineBlockEntity {
    public static final int SIZE = 9, HALF = 4, SEEDS = 9, OUT = 9, CAPACITY = 400_000, MAX_INPUT = 4_000, TANK = 8_000;
    public static final int FE_MOVE = 10, FE_JOB = 60, DWELL = 8, WATER_PER = 10;
    public static final float SPEED = 0.2F;
    public static final int JOB_NONE = 0, JOB_TILL = 1, JOB_PLANT = 2, JOB_HARVEST = 3;
    public static final int DATA_CELL = 0, DATA_ENERGY = 1, DATA_WATER = 2, DATA_HARVESTED = 3, DATA_STATE = 4, DATA_COUNT = 5;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CAPACITY, MAX_INPUT, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private final HydrogenTanks water = new HydrogenTanks(TANK, new net.minecraft.world.level.material.Fluid[] {Fluids.WATER}, new boolean[] {false}, this::setChanged);
    private final SimpleContainer slots = new SimpleContainer(SEEDS + OUT) {
        @Override
        public boolean canPlaceItem(int index, ItemStack stack) {
            return index < SEEDS && plantable(stack);
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(FarmGantryBlockEntity.this, player);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            FarmGantryBlockEntity.this.setChanged();
        }
    };
    private final ResourceHandler<ItemResource> items = new net.neoforged.neoforge.transfer.DelegatingResourceHandler<>(VanillaContainerWrapper.of(slots)) {
        @Override
        public int insert(int index, ItemResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
            return index < SEEDS ? super.insert(index, resource, amount, tx) : 0;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
            return index >= SEEDS ? super.extract(index, resource, amount, tx) : 0;
        }
    };
    /** Head position in field cells (u across, v forward from the gantry), the cell it's heading for, and the dwell timer. */
    private float headU = 0, headV = 0;
    private int cell, dwell, job, harvested, state;
    private int clientJob;
    private float clientU, clientV, clientTargetU, clientTargetV;
    private int clientDwell;
    private long clientStamp;
    private boolean clientOn;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case DATA_CELL -> cell;
                case DATA_ENERGY -> energy.getAmountAsInt() / 100;
                case DATA_WATER -> water.amount(0);
                case DATA_HARVESTED -> harvested & 0x7FFF;
                case DATA_STATE -> state;
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

    public FarmGantryBlockEntity(BlockPos pos, BlockState state) {
        super(ModFarm.FARM_GANTRY_BE.get(), pos, state);
    }

    /** For tests and the SCADA tooltip: where the head is and what it's doing. */
    public String debugState() {
        return "cell=" + cell + " head=(" + headU + "," + headV + ") dwell=" + dwell + " job=" + job + " fe=" + energy.getAmountAsInt() + " state=" + state;
    }

    public static boolean plantable(ItemStack stack) {
        return stack.getItem() instanceof BlockItem b && (b.getBlock() instanceof CropBlock || b.getBlock() instanceof StemBlock);
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
    protected Container inventory() {
        return slots;
    }

    public Direction facing() {
        return getBlockState().getValue(MachineBlock.FACING);
    }

    /**
     * The ground block of field cell (u, v): u from -HALF..HALF across, v from 1..SIZE out in front, one
     * below the gantry (it stands on the surface; crops grow level with it).
     */
    public BlockPos cellPos(int u, int v) {
        Direction f = facing();
        Direction right = f.getClockWise();
        return worldPosition.relative(f, v).relative(right, u).below();
    }

    /** Snake order: row by row away from the gantry, alternating direction. */
    public static int[] cellUV(int index) {
        int row = index / SIZE, col = index % SIZE;
        int u = (row % 2 == 0 ? col : SIZE - 1 - col) - HALF;
        return new int[] {u, row + 1};
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState st) {
        boolean on = enabled(level, pos) && energy.getAmountAsInt() >= FE_JOB;
        state = on ? 1 : 0;
        if (on) {
            int[] target = cellUV(cell);
            if (dwell > 0) {
                if (--dwell == 0) {
                    job = JOB_NONE;
                    cell = (cell + 1) % (SIZE * SIZE);
                    sync(level, st);
                }
            } else {
                float du = target[0] - headU, dv = target[1] - headV;
                float d = (float) Math.sqrt(du * du + dv * dv);
                if (d > SPEED) {
                    headU += du / d * SPEED;
                    headV += dv / d * SPEED;
                    energy.set(energy.getAmountAsInt() - FE_MOVE);
                } else {
                    headU = target[0];
                    headV = target[1];
                    job = work(level, cellPos(target[0], target[1]));
                    if (job != JOB_NONE) energy.set(energy.getAmountAsInt() - FE_JOB);
                    dwell = job == JOB_NONE ? 1 : DWELL;
                    sync(level, st);
                }
            }
        }
        setRunning(level, pos, st, on);
    }

    private void sync(ServerLevel level, BlockState st) {
        level.sendBlockUpdated(worldPosition, st, st, Block.UPDATE_CLIENTS);
    }

    /** Does one job at the square whose ground is {@code ground}; returns which. */
    private int work(ServerLevel level, BlockPos ground) {
        BlockPos above = ground.above();
        BlockState soil = level.getBlockState(ground), crop = level.getBlockState(above);
        if (water.amount(0) >= WATER_PER && soil.getBlock() instanceof FarmlandBlock && soil.getValue(FarmlandBlock.MOISTURE) < FarmlandBlock.MAX_MOISTURE) {
            level.setBlock(ground, soil.setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE), Block.UPDATE_CLIENTS);
            water.add(0, -WATER_PER);
        }
        // Harvest: ripe crops (replanted from their own drops), and melons/pumpkins grown beside their stems.
        if (crop.getBlock() instanceof CropBlock c && c.isMaxAge(crop) || crop.is(Blocks.MELON) || crop.is(Blocks.PUMPKIN)) {
            List<ItemStack> drops = Block.getDrops(crop, level, above, null);
            boolean replant = crop.getBlock() instanceof CropBlock;
            level.levelEvent(2001, above, Block.getId(crop));
            level.setBlock(above, replant ? crop.getBlock().defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            if (replant) {
                // The replanted seed comes out of the harvest.
                ItemStack seed = new ItemStack(crop.getBlock().asItem());
                for (ItemStack d : drops) {
                    if (!seed.isEmpty() && ItemStack.isSameItem(d, seed)) {
                        d.shrink(1);
                        seed = ItemStack.EMPTY;
                    }
                }
            }
            for (ItemStack d : drops) if (!d.isEmpty()) output(level, d);
            harvested++;
            return JOB_HARVEST;
        }
        // Till: dirt or grass with air above becomes farmland, and is sown in the same drop of the head.
        boolean tilled = false;
        if ((soil.is(Blocks.DIRT) || soil.is(Blocks.GRASS_BLOCK) || soil.is(Blocks.DIRT_PATH) || soil.is(Blocks.COARSE_DIRT)) && crop.isAir()) {
            soil = Blocks.FARMLAND.defaultBlockState();
            level.setBlock(ground, soil, Block.UPDATE_ALL);
            level.playSound(null, ground, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 0.8F, 1F);
            tilled = true;
        }
        // Plant: empty farmland and a seed in the hopper.
        if (soil.getBlock() instanceof FarmlandBlock && crop.isAir()) {
            for (int i = 0; i < SEEDS; i++) {
                ItemStack s = slots.getItem(i);
                if (plantable(s)) {
                    BlockState plant = ((BlockItem) s.getItem()).getBlock().defaultBlockState();
                    if (!plant.canSurvive(level, above)) continue;
                    level.setBlock(above, plant, Block.UPDATE_ALL);
                    s.shrink(1);
                    level.playSound(null, above, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 0.8F, 1F);
                    return JOB_PLANT;
                }
            }
        }
        if (tilled) return JOB_TILL;
        return JOB_NONE;
    }

    private void output(ServerLevel level, ItemStack stack) {
        // Seeds go back into the seed slots first; everything else to the output.
        if (plantable(stack)) {
            for (int i = 0; i < SEEDS && !stack.isEmpty(); i++) stack = insert(i, stack);
        }
        for (int i = SEEDS; i < SEEDS + OUT && !stack.isEmpty(); i++) stack = insert(i, stack);
        if (!stack.isEmpty()) net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX() + .5, worldPosition.getY() + 1, worldPosition.getZ() + .5, stack);
    }

    private ItemStack insert(int i, ItemStack stack) {
        ItemStack s = slots.getItem(i);
        if (s.isEmpty()) {
            slots.setItem(i, stack.copy());
            return ItemStack.EMPTY;
        }
        if (ItemStack.isSameItemSameComponents(s, stack)) {
            int move = Math.min(stack.getCount(), s.getMaxStackSize() - s.getCount());
            s.grow(move);
            stack.shrink(move);
        }
        return stack;
    }

    // ------------------------------------------------------------------ client view (the renderer moves the head itself between syncs)

    /** Head (u, v) at render time: from the synced position toward the synced target at the machine's speed. */
    public float[] clientHead(float partial) {
        if (level == null) return new float[] {0, 1, 0};
        float elapsed = (level.getGameTime() - clientStamp) + partial;
        float du = clientTargetU - clientU, dv = clientTargetV - clientV;
        float d = (float) Math.sqrt(du * du + dv * dv);
        float f = !clientOn || d < 1e-3 ? 1 : Math.min(1, elapsed * SPEED / d);
        // The tool head drops while it works the square.
        float dip = clientJob != JOB_NONE && f >= 1 ? (float) Math.sin(Math.min(1, elapsed / DWELL) * Math.PI) : 0;
        return new float[] {clientU + du * f, clientV + dv * f, dip};
    }

    public int clientJob() {
        return clientJob;
    }

    public boolean clientOn() {
        return clientOn;
    }

    @Override
    public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), isRunning() ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF, isRunning() ? FE_MOVE : 0, 0,
                Component.translatable("gui.powerfactor.farm.scada", harvested), true, remoteEnabled(), false, 0, 0);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new FarmGantryMenu(id, inventory, this, slots, data);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        water.deserialize(input.childOrEmpty("water"));
        slots.clearContent();
        ContainerHelper.loadAllItems(input, slots.getItems());
        cell = Math.clamp(input.getIntOr("cell", 0), 0, SIZE * SIZE - 1);
        harvested = input.getIntOr("harvested", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        water.serialize(output.child("water"));
        ContainerHelper.saveAllItems(output, slots.getItems());
        output.putInt("cell", cell);
        output.putInt("harvested", harvested);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        int[] target = cellUV(cell);
        tag.putFloat("u", headU);
        tag.putFloat("v", headV);
        tag.putFloat("tu", dwell > 0 ? headU : target[0]);
        tag.putFloat("tv", dwell > 0 ? headV : target[1]);
        tag.putInt("job", job);
        tag.putBoolean("on", state == 1);
        return tag;
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        // Continue from where the head is now drawn, so a sync never makes it jump.
        float[] now = clientHead(0);
        clientU = clientStamp == 0 ? input.getFloatOr("u", 0) : now[0];
        clientV = clientStamp == 0 ? input.getFloatOr("v", 1) : now[1];
        clientTargetU = input.getFloatOr("tu", 0);
        clientTargetV = input.getFloatOr("tv", 1);
        clientJob = input.getIntOr("job", 0);
        clientOn = input.getBooleanOr("on", false);
        clientStamp = level == null ? 1 : level.getGameTime();
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ValueInput input) {
        handleUpdateTag(input);
    }
}
