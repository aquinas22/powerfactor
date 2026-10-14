package com.powerfactor.storage.craft;

import com.powerfactor.registry.ModStorage;
import com.powerfactor.storage.NodeBlockEntity;
import com.powerfactor.storage.StorageNetwork;
import com.powerfactor.upgrade.UpgradeItem;
import com.powerfactor.upgrade.UpgradeType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * Executes crafting patterns for the network: holds up to nine crafting patterns, takes one job at a
 * time, and after a short animated cycle crafts with the real recipe (components, remainders and all),
 * returning the result and any leftovers to the network.
 */
public class AssemblerBlockEntity extends NodeBlockEntity implements PatternHolder {
    public static final int PATTERNS = 9, UPGRADES = 4, BASE_TICKS = 16, FE_PER_CRAFT = 40;

    private final SimpleContainer patternSlots = new SimpleContainer(PATTERNS) {
        @Override
        public boolean canPlaceItem(int index, ItemStack stack) {
            return EncodedPatternItem.crafting(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(AssemblerBlockEntity.this, player);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            patternsChanged();
        }
    };
    private final SimpleContainer upgrades = new SimpleContainer(UPGRADES) {
        @Override
        public boolean canPlaceItem(int index, ItemStack stack) {
            return stack.getItem() instanceof UpgradeItem u && u.type() == UpgradeType.SPEED;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            AssemblerBlockEntity.this.setChanged();
        }
    };
    private List<PatternData> cached;
    /** The craft in progress. */
    private final List<ItemStack> grid = new ArrayList<>();
    private ItemStack showing = ItemStack.EMPTY;
    private int progress, duration;
    /** Products the network couldn't take yet. */
    private final List<ItemStack> pending = new ArrayList<>();

    public AssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(ModStorage.ASSEMBLER_BE.get(), pos, state);
    }

    public Container patternSlots() {
        return patternSlots;
    }

    public Container upgrades() {
        return upgrades;
    }

    private void patternsChanged() {
        cached = null;
        setChanged();
        StorageNetwork n = network();
        if (n != null) n.markPatternsDirty();
    }

    @Override
    public List<PatternData> patterns() {
        if (cached == null) {
            List<PatternData> list = new ArrayList<>();
            for (int i = 0; i < PATTERNS; i++) {
                PatternData p = EncodedPatternItem.data(patternSlots.getItem(i));
                if (p != null && !p.processing() && p.valid()) list.add(p);
            }
            cached = list;
        }
        return cached;
    }

    private int speedCards() {
        int n = 0;
        for (int i = 0; i < UPGRADES; i++) if (!upgrades.getItem(i).isEmpty()) n++;
        return n;
    }

    @Override
    public boolean ready(PatternData pattern) {
        return grid.isEmpty() && pending.isEmpty() && active();
    }

    @Override
    public boolean accept(ServerLevel level, PatternData pattern, List<ItemStack> inputs) {
        StorageNetwork n = onlineNetwork();
        if (!ready(pattern) || inputs.size() != 9 || n == null || !n.controller().spend(FE_PER_CRAFT)) return false;
        grid.clear();
        grid.addAll(inputs);
        showing = pattern.primaryOutput();
        duration = Math.max(2, BASE_TICKS >> speedCards());
        progress = 0;
        sync(level);
        return true;
    }

    @Override
    public int idleDraw() {
        return 2 + speedCards();
    }

    @Override
    public void networkTick(ServerLevel level, StorageNetwork network) {
        flushPending(network);
        if (grid.isEmpty()) return;
        if (++progress < duration) return;
        CraftingInput input = CraftingInput.of(3, 3, grid);
        var recipe = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        if (recipe.isPresent()) {
            ItemStack result = recipe.get().value().assemble(input);
            NonNullList<ItemStack> rest = recipe.get().value().getRemainingItems(input);
            pending.add(result);
            for (ItemStack s : rest) if (!s.isEmpty()) pending.add(s);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, worldPosition.getX() + 0.5, worldPosition.getY() + 0.6, worldPosition.getZ() + 0.5,
                    6, 0.2, 0.2, 0.2, 0.1);
            level.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.12F, 1.8F);
        } else {
            // The recipe vanished (datapack change): give the ingredients back.
            for (ItemStack s : grid) if (!s.isEmpty()) pending.add(s);
        }
        grid.clear();
        showing = ItemStack.EMPTY;
        progress = 0;
        flushPending(network);
        sync(level);
    }

    private void flushPending(StorageNetwork network) {
        for (int i = pending.size() - 1; i >= 0; i--) {
            ItemStack s = pending.get(i);
            long put = network.insert(ItemResource.of(s), s.getCount(), false);
            if (put >= s.getCount()) pending.remove(i);
            else s.shrink((int) put);
        }
    }

    private void sync(ServerLevel level) {
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /** For the renderer: the item being assembled and how far along (0..1). */
    public ItemStack showing() {
        return showing;
    }

    public float progress(float partial) {
        return duration <= 0 || showing.isEmpty() ? 0 : Math.min(1, (progress + partial) / duration);
    }

    @Override
    public void clientTick() {
        super.clientTick();
        if (!showing.isEmpty() && progress < duration) progress++;
    }

    @Override
    public void writeMenuData(net.minecraft.network.RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(worldPosition);
        buf.writeBoolean(true);
    }

    @Override
    public boolean hasMenu() {
        return true;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new PatternRackMenu(containerId, inventory, this);
    }

    @Override
    public void dropContents(ServerLevel level, BlockPos pos) {
        Containers.dropContents(level, pos, patternSlots);
        Containers.dropContents(level, pos, upgrades);
        for (ItemStack s : grid) Containers.dropItemStack(level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, s);
        for (ItemStack s : pending) Containers.dropItemStack(level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, s);
        grid.clear();
        pending.clear();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        patternSlots.clearContent();
        upgrades.clearContent();
        input.child("patterns").ifPresent(c -> ContainerHelper.loadAllItems(c, patternSlots.getItems()));
        input.child("upgrades").ifPresent(c -> ContainerHelper.loadAllItems(c, upgrades.getItems()));
        cached = null;
        grid.clear();
        input.read("grid", ItemStack.OPTIONAL_CODEC.listOf()).ifPresent(grid::addAll);
        pending.clear();
        input.read("pending", ItemStack.CODEC.listOf()).ifPresent(l -> l.forEach(s -> pending.add(s.copy())));
        showing = input.read("showing", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        progress = input.getIntOr("progress", 0);
        duration = input.getIntOr("duration", BASE_TICKS);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output.child("patterns"), patternSlots.getItems());
        ContainerHelper.saveAllItems(output.child("upgrades"), upgrades.getItems());
        output.store("grid", ItemStack.OPTIONAL_CODEC.listOf(), List.copyOf(grid));
        output.store("pending", ItemStack.CODEC.listOf(), List.copyOf(pending));
        output.store("showing", ItemStack.OPTIONAL_CODEC, showing);
        output.putInt("progress", progress);
        output.putInt("duration", duration);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
}
