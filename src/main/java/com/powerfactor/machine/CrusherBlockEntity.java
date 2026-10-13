package com.powerfactor.machine;

import com.powerfactor.PowerFactorConfig;
import com.powerfactor.recipe.CrushingOutput;
import com.powerfactor.recipe.CrushingRecipe;
import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.registry.ModRecipes;
import com.powerfactor.upgrade.UpgradeType;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Jaw crusher: ores to two dusts, cobble to gravel to sand. Driven by an induction motor, so it
 * cares about voltage, frequency and power factor (see {@link MotorDrive}).
 */
public class CrusherBlockEntity extends ProcessingBlockEntity {
    public static final int INPUT = 0, OUTPUT = 1, BONUS = 2;

    private final RecipeManager.CachedCheck<SingleRecipeInput, CrushingRecipe> recipes = RecipeManager.createCheck(ModRecipes.CRUSHING.get());

    public CrusherBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRUSHER.get(), pos, state, new int[] {INPUT}, new int[] {OUTPUT, BONUS}, 8_000,
                PowerFactorConfig.SPEC.isLoaded() ? PowerFactorConfig.CRUSHER_FE_PER_TICK.getAsInt() : 20,
                new MotorDrive(PowerFactorConfig.SPEC.isLoaded() ? PowerFactorConfig.CRUSHER_EE_PER_TICK.getAsInt() : 15),
                EnumSet.allOf(UpgradeType.class));
    }

    @Override
    protected @Nullable Job findJob(ServerLevel level) {
        ItemStack input = items.getItem(INPUT);
        if (input.isEmpty()) {
            return null;
        }
        var holder = recipes.getRecipeFor(new SingleRecipeInput(input), level).orElse(null);
        if (holder == null) {
            return null;
        }
        CrushingRecipe recipe = holder.value();
        ItemStack result = recipe.result().resolve();
        if (result.isEmpty()) return null;
        boolean fits = fits(result, OUTPUT);
        for (CrushingOutput bonus : recipe.bonus()) {
            fits &= fits(bonus.resolve(), BONUS);
        }
        return new Job(holder.id().identifier(), recipe.time(), input.getHoverName(), fits ? WorkState.READY : WorkState.OUTPUT_FULL, () -> {
            input.shrink(1);
            merge(result, OUTPUT);
            for (CrushingOutput bonus : recipe.bonus()) {
                if (level.getRandom().nextFloat() < bonus.chance()) {
                    merge(bonus.resolve(), BONUS);
                }
            }
        });
    }

    @Override
    protected boolean isValidInput(int slot, ItemStack stack) {
        return true;
    }

    @Override
    public net.minecraft.sounds.SoundEvent loopSound() {
        return com.powerfactor.registry.ModSounds.CRUSHER_RUN.get();
    }

    // ---- client sync of the input slot, so the jaw renderer can show the ore being crushed

    private net.minecraft.world.item.Item syncedInput = net.minecraft.world.item.Items.AIR;
    private ItemStack clientInput = ItemStack.EMPTY;
    private float swing, swingPrev;

    @Override
    public void serverTick(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state) {
        super.serverTick(level, pos, state);
        net.minecraft.world.item.Item now = items.getItem(INPUT).getItem();
        if (now != syncedInput) {
            syncedInput = now;
            level.sendBlockUpdated(pos, state, state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void clientTick() {
        super.clientTick();
        swingPrev = swing;
        boolean running = getBlockState().getValueOrElse(com.powerfactor.machine.MachineBlock.RUNNING, false);
        swing = running ? Math.min(1, swing + 0.05F) : Math.max(0, swing - 0.03F);
    }

    /** 0 when stopped, 1 at full stroke; eases over a second or two. */
    public float jawSwing(float partialTicks, boolean running) {
        return swingPrev + (swing - swingPrev) * partialTicks;
    }

    public ItemStack shownInput() {
        return clientInput;
    }

    @Override protected double animationRate() { return 0.9 * animationSpeed(); }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = super.getUpdateTag(registries);
        tag.putString("shown_input", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(items.getItem(INPUT).getItem()).toString());
        return tag;
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        super.loadAdditional(input);
        input.getString("shown_input").ifPresent(id -> clientInput = new ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(id))));
    }

    public Drive.State motor() {
        return drive.state();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new CrusherMenu(containerId, inventory, items, upgrades, data, this);
    }
}
