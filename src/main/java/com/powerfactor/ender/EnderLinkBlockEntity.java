package com.powerfactor.ender;

import com.powerfactor.api.ReactiveLoad;
import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.registry.ModBlockEntities;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import org.jspecify.annotations.Nullable;

/**
 * An Ender link. Transmitters turn FE into an EE carrier and beam it into their channel; that
 * conversion draws EE from their own grid (0.9 EE per FE sent). Receivers pull the channel's EE and
 * rectify it back to FE at 85%. Both radiate interference in proportion to how hard they work.
 */
public class EnderLinkBlockEntity extends MachineBlockEntity implements ReactiveLoad, com.powerfactor.api.MachineAudio.Looping {
    public static final int RATE = 256, BUFFER = 16_000;
    public static final double EE_PER_FE = 0.9, EFFICIENCY = 0.85;

    private final boolean transmitter;
    private final SimpleEnergyHandler buffer;
    private int channel;
    private int lastFlow;
    private double load;
    private float spin, prevSpin;
    private boolean clientActive;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case EnderLinkMenu.DATA_CHANNEL -> channel;
                case EnderLinkMenu.DATA_FLOW -> lastFlow;
                case EnderLinkMenu.DATA_STORED -> buffer.getAmountAsInt() / 10;
                case EnderLinkMenu.DATA_INTERFERENCE -> level == null ? 0 : (int) Math.round(EnderNetwork.interference(level, worldPosition) * 100);
                case EnderLinkMenu.DATA_EE -> (int) Math.round(eeDemand());
                case EnderLinkMenu.DATA_REDSTONE -> redstoneMode().ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return EnderLinkMenu.DATA_COUNT;
        }
    };

    public EnderLinkBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENDER_LINK.get(), pos, state);
        this.transmitter = state.getBlock() instanceof EnderLinkBlock b && b.transmitter();
        this.buffer = new SimpleEnergyHandler(BUFFER, transmitter ? RATE * 4 : 0, transmitter ? 0 : RATE * 4) {
            @Override
            protected void onEnergyChanged(int previousAmount) {
                setChanged();
            }
        };
    }

    public boolean transmitter() {
        return transmitter;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        lastFlow = 0;
        if (enabled(level, pos)) {
            if (transmitter) {
                int send = (int) Math.min(Math.min(RATE, buffer.getAmountAsInt()), EnderNetwork.room(channel) / EE_PER_FE);
                if (send > 0) {
                    buffer.set(buffer.getAmountAsInt() - send);
                    EnderNetwork.deposit(channel, send * EE_PER_FE);
                    lastFlow = send;
                }
            } else {
                int room = buffer.getCapacityAsInt() - buffer.getAmountAsInt();
                double ee = EnderNetwork.withdraw(channel, Math.min(RATE * EE_PER_FE, room / EFFICIENCY * EE_PER_FE));
                int fe = (int) (ee / EE_PER_FE * EFFICIENCY);
                if (fe > 0) {
                    buffer.set(buffer.getAmountAsInt() + fe);
                    lastFlow = fe;
                }
                for (Direction d : Direction.values()) {
                    EnergyHandler target = level.getCapability(Capabilities.Energy.BLOCK, pos.relative(d), d.getOpposite());
                    if (target != null && !(level.getBlockEntity(pos.relative(d)) instanceof EnderLinkBlockEntity)) {
                        EnergyHandlerUtil.move(buffer, target, buffer.getAmountAsInt(), null);
                    }
                }
            }
        }
        load = (double) lastFlow / RATE;
        if (load > 0) {
            EnderNetwork.radiate(level, pos, load);
            if (load > 0.8 && level.getRandom().nextInt(200) == 0) {
                level.playSound(null, pos, ModSounds.WIRELESS_SURGE.get(), SoundSource.BLOCKS, 0.8F, 0.9F + level.getRandom().nextFloat() * 0.2F);
            }
        }
        boolean active = lastFlow > 0;
        if (active != isRunning()) {
            setRunning(level, pos, state, active);
        }
    }

    @Override
    public void clientTick() {
        super.clientTick();
        prevSpin = spin;
        spin += isRunning() ? 9F : 1.5F;
    }

    public float spin(float partial) {
        return prevSpin + (spin - prevSpin) * partial;
    }

    /** Transmitting is a big reactive load on the sending grid. */
    @Override
    public double eeDemand() {
        return transmitter ? lastFlow * EE_PER_FE : 0;
    }

    public void setChannel(int channel) {
        this.channel = Math.floorMod(channel, 16);
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public int channel() {
        return channel;
    }

    public EnergyHandler getEnergy() {
        return buffer;
    }

    @Override
    public boolean soundActive() {
        return isRunning();
    }

    @Override
    public SoundEvent loopSound() {
        return ModSounds.WIRELESS_HUM.get();
    }

    @Override
    public float loopVolume() {
        return 0.4F;
    }

    @Override
    public ScadaReading scadaReading() {
        double interference = level == null ? 0 : EnderNetwork.interference(level, worldPosition);
        ScadaReading.Status status = interference > 1.0 ? ScadaReading.Status.ALARM : interference > 0.5 ? ScadaReading.Status.WARNING
                : lastFlow > 0 ? ScadaReading.Status.RUNNING : ScadaReading.Status.OFF;
        Component detail = Component.translatable(transmitter ? "scada.powerfactor.ender.tx" : "scada.powerfactor.ender.rx", channel + 1,
                (int) Math.round(interference * 100));
        return new ScadaReading(worldPosition, getDisplayName(), status, lastFlow, (int) Math.round(eeDemand()), detail, true, remoteEnabled(),
                !transmitter, 1F, 60F);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        buffer.deserialize(input);
        channel = input.getIntOr("channel", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        buffer.serialize(output);
        output.putInt("channel", channel);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("channel", channel);
        return tag;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new EnderLinkMenu(id, inventory, worldPosition, data, this);
    }
}
