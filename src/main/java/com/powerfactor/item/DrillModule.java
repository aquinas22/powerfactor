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
    /** Every module, the tools it fits, and how many of it one tool takes. */
    public enum Type {
        AREA(1, "drill_area_module", ToolKind.DRILL),
        SPEED(3, "drill_speed_module", ToolKind.DRILL, ToolKind.CHAINSAW),
        SILK(1, "drill_silk_module", ToolKind.DRILL),
        FORTUNE(3, "drill_fortune_module", ToolKind.DRILL),
        MAGNET(1, "drill_magnet_module", ToolKind.DRILL, ToolKind.CHAINSAW),
        BATTERY(3, "drill_battery_module", ToolKind.values()),
        EFFICIENCY(3, "efficiency_module", ToolKind.values()),
        THRUSTER(3, "thruster_module", ToolKind.JETPACK),
        PLATING(3, "plating_module", ToolKind.JETPACK),
        OVERCHARGE(3, "overcharge_module", ToolKind.CHAINSAW, ToolKind.SABER, ToolKind.ARC_GUN),
        CHAIN(2, "chain_module", ToolKind.SABER, ToolKind.ARC_GUN);

        public final int max;
        public final String id;
        private final java.util.Set<ToolKind> fits;

        Type(int max, String id, ToolKind... fits) {
            this.max = max;
            this.id = id;
            this.fits = java.util.EnumSet.copyOf(java.util.List.of(fits));
        }

        public boolean fits(ToolKind kind) {
            return kind != null && fits.contains(kind);
        }

        public java.util.Set<ToolKind> tools() {
            return fits;
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
        Component tools = Component.empty();
        boolean first = true;
        for (ToolKind k : type.tools()) {
            if (!first) {
                tools = tools.copy().append(", ");
            }
            tools = tools.copy().append(Component.translatable("tool_kind.powerfactor." + k.name().toLowerCase(java.util.Locale.ROOT)));
            first = false;
        }
        builder.accept(Component.translatable("tooltip.powerfactor.module.fits", tools).withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.powerfactor.module.max", type.max).withStyle(ChatFormatting.DARK_GRAY));
        builder.accept(Component.translatable("tooltip.powerfactor.drill_module.install").withStyle(ChatFormatting.DARK_GRAY));
    }
}
