package com.powerfactor.client;

import com.powerfactor.elevator.ElevatorBlock;
import com.powerfactor.elevator.ElevatorPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Standing on an elevator, a jump rides up and a sneak rides down (each press once). */
public final class ElevatorClient {
    private static boolean jumpDown, sneakDown;

    public static void tick() {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.level == null || mc.gui.screen() != null) {
            jumpDown = sneakDown = false;
            return;
        }
        boolean jump = player.input.keyPresses.jump(), sneak = player.input.keyPresses.shift();
        BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.3, player.getZ());
        boolean on = mc.level.getBlockState(below).getBlock() instanceof ElevatorBlock && !player.getAbilities().flying;
        if (on && jump && !jumpDown && ElevatorBlock.target(mc.level, below, true) != null) ClientPacketDistributor.sendToServer(new ElevatorPayload(true));
        if (on && sneak && !sneakDown && ElevatorBlock.target(mc.level, below, false) != null) ClientPacketDistributor.sendToServer(new ElevatorPayload(false));
        jumpDown = jump;
        sneakDown = sneak;
    }

    private ElevatorClient() {}
}
