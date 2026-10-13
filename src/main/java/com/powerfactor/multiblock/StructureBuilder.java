package com.powerfactor.multiblock;

import com.powerfactor.PowerFactor;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.registry.ModSounds;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** All-or-nothing assembly: preflight materials and space, then honour placement protection. */
@EventBusSubscriber(modid = PowerFactor.MODID)
public final class StructureBuilder {
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        BlockPos origin = event.getPos();
        var state = event.getLevel().getBlockState(origin);
        if (state.hasProperty(com.powerfactor.machine.TallMachineBlock.HALF)
                && state.getValue(com.powerfactor.machine.TallMachineBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
            origin = origin.below();
        }
        if (!player.isShiftKeyDown() || event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getLevel().getBlockEntity(origin) instanceof Buildable controller)) {
            return;
        }
        Structure structure = controller.buildStructure();
        ItemStack held = player.getMainHandItem();
        if (!held.isEmpty() && structure.parts().stream().noneMatch(p -> held.is(p.block().get().asItem()))) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getLevel() instanceof ServerLevel level) {
            assemble(level, origin, level.getBlockState(origin).getValue(MachineBlock.FACING), player, structure);
        }
    }

    public static boolean assemble(ServerLevel level, BlockPos origin, Direction facing, Player player, Structure structure) {
        List<Structure.Part> missing = new ArrayList<>();
        Map<Item, Integer> needed = new LinkedHashMap<>();
        for (var part : structure.parts()) {
            BlockPos at = structure.worldPos(origin, facing, part);
            if (!level.hasChunkAt(at) || level.isOutsideBuildHeight(at) || !level.getWorldBorder().isWithinBounds(at)) {
                return fail(player, "blocked");
            }
            if (part.matches(level.getBlockState(at))) {
                if (level.getBlockEntity(at) instanceof CasingBlockEntity casing && casing.controllerPos() != null
                        && !origin.equals(casing.controllerPos())) return fail(player, "blocked");
                continue;
            }
            var state = part.block().get().defaultBlockState();
            ItemStack material = new ItemStack(part.block().get());
            if (!level.getBlockState(at).canBeReplaced() || level.getBlockEntity(at) != null || !player.mayBuild()
                    || !level.mayInteract(player, at) || !player.mayUseItemAt(at, Direction.UP, material)
                    || !state.canSurvive(level, at) || !level.isUnobstructed(state, at, CollisionContext.of(player))) {
                return fail(player, "blocked");
            }
            missing.add(part);
            needed.merge(material.getItem(), 1, Integer::sum);
        }
        if (missing.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("gui.powerfactor.multiblock.complete"));
            return true;
        }
        if (!player.isCreative()) {
            for (var entry : needed.entrySet()) {
                int have = 0;
                for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                    ItemStack stack = player.getInventory().getItem(slot);
                    if (stack.is(entry.getKey())) {
                        have += stack.getCount();
                    }
                }
                if (have < entry.getValue()) {
                    player.sendOverlayMessage(Component.translatable("gui.powerfactor.multiblock.materials", entry.getValue() - have,
                            new ItemStack(entry.getKey()).getHoverName()));
                    return false;
                }
            }
        }
        List<BlockSnapshot> snapshots = new ArrayList<>();
        for (var part : missing) {
            BlockPos at = structure.worldPos(origin, facing, part);
            snapshots.add(BlockSnapshot.create(level.dimension(), level, at));
            // Defer neighbour notifications until placement hooks have approved the entire structure.
            if (!level.setBlock(at, part.block().get().defaultBlockState(), Block.UPDATE_CLIENTS)) {
                rollback(level, snapshots);
                return fail(player, "blocked");
            }
        }
        if (EventHooks.onMultiBlockPlace(player, snapshots, Direction.UP)) {
            rollback(level, snapshots);
            return fail(player, "blocked");
        }
        if (!player.isCreative()) {
            for (var entry : needed.entrySet()) {
                int remaining = entry.getValue();
                for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
                    ItemStack stack = player.getInventory().getItem(slot);
                    if (stack.is(entry.getKey())) {
                        int take = Math.min(remaining, stack.getCount());
                        stack.shrink(take);
                        remaining -= take;
                    }
                }
            }
            player.getInventory().setChanged();
        }
        for (var snapshot : snapshots) {
            level.updateNeighborsAt(snapshot.getPos(), level.getBlockState(snapshot.getPos()).getBlock());
        }
        level.playSound(null, origin, ModSounds.UPGRADE_INSTALL.get(), SoundSource.BLOCKS, 0.8F, 0.8F);
        player.sendOverlayMessage(Component.translatable("gui.powerfactor.multiblock.built", missing.size()));
        return true;
    }

    private static void rollback(ServerLevel level, List<BlockSnapshot> snapshots) {
        boolean restoring = level.restoringBlockSnapshots;
        level.restoringBlockSnapshots = true;
        try {
            for (int i = snapshots.size() - 1; i >= 0; i--) {
                snapshots.get(i).restore(Block.UPDATE_ALL);
            }
        } finally {
            level.restoringBlockSnapshots = restoring;
        }
    }

    private static boolean fail(Player player, String reason) {
        player.sendOverlayMessage(Component.translatable("gui.powerfactor.multiblock." + reason));
        return false;
    }

    private StructureBuilder() {}
}
