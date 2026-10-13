package com.powerfactor.machine;

import com.powerfactor.recipe.CrushingOutput;
import com.powerfactor.recipe.MachineRecipe;
import com.powerfactor.recipe.MachineRecipeInput;
import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import org.jspecify.annotations.Nullable;

/** Alloy furnace, arc furnace, wire drawer, rolling mill, ore washer: see {@link RecipeMachine}. */
public class RecipeMachineBlockEntity extends ProcessingBlockEntity {
    public static final int TANK_CAPACITY = 8_000;

    private final RecipeMachine machine;
    private final RecipeManager.CachedCheck<MachineRecipeInput, MachineRecipe> recipes;
    private final @Nullable FluidStacksResourceHandler tank;
    private final java.util.Map<net.minecraft.core.Direction, net.neoforged.neoforge.transfer.ResourceHandler<FluidResource>> sidedFluids = new java.util.EnumMap<>(net.minecraft.core.Direction.class);
    private final com.powerfactor.animation.RotaryMotion workshopMotion = new com.powerfactor.animation.RotaryMotion();

    @Override
    public void clientTick() {
        super.clientTick();
        if (machine.animatedWorkshop()) {
            workshopMotion.tick(animationSpeed() * (machine == RecipeMachine.CENTRIFUGE ? 0.5 : machine == RecipeMachine.SAWMILL ? 0.7 : 0.12),
                    0.08, 0.06);
        }
    }

    public float workshopPhase(float partialTick) {
        return workshopMotion.phase(partialTick);
    }

    @Override protected double animationRate() {
        return super.animationRate() * (machine == RecipeMachine.ORE_WASHER ? 0.6 : 1);
    }

