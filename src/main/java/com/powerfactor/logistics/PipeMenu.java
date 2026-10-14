package com.powerfactor.logistics;

import com.powerfactor.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Server-owned ghost filters: every edit copies a reference without moving or consuming an item. */
public final class PipeMenu extends AbstractContainerMenu {
    private static final int DATA_COUNT = 6;
    public static final int SELECT_FACE = 0, CYCLE_MODE = 10, TOGGLE_EXACT = 11, PRIORITY_DOWN = 12,
            PRIORITY_UP = 13, CLEAR = 14, CONNECTION = 15;
    private final SimpleContainer references = new SimpleContainer(PipeSettings.SAMPLES);
    private final @Nullable PipeBlockEntity pipe;
    private final ContainerData data;
    private BlockPos pos;
    private Direction selection;

    public PipeMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, null, buffer.readBlockPos(), buffer.readEnum(Direction.class));
    }
    public PipeMenu(int id, Inventory inventory, PipeBlockEntity pipe, Direction side) {
        this(id, inventory, pipe, pipe.getBlockPos(), side);
    }
    private PipeMenu(int id, Inventory inventory, @Nullable PipeBlockEntity pipe, BlockPos pos, Direction side) {
        super(ModMenus.PIPE_ROUTING.get(), id);
        this.pipe = pipe; this.pos = pos; selection = side;
        data = pipe == null ? new SimpleContainerData(DATA_COUNT) : new ContainerData() {
            public int get(int index) {
                var face = pipe.face(selection);
                return switch (index) {
                    case 0 -> selection.ordinal(); case 1 -> face.mode(); case 2 -> face.exact() ? 1 : 0;
                    case 3 -> face.priority() + 5; case 4 -> pipe.getBlockState().getValue(PipeBlock.SIDES.get(selection)).ordinal();
                    case 5 -> pipe.kind().ordinal(); default -> 0;
                };
            }
            public void set(int index, int value) {}
            public int getCount() { return DATA_COUNT; }
        };
        if (pipe == null) data.set(0, side.ordinal());
        addDataSlots(data);
        for (int i = 0; i < PipeSettings.SAMPLES; i++) {
            addSlot(new Slot(references, i, 47 + i * 38, 90) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                @Override public boolean mayPickup(Player player) { return false; }
            });
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, 9 + row * 9 + col, 41 + col * 18, 150 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 41 + col * 18, 208));
        refreshReferences();
    }

    public Direction selectedSide() { return pipe == null ? Direction.values()[Math.clamp(data.get(0), 0, 5)] : selection; }
    public int mode() { return data.get(1); }
    public boolean exact() { return data.get(2) != 0; }
    public int priority() { return data.get(3) - 5; }
    public PipeSide connection() { return PipeSide.values()[Math.clamp(data.get(4), 0, 3)]; }
    public PipeKind kind() { return PipeKind.values()[Math.clamp(data.get(5), 0, 2)]; }
    public boolean emptyList() { return references.isEmpty(); }

    private void refreshReferences() {
        if (pipe != null) for (int i = 0; i < PipeSettings.SAMPLES; i++)
            references.setItem(i, pipe.face(selection).samples().get(i).copy());
    }
    @Override public void broadcastChanges() { refreshReferences(); super.broadcastChanges(); }
    @Override public boolean stillValid(Player player) {
        return pipe == null || !pipe.isRemoved() && player.level() == pipe.getLevel()
                && player.level().getBlockEntity(pos) == pipe && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64
                && !player.isSpectator() && player.level().mayInteract(player, pos)
                && !player.blockActionRestricted(player.level(), pos, player.gameMode());
    }

    @Override public boolean clickMenuButton(Player player, int button) {
        if (pipe == null || !stillValid(player)) return false;
        if (button >= SELECT_FACE && button < SELECT_FACE + 6) selection = Direction.values()[button - SELECT_FACE];
        else {
            var face = pipe.face(selection);
            switch (button) {
                case CYCLE_MODE -> pipe.setFace(selection, face.withMode((face.mode() + 1) % 3));
                case TOGGLE_EXACT -> pipe.setFace(selection, face.withExact(!face.exact()));
                case PRIORITY_DOWN -> pipe.setFace(selection, face.withPriority(face.priority() - 1));
                case PRIORITY_UP -> pipe.setFace(selection, face.withPriority(face.priority() + 1));
                case CLEAR -> pipe.setFace(selection, face.clear());
                case CONNECTION -> ((PipeBlock) pipe.getBlockState().getBlock()).cycleSide(player.level(), pos, player, selection);
                default -> { return false; }
            }
        }
        broadcastChanges();
        return true;
    }

    @Override public void clicked(int slot, int button, ContainerInput input, Player player) {
        if (slot >= 0 && slot < PipeSettings.SAMPLES) {
            if (pipe != null && stillValid(player) && input == ContainerInput.PICKUP && (button == 0 || button == 1)) {
                if (!pipe.setSample(selection, slot, button == 1 ? ItemStack.EMPTY : getCarried()))
                    player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.powerfactor.pipe.invalid_sample"));
                broadcastChanges();
            }
            return;
        }
        if (pipe != null && !stillValid(player)) return;
        // Pick-all and creative cloning may never draw from ghost references.
        super.clicked(slot, button, input, player);
    }
    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != references && super.canTakeItemForPickAll(stack, slot);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (pipe == null || !stillValid(player) || index < PipeSettings.SAMPLES || index >= slots.size()) return ItemStack.EMPTY;
        var sample = slots.get(index).getItem();
        if (sample.isEmpty()) return ItemStack.EMPTY;
        for (int i = 0; i < PipeSettings.SAMPLES; i++) if (references.getItem(i).isEmpty()) {
            if (!pipe.setSample(selection, i, sample))
                player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.powerfactor.pipe.invalid_sample"));
            broadcastChanges(); break;
        }
        return ItemStack.EMPTY;
    }
}
