package com.powerfactor.quantum;

import com.powerfactor.api.ReactiveLoad;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.registry.ModQuantum;
import com.powerfactor.scada.ScadaReading;
import com.powerfactor.storage.ItemIO;
import com.powerfactor.storage.NodeBlockEntity;
import com.powerfactor.storage.StorageNetwork;
import com.powerfactor.upgrade.UpgradeItem;
import com.powerfactor.upgrade.UpgradeType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * An area miner. The Industrial Quarry runs a laser gantry over a square in front of it; the Quantum
 * Quarry folds space instead: no frame, many blocks per tick, a far bigger square, and cheaper per block.
 * Mining works top-down, layer by layer, to just above the world floor. Products go into an adjacent
 * storage network first, then any adjacent inventory; when nowhere has room the quarry waits.
 *
 * <p>Energy per block follows QuarryPlus's well-tested model: 25 FE times hardness, times 1.585 per
 * Fortune level, times four for Silk Touch.
 */
public class QuarryBlockEntity extends MachineBlockEntity implements ReactiveLoad {
    public enum Tier {
        INDUSTRIAL(9, 33, 4, 1_000_000, 50_000, 1.0, 1),
        QUANTUM(9, 65, 1, 20_000_000, 2_000_000, 0.6, 4);

        public final int minSize, maxSize, interval, capacity, maxInput, perBlockScale;
        public final double costScale;

        Tier(int minSize, int maxSize, int interval, int capacity, int maxInput, double costScale, int perBlock) {
            this.minSize = minSize;
            this.maxSize = maxSize;
            this.interval = interval;
            this.capacity = capacity;
            this.maxInput = maxInput;
            this.costScale = costScale;
            this.perBlockScale = perBlock;
        }
    }

    public enum Status { IDLE, MINING, NO_POWER, OUTPUT_FULL, DONE, DISABLED, WAITING_CHUNKS }

    public static final int DATA_STATUS = 0, DATA_SIZE = 1, DATA_DEPTH = 2, DATA_TOTAL_DEPTH = 3, DATA_FLAGS = 4, DATA_FORTUNE = 5,
            DATA_ENERGY_HI = 6, DATA_ENERGY_LO = 7, DATA_FE = 8, DATA_MINED_HI = 9, DATA_MINED_LO = 10, DATA_TIER = 11, DATA_COUNT = 12;
    public static final int FLAG_SILK = 1, FLAG_VOID = 2;
    public static final int UPGRADES = 4, BUFFER = 18;

    private final Tier tier;
    private final SimpleEnergyHandler energy;
    private final SimpleContainer upgrades = new SimpleContainer(UPGRADES) {
        @Override
        public boolean canPlaceItem(int index, ItemStack stack) {
            return stack.getItem() instanceof UpgradeItem u && u.type() == UpgradeType.SPEED;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            QuarryBlockEntity.this.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(QuarryBlockEntity.this, player);
        }
    };
    private final List<ItemStack> buffer = new ArrayList<>();
    private int size;
    private boolean silk, voidStone = true;
    private int fortune;
    /** Next position: layer y and index within the layer. */
    private int cursorY = Integer.MIN_VALUE, cursorIndex;
    private Status status = Status.IDLE;
    private long mined;
    private int lastFe;
    /** Synced to clients for the renderer: the block being mined. */
    private @Nullable BlockPos target;
    private long lastSync;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case DATA_STATUS -> status.ordinal();
                case DATA_SIZE -> size;
                case DATA_DEPTH -> cursorY == Integer.MIN_VALUE ? 0 : top() - cursorY;
                case DATA_TOTAL_DEPTH -> top() - bottom();
                case DATA_FLAGS -> (silk ? FLAG_SILK : 0) | (voidStone ? FLAG_VOID : 0);
                case DATA_FORTUNE -> fortune;
                case DATA_ENERGY_HI -> energy.getAmountAsInt() >>> 16;
                case DATA_ENERGY_LO -> energy.getAmountAsInt() & 0xFFFF;
                case DATA_FE -> Math.min(65535, lastFe);
                case DATA_MINED_HI -> (int) (Math.min(mined, Integer.MAX_VALUE) >>> 16);
                case DATA_MINED_LO -> (int) (mined & 0xFFFF);
                case DATA_TIER -> tier.ordinal();
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

    public QuarryBlockEntity(Tier tier, BlockPos pos, BlockState state) {
        super(tier == Tier.QUANTUM ? ModQuantum.QUANTUM_QUARRY_BE.get() : ModQuantum.QUARRY_BE.get(), pos, state);
        this.tier = tier;
        this.size = tier == Tier.QUANTUM ? 33 : 17;
        this.energy = new SimpleEnergyHandler(tier.capacity, tier.maxInput, 0) {
            @Override
            protected void onEnergyChanged(int previousAmount) {
                setChanged();
            }
        };
    }

