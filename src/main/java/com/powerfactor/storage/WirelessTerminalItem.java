package com.powerfactor.storage;

import com.powerfactor.item.PoweredItem;
import com.powerfactor.registry.ModComponents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A handheld crafting terminal. Sneak-use on a Storage Controller to link it. The standard model
 * reaches 64 blocks; the quantum model is entangled with its controller and works at any distance in
 * the same dimension, as long as the controller's chunk is loaded.
 */
public class WirelessTerminalItem extends PoweredItem {
    public static final int OPEN_COST = 400, TICK_COST = 2;
    private final boolean quantum;

    public WirelessTerminalItem(boolean quantum, Properties properties) {
        super(properties);
        this.quantum = quantum;
    }

    @Override
    public int capacity() {
        return quantum ? 2_000_000 : 400_000;
    }

    public double range() {
        return quantum ? Double.MAX_VALUE : 64;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && player.isShiftKeyDown() && context.getLevel().getBlockEntity(context.getClickedPos()) instanceof StorageControllerBlockEntity) {
            if (!context.getLevel().isClientSide()) {
                context.getItemInHand().set(ModComponents.LINKED_CONTROLLER.get(), GlobalPos.of(context.getLevel().dimension(), context.getClickedPos()));
                player.sendOverlayMessage(Component.translatable("gui.powerfactor.wireless.linked"));
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        Host host = new Host(sp, hand, this);
        Component problem = host.problem();
        if (problem != null) {
            sp.sendOverlayMessage(problem.copy().withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        if (!drain(stack, OPEN_COST, sp)) {
            sp.sendOverlayMessage(Component.translatable("gui.powerfactor.wireless.empty").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new TerminalMenu(id, inv, TerminalBlockEntity.Kind.CRAFTING, host),
                stack.getHoverName()), buf -> buf.writeVarInt(TerminalBlockEntity.Kind.CRAFTING.ordinal()));
        return InteractionResult.SUCCESS;
    }

    /** The linked controller, followed live while the screen is open. */
    static final class Host implements TerminalMenu.Host {
        private final ServerPlayer player;
        private final InteractionHand hand;
        private final WirelessTerminalItem item;

        Host(ServerPlayer player, InteractionHand hand, WirelessTerminalItem item) {
            this.player = player;
            this.hand = hand;
            this.item = item;
        }

        private @Nullable StorageControllerBlockEntity controller() {
            GlobalPos link = player.getItemInHand(hand).get(ModComponents.LINKED_CONTROLLER.get());
            if (link == null || !(player.level() instanceof ServerLevel level) || !level.dimension().equals(link.dimension())) return null;
            if (!level.isLoaded(link.pos())) return null;
            return level.getBlockEntity(link.pos()) instanceof StorageControllerBlockEntity c ? c : null;
        }

        @Nullable Component problem() {
            ItemStack stack = player.getItemInHand(hand);
            GlobalPos link = stack.get(ModComponents.LINKED_CONTROLLER.get());
            if (link == null) return Component.translatable("gui.powerfactor.wireless.unlinked");
            if (!link.dimension().equals(player.level().dimension())) return Component.translatable("gui.powerfactor.wireless.dimension");
            if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(link.pos())) > item.range() * item.range()) return Component.translatable("gui.powerfactor.wireless.range");
            if (controller() == null) return Component.translatable("gui.powerfactor.wireless.missing");
            return null;
        }

        @Override
        public @Nullable StorageNetwork hostNetwork() {
            StorageControllerBlockEntity c = controller();
            return c == null ? null : c.ownNetwork();
        }

        @Override
        public boolean hostValid(Player p) {
            ItemStack stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof WirelessTerminalItem) || problem() != null) return false;
            return drain(stack, TICK_COST, player);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        GlobalPos link = stack.get(ModComponents.LINKED_CONTROLLER.get());
        builder.accept(link == null ? Component.translatable("tooltip.powerfactor.wireless.unlinked").withStyle(ChatFormatting.GRAY)
                : Component.translatable("tooltip.powerfactor.wireless.linked", link.pos().toShortString()).withStyle(ChatFormatting.DARK_AQUA));
        builder.accept(Component.translatable(quantum ? "tooltip.powerfactor.wireless.quantum" : "tooltip.powerfactor.wireless.range")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
