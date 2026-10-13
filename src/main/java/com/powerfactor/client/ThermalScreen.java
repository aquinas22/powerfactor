package com.powerfactor.client;

import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.steam.ThermalMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Live instrumentation for the steam plant and its water/heat supply machines. */
public final class ThermalScreen extends MachineScreen<ThermalMenu> {
    public ThermalScreen(ThermalMenu menu, Inventory inventory, Component title) { super(menu, inventory, title, 166); }
    @Override protected List<Tab> tabs() { return menu.device == ThermalMenu.Device.RAIN_COLLECTOR ? List.of(Tab.CONFIG) : List.of(Tab.ELECTRICAL, Tab.CONFIG); }
    @Override protected MachineBlockEntity.RedstoneMode redstoneMode() { return menu.redstoneMode(); }
    private boolean active() { return (menu.get(ThermalMenu.DATA_FLAGS) & 1) != 0; }
    private boolean fault() { return (menu.get(ThermalMenu.DATA_FLAGS) & 4) != 0; }
    private Component status() {
        String key = switch (menu.device) {
            case STEAM_TURBINE -> (menu.get(ThermalMenu.DATA_FLAGS) & 2) == 0 ? "multiblock.incomplete" : fault() ? "steam_turbine.backed_up"
                    : active() ? menu.get(ThermalMenu.DATA_FE) > 0 ? "panel.generating" : "steam_turbine.idling"
                    : menu.unsigned(ThermalMenu.DATA_TANK) == 0 ? "steam_turbine.no_steam" : "turbine.syncing";
            case COOLING_TOWER -> fault() ? "cooling.blocked" : active() ? "cooling.running" : "cooling.idle";
            case GEOTHERMAL -> menu.get(ThermalMenu.DATA_RATE) == 0 ? "geothermal.no_heat" : active() ? "panel.generating" : "battery.standby";
            case THERMOELECTRIC -> menu.get(ThermalMenu.DATA_RATE) == 0 ? "thermoelectric.no_gradient" : active() ? "panel.generating" : "battery.standby";
            case RAIN_COLLECTOR -> active() ? "rain.collecting" : "rain.waiting";
        };
        return Component.translatable("gui.powerfactor." + key);
    }
    private int color() {
        boolean incomplete = menu.device == ThermalMenu.Device.STEAM_TURBINE && (menu.get(ThermalMenu.DATA_FLAGS) & 2) == 0;
        return fault() || incomplete ? Skin.FE : active() ? Skin.TEAL : Skin.OUTPUT;
    }
    @Override protected Electrical electrical() {
        boolean turbine = menu.device == ThermalMenu.Device.STEAM_TURBINE;
        int fe = menu.get(ThermalMenu.DATA_FE), ee = turbine ? (short) menu.get(ThermalMenu.DATA_EE) : 0;
        return new Electrical(turbine ? active() ? 1 : 0 : 1, turbine ? active() ? menu.unsigned(ThermalMenu.DATA_RPM) / 60.0 : 0 : 60,
                fe, ee, turbine ? 1000 : menu.device == ThermalMenu.Device.COOLING_TOWER ? 32 : 64, status(), color());
    }
    private int bufferCapacity() { return menu.device == ThermalMenu.Device.STEAM_TURBINE ? 128000 : 8000; }
    private int tankCapacity() { return menu.device == ThermalMenu.Device.STEAM_TURBINE ? 32000 : 16000; }
    private String fluid(boolean second) {
        return switch (menu.device) {
            case STEAM_TURBINE -> second ? "fluid.powerfactor.hot_water" : "fluid.powerfactor.steam";
            case COOLING_TOWER -> second ? "block.minecraft.water" : "fluid.powerfactor.hot_water";
            default -> "block.minecraft.water";
        };
    }
    @Override protected void extractMain(GuiGraphicsExtractor g, int mx, int my) {
        skinBackground(g);
        Skin.lamp(g, leftPos + 162, topPos + 6, color());
        Skin.lcd(g, leftPos + 56, topPos + 20, 94, 52);
        if (menu.device == ThermalMenu.Device.STEAM_TURBINE || menu.device == ThermalMenu.Device.COOLING_TOWER) {
            GuiUtil.fluidGauge(g, leftPos + 8, topPos + 20, 16, 52, menu.unsigned(ThermalMenu.DATA_TANK), tankCapacity(),
                    menu.device == ThermalMenu.Device.STEAM_TURBINE ? 0xFFDCE7EF : 0xFF4AABB3);
            GuiUtil.fluidGauge(g, leftPos + 32, topPos + 20, 16, 52, menu.unsigned(ThermalMenu.DATA_TANK2), tankCapacity(),
                    menu.device == ThermalMenu.Device.STEAM_TURBINE ? 0xFF4AABB3 : 0xFF3F76E4);
        } else if (menu.device == ThermalMenu.Device.RAIN_COLLECTOR) {
            GuiUtil.fluidGauge(g, leftPos + 8, topPos + 20, 40, 52, menu.unsigned(ThermalMenu.DATA_TANK), 16000, 0xFF3F76E4);
        } else {
            Skin.scope(g, leftPos + 8, topPos + 20, 40, 52, menu.get(ThermalMenu.DATA_FE) / 64F, active());
        }
        if (menu.device != ThermalMenu.Device.RAIN_COLLECTOR) {
            Skin.energyTube(g, leftPos + 156, topPos + 20, 12, 52, menu.unsigned(ThermalMenu.DATA_STORED) * 10, bufferCapacity(), active());
        }
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        g.text(font, Skin.fit(font, status(), 86), 60, 24, color(), false);
        if (menu.device == ThermalMenu.Device.STEAM_TURBINE) {
            g.text(font, menu.unsigned(ThermalMenu.DATA_RPM) + " RPM", 60, 36, Skin.LCD_TEXT, false);
            g.text(font, menu.get(ThermalMenu.DATA_FE) + " FE/t", 60, 48, Skin.FE, false);
            g.text(font, menu.get(ThermalMenu.DATA_RATE) + " mB/t", 60, 60, Skin.TEXT, false);
        } else if (menu.device == ThermalMenu.Device.COOLING_TOWER || menu.device == ThermalMenu.Device.RAIN_COLLECTOR) {
            g.text(font, menu.get(ThermalMenu.DATA_RATE) + " mB/t", 60, 37, Skin.LCD_TEXT, false);
            g.text(font, menu.device == ThermalMenu.Device.RAIN_COLLECTOR ? "0 FE/t" : menu.get(ThermalMenu.DATA_FE) + " FE/t", 60, 50, Skin.FE, false);
        } else {
            g.text(font, menu.get(ThermalMenu.DATA_FE) + " FE/t", 60, 37, Skin.FE, false);
            g.text(font, Skin.fit(font, Component.translatable("gui.powerfactor.thermal.available", menu.get(ThermalMenu.DATA_RATE)), 86), 60, 50, Skin.DIM, false);
        }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        super.extractRenderState(g, mx, my, partial);
        if (isHovering(56, 20, 94, 52, mx, my) || isHovering(160, 3, 12, 12, mx, my)) {
            g.setTooltipForNextFrame(font, status(), mx, my);
        } else if (menu.device != ThermalMenu.Device.RAIN_COLLECTOR && isHovering(156, 20, 12, 52, mx, my)) {
            GuiUtil.energyTooltip(g, font, menu.unsigned(ThermalMenu.DATA_STORED) * 10, bufferCapacity(), mx, my);
        } else if (menu.device == ThermalMenu.Device.STEAM_TURBINE || menu.device == ThermalMenu.Device.COOLING_TOWER || menu.device == ThermalMenu.Device.RAIN_COLLECTOR) {
            boolean second = menu.device != ThermalMenu.Device.RAIN_COLLECTOR && isHovering(32, 20, 16, 52, mx, my);
            if (second || isHovering(8, 20, menu.device == ThermalMenu.Device.RAIN_COLLECTOR ? 40 : 16, 52, mx, my)) {
                g.setTooltipForNextFrame(font, Component.translatable("gui.powerfactor.fluid_amount", Component.translatable(fluid(second)),
                        String.format("%,d", menu.unsigned(second ? ThermalMenu.DATA_TANK2 : ThermalMenu.DATA_TANK)), String.format("%,d", tankCapacity())), mx, my);
            }
        }
    }
}
