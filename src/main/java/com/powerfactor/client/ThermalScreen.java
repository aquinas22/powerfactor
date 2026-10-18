package com.powerfactor.client;

import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.steam.ThermalMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Live instrumentation for the steam plant and its water/heat supply machines. */
public final class ThermalScreen extends MachineScreen<ThermalMenu> {
    private static final int TOP = Skin.Layout.CONTENT_TOP, H = Skin.Layout.contentHeight(166);
    public ThermalScreen(ThermalMenu menu, Inventory inventory, Component title) { super(menu, inventory, title, 166); }
    @Override protected List<Tab> tabs() { return menu.device == ThermalMenu.Device.RAIN_COLLECTOR || exchanger() ? List.of(Tab.CONFIG) : List.of(Tab.ELECTRICAL, Tab.CONFIG); }
    private boolean exchanger() { return menu.device == ThermalMenu.Device.HEAT_EXCHANGER; }
    /** Heat exchanger: the loop coolant in its hot side, or null when empty. */
    private com.powerfactor.fission.ReactorCore.@org.jspecify.annotations.Nullable Coolant loopCoolant() {
        int i = menu.get(ThermalMenu.DATA_EE) - 1;
        return i < 0 || i >= com.powerfactor.fission.ReactorCore.Coolant.values().length ? null : com.powerfactor.fission.ReactorCore.Coolant.values()[i];
    }
    private static final int[] HOT_COLORS = {0xFFE8F0F4, 0xFFA8D8F4, 0xFFF2B456, 0xFFFF6A2E}, COLD_COLORS = {0xFF3F76E4, 0xFF5E9CD8, 0xFFC4CAD2, 0xFFDCEEE6};
    private static final String[] HOT_FLUIDS = {"fluid.powerfactor.steam", "fluid.powerfactor.hot_heavy_water", "fluid.powerfactor.hot_sodium",
            "fluid.powerfactor.hot_molten_salt"}, COLD_FLUIDS = {"block.minecraft.water", "fluid.powerfactor.heavy_water", "fluid.powerfactor.liquid_sodium",
            "fluid.powerfactor.molten_salt"};
    private int loopIndex() { var c = loopCoolant(); return c == null ? 2 : c.ordinal(); }
    /** Exchanger gauges left to right: hot in, cold out | LCD | water in, steam out. */
    private static final int[] EX_GAUGES = {8, 32, 128, 152};
    private static final int EX_LCD_X = 56, EX_LCD_W = 64;
    @Override protected MachineBlockEntity.RedstoneMode redstoneMode() { return menu.redstoneMode(); }
    private boolean active() { return (menu.get(ThermalMenu.DATA_FLAGS) & 1) != 0; }
    private boolean fault() { return (menu.get(ThermalMenu.DATA_FLAGS) & 4) != 0; }
    private Component status() {
        String key = switch (menu.device) {
            case STEAM_TURBINE, HP_TURBINE -> (menu.get(ThermalMenu.DATA_FLAGS) & 2) == 0 ? "multiblock.incomplete" : fault() ? "steam_turbine.backed_up"
                    : active() ? menu.get(ThermalMenu.DATA_FE) > 0 ? "panel.generating" : "steam_turbine.idling"
                    : menu.unsigned(ThermalMenu.DATA_TANK) == 0 ? "steam_turbine.no_steam" : "turbine.syncing";
            case COOLING_TOWER -> fault() ? "cooling.blocked" : active() ? "cooling.running" : "cooling.idle";
            case GEOTHERMAL -> menu.get(ThermalMenu.DATA_RATE) == 0 ? "geothermal.no_heat" : active() ? "panel.generating" : "battery.standby";
            case THERMOELECTRIC -> menu.get(ThermalMenu.DATA_RATE) == 0 ? "thermoelectric.no_gradient" : active() ? "panel.generating" : "battery.standby";
            case RAIN_COLLECTOR -> active() ? "rain.collecting" : "rain.waiting";
            case HEAT_EXCHANGER -> active() ? "heat_exchanger.running" : menu.unsigned(ThermalMenu.DATA_TANK) == 0 ? "heat_exchanger.no_hot"
                    : menu.unsigned(ThermalMenu.DATA_TANK3) == 0 ? "heat_exchanger.no_water" : "heat_exchanger.backed_up";
        };
        return Component.translatable("gui.powerfactor." + key);
    }
    private int color() {
        boolean incomplete = turbine() && (menu.get(ThermalMenu.DATA_FLAGS) & 2) == 0;
        return fault() || incomplete ? Skin.FE : active() ? Skin.TEAL : Skin.OUTPUT;
    }
    @Override protected Electrical electrical() {
        boolean turbine = turbine();
        int fe = menu.get(ThermalMenu.DATA_FE), ee = turbine ? (short) menu.get(ThermalMenu.DATA_EE) : 0;
        return new Electrical(turbine ? active() ? 1 : 0 : 1, turbine ? active() ? menu.unsigned(ThermalMenu.DATA_RPM) / 60.0 : 0 : 60,
                fe, ee, turbine ? (highPressure() ? com.powerfactor.steam.HighPressureTurbineBlockEntity.HP.ratedCe() : 1000) : menu.device == ThermalMenu.Device.COOLING_TOWER ? 32 : 64, status(), color());
    }
    private boolean turbine() { return menu.device == ThermalMenu.Device.STEAM_TURBINE || menu.device == ThermalMenu.Device.HP_TURBINE; }
    private boolean highPressure() { return menu.device == ThermalMenu.Device.HP_TURBINE; }
    private int bufferCapacity() { return highPressure() ? com.powerfactor.steam.HighPressureTurbineBlockEntity.HP.buffer()
            : turbine() ? 128000 : 8000; }
    private int tankCapacity() { return highPressure() ? com.powerfactor.steam.HighPressureTurbineBlockEntity.HP.tank() : turbine() ? 32000 : 16000; }
    private String fluid(boolean second) {
        return switch (menu.device) {
            case STEAM_TURBINE -> second ? "fluid.powerfactor.hot_water" : "fluid.powerfactor.steam";
            case COOLING_TOWER -> second ? "block.minecraft.water" : "fluid.powerfactor.hot_water";
            default -> "block.minecraft.water";
        };
    }
    @Override protected void extractMain(GuiGraphicsExtractor g, int mx, int my) {
        if (exchanger()) {
            skinBackground(g);
            Skin.lamp(g, leftPos + 162, topPos + 6, color());
            int cap = com.powerfactor.fission.HeatExchangerBlockEntity.TANK, li = loopIndex();
            int[] amounts = {menu.unsigned(ThermalMenu.DATA_TANK), menu.unsigned(ThermalMenu.DATA_TANK2), menu.unsigned(ThermalMenu.DATA_TANK3),
                    menu.unsigned(ThermalMenu.DATA_RPM)};
            int[] colors = {HOT_COLORS[li], COLD_COLORS[li], COLD_COLORS[0], HOT_COLORS[0]};
            for (int i = 0; i < 4; i++) GuiUtil.fluidGauge(g, leftPos + EX_GAUGES[i], topPos + TOP, 16, H, amounts[i], cap, colors[i]);
            Skin.lcd(g, leftPos + EX_LCD_X, topPos + TOP, EX_LCD_W, H);
            return;
        }
        skinBackground(g);
        Skin.lamp(g, leftPos + 162, topPos + 6, color());
        Skin.lcd(g, leftPos + 56, topPos + TOP, 92, H);   // 8 px either side: between the gauges and the FE tube
        if (turbine() || menu.device == ThermalMenu.Device.COOLING_TOWER) {
            GuiUtil.fluidGauge(g, leftPos + 8, topPos + TOP, 16, H, menu.unsigned(ThermalMenu.DATA_TANK), tankCapacity(),
                    turbine() ? 0xFFDCE7EF : 0xFF4AABB3);
            GuiUtil.fluidGauge(g, leftPos + 32, topPos + TOP, 16, H, menu.unsigned(ThermalMenu.DATA_TANK2), tankCapacity(),
                    turbine() ? 0xFF4AABB3 : 0xFF3F76E4);
        } else if (menu.device == ThermalMenu.Device.RAIN_COLLECTOR) {
            GuiUtil.fluidGauge(g, leftPos + 8, topPos + TOP, 40, H, menu.unsigned(ThermalMenu.DATA_TANK), 16000, 0xFF3F76E4);
        } else {
            Skin.scope(g, leftPos + 8, topPos + TOP, 40, H, menu.get(ThermalMenu.DATA_FE) / 64F, active());
        }
        if (menu.device != ThermalMenu.Device.RAIN_COLLECTOR) {
            Skin.energyTube(g, leftPos + 156, topPos + TOP, 12, H, menu.unsigned(ThermalMenu.DATA_STORED) * 10, bufferCapacity(), active());
        }
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        if (exchanger()) {
            int li = loopIndex();
            Skin.lcdRows(g, font, EX_LCD_X, TOP, EX_LCD_W, H, java.util.List.of(
                    new Skin.Row(Skin.statusIcon(color()), status(), color()),
                    new Skin.Row(Skin.Icon.STEAM, menu.get(ThermalMenu.DATA_RATE) + " mB/t", Skin.LCD_TEXT),
                    new Skin.Row(Skin.Icon.HEAT, Skin.compact(menu.get(ThermalMenu.DATA_FE) * 100) + " FE/t", 0xFFFFB347),
                    new Skin.Row(Skin.Icon.FLUID, loopCoolant() == null ? Component.literal("—")
                            : Component.translatable("gui.powerfactor.heat_exchanger.short." + loopCoolant().name().toLowerCase(java.util.Locale.ROOT)),
                            loopCoolant() == null ? Skin.LCD_DIM : COLD_COLORS[li], Component.translatable(COLD_FLUIDS[li]))));
            return;
        }
        java.util.List<Skin.Row> rows = new java.util.ArrayList<>(java.util.List.of(new Skin.Row(Skin.statusIcon(color()), status(), color())));
        if (turbine()) {
            rows.add(new Skin.Row(Skin.Icon.ROTOR, menu.unsigned(ThermalMenu.DATA_RPM) + " RPM", Skin.LCD_TEXT));
            rows.add(new Skin.Row(Skin.Icon.FE, menu.get(ThermalMenu.DATA_FE) + "/t", Skin.FE));
            rows.add(new Skin.Row(Skin.Icon.STEAM, menu.get(ThermalMenu.DATA_RATE) + " mB/t", Skin.LCD_DIM));
        } else if (menu.device == ThermalMenu.Device.COOLING_TOWER || menu.device == ThermalMenu.Device.RAIN_COLLECTOR) {
            rows.add(new Skin.Row(Skin.Icon.FLUID, menu.get(ThermalMenu.DATA_RATE) + " mB/t", Skin.LCD_TEXT));
            rows.add(new Skin.Row(Skin.Icon.FE, (menu.device == ThermalMenu.Device.RAIN_COLLECTOR ? 0 : menu.get(ThermalMenu.DATA_FE)) + "/t", Skin.FE));
        } else {
            rows.add(new Skin.Row(Skin.Icon.FE, menu.get(ThermalMenu.DATA_FE) + "/t", Skin.FE));
            rows.add(new Skin.Row(Skin.Icon.HEAT, Component.translatable("gui.powerfactor.thermal.available", menu.get(ThermalMenu.DATA_RATE)), Skin.LCD_DIM));
        }
        Skin.lcdRows(g, font, 56, TOP, 92, H, rows);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        super.extractRenderState(g, mx, my, partial);
        if (exchanger()) {
            int li = loopIndex(), cap = com.powerfactor.fission.HeatExchangerBlockEntity.TANK;
            String[] fluids = {HOT_FLUIDS[li], COLD_FLUIDS[li], COLD_FLUIDS[0], HOT_FLUIDS[0]};
            int[] data = {ThermalMenu.DATA_TANK, ThermalMenu.DATA_TANK2, ThermalMenu.DATA_TANK3, ThermalMenu.DATA_RPM};
            for (int i = 0; i < 4; i++) {
                if (isHovering(EX_GAUGES[i], TOP, 16, H, mx, my)) {
                    g.setTooltipForNextFrame(font, Component.translatable("gui.powerfactor.fluid_amount", Component.translatable(fluids[i]),
                            String.format("%,d", menu.unsigned(data[i])), String.format("%,d", cap)), mx, my);
                }
            }
            if (isHovering(EX_LCD_X, TOP, EX_LCD_W, H, mx, my) || isHovering(160, 3, 12, 12, mx, my)) g.setTooltipForNextFrame(font, status(), mx, my);
            return;
        }
        if (isHovering(56, TOP, 92, H, mx, my) || isHovering(160, 3, 12, 12, mx, my)) {
            g.setTooltipForNextFrame(font, status(), mx, my);
        } else if (menu.device != ThermalMenu.Device.RAIN_COLLECTOR && isHovering(156, TOP, 12, H, mx, my)) {
            GuiUtil.energyTooltip(g, font, menu.unsigned(ThermalMenu.DATA_STORED) * 10, bufferCapacity(), mx, my);
        } else if (turbine() || menu.device == ThermalMenu.Device.COOLING_TOWER || menu.device == ThermalMenu.Device.RAIN_COLLECTOR) {
            boolean second = menu.device != ThermalMenu.Device.RAIN_COLLECTOR && isHovering(32, TOP, 16, H, mx, my);
            if (second || isHovering(8, TOP, menu.device == ThermalMenu.Device.RAIN_COLLECTOR ? 40 : 16, H, mx, my)) {
                g.setTooltipForNextFrame(font, Component.translatable("gui.powerfactor.fluid_amount", Component.translatable(fluid(second)),
                        String.format("%,d", menu.unsigned(second ? ThermalMenu.DATA_TANK2 : ThermalMenu.DATA_TANK)), String.format("%,d", tankCapacity())), mx, my);
            }
        }
    }
}
