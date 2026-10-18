package com.powerfactor.quantum;

import com.powerfactor.exotic.ExoticMaterial;
import com.powerfactor.exotic.ExoticTool;
import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.multiblock.CasingBlock;
import com.powerfactor.multiblock.CasingBlockEntity;
import com.powerfactor.multiblock.Structure;
import com.powerfactor.registry.ModQuantum;
import com.powerfactor.scada.ScadaReading;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import org.jspecify.annotations.Nullable;

/**
 * The Starforge: a 3x3x3 forge of adamantium frames, cortanium pylons, a nether-star crucible and
 * a qubit focusing lens. It is the only way to make paxels: it fuses a pickaxe, axe and shovel of one
 * material around a catalyst block, merging their enchantments, at a ruinous energy cost.
 */
public class StarforgeBlockEntity extends MachineBlockEntity implements com.powerfactor.upgrade.Upgradeable, com.powerfactor.multiblock.Buildable {
    public static final int CAPACITY = 250_000_000, MAX_INPUT = 2_000_000, MAX_RATE = 400_000;
    public static final int PICK = 0, AXE = 1, SHOVEL = 2, CATALYST = 3, OUTPUT = 4;
    public static final int DATA_PROGRESS = 0, DATA_FORMED = 1, DATA_ENERGY_HI = 2, DATA_ENERGY_LO = 3, DATA_RATE = 4, DATA_RECIPE = 5, DATA_COUNT = 6;

    /** Layer 0: frames around the controller and an adamantium hearth; layer 1: pylons and the crucible; layer 2: frames and the lens. */
    public static final Structure STRUCTURE = new Structure.Builder()
            .key('F', ModQuantum.STARFORGE_FRAME)
            .key('A', () -> ModQuantum.STORAGE_BLOCKS.get("adamantium").get())
            .key('P', ModQuantum.STARFORGE_PYLON)
            .key('K', ModQuantum.STARFORGE_CRUCIBLE)
            .key('Q', () -> ModQuantum.STORAGE_BLOCKS.get("qubit_crystal").get())
            .layer(0, "FCF", "FAF", "FFF")
            .layer(1, "P.P", ".K.", "P.P")
            .layer(2, "FFF", "FQF", "FFF")
            .build();

    public record Recipe(String id, Supplier<Item> pick, Supplier<Item> axe, Supplier<Item> shovel, Supplier<Item> catalyst, Supplier<Item> paxel,
            long energy) {}

    public static final List<Recipe> RECIPES = List.of(
            new Recipe("diamond", () -> Items.DIAMOND_PICKAXE, () -> Items.DIAMOND_AXE, () -> Items.DIAMOND_SHOVEL, () -> Items.DIAMOND_BLOCK,
                    () -> ModQuantum.DIAMOND_PAXEL.get(), 10_000_000L),
            new Recipe("netherite", () -> Items.NETHERITE_PICKAXE, () -> Items.NETHERITE_AXE, () -> Items.NETHERITE_SHOVEL, () -> Items.NETHERITE_BLOCK,
                    () -> ModQuantum.NETHERITE_PAXEL.get(), 25_000_000L),
            exotic(ExoticMaterial.ADAMANTIUM, "adamantium", 40_000_000L),
            exotic(ExoticMaterial.FARADAENIUM, "faradaenium", 60_000_000L),
            exotic(ExoticMaterial.CORTANIUM, "cortanium", 100_000_000L),
            new Recipe("quantum", tool(ExoticMaterial.QUANTUM, ExoticTool.Kind.PICKAXE), tool(ExoticMaterial.QUANTUM, ExoticTool.Kind.AXE),
                    tool(ExoticMaterial.QUANTUM, ExoticTool.Kind.SHOVEL), () -> ModQuantum.SINGULARITY.get(),
                    tool(ExoticMaterial.QUANTUM, ExoticTool.Kind.PAXEL), 200_000_000L));

    private static Recipe exotic(ExoticMaterial m, String block, long energy) {
        return new Recipe(m.id, tool(m, ExoticTool.Kind.PICKAXE), tool(m, ExoticTool.Kind.AXE), tool(m, ExoticTool.Kind.SHOVEL),
                () -> ModQuantum.STORAGE_BLOCKS.get(block).get().asItem(), tool(m, ExoticTool.Kind.PAXEL), energy);
    }

