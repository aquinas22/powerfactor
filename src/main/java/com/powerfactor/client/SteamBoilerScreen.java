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
        Skin.well(g, x + 16, y + 24, 16, 23);
        int flame = 19 * menu.get(SteamBoilerMenu.FUEL) / 1000;
        if (flame > 0) g.fillGradient(x + 18, y + 45 - flame, x + 30, y + 45, 0xFFFFD05A, 0xFFC94A24);
        GuiUtil.fluidGauge(g, x + 46, y + 21, 16, 50, menu.get(SteamBoilerMenu.WATER), SteamBoilerBlockEntity.TANK, 0xFF3F76E4);
        GuiUtil.fluidGauge(g, x + 140, y + 21, 16, 50, menu.get(SteamBoilerMenu.STEAM), SteamBoilerBlockEntity.TANK, 0xFFDCE7EF);
        Skin.lcd(g, x + 68, y + 21, 66, 50);
        Skin.lcd(g, x + 8, y + 78, 160, 27);
        Skin.bar(g, x + 12, y + 92, 70, menu.get(SteamBoilerMenu.HEAT) / 1000F, menu.get(SteamBoilerMenu.HEAT) >= 750 ? Skin.FE : Skin.OUTPUT);
        Skin.bar(g, x + 94, y + 92, 70, menu.get(SteamBoilerMenu.PRESSURE) / 1000F, Skin.INPUT);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        g.text(font, (20 + menu.get(SteamBoilerMenu.HEAT) / 5) + " °C", 72, 25, Skin.LCD_TEXT, false);
        g.text(font, menu.get(SteamBoilerMenu.RATE) + " mB/t", 72, 37, Skin.TEXT, false);
        g.text(font, menu.get(SteamBoilerMenu.PRESSURE) / 10 + "%", 72, 49, Skin.INPUT, false);
        g.text(font, Skin.fit(font, status(), 152), 12, 81, statusColor(), false);
        g.text(font, Component.translatable("gui.powerfactor.heat"), 12, 97, Skin.DIM, false);
        g.text(font, Component.translatable("gui.powerfactor.pressure"), 94, 97, Skin.DIM, false);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        super.extractRenderState(g, mx, my, partial);
        if (isHovering(46, 21, 16, 50, mx, my) || isHovering(140, 21, 16, 50, mx, my)) {
            boolean steam = isHovering(140, 21, 16, 50, mx, my);
            g.setTooltipForNextFrame(font, Component.translatable("gui.powerfactor.fluid_amount",
                    Component.translatable(steam ? "fluid.powerfactor.steam" : "block.minecraft.water"),
                    String.format("%,d", menu.get(steam ? SteamBoilerMenu.STEAM : SteamBoilerMenu.WATER)), "32,000"), mx, my);
        } else if (isHovering(8, 78, 160, 27, mx, my)) {
            g.setComponentTooltipForNextFrame(font, List.of(status(), Component.translatable(!formed() ? "gui.powerfactor.multiblock.auto_hint"
                    : trip() ? "gui.powerfactor.boiler.trip_help" : "gui.powerfactor.boiler.help")), mx, my);
        }
    }
}
