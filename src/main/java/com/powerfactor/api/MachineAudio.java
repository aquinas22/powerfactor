package com.powerfactor.api;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Hook for client-side machine loops. Block entities call {@link #tick} from their client tick; the
 * client entry point installs the real implementation so common code never touches client classes.
 */
public final class MachineAudio {
    public interface Looping {
        /** True while the machine should be making its running sound. */
        boolean soundActive();

        /** Which loop to play; may change (e.g. crusher loaded vs idle). */
        net.minecraft.sounds.SoundEvent loopSound();

        default float loopPitch() {
            return 1.0F;
        }

        default float loopVolume() {
            return 0.6F;
        }
    }

    public static java.util.function.Consumer<BlockEntity> handler = be -> {};

    public static void tick(BlockEntity be) {
        handler.accept(be);
    }

    private MachineAudio() {}
}