    private static Supplier<Item> tool(ExoticMaterial m, ExoticTool.Kind k) {
        return () -> ModQuantum.TOOLS.get(m).get(k).get();
    }

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CAPACITY, MAX_INPUT, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private final SimpleContainer slots = new SimpleContainer(5) {
        @Override
        public boolean canPlaceItem(int index, ItemStack stack) {
            return switch (index) {
                case PICK -> stack.is(net.minecraft.tags.ItemTags.PICKAXES) && !stack.is(com.powerfactor.registry.ModTags.PAXELS);
                case AXE -> stack.is(net.minecraft.tags.ItemTags.AXES) && !stack.is(com.powerfactor.registry.ModTags.PAXELS);
                case SHOVEL -> stack.is(net.minecraft.tags.ItemTags.SHOVELS) && !stack.is(com.powerfactor.registry.ModTags.PAXELS);
                case CATALYST -> RECIPES.stream().anyMatch(r -> stack.is(r.catalyst().get()));
                default -> false;
            };
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(StarforgeBlockEntity.this, player);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            StarforgeBlockEntity.this.setChanged();
        }
    };
    private long banked;
    private int rate, recipe = -1;
    private boolean formed;
    private final Set<BlockPos> linked = new HashSet<>();
    private float clientProgress;
    private int flash;

    private final com.powerfactor.upgrade.UpgradeInventory upgrades = new com.powerfactor.upgrade.UpgradeInventory(
            com.powerfactor.upgrade.UpgradeInventory.CONSOLE, this::setChanged, p -> net.minecraft.world.Container.stillValidBlockEntity(this, p));

