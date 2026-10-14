package com.powerfactor.storage.craft;

import java.util.List;
import net.minecraft.server.level.ServerLevel;

/** A network device that can execute patterns: the Assembler (crafting) or Pattern Provider (processing). */
public interface PatternHolder {
    List<PatternData> patterns();

    /**
     * Try to accept one execution of {@code pattern}: the inputs are already pulled from the job and
     * are handed over in {@code inputs} (one stack per grid cell for crafting patterns). Return false
     * to refuse, in which case the job keeps the inputs.
     */
    boolean accept(ServerLevel level, PatternData pattern, List<net.minecraft.world.item.ItemStack> inputs);

    /** Whether it can take another execution right now (not busy, has room). */
    boolean ready(PatternData pattern);
}