    public Tier tier() {
        return tier;
    }

    public SimpleEnergyHandler getEnergy() {
        return energy;
    }

    public Container upgrades() {
        return upgrades;
    }

    // ------------------------------------------------------------------ area

    public Direction facing() {
        return getBlockState().getValue(MachineBlock.FACING);
    }

    /** The near-left corner of the square, which starts one block in front of the quarry. */
    public BlockPos corner() {
        Direction f = facing();
        Direction right = f.getClockWise();
        return worldPosition.relative(f, 1).relative(right, -(size / 2));
    }

    public BlockPos farCorner() {
        Direction f = facing();
        return corner().relative(f, size - 1).relative(f.getClockWise(), size - 1);
    }

    public int size() {
        return size;
    }

    int top() {
        return worldPosition.getY() - 1 + 1;
    }

    int bottom() {
        return level == null ? -63 : level.getMinY() + 1;
    }

    private BlockPos at(int y, int index) {
        Direction f = facing();
        int row = index / size, col = index % size;
        // Snake through rows so the head never jumps across the whole square.
        if ((row & 1) == 1) col = size - 1 - col;
        BlockPos c = corner();
        return new BlockPos(c.getX(), y, c.getZ()).relative(f, row).relative(f.getClockWise(), col);
    }

    // ------------------------------------------------------------------ mining

    private int speedCards() {
        int n = 0;
        for (int i = 0; i < UPGRADES; i++) if (!upgrades.getItem(i).isEmpty()) n++;
        return n;
    }

    /** Blocks per operation and ticks between operations. */
    public int blocksPerOp() {
        return tier.perBlockScale << speedCards();
    }

    public int interval() {
        return Math.max(1, tier.interval >> speedCards());
    }