    public RecipeMachineBlockEntity(RecipeMachine machine, BlockPos pos, BlockState state) {
        super(ModBlockEntities.RECIPE_MACHINES.get(machine).get(), pos, state, range(0, machine.inputs + (machine.electrode ? 0 : 0), machine),
                range(machine.inputs, machine.outputs, null), 8_000, machine.fePerTick, machine.drive.get(), machine.upgrades);
        this.machine = machine;
        this.recipes = RecipeManager.createCheck(machine.kind.type());
        this.tank = machine.tank ? new FluidStacksResourceHandler(1, TANK_CAPACITY) {
            @Override
            public boolean isValid(int index, FluidResource resource) {
                return resource.getFluid() == Fluids.WATER;
            }

            @Override
            protected void onContentsChanged(int index, FluidStack previousContents) {
                setChanged();
                if (level != null && !level.isClientSide() && previousContents.getAmount() / 250 != getAmountAsInt(index) / 250)
                    level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
        } : null;
        if (tank != null) for (var side : net.minecraft.core.Direction.values()) {
            sidedFluids.put(side, new net.neoforged.neoforge.transfer.DelegatingResourceHandler<>(tank) {
                @Override public int insert(int slot, FluidResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
                    return resourceMode(side).input() ? super.insert(slot, resource, amount, tx) : 0;
                }
                @Override public int insert(FluidResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
                    return resourceMode(side).input() ? super.insert(resource, amount, tx) : 0;
                }
                @Override public int extract(int slot, FluidResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
                    return resourceMode(side).output() ? super.extract(slot, resource, amount, tx) : 0;
                }
                @Override public int extract(FluidResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
                    return resourceMode(side).output() ? super.extract(resource, amount, tx) : 0;
                }
            });
        }
    }

    /** Input slots, plus the electrode slot (which hoppers may fill) for the arc furnace. */
    private static int[] range(int start, int count, @Nullable RecipeMachine withElectrode) {
        int extra = withElectrode != null && withElectrode.electrode ? 1 : 0;
        int[] slots = new int[count + extra];
        for (int i = 0; i < count; i++) {
            slots[i] = start + i;
        }
        if (extra == 1) {
            slots[count] = withElectrode.electrodeSlot();
        }
        return slots;
    }

    public RecipeMachine machine() {
        return machine;
    }

    @Override
    protected @Nullable Job findJob(ServerLevel level) {
        List<ItemStack> inputs = new ArrayList<>();
        boolean any = false;
        for (int i = 0; i < machine.inputs; i++) {
            ItemStack s = items.getItem(i);
            inputs.add(s);
            any |= !s.isEmpty();
        }
        if (!any) {
            return null;
        }
        if (machine.electrode && items.getItem(machine.electrodeSlot()).isEmpty()) {
            return null;
        }
        FluidStack fluid = tank == null ? FluidStack.EMPTY : tank.copyToList().getFirst();
        MachineRecipeInput input = new MachineRecipeInput(inputs, fluid, machine.electrode ? items.getItem(machine.electrodeSlot()) : ItemStack.EMPTY);
        RecipeHolder<MachineRecipe> holder = recipes.getRecipeFor(input, level).orElse(null);
        boolean needsWater = false;
        if (holder == null && tank != null) {
            input = new MachineRecipeInput(inputs, new FluidStack(Fluids.WATER, TANK_CAPACITY), input.tool());
            holder = recipes.getRecipeFor(input, level).orElse(null);
            needsWater = holder != null;
        }
        if (holder == null) {
            return null;
        }
        MachineRecipe recipe = holder.value();
        int[] slots = recipe.slotsFor(input);
        if (slots == null) {
            return null;
        }
        // Main product to the first output; byproducts to the last.
        List<CrushingOutput> results = recipe.results();
        int mainSlot = machine.inputs, byproductSlot = machine.inputs + machine.outputs - 1;
        boolean fits = true;
        for (int r = 0; r < results.size(); r++) {
            fits &= fits(results.get(r).resolve(), r == 0 ? mainSlot : byproductSlot);
        }
        return new Job(holder.id().identifier(), recipe.time(), inputs.get(slots[0]).getHoverName(),
                needsWater ? WorkState.NEEDS_FLUID : fits ? WorkState.READY : WorkState.OUTPUT_FULL, () -> {
            for (int k = 0; k < slots.length; k++) {
                items.getItem(slots[k]).shrink(recipe.inputs().get(k).count());
            }
            recipe.fluid().ifPresent(f -> {
                FluidStack in = tank.copyToList().getFirst();
                tank.set(0, FluidResource.of(in), Math.max(0, in.getAmount() - f.amount()));
            });
            if (machine.electrode) {
                ItemStack electrode = items.getItem(machine.electrodeSlot());
                electrode.setDamageValue(electrode.getDamageValue() + 1);
                if (electrode.getDamageValue() >= electrode.getMaxDamage()) {
                    items.setItem(machine.electrodeSlot(), ItemStack.EMPTY);
                }
            }
            for (int r = 0; r < results.size(); r++) {
                CrushingOutput out = results.get(r);
                if (out.chance() >= 1 || level.getRandom().nextFloat() < out.chance()) {
                    merge(out.resolve(), r == 0 ? mainSlot : byproductSlot);
                }
            }
        });
    }

    @Override
    protected boolean isValidInput(int slot, ItemStack stack) {
        boolean tool = machine.isTool(stack);
        return machine.electrode && slot == machine.electrodeSlot() ? tool : !tool || !machine.electrode;
    }

    @Override
    protected int tankAmount() {
        return tank == null ? 0 : tank.getAmountAsInt(0);
    }

    public @Nullable FluidStacksResourceHandler getTank() {
        return tank;
    }
    public net.neoforged.neoforge.transfer.@Nullable ResourceHandler<FluidResource> getFluidHandler(net.minecraft.core.@Nullable Direction side) {
        return side == null ? tank : sidedFluids.get(side);
    }

    @Override
    public SoundEvent loopSound() {
        return machine.sound.get();
    }

    @Override
    public float loopVolume() {
        return machine == RecipeMachine.ARC_FURNACE ? 0.25F : 0.45F;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        if (tank != null) {
            tank.deserialize(input.childOrEmpty("tank"));
        }
        input.getString("shown_input").ifPresent(id -> clientInput = new ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(id))));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (tank != null) {
            tank.serialize(output.child("tank"));
        }
    }

    // ---- the laser engraver shows its wafer and progress on the bed

    private net.minecraft.world.item.Item syncedInput = net.minecraft.world.item.Items.AIR;
    private ItemStack clientInput = ItemStack.EMPTY;

    @Override
    public void serverTick(ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        super.serverTick(level, pos, state);
        if (machine == RecipeMachine.LASER_ENGRAVER) {
            net.minecraft.world.item.Item now = items.getItem(0).getItem();
            if (now != syncedInput) {
                syncedInput = now;
                level.sendBlockUpdated(pos, state, state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
        }
    }

    public ItemStack shownInput() {
        return clientInput;
    }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = super.getUpdateTag(registries);
        if (machine == RecipeMachine.LASER_ENGRAVER) {
            tag.putString("shown_input", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(items.getItem(0).getItem()).toString());
        }
        return tag;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new RecipeMachineMenu(machine, containerId, inventory, items, upgrades, data, this);
    }
}
