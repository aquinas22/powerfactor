package com.powerfactor.storage;

import com.powerfactor.registry.ModStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Grid, Crafting and Pattern terminals: the screens onto a storage network. */
public class TerminalBlockEntity extends NodeBlockEntity implements TerminalMenu.Host {
    public enum Kind { GRID, CRAFTING, PATTERN }

    private final Kind kind;

    public TerminalBlockEntity(Kind kind, BlockPos pos, BlockState state) {
        super(switch (kind) {
            case GRID -> ModStorage.GRID_TERMINAL_BE.get();
            case CRAFTING -> ModStorage.CRAFTING_TERMINAL_BE.get();
            case PATTERN -> ModStorage.PATTERN_TERMINAL_BE.get();
        }, pos, state);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public @Nullable StorageNetwork hostNetwork() {
        return network();
    }

    @Override
    public boolean hostValid(Player player) {
        return !isRemoved() && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(worldPosition)) < 64;
    }

    @Override
    public int idleDraw() {
        return kind == Kind.GRID ? 2 : 3;
    }

    @Override
    public boolean hasMenu() {
        return true;
    }

    @Override
    public void writeMenuData(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(kind.ordinal());
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new TerminalMenu(containerId, inventory, kind, this);
    }
}
