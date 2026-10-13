package com.powerfactor.steam;

import com.powerfactor.PowerFactorConfig;
import com.powerfactor.api.MachineAudio;
import com.powerfactor.device.HydrogenTanks;
import com.powerfactor.machine.CoalTurbineBlockEntity;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.multiblock.Buildable;
import com.powerfactor.multiblock.Structure;
import com.powerfactor.multiblock.StructureAssembly;
import com.powerfactor.power.BoilerModel;
import com.powerfactor.registry.ModBlockEntities;
import com.powerfactor.registry.ModBlocks;
import com.powerfactor.registry.ModFluids;
import com.powerfactor.registry.ModSounds;
import com.powerfactor.scada.ScadaReading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
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
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import org.jspecify.annotations.Nullable;

/** A 3x3x3 coal-fired pressure vessel: cold water in, usable steam out. */
public final class SteamBoilerBlockEntity extends MachineBlockEntity implements Buildable, MachineAudio.Looping {
    public static final int TANK = 32000, WATER = 0, STEAM = 1;
    public static final Structure STRUCTURE = new Structure.Builder()
            .key('B', ModBlocks.BOILER_CASING).key('H', ModBlocks.BOILER_FIREBOX).key('D', ModBlocks.STEAM_DRUM)
            .layer(0, "BCB", "BHB", "BBB")
            .layer(1, "BBB", "BDB", "BBB")
            .layer(2, "BBB", "BDB", "BBB").build();
    private final StructureAssembly assembly = new StructureAssembly();
    private final BoilerModel boiler = new BoilerModel();
    private final HydrogenTanks tanks = new HydrogenTanks(TANK, new net.minecraft.world.level.material.Fluid[] {Fluids.WATER, ModFluids.STEAM.get()},
            new boolean[] {false, true}, this::setChanged);
    private final SimpleContainer fuel = new SimpleContainer(1) {
        @Override public boolean canPlaceItem(int slot, ItemStack stack) { return CoalTurbineBlockEntity.isFuel(stack); }
        @Override public boolean stillValid(Player player) { return Container.stillValidBlockEntity(SteamBoilerBlockEntity.this, player); }
        @Override public void setChanged() { super.setChanged(); SteamBoilerBlockEntity.this.setChanged(); }
    };
    private final ResourceHandler<ItemResource> fuelHandler = VanillaContainerWrapper.of(fuel);
    private int lastSteam, syncedHeat;
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case SteamBoilerMenu.WATER -> tanks.amount(WATER);
                case SteamBoilerMenu.STEAM -> tanks.amount(STEAM);
                case SteamBoilerMenu.HEAT -> boiler.heat();
                case SteamBoilerMenu.PRESSURE -> tanks.amount(STEAM) * 1000 / TANK;
                case SteamBoilerMenu.FUEL -> boiler.fuelPermille();
                case SteamBoilerMenu.FLAGS -> (isRunning() ? 1 : 0) | (formed() ? 2 : 0) | (boiler.tripped() ? 4 : 0) | (boiler.pressureHeld() ? 8 : 0);
                case SteamBoilerMenu.REDSTONE -> redstoneMode().ordinal();
                case SteamBoilerMenu.RATE -> lastSteam;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {}
        @Override public int getCount() { return SteamBoilerMenu.COUNT; }
    };

    public SteamBoilerBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.STEAM_BOILER.get(), pos, state); }
    @Override public Structure buildStructure() { return STRUCTURE; }
    public boolean formed() { return assembly.formed(); }
    public BoilerModel model() { return boiler; }
    public HydrogenTanks getTanks() { return tanks; }
    public ResourceHandler<ItemResource> getFuelHandler() { return fuelHandler; }
    public SimpleContainer fuel() { return fuel; }
    @Override protected Container inventory() { return fuel; }

    @Override public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(MachineBlock.FACING);
        assembly.tick(level, pos, facing, STRUCTURE);
        boolean enabled = formed() && enabled(level, pos);
        if (enabled && !boiler.tripped() && !boiler.pressureHeld() && tanks.amount(STEAM) < TANK * 3 / 5 && boiler.needsFuel()) {
            ItemStack stack = fuel.getItem(0);
            int burn = CoalTurbineBlockEntity.isFuel(stack) ? stack.getBurnTime(null, level.fuelValues()) : 0;
            if (burn > 0) {
                int energy = PowerFactorConfig.SPEC.isLoaded() ? PowerFactorConfig.TURBINE_FE_PER_BURN_TICK.getAsInt() : 40;
                boiler.addFuel((long) burn * energy * 2);
                var remainder = stack.getCraftingRemainder();
                stack.shrink(1);
                if (stack.isEmpty()) fuel.setItem(0, remainder == null ? ItemStack.EMPTY : remainder.create());
                fuel.setChanged();
            }
        }
        int previousHeat = boiler.heat();
        var output = boiler.tick(enabled, tanks.amount(WATER), tanks.amount(STEAM), TANK);
        lastSteam = output.steamMade();
        if (lastSteam > 0) {
            tanks.add(WATER, -output.waterUsed());
            tanks.add(STEAM, lastSteam);
        }
        if (previousHeat != boiler.heat() || output.fuelSpent() > 0) setChanged();
        setRunning(level, pos, getBlockState(), output.firing());
        if (Math.abs(boiler.heat() - syncedHeat) >= 10) {
            syncedHeat = boiler.heat();
            level.sendBlockUpdated(pos, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        if (output.firing() && level.getGameTime() % 10 == 0) {
            BlockPos chimney = pos.offset(new BlockPos(0, 3, 2).rotate(Structure.rotation(facing)));
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    chimney.getX() + 0.5, chimney.getY() + 0.7, chimney.getZ() + 0.5, 1, 0.07, 0, 0.07, 0.02);
        }
    }

    @Override public void setRemoved() {
        if (level instanceof ServerLevel server) assembly.release(server, worldPosition);
        super.setRemoved();
    }
    @Override public int comparatorSignal() { return 15 * tanks.amount(STEAM) / TANK; }
    @Override public boolean soundActive() { return isRunning(); }
    @Override public SoundEvent loopSound() { return ModSounds.BOILER_RUN.get(); }
    @Override public float loopVolume() { return 0.6F; }
    @Override public ScadaReading scadaReading() {
        var status = !formed() || boiler.tripped() ? ScadaReading.Status.ALARM
                : lastSteam > 0 ? ScadaReading.Status.RUNNING : boiler.pressureHeld() ? ScadaReading.Status.OFF
                : tanks.amount(WATER) == 0 ? ScadaReading.Status.WARNING : ScadaReading.Status.OFF;
        return new ScadaReading(worldPosition, getDisplayName(), status, 0, 0,
                Component.translatable(!formed() ? "scada.powerfactor.multiblock.incomplete" : boiler.tripped() ? "scada.powerfactor.boiler.tripped"
                        : boiler.pressureHeld() ? "scada.powerfactor.boiler.pressure_hold"
                        : "scada.powerfactor.boiler", boiler.temperatureC(), tanks.amount(STEAM), lastSteam),
                true, remoteEnabled(), false, 0, 0);
    }
    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, fuel.getItems());
        tanks.deserialize(input.childOrEmpty("tanks"));
        boiler.load(input.getIntOr("thermal", 0), input.getBooleanOr("tripped", false),
                input.getLongOr("fuel_heat", 0), input.getLongOr("fuel_heat_max", 0), input.getBooleanOr("pressure_hold", false));
    }
    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, fuel.getItems());
        tanks.serialize(output.child("tanks"));
        output.putInt("thermal", boiler.heat());
        output.putBoolean("tripped", boiler.tripped());
        output.putLong("fuel_heat", boiler.fuel());
        output.putLong("fuel_heat_max", boiler.fuelMax());
        output.putBoolean("pressure_hold", boiler.pressureHeld());
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putInt("thermal", boiler.heat());
        tag.putBoolean("tripped", boiler.tripped());
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new SteamBoilerMenu(id, inventory, worldPosition, fuel, data, this);
    }
}