    public int costFor(BlockState state, BlockPos pos) {
        float hardness = Math.max(0.2F, state.getDestroySpeed(level, pos));
        double cost = 25 * hardness * Math.pow(1.585, fortune) * (silk ? 4 : 1) * tier.costScale;
        return (int) Math.ceil(Math.max(10, cost));
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        lastFe = 0;
        if (cursorY == Integer.MIN_VALUE) cursorY = top();
        flushBuffer(level);
        if (!enabled(level, pos)) {
            status = Status.DISABLED;
        } else if (cursorY < bottom()) {
            status = Status.DONE;
        } else if (buffer.size() >= BUFFER) {
            status = Status.OUTPUT_FULL;
        } else if ((level.getGameTime() + pos.asLong()) % interval() == 0) {
            mine(level);
        }
        setRunning(level, pos, state, status == Status.MINING);
        if (target != null && level.getGameTime() - lastSync >= 2) {
            lastSync = level.getGameTime();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private void mine(ServerLevel level) {
        int budget = blocksPerOp();
        int scanned = 0;
        int layer = size * size;
        ItemStack tool = tool(level);
        while (budget > 0 && scanned < 512 && cursorY >= bottom()) {
            BlockPos p = at(cursorY, cursorIndex);
            if (!level.isLoaded(p)) {
                status = Status.WAITING_CHUNKS;
                return;
            }
            BlockState s = level.getBlockState(p);
            scanned++;
            if (minable(level, p, s)) {
                boolean fluid = !s.getFluidState().isEmpty() && s.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock;
                int cost = fluid ? 20 : costFor(s, p);
                if (energy.getAmountAsInt() < cost) {
                    status = Status.NO_POWER;
                    return;
                }
                energy.set(energy.getAmountAsInt() - cost);
                lastFe += cost;
                if (!fluid) collect(level, p, s, tool);
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                effects(level, p, s);
                target = p;
                mined++;
                budget--;
                if (buffer.size() >= BUFFER) {
                    advance(layer);
                    status = Status.OUTPUT_FULL;
                    return;
                }
            }
            advance(layer);
        }
        status = cursorY < bottom() ? Status.DONE : Status.MINING;
    }

    private void advance(int layer) {
        if (++cursorIndex >= layer) {
            cursorIndex = 0;
            cursorY--;
        }
    }

    private boolean minable(ServerLevel level, BlockPos p, BlockState s) {
        if (s.isAir() || p.equals(worldPosition)) return false;
        if (!s.getFluidState().isEmpty() && s.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) return true;
        if (s.getDestroySpeed(level, p) < 0 || level.getBlockEntity(p) != null) return false;
        return !s.is(BlockTags.WITHER_IMMUNE);
    }

    private ItemStack tool(ServerLevel level) {
        ItemStack pick = new ItemStack(Items.NETHERITE_PICKAXE);
        var enchants = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        if (silk) pick.enchant(enchants.getOrThrow(Enchantments.SILK_TOUCH), 1);
        else if (fortune > 0) pick.enchant(enchants.getOrThrow(Enchantments.FORTUNE), fortune);
        return pick;
    }

    private void collect(ServerLevel level, BlockPos p, BlockState s, ItemStack tool) {
        for (ItemStack drop : Block.getDrops(s, level, p, null, null, tool)) {
            if (drop.isEmpty() || voidStone && isFiller(drop)) continue;
            merge(drop);
        }
    }

    /** Common rubble the quarry can discard: stones, dirt, gravel, sand, netherrack and their kin. */
    public static boolean isFiller(ItemStack s) {
        return s.is(net.neoforged.neoforge.common.Tags.Items.COBBLESTONES) || s.is(Items.STONE) || s.is(Items.DEEPSLATE)
                || s.is(Items.COBBLED_DEEPSLATE) || s.is(Items.DIRT) || s.is(Items.GRAVEL) || s.is(Items.NETHERRACK) || s.is(Items.TUFF)
                || s.is(Items.ANDESITE) || s.is(Items.DIORITE) || s.is(Items.GRANITE) || s.is(Items.CALCITE) || s.is(Items.SAND)
                || s.is(Items.END_STONE) || s.is(Items.BLACKSTONE) || s.is(Items.BASALT);
    }

    private void merge(ItemStack drop) {
        for (ItemStack b : buffer) {
            if (ItemStack.isSameItemSameComponents(b, drop) && b.getCount() < b.getMaxStackSize()) {
                int n = Math.min(drop.getCount(), b.getMaxStackSize() - b.getCount());
                b.grow(n);
                drop.shrink(n);
                if (drop.isEmpty()) return;
            }
        }
        buffer.add(drop.copy());
    }

    private void effects(ServerLevel level, BlockPos p, BlockState s) {
        double x = p.getX() + .5, y = p.getY() + .5, z = p.getZ() + .5;
        if (tier == Tier.QUANTUM) {
            if (level.getRandom().nextInt(3) == 0) level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 6, .3, .3, .3, .05);
            if (level.getRandom().nextInt(12) == 0) {
                level.playSound(null, p, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.4F, 1.5F);
            }
        } else {
            level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, s), x, y, z, 8, .3, .3, .3, .1);
            level.sendParticles(ParticleTypes.LAVA, x, y + .4, z, 1, .1, .1, .1, 0);
            if (level.getRandom().nextInt(4) == 0) level.playSound(null, p, s.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.5F, 0.8F);
        }
    }

    /** Products go to a storage network if one touches the quarry, else to any adjacent inventory. */
    private void flushBuffer(ServerLevel level) {
        if (buffer.isEmpty()) return;
        StorageNetwork network = null;
        List<ResourceHandler<ItemResource>> handlers = new ArrayList<>();
        for (Direction d : Direction.values()) {
            if (d == facing()) continue;
            BlockPos n = worldPosition.relative(d);
            if (level.getBlockEntity(n) instanceof NodeBlockEntity node && node.onlineNetwork() != null) {
                if (network == null) network = node.onlineNetwork();
                continue;
            }
            ResourceHandler<ItemResource> h = ItemIO.handler(level, n, d.getOpposite());
            if (h != null) handlers.add(h);
        }
        for (int i = buffer.size() - 1; i >= 0; i--) {
            ItemStack s = buffer.get(i);
            ItemResource r = ItemResource.of(s);
            if (network != null) s.shrink((int) network.insert(r, s.getCount(), false));
            for (ResourceHandler<ItemResource> h : handlers) {
                if (s.isEmpty()) break;
                s.shrink((int) ItemIO.insert(h, r, s.getCount(), false));
            }
            if (s.isEmpty()) buffer.remove(i);
        }
        setChanged();
    }

    // ------------------------------------------------------------------ controls

    public boolean button(int id) {
        switch (id) {
            case 0 -> setSize(size - 2);
            case 1 -> setSize(size + 2);
            case 2 -> silk = !silk;
            case 3 -> fortune = Math.max(0, fortune - 1);
            case 4 -> fortune = Math.min(tier == Tier.QUANTUM ? 3 : 2, fortune + 1);
            case 5 -> voidStone = !voidStone;
            case 6 -> {
                cursorY = top();
                cursorIndex = 0;
                status = Status.IDLE;
            }
            default -> {
                return redstoneButton(id - 10);
            }
        }
        setChanged();
        return true;
    }

    private void setSize(int s) {
        int clamped = Math.clamp(s, tier.minSize, tier.maxSize);
        if (clamped != size) {
            size = clamped;
            cursorY = top();
            cursorIndex = 0;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ------------------------------------------------------------------ client view

    public @Nullable BlockPos target() {
        return target;
    }

    public Status status() {
        return status;
    }

    @Override
    protected double animationRate() {
        return isRunning() ? 1 : 0.05;
    }

    @Override
    public double eeDemand() {
        // Gantry motors for the industrial tier; the quantum tier's field coils are nearly resistive.
        return tier == Tier.QUANTUM ? lastFe * 0.02 : lastFe * 0.25;
    }

    @Override
    public ScadaReading scadaReading() {
        ScadaReading.Status s = switch (status) {
            case MINING -> ScadaReading.Status.RUNNING;
            case NO_POWER, OUTPUT_FULL, WAITING_CHUNKS -> ScadaReading.Status.WARNING;
            default -> ScadaReading.Status.OFF;
        };
        return new ScadaReading(worldPosition, getDisplayName(), s, lastFe, (int) eeDemand(),
                Component.translatable("scada.powerfactor.quarry", Component.translatable("gui.powerfactor.quarry.status." + status.name().toLowerCase(
                        java.util.Locale.ROOT)), String.format("%,d", mined)), true, remoteEnabled(), false, 1F, 60F);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new QuarryMenu(containerId, inventory, this, upgrades, data);
    }

    @Override
    protected Container inventory() {
        SimpleContainer all = new SimpleContainer(UPGRADES + buffer.size());
        for (int i = 0; i < UPGRADES; i++) all.setItem(i, upgrades.getItem(i));
        for (int i = 0; i < buffer.size(); i++) all.setItem(UPGRADES + i, buffer.get(i));
        return all;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        upgrades.clearContent();
        input.child("upgrades").ifPresent(c -> ContainerHelper.loadAllItems(c, upgrades.getItems()));
        buffer.clear();
        input.read("buffer", ItemStack.CODEC.listOf()).ifPresent(l -> l.forEach(s -> buffer.add(s.copy())));
        size = Math.clamp(input.getIntOr("size", size), tier.minSize, tier.maxSize);
        silk = input.getBooleanOr("silk", false);
        voidStone = input.getBooleanOr("void_stone", true);
        fortune = input.getIntOr("fortune", 0);
        cursorY = input.getIntOr("cursor_y", Integer.MIN_VALUE);
        cursorIndex = input.getIntOr("cursor_index", 0);
        mined = input.getLongOr("mined", 0);
        status = Status.values()[Math.clamp(input.getIntOr("status", 0), 0, Status.values().length - 1)];
        target = input.read("target", BlockPos.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        ContainerHelper.saveAllItems(output.child("upgrades"), upgrades.getItems());
        output.store("buffer", ItemStack.CODEC.listOf(), List.copyOf(buffer));
        output.putInt("size", size);
        output.putBoolean("silk", silk);
        output.putBoolean("void_stone", voidStone);
        output.putInt("fortune", fortune);
        output.putInt("cursor_y", cursorY);
        output.putInt("cursor_index", cursorIndex);
        output.putLong("mined", mined);
        output.putInt("status", status.ordinal());
        if (target != null) output.store("target", BlockPos.CODEC, target);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("size", size);
        tag.putInt("status", status.ordinal());
        tag.putInt("cursor_y", cursorY);
        if (target != null) tag.putLong("target_packed", target.asLong());
        return tag;
    }

    /** Client: the update tag is a small subset. */
    @Override
    public void handleUpdateTag(ValueInput input) {
        size = input.getIntOr("size", size);
        status = Status.values()[Math.clamp(input.getIntOr("status", 0), 0, Status.values().length - 1)];
        cursorY = input.getIntOr("cursor_y", cursorY);
        input.getLong("target_packed").ifPresent(l -> target = BlockPos.of(l));
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ValueInput input) {
        handleUpdateTag(input);
    }

    /** Client: where the mining head is drawn, eased toward the target each tick. */
    private net.minecraft.world.phys.Vec3 head, prevHead;

    @Override
    public void clientTick() {
        super.clientTick();
        net.minecraft.world.phys.Vec3 goal = target == null ? net.minecraft.world.phys.Vec3.atCenterOf(worldPosition)
                : net.minecraft.world.phys.Vec3.atCenterOf(target);
        if (head == null) head = goal;
        prevHead = head;
        head = head.lerp(goal, tier == Tier.QUANTUM ? 0.55 : 0.3);
    }

    public net.minecraft.world.phys.Vec3 head(float partial) {
        if (head == null) return net.minecraft.world.phys.Vec3.atCenterOf(target == null ? worldPosition : target);
        return prevHead == null ? head : prevHead.lerp(head, partial);
    }

    /** Client: the depth of the current layer, for the renderer's corner pylons. */
    public int cursorY() {
        return cursorY;
    }
}
