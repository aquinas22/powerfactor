package com.powerfactor;

import com.powerfactor.item.ChainsawItem;
import com.powerfactor.item.JetpackServer;
import com.powerfactor.item.TreeFeller;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Game-bus hooks for the powered hand tools: chainsaw felling and speed, jetpack flight. */
@EventBusSubscriber(modid = PowerFactor.MODID)
public final class ToolEvents {
    @SubscribeEvent
    static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        var stack = event.getEntity().getMainHandItem();
        // Woodcutter module: the drill cuts wood like a chainsaw.
        if (stack.getItem() instanceof com.powerfactor.item.DrillItem && com.powerfactor.item.DrillItem.powered(stack)
                && com.powerfactor.item.ToolModules.count(stack, com.powerfactor.item.DrillModule.Type.WOODCUTTER) > 0
                && event.getState().is(BlockTags.MINEABLE_WITH_AXE)) {
            event.setNewSpeed(Math.max(event.getNewSpeed(), ChainsawItem.SPEED));
        }
        if (stack.getItem() instanceof ChainsawItem) {
            if (!ChainsawItem.powered(stack)) {
                event.setNewSpeed(Math.min(event.getNewSpeed(), ChainsawItem.UNPOWERED_SPEED));
            } else {
                int speed = com.powerfactor.item.ToolModules.count(stack, com.powerfactor.item.DrillModule.Type.SPEED);
                event.setNewSpeed(event.getNewSpeed() * (1 + 0.5F * speed));
            }
        }
    }

    /** One cut at a tree's log brings the whole tree down (unless sneaking). */
    @SubscribeEvent
    static void onBreak(BreakBlockEvent event) {
        if (event.getPlayer() instanceof ServerPlayer p && p.level() instanceof ServerLevel l && !event.isCanceled()) {
            com.powerfactor.item.VeinMiner.onBreak(l, p, event.getPos(), event.getState());
        }
        if (!(event.getPlayer() instanceof ServerPlayer player) || player.isShiftKeyDown() || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        var stack = player.getMainHandItem();
        if (!TreeFeller.canFell(stack, player) || !event.getState().is(BlockTags.LOGS)) {
            return;
        }
        var tree = TreeFeller.findTree(level, event.getPos());
        if (!tree.isEmpty()) {
            TreeFeller.fell(level, player, stack, event.getPos(), tree);
        }
    }

    /** A flat chainsaw is a blunt instrument. */
    @SubscribeEvent
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || event.getSource().getDirectEntity() != attacker) {
            return;
        }
        var held = attacker.getMainHandItem();
        if (held.getItem() instanceof ChainsawItem) {
            event.setAmount(ChainsawItem.powered(held) ? event.getAmount() + com.powerfactor.item.ToolModules.overcharge(held, 3F)
                    : Math.min(event.getAmount(), 2F));
        } else if (held.getItem() instanceof com.powerfactor.item.ArcSaberItem && com.powerfactor.item.ArcSaberItem.lit(held)) {
            event.setAmount(event.getAmount() + com.powerfactor.item.ToolModules.overcharge(held, 3F));
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            TreeFeller.tick(level);
            com.powerfactor.item.VeinMiner.tick(level);
        }
        com.powerfactor.radio.RadioNet.tick(event.getServer());
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.tickCount % 20 == 0) com.powerfactor.fission.Radiation.tick(player);
            JetpackServer.tick(player);
            com.powerfactor.grid.WireBlock.checkPlayer((ServerLevel) player.level(), player);
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        JetpackServer.forget(event.getEntity().getUUID());
        com.powerfactor.item.VeinMiner.forget(event.getEntity().getUUID());
    }

    /** Any digging tool and a Vein Mining Matrix in an anvil: the tool learns vein mining, for 30 levels. */
    @SubscribeEvent
    static void onAnvil(net.neoforged.neoforge.event.AnvilUpdateEvent event) {
        if (!event.getRight().is(com.powerfactor.registry.ModItems.VEIN_MATRIX.get()) || !com.powerfactor.item.VeinMiner.fits(event.getLeft())) return;
        var out = event.getLeft().copy();
        out.set(com.powerfactor.registry.ModComponents.VEIN_MINER.get(), net.minecraft.util.Unit.INSTANCE);
        event.setOutput(out);
        event.setXpCost(30);
        event.setMaterialCost(1);
    }

    /** Fitted tools say so. */
    @SubscribeEvent
    static void onTooltip(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event) {
        if (com.powerfactor.item.VeinMiner.fitted(event.getItemStack())) {
            event.getToolTip().add(1, net.minecraft.network.chat.Component.translatable("tooltip.powerfactor.vein_miner").withStyle(net.minecraft.ChatFormatting.AQUA));
        }
    }

    private ToolEvents() {}
}
