package com.powerfactor.logistics;

import com.powerfactor.machine.MachineBlock;
import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.scada.ScadaReading;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Robot Arm: reaches into the inventory behind it, swings round and sets the stack into the inventory
 * in front. A cycle is reach, grip, swing, release, return; each move costs FE. Use the arm with an
 * item to make it move only that item; sneak-use with an empty hand to clear the filter.
 */
public class RobotArmBlockEntity extends MachineBlockEntity {
    public static final int CAPACITY = 20_000, MAX_INPUT = 1_000, MOVE_COST = 250, STACK = 16;
    /** Phase lengths in ticks: reach to the source, swing across, return home. */
    public static final int REACH = 8, SWING = 12, RETURN = 8;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CAPACITY, MAX_INPUT, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private ItemStack held = ItemStack.EMPTY, filter = ItemStack.EMPTY;
    /** 0 idle, 1 reaching back, 2 swinging forward with the item, 3 returning. */
    private int stage, t, moved;
    private boolean dirty;

    public RobotArmBlockEntity(BlockPos pos, BlockState state) {
        super(com.powerfactor.registry.ModQuantum.ROBOT_ARM_BE.get(), pos, state);
    }

    public SimpleEnergyHandler getEnergy() {
        return energy;
    }

    private Direction facing() {
        return getBlockState().getValue(MachineBlock.FACING);
    }

    private @Nullable ResourceHandler<ItemResource> side(ServerLevel level, Direction d) {
        return level.getCapability(Capabilities.Item.BLOCK, worldPosition.relative(d), d.getOpposite());
    }

    private boolean accepts(ItemResource r) {
        return filter.isEmpty() || r.matches(filter);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        boolean on = enabled(level, pos);
        Direction front = facing(), back = front.getOpposite();
        switch (stage) {
            case 0 -> {
                if (!on || energy.getAmountAsInt() < MOVE_COST || level.getGameTime() % 4 != 0) break;
                // Only start a cycle if there is something to take and somewhere to put it.
                var src = side(level, back);
                var dst = side(level, front);
                if (src == null || dst == null || firstMovable(src) == null) break;
                stage = 1;
                t = 0;
                dirty = true;
            }
            case 1 -> {
                if (++t >= REACH) {
                    var src = side(level, back);
                    ItemResource r = src == null ? null : firstMovable(src);
                    if (r != null) {
                        try (Transaction tx = Transaction.openRoot()) {
                            int n = src.extract(r, Math.min(STACK, r.getMaxStackSize()), tx);
                            tx.commit();
                            held = r.toStack(n);
                        }
                        energy.set(energy.getAmountAsInt() - MOVE_COST);
                        level.playSound(null, pos, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.4F, 1.8F);
                    }
                    stage = held.isEmpty() ? 3 : 2;
                    t = 0;
                    dirty = true;
                }
            }
            case 2 -> {
                if (++t >= SWING) {
                    var dst = side(level, front);
                    if (dst != null && !held.isEmpty()) {
                        try (Transaction tx = Transaction.openRoot()) {
                            int n = dst.insert(ItemResource.of(held), held.getCount(), tx);
                            tx.commit();
                            held.shrink(n);
                            moved += n;
                        }
                        level.playSound(null, pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.4F, 1.6F);
                    }
                    // Whatever didn't fit stays in the gripper; the arm waits at the target until it does.
                    if (held.isEmpty()) {
                        held = ItemStack.EMPTY;
                        stage = 3;
                        t = 0;
                    } else {
                        t = SWING - 10;
                    }
                    dirty = true;
                }
            }
            case 3 -> {
                if (++t >= RETURN) {
                    stage = 0;
                    t = 0;
                    dirty = true;
                }
            }
            default -> stage = 0;
        }
        setRunning(level, pos, state, stage != 0);
        if (dirty) {
            dirty = false;
            setChanged();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private @Nullable ItemResource firstMovable(ResourceHandler<ItemResource> src) {
        for (int i = 0; i < src.size(); i++) {
            ItemResource r = src.getResource(i);
            if (!r.isEmpty() && src.getAmountAsLong(i) > 0 && accepts(r)) return r;
        }
        return null;
    }

    public void setFilter(ItemStack stack) {
        filter = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    public ItemStack filter() {
        return filter;
    }

    // ------------------------------------------------------------------ client view

    private int clientStage, clientT;
    private long clientStamp;
    private ItemStack clientHeld = ItemStack.EMPTY, clientFilter = ItemStack.EMPTY;

    /** Stage and how far through it the arm is (0..1), advanced locally between syncs. */
    public int clientStage() {
        return clientStage;
    }

    public float clientProgress(float partial) {
        int len = switch (clientStage) {
            case 1 -> REACH;
            case 2 -> SWING;
            case 3 -> RETURN;
            default -> 1;
        };
        float ticks = clientT + (level == null ? 0 : level.getGameTime() - clientStamp) + partial;
        return Math.clamp(ticks / len, 0, 1);
    }

    public ItemStack clientHeld() {
        return clientHeld;
    }

    public ItemStack clientFilter() {
        return clientFilter;
    }

    @Override
    public boolean hasMenu() {
        return false;
    }

    @Override
    public Component describe() {
        Component what = filter.isEmpty() ? Component.translatable("gui.powerfactor.robot_arm.any") : filter.getHoverName();
        return Component.translatable("gui.powerfactor.robot_arm.status", what, String.format("%,d", moved), String.format("%,d", energy.getAmountAsInt()))
                .withStyle(ChatFormatting.AQUA);
    }

    @Override
    public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), stage != 0 ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF, 0, 0,
                describe(), true, remoteEnabled(), true, 1F, 60F);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return null;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        held = input.read("held", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        filter = input.read("filter", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        stage = input.getIntOr("stage", 0);
        t = input.getIntOr("t", 0);
        moved = input.getIntOr("moved", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        if (!held.isEmpty()) output.store("held", ItemStack.OPTIONAL_CODEC, held);
        if (!filter.isEmpty()) output.store("filter", ItemStack.OPTIONAL_CODEC, filter);
        output.putInt("stage", stage);
        output.putInt("t", t);
        output.putInt("moved", moved);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        // Whatever the gripper was carrying drops with the arm.
        if (level != null && !held.isEmpty()) net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), held);
        held = ItemStack.EMPTY;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("stage", stage);
        tag.putInt("t", t);
        var ops = registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
        if (!held.isEmpty()) ItemStack.CODEC.encodeStart(ops, held).ifSuccess(n -> tag.put("held", n));
        if (!filter.isEmpty()) ItemStack.CODEC.encodeStart(ops, filter).ifSuccess(n -> tag.put("filter", n));
        return tag;
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        clientStage = input.getIntOr("stage", 0);
        clientT = input.getIntOr("t", 0);
        clientStamp = level == null ? 0 : level.getGameTime();
        clientHeld = input.read("held", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        clientFilter = input.read("filter", ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ValueInput input) {
        handleUpdateTag(input);
    }
}
