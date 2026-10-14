package com.powerfactor.weapon;

import com.powerfactor.item.PoweredItem;
import com.powerfactor.registry.ModQuantum;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** Fires a captured Singularity. Each shot costs one Singularity item and a large FE discharge. */
public class SingularityLauncherItem extends PoweredItem {
    public static final int SHOT_FE = 250_000;

    public SingularityLauncherItem(Properties properties) {
        super(properties);
    }

    @Override
    public int capacity() {
        return 1_000_000;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean creative = player.getAbilities().instabuild;
        ItemStack ammo = findAmmo(player);
        if (!creative && (ammo.isEmpty() || energy(stack) < SHOT_FE)) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable(ammo.isEmpty() ? "gui.powerfactor.launcher.no_ammo" : "gui.powerfactor.launcher.no_power")
                        .withStyle(ChatFormatting.RED));
            }
            level.playSound(player, player.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.8F, 0.8F);
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel server) {
            drain(stack, SHOT_FE, player);
            if (!creative) ammo.shrink(1);
            server.addFreshEntity(new SingularityEntity(server, player, player.getViewVector(1).scale(1.1)));
            server.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.PLAYERS, 1.2F, 0.6F);
            server.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 0.5F);
            FxPayload.send(server, new FxPayload(FxPayload.BOLT, player.getEyePosition(), player.getEyePosition(), 0, 1.6F, player.getId()));
        }
        player.getCooldowns().addCooldown(stack, 80);
        return InteractionResult.SUCCESS;
    }

    private static ItemStack findAmmo(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(ModQuantum.SINGULARITY.get())) return s;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        builder.accept(Component.translatable("tooltip.powerfactor.singularity_launcher").withStyle(ChatFormatting.LIGHT_PURPLE));
        builder.accept(Component.translatable("tooltip.powerfactor.singularity_launcher.ammo", String.format("%,d", SHOT_FE))
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
