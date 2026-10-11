package com.powerfactor.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A module that goes in the electric drill's module slots. */
public class DrillModule extends Item {
    public enum Type {
        AREA(1), SPEED(3), SILK(1), FORTUNE(3), MAGNET(1), BATTERY(3);

        public final int max;

        Type(int max) {
            this.max = max;
        }
    }

    private final Type type;

    public DrillModule(Type type, Properties properties) {
        super(properties.stacksTo(type.max));
        this.type = type;
    }

    public Type type() {
        return type;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.powerfactor.drill_module." + type.name().toLowerCase(java.util.Locale.ROOT))
                .withStyle(ChatFormatting.AQUA));
        builder.accept(Component.translatable("tooltip.powerfactor.drill_module.install").withStyle(ChatFormatting.DARK_GRAY));
    }
}
