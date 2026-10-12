package com.powerfactor.machine;

import com.powerfactor.PowerFactor;
import com.powerfactor.recipe.MachineRecipeKind;
import com.powerfactor.registry.ModSounds;
import com.powerfactor.upgrade.UpgradeType;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Everything that differs between the recipe-driven machines. */
public enum RecipeMachine {
    ALLOY_FURNACE("alloy_furnace", MachineRecipeKind.ALLOYING, 2, 1, false, false, 24, true,
            () -> new HeaterDrive(24, 1.33, false), EnumSet.complementOf(EnumSet.of(UpgradeType.VFD)), ModSounds.ALLOY_FURNACE_RUN::get,
            new int[][] {{71, 24}, {71, 46}}, new int[][] {{122, 35}}, null, 93, 39),
    ARC_FURNACE("arc_furnace", MachineRecipeKind.ARC_SMELTING, 2, 1, true, false, 64, true,
            () -> new HeaterDrive(64, 0.85, true), EnumSet.complementOf(EnumSet.of(UpgradeType.VFD)), ModSounds.ARC_FURNACE_RUN::get,
            new int[][] {{71, 24}, {71, 46}}, new int[][] {{122, 35}}, new int[] {94, 58}, 93, 39),
    WIRE_DRAWER("wire_drawer", MachineRecipeKind.WIRE_DRAWING, 1, 1, false, false, 12, false,
            () -> new MotorDrive(9), EnumSet.allOf(UpgradeType.class), ModSounds.WIRE_DRAWER_RUN::get,
            new int[][] {{71, 35}}, new int[][] {{122, 35}}, null, 93, 39),
    ROLLING_MILL("rolling_mill", MachineRecipeKind.ROLLING, 1, 1, false, false, 24, false,
            () -> new MotorDrive(18), EnumSet.allOf(UpgradeType.class), ModSounds.ROLLING_MILL_RUN::get,
            new int[][] {{71, 35}}, new int[][] {{122, 35}}, null, 93, 39),
    ORE_WASHER("ore_washer", MachineRecipeKind.WASHING, 1, 2, false, true, 12, false,
            () -> new MotorDrive(9), EnumSet.allOf(UpgradeType.class), ModSounds.ORE_WASHER_RUN::get,
            new int[][] {{71, 35}}, new int[][] {{122, 35}, {124, 60}}, null, 93, 39),
    /** Fibre laser through a photomask: the mask in the tool slot decides which chip it makes. */
    LASER_ENGRAVER("laser_engraver", MachineRecipeKind.LASER_ENGRAVING, 2, 1, true, false, 48, false,
            () -> new HeaterDrive(48, 0.33, false), EnumSet.complementOf(EnumSet.of(UpgradeType.VFD)), ModSounds.LASER_ENGRAVER_RUN::get,
            new int[][] {{71, 24}, {71, 46}}, new int[][] {{122, 35}}, new int[] {94, 58}, 0, 0);

    public final String id;
    public final MachineRecipeKind kind;
    public final int inputs, outputs;
    public final boolean electrode, tank;
    public final int fePerTick;
    public final boolean heater;
    public final Supplier<Drive> drive;
    public final Set<UpgradeType> upgrades;
    public final Supplier<SoundEvent> sound;
    public final int[][] inputPos, outputPos;
    public final int[] electrodePos;
    public final int arrowX, arrowY;

    RecipeMachine(String id, MachineRecipeKind kind, int inputs, int outputs, boolean electrode, boolean tank, int fePerTick, boolean heater,
            Supplier<Drive> drive, Set<UpgradeType> upgrades, Supplier<SoundEvent> sound, int[][] inputPos, int[][] outputPos,
            int[] electrodePos, int arrowX, int arrowY) {
        this.id = id;
        this.kind = kind;
        this.inputs = inputs;
        this.outputs = outputs;
        this.electrode = electrode;
        this.tank = tank;
        this.fePerTick = fePerTick;
        this.heater = heater;
        this.drive = drive;
        this.upgrades = upgrades;
        this.sound = sound;
        this.inputPos = inputPos;
        this.outputPos = outputPos;
        this.electrodePos = electrodePos;
        this.arrowX = arrowX;
        this.arrowY = arrowY;
    }

    /** What the tool slot takes: graphite electrodes for the arc furnace, photomasks for the engraver. */
    public boolean isTool(net.minecraft.world.item.ItemStack stack) {
        return this == LASER_ENGRAVER ? stack.getItem() instanceof com.powerfactor.item.PhotomaskItem
                : stack.is(com.powerfactor.registry.ModItems.GRAPHITE_ELECTRODE.get());
    }

    public int slotCount() {
        return inputs + outputs + (electrode ? 1 : 0);
    }

    public int electrodeSlot() {
        return inputs + outputs;
    }

    /** CE a stock machine draws at full tilt, for sizing the analyzer's waves. */
    public double ratedCe() {
        return fePerTick * 1.7;
    }

    public Identifier guiTexture() {
        return PowerFactor.id(switch (this) {
            case ALLOY_FURNACE -> "textures/gui/alloy_furnace.png";
            case ARC_FURNACE -> "textures/gui/arc_furnace.png";
            case LASER_ENGRAVER -> "textures/gui/laser_engraver.png";
            case ORE_WASHER -> "textures/gui/ore_washer.png";
            default -> "textures/gui/single_machine.png";
        });
    }
}