    public com.powerfactor.upgrade.UpgradeInventory upgrades() {
        return upgrades;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && !level.isClientSide()) {
            net.minecraft.world.Containers.dropContents(level, pos, upgrades);
            // Only a real removal frees the casings: setRemoved also runs when the chunk unloads, and must not touch other blocks then.
            CasingBlockEntity.release(level, linked, worldPosition);
        }
    }

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case DATA_PROGRESS -> (int) (1000 * progress());
                case DATA_FORMED -> formed ? 1 : 0;
                case DATA_ENERGY_HI -> (energy.getAmountAsInt() >>> 14) & 0xFFFF;
                case DATA_ENERGY_LO -> energy.getAmountAsInt() & 0x3FFF;
                case DATA_RATE -> rate / 100;
                case DATA_RECIPE -> recipe + 1;
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

    public StarforgeBlockEntity(BlockPos pos, BlockState state) {
        super(ModQuantum.STARFORGE_BE.get(), pos, state);
    }

    public SimpleEnergyHandler getEnergy() {
        return energy;
    }

    private final net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> items =
            net.neoforged.neoforge.transfer.item.VanillaContainerWrapper.of(slots);

    /** Hoppers and pipes: inputs go to the first empty matching slot; the paxel can be extracted. */
    public net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> getItems() {
        return items;
    }

    public Container slots() {
        return slots;
    }

    @Override
    public Structure buildStructure() {
        return STRUCTURE;
    }

    private Direction facingDir() {
        return getBlockState().getValue(com.powerfactor.machine.MachineBlock.FACING);
    }

    private float progress() {
        return recipe < 0 ? 0 : (float) Math.min(1, (double) banked / need(RECIPES.get(recipe)));
    }

    /** The forging energy after Speed (+15%/card) and Efficiency (-15%/card) cards. */
    long need(Recipe r) {
        return (long) (r.energy() * upgrades.effects().energyPerOperation());
    }

    public static int recipeFor(Container slots) {
        for (int i = 0; i < RECIPES.size(); i++) {
            Recipe r = RECIPES.get(i);
            if (slots.getItem(PICK).is(r.pick().get()) && slots.getItem(AXE).is(r.axe().get()) && slots.getItem(SHOVEL).is(r.shovel().get())
                    && slots.getItem(CATALYST).is(r.catalyst().get())) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        rate = 0;
        if (flash > 0) flash--;
        if (level.getGameTime() % 20 == 0) checkStructure(level, pos, state);
        int r = recipeFor(slots);
        if (r != recipe) {
            recipe = r;
            banked = 0;
        }
        if (formed && recipe >= 0 && slots.getItem(OUTPUT).isEmpty() && enabled(level, pos)) {
            Recipe rec = RECIPES.get(recipe);
            int draw = (int) Math.min(Math.min((long) (MAX_RATE * upgrades.effects().speedMultiplier()), energy.getAmountAsInt()), need(rec) - banked);
            energy.set(energy.getAmountAsInt() - draw);
            banked += draw;
            rate = draw;
            if (draw > 0 && level.getGameTime() % 4 == 0) {
                level.sendParticles(ParticleTypes.LAVA, pos.getX() + .5, pos.getY() + 1.5, pos.getZ() + .5, 1, .2, .1, .2, 0);
            }
            if (banked >= need(rec)) forge(level, pos, rec);
        }
        setRunning(level, pos, state, rate > 0);
        if (level.getGameTime() % 5 == 0) level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }

    private void forge(ServerLevel level, BlockPos pos, Recipe rec) {
        // Merge the three tools' enchantments, keeping the highest level of each.
        ItemEnchantments.Mutable merged = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (int s : new int[] {PICK, AXE, SHOVEL}) {
            ItemEnchantments e = EnchantmentHelper.getEnchantmentsForCrafting(slots.getItem(s));
            for (var entry : e.entrySet()) {
                merged.upgrade(entry.getKey(), entry.getIntValue());
            }
        }
        ItemStack paxel = new ItemStack(rec.paxel().get());
        EnchantmentHelper.setEnchantments(paxel, merged.toImmutable());
        for (int s : new int[] {PICK, AXE, SHOVEL, CATALYST}) slots.getItem(s).shrink(1);
        slots.setItem(OUTPUT, paxel);
        banked = 0;
        flash = 30;
        BlockPos crucible = STRUCTURE.worldPos(pos, facingDir(), STRUCTURE.parts().stream().filter(p -> p.block().get() == ModQuantum.STARFORGE_CRUCIBLE.get())
                .findFirst().orElseThrow());
        double x = crucible.getX() + .5, y = crucible.getY() + .8, z = crucible.getZ() + .5;
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 80, .1, .1, .1, .4);
        level.sendParticles(ParticleTypes.LAVA, x, y, z, 30, .3, .3, .3, 0);
        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.2F, 0.6F);
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.2F, 1.4F);
    }

    private void checkStructure(ServerLevel level, BlockPos pos, BlockState state) {
        if (!STRUCTURE.loaded(level, pos, facingDir())) return;
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
            level.playSound(null, pos, now ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1F, 0.7F);
        }
        if (state.getValue(CasingBlock.FORMED) != now) level.setBlock(pos, state.setValue(CasingBlock.FORMED, now), Block.UPDATE_ALL);
    }

    public boolean formed() {
        return getBlockState().getValue(CasingBlock.FORMED);
    }

    public float clientProgress() {
        return clientProgress;
    }

    public int flash() {
        return flash;
    }

    @Override
    public void clientTick() {
        super.clientTick();
        if (flash > 0) flash--;
    }

    @Override
    protected double animationRate() {
        return isRunning() ? 1 : 0.03;
    }

    @Override
    public ScadaReading scadaReading() {
        return new ScadaReading(worldPosition, getDisplayName(), rate > 0 ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF, rate, 0,
                Component.translatable("scada.powerfactor.starforge", Math.round(progress() * 100)), true, remoteEnabled(), false, 1F, 60F);
    }

    @Override
    protected Container inventory() {
        return slots;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new StarforgeMenu(containerId, inventory, this, slots, data);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        slots.clearContent();
        ContainerHelper.loadAllItems(input, slots.getItems());
        banked = input.getLongOr("banked", 0);
        upgrades.load(input);
        recipe = input.getIntOr("recipe", -1);
        linked.clear();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        ContainerHelper.saveAllItems(output, slots.getItems());
        output.putLong("banked", banked);
        upgrades.save(output);
        output.putInt("recipe", recipe);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putFloat("progress", progress());
        tag.putInt("flash", flash);
        return tag;
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        clientProgress = input.getFloatOr("progress", 0);
        flash = Math.max(flash, input.getIntOr("flash", 0));
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ValueInput input) {
        handleUpdateTag(input);
    }
}
