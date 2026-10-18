package com.powerfactor.powernet;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

/** Net Configurator: sneak-use a device to copy its network and settings; use another to paste them. */
public class ConfiguratorItem extends Item {
    public ConfiguratorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel().getBlockEntity(ctx.getClickedPos()) instanceof PowerNodeBlockEntity d)) return InteractionResult.PASS;
        if (!(ctx.getPlayer() instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        ItemStack stack = ctx.getItemInHand();
        if (player.isShiftKeyDown()) {
            CompoundTag t = new CompoundTag();
            t.putInt("network", d.network());
            t.putInt("priority", d.priority());
            t.putInt("limit", d.limit());
            t.putBoolean("surge", d.surge());
            t.putBoolean("unlimited", d.unlimited());
            t.putBoolean("chunk_load", d.chunkLoad());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
            player.sendOverlayMessage(Component.translatable("gui.powerfactor.power_net.copied").withStyle(ChatFormatting.AQUA));
        } else {
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            if (data == null) return InteractionResult.PASS;
            CompoundTag t = data.copyTag();
            PowerNet net = PowerNets.get(player.level().getServer()).net(t.getIntOr("network", -1));
            if (net != null && net.canUse(player)) d.setNetwork(net.id);
            d.applySettings(d.customName(), t.getIntOr("priority", 0), t.getIntOr("limit", d.kind().defaultLimit), t.getBooleanOr("surge", false),
                    t.getBooleanOr("unlimited", false), t.getBooleanOr("chunk_load", false));
            player.sendOverlayMessage(Component.translatable("gui.powerfactor.power_net.pasted").withStyle(ChatFormatting.AQUA));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(DataComponents.CUSTOM_DATA);
    }
}
