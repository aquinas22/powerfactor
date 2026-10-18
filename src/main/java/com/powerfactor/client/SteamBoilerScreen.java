package com.powerfactor.client;

import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.steam.SteamBoilerBlockEntity;
import com.powerfactor.steam.SteamBoilerMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Pressure vessel HMI: fuel, water, steam, temperature, pressure and trip guidance. */
public final class SteamBoilerScreen extends MachineScreen<SteamBoilerMenu> {
    private static final int ROW_Y = Skin.Layout.CONTENT_TOP, ROW_H = 44, STRIP_Y = 70, STRIP_H = Skin.Layout.contentBottom(198) - 70;
    public SteamBoilerScreen(SteamBoilerMenu menu, Inventory inventory, Component title) { super(menu, inventory, title, 198); }
    @Override protected List<Tab> tabs() { return List.of(Tab.CONFIG); }
    @Override protected MachineBlockEntity.RedstoneMode redstoneMode() { return menu.redstoneMode(); }
    private boolean formed() { return (menu.get(SteamBoilerMenu.FLAGS) & 2) != 0; }
    private boolean trip() { return (menu.get(SteamBoilerMenu.FLAGS) & 4) != 0; }
    private Component status() {
        return Component.translatable(!formed() ? "gui.powerfactor.multiblock.incomplete" : trip() ? "gui.powerfactor.boiler.tripped"
                : (menu.get(SteamBoilerMenu.FLAGS) & 8) != 0 ? "gui.powerfactor.boiler.pressure_hold"
                : menu.get(SteamBoilerMenu.RATE) > 0 ? "gui.powerfactor.boiler.boiling"
                : menu.get(SteamBoilerMenu.WATER) == 0 ? "gui.powerfactor.boiler.dry"
                : (menu.get(SteamBoilerMenu.FLAGS) & 1) != 0 ? "gui.powerfactor.boiler.warming" : "gui.powerfactor.boiler.off");
    }
    private int statusColor() { return !formed() || trip() ? 0xFFFF6250 : menu.get(SteamBoilerMenu.RATE) > 0 ? Skin.TEAL : Skin.OUTPUT; }
    @Override protected Electrical electrical() { return new Electrical(0, 0, 0, 0, 1, status(), statusColor()); }
    @Override protected void extractMain(GuiGraphicsExtractor g, int mx, int my) {
        skinBackground(g);
        int x = leftPos, y = topPos;
        Skin.lamp(g, x + 162, y + 6, statusColor());
        // Top row (y 20-64): firebox, water, readout, steam. Bottom strip (y 70-100): status, heat and pressure.
        // Firebox column (x 8-26): the flame window over its fuel slot.
        Skin.well(g, x + 9, y + ROW_Y, 16, 22);
        int flame = 18 * menu.get(SteamBoilerMenu.FUEL) / 1000;
        if (flame > 0) g.fillGradient(x + 11, y + ROW_Y + 20 - flame, x + 23, y + ROW_Y + 20, 0xFFFFD05A, 0xFFC94A24);
        GuiUtil.fluidGauge(g, x + 34, y + ROW_Y, 16, ROW_H, menu.get(SteamBoilerMenu.WATER), SteamBoilerBlockEntity.TANK, 0xFF3F76E4);
        GuiUtil.fluidGauge(g, x + 152, y + ROW_Y, 16, ROW_H, menu.get(SteamBoilerMenu.STEAM), SteamBoilerBlockEntity.TANK, 0xFFDCE7EF);
        Skin.lcd(g, x + 58, y + ROW_Y, 86, ROW_H);
        Skin.lcd(g, x + 8, y + STRIP_Y, 160, STRIP_H);
        Skin.bar(g, x + 23, y + STRIP_Y + 21, 61, menu.get(SteamBoilerMenu.HEAT) / 1000F, menu.get(SteamBoilerMenu.HEAT) >= 750 ? Skin.FE : Skin.OUTPUT);
        Skin.bar(g, x + 92, y + STRIP_Y + 21, 61, menu.get(SteamBoilerMenu.PRESSURE) / 1000F, Skin.INPUT);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        Skin.lcdRows(g, font, 58, ROW_Y, 86, ROW_H, java.util.List.of(new Skin.Row(Skin.Icon.TEMP, (20 + menu.get(SteamBoilerMenu.HEAT) / 5) + " °C", Skin.LCD_TEXT),
                new Skin.Row(Skin.Icon.STEAM, menu.get(SteamBoilerMenu.RATE) + " mB/t", Skin.LCD_DIM),
                new Skin.Row(Skin.Icon.PF, Component.literal(menu.get(SteamBoilerMenu.PRESSURE) / 10 + "%"), Skin.INPUT, Component.translatable("gui.powerfactor.pressure"))));
        Skin.icon(g, Skin.statusIcon(statusColor()), 12, STRIP_Y + 3);
        Skin.condensed(g, font, status(), 23, STRIP_Y + 4, 141, statusColor());
        Skin.icon(g, Skin.Icon.HEAT, 12, STRIP_Y + 18, Component.translatable("gui.powerfactor.heat"));
        Skin.icon(g, Skin.Icon.PF, 155, STRIP_Y + 18, Component.translatable("gui.powerfactor.pressure"));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        super.extractRenderState(g, mx, my, partial);
        if (isHovering(34, ROW_Y, 16, ROW_H, mx, my) || isHovering(152, ROW_Y, 16, ROW_H, mx, my)) {
            boolean steam = isHovering(152, ROW_Y, 16, ROW_H, mx, my);
            g.setTooltipForNextFrame(font, Component.translatable("gui.powerfactor.fluid_amount",
                    Component.translatable(steam ? "fluid.powerfactor.steam" : "block.minecraft.water"),
                    String.format("%,d", menu.get(steam ? SteamBoilerMenu.STEAM : SteamBoilerMenu.WATER)), "32,000"), mx, my);
        } else if (isHovering(8, STRIP_Y, 160, STRIP_H, mx, my)) {
            g.setComponentTooltipForNextFrame(font, List.of(status(), Component.translatable(!formed() ? "gui.powerfactor.multiblock.auto_hint"
                    : trip() ? "gui.powerfactor.boiler.trip_help" : "gui.powerfactor.boiler.help")), mx, my);
        }
    }
}
