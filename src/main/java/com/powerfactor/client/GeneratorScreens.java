package com.powerfactor.client;

import com.powerfactor.generator.BatteryBankBlockEntity;
import com.powerfactor.generator.DieselGeneratorBlockEntity;
import com.powerfactor.generator.DieselMenu;
import com.powerfactor.generator.PowerMeterBlockEntity;
import com.powerfactor.grid.CableTier;
import com.powerfactor.machine.MachineBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** GUIs for the diesel generator, battery bank and power meter. */
public final class GeneratorScreens {
    private abstract static class Base<M extends DieselMenu> extends MachineScreen<M> {
        private record Gauge(int x, int y, int w, int h, Component tooltip) {}
        private final List<Gauge> gauges = new ArrayList<>();

        Base(M menu, Inventory inventory, Component title) {
            super(menu, inventory, title, 166);
        }

        @Override
        protected List<Tab> tabs() {
            return List.of(Tab.ELECTRICAL, Tab.CONFIG);
        }

        @Override
        protected MachineBlockEntity.RedstoneMode redstoneMode() {
            return menu.redstoneMode();
        }

        @Override
        protected void extractMain(GuiGraphicsExtractor g, int mouseX, int mouseY) {
            gauges.clear();
            skinBackground(g);
            Skin.lamp(g, leftPos + MAIN_W - 14, topPos + 6, electrical().statusColor());
        }

        protected void tank(GuiGraphicsExtractor g, int x, int amount, int capacity, int color, String key) {
            GuiUtil.fluidGauge(g, leftPos + x, topPos + 20, 16, 52, amount, capacity, color);
            gauges.add(new Gauge(x, 20, 16, 52, Component.translatable("gui.powerfactor.fluid_amount", Component.translatable(key),
                    String.format("%,d", amount), String.format("%,d", capacity))));
        }

        protected void energy(GuiGraphicsExtractor g, int x, int w, int stored, int capacity, boolean active) {
            Skin.energyTube(g, leftPos + x, topPos + 20, w, 52, stored, capacity, active);
            gauges.add(new Gauge(x, 20, w, 52, Component.translatable("gui.powerfactor.buffer", String.format("%,d", stored),
                    String.format("%,d", capacity))));
        }

        protected void preview(GuiGraphicsExtractor g, int x, int y, int w, int h) {
            Skin.well(g, leftPos + x, topPos + y, w, h);
            g.fillGradient(leftPos + x + 1, topPos + y + 1, leftPos + x + w - 1, topPos + y + h - 1, 0xFF1C2A33, 0xFF0B1116);
            if (minecraft.level != null && minecraft.level.getBlockEntity(menu.pos) instanceof com.powerfactor.oil.PumpjackBlockEntity pump) {
                MachineView.draw(g, pump, leftPos + x + 1, topPos + y + 1, leftPos + x + w - 1, topPos + y + h - 1,
                        45, framePartialTick);
            }
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            super.extractRenderState(g, mouseX, mouseY, a);
            for (Gauge gauge : gauges) {
                if (isHovering(gauge.x(), gauge.y(), gauge.w(), gauge.h(), mouseX, mouseY)) {
                    g.setTooltipForNextFrame(font, gauge.tooltip(), mouseX, mouseY);
                    return;
                }
            }
            if (isHovering(MAIN_W - 16, 3, 12, 12, mouseX, mouseY)) {
                g.setTooltipForNextFrame(font, electrical().status(), mouseX, mouseY);
            }
        }
    }

    public static class Diesel extends Base<DieselMenu> {
        public Diesel(DieselMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        @Override
        protected Electrical electrical() {
            boolean online = (menu.get(DieselMenu.DATA_FLAGS) & 1) != 0;
            double hz = menu.unsigned(DieselMenu.DATA_RPM) / 60.0;
            return new Electrical(online ? 1.0 : 0, online ? hz : 0, menu.get(DieselMenu.DATA_FE), menu.get(DieselMenu.DATA_EE), 125,
                    Component.translatable(online ? "gui.powerfactor.panel.generating" : "gui.powerfactor.panel.breaker_open"),
                    online ? 0xFF55FF55 : 0xFFFFB020);
        }

        @Override
        protected void extractMain(GuiGraphicsExtractor g, int mouseX, int mouseY) {
            super.extractMain(g, mouseX, mouseY);
            boolean lava = (menu.get(DieselMenu.DATA_FLAGS) & 2) != 0;
            tank(g, 44, menu.unsigned(DieselMenu.DATA_TANK), DieselGeneratorBlockEntity.TANK,
                    lava ? 0xFFE0571A : 0xFFC8A030, lava ? "block.minecraft.lava" : "gui.powerfactor.fuel");
            energy(g, 156, 12, menu.unsigned(DieselMenu.DATA_STORED) * 10, 10_000, menu.get(DieselMenu.DATA_FE) > 0);
            Skin.scope(g, leftPos + 8, topPos + 20, 30, 52, menu.get(DieselMenu.DATA_FE) / 125F, menu.get(DieselMenu.DATA_FE) > 0);
            Skin.lcd(g, leftPos + 66, topPos + 20, 84, 52);
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
            super.extractLabels(g, xm, ym);
            g.text(font, String.format("%.1f Hz", menu.unsigned(DieselMenu.DATA_RPM) / 60.0), 70, 24, Skin.TEXT, false);
            g.text(font, menu.get(DieselMenu.DATA_FE) + " FE/t", 70, 36, GuiUtil.FE_COLOR, false);
            g.text(font, menu.get(DieselMenu.DATA_EE) + " EE/t", 70, 48, GuiUtil.EE_COLOR, false);
            g.text(font, menu.unsigned(DieselMenu.DATA_TANK) + " mB", 70, 60, Skin.TEXT, false);
        }
    }

    public static class Battery extends Base<DieselMenu.Battery> {
        public Battery(DieselMenu.Battery menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        @Override
        protected Electrical electrical() {
            return new Electrical(1.0, 60.0, menu.get(DieselMenu.DATA_FE), 0, BatteryBankBlockEntity.RATE,
                    Component.translatable((menu.get(DieselMenu.DATA_FLAGS) & 1) != 0 ? "gui.powerfactor.battery.support"
                            : "gui.powerfactor.battery.standby"), 0xFF55FF55);
        }

        @Override
        protected void extractMain(GuiGraphicsExtractor g, int mouseX, int mouseY) {
            super.extractMain(g, mouseX, mouseY);
            energy(g, 76, 24, menu.unsigned(DieselMenu.DATA_STORED) * 1000, BatteryBankBlockEntity.CAPACITY, menu.get(DieselMenu.DATA_EE) > 0);
            Skin.scope(g, leftPos + 8, topPos + 20, 62, 52, menu.get(DieselMenu.DATA_FE) / (float) BatteryBankBlockEntity.RATE,
                    menu.get(DieselMenu.DATA_FE) > 0);
            Skin.lcd(g, leftPos + 104, topPos + 20, 64, 52);
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
            super.extractLabels(g, xm, ym);
            int pct = (int) (100L * menu.unsigned(DieselMenu.DATA_STORED) * 1000 / BatteryBankBlockEntity.CAPACITY);
            g.text(font, pct + "%", 106, 24, Skin.TEXT, false);
            g.text(font, "+" + menu.get(DieselMenu.DATA_EE) + " FE/t", 106, 38, 0xFF7CFF8A, false);
            g.text(font, "-" + menu.get(DieselMenu.DATA_FE) + " FE/t", 106, 50, GuiUtil.FE_COLOR, false);
        }
    }

    public static class Meter extends Base<DieselMenu.Meter> {
        public Meter(DieselMenu.Meter menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        @Override
        protected MachineBlockEntity.RedstoneMode redstoneMode() {
            return MachineBlockEntity.RedstoneMode.values()[Math.clamp(menu.get(PowerMeterBlockEntity.DATA_REDSTONE), 0, 2)];
        }

        private double volts() {
            return menu.unsigned(PowerMeterBlockEntity.DATA_VOLTS) / 1000.0;
        }

        @Override
        protected Electrical electrical() {
            int tier = menu.get(PowerMeterBlockEntity.DATA_TIER);
            double rating = tier < 0 || tier >= CableTier.values().length ? 512 : CableTier.values()[tier].ratingCe;
            double hz = menu.unsigned(PowerMeterBlockEntity.DATA_HZ) / 100.0;
            boolean connected = tier >= 0 && tier < CableTier.values().length;
            boolean powered = connected && hz > 0 && volts() > 0;
            boolean warning = powered && (menu.get(PowerMeterBlockEntity.DATA_LOAD) > 1000 || volts() < 0.9 || volts() > 1.1);
            return new Electrical(tier < 0 ? 0 : volts(), menu.unsigned(PowerMeterBlockEntity.DATA_HZ) / 100.0,
                    menu.get(PowerMeterBlockEntity.DATA_FE), (short) menu.get(PowerMeterBlockEntity.DATA_EE), rating,
                    Component.translatable(!connected ? "gui.powerfactor.meter.no_grid" : !powered ? "gui.powerfactor.work.no_energy"
                            : "gui.powerfactor.meter.loading", menu.get(PowerMeterBlockEntity.DATA_LOAD) / 10),
                    !powered ? Skin.DIM : warning ? 0xFFFFB020 : 0xFFB8F5C0);
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
            super.extractLabels(g, xm, ym);
            int tier = menu.get(PowerMeterBlockEntity.DATA_TIER);
            if (tier < 0 || tier >= CableTier.values().length) {
                g.text(font, Component.translatable("gui.powerfactor.meter.no_grid"), 8, 24, 0xFFFF6A5A, false);
                return;
            }
            CableTier t = CableTier.values()[tier];
            int fe = menu.get(PowerMeterBlockEntity.DATA_FE), ee = (short) menu.get(PowerMeterBlockEntity.DATA_EE);
            double ce = Math.hypot(fe, ee);
            String[] lines = {
                    Component.translatable("gui.powerfactor.meter.tier." + t.name().toLowerCase(java.util.Locale.ROOT)).getString()
                            + "  " + Math.round(volts() * t.volts) + " V",
                    String.format("%.2f Hz   PF %s", menu.unsigned(PowerMeterBlockEntity.DATA_HZ) / 100.0,
                            ce < 0.5 ? "---" : String.format("%.2f %s", fe / ce, ee >= 0 ? "lag" : "lead")),
                    fe + " FE/t   " + ee + " EE/t   " + Math.round(ce) + " CE/t",
                    Component.translatable("gui.powerfactor.meter.loss", menu.get(PowerMeterBlockEntity.DATA_LOSS) / 10.0,
                            menu.get(PowerMeterBlockEntity.DATA_LOAD) / 10).getString(),
                    Component.translatable("gui.powerfactor.meter.interference", menu.get(PowerMeterBlockEntity.DATA_INTERFERENCE)).getString()};
            for (int i = 0; i < lines.length; i++) {
                g.text(font, Skin.fit(font, Component.literal(lines[i]), 152), 12, 23 + i * 10, i == 2 ? Skin.LCD_TEXT : Skin.TEXT, false);
            }
        }

        @Override
        protected void extractMain(GuiGraphicsExtractor g, int mouseX, int mouseY) {
            super.extractMain(g, mouseX, mouseY);
            Skin.lcd(g, leftPos + 8, topPos + 20, 160, 54);
        }
    }

    public static class Electrolyzer extends Base<DieselMenu.Electrolyzer> {
        public Electrolyzer(DieselMenu.Electrolyzer menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        private boolean running() {
            return (menu.get(DieselMenu.DATA_FLAGS) & 1) != 0;
        }

        @Override
        protected Electrical electrical() {
            return new Electrical(1.0, 60.0, menu.get(DieselMenu.DATA_FE), menu.get(DieselMenu.DATA_EE), 110,
                    Component.translatable(running() ? "gui.powerfactor.electrolyzer.running" : "gui.powerfactor.electrolyzer.idle"),
                    running() ? 0xFF55FF55 : 0xFFFFB020);
        }

        @Override
        protected void extractMain(GuiGraphicsExtractor g, int mouseX, int mouseY) {
            super.extractMain(g, mouseX, mouseY);
            int cap = com.powerfactor.device.ElectrolyzerBlockEntity.TANK;
            tank(g, 26, menu.unsigned(DieselMenu.DATA_TANK), cap, 0xFF3F76E4, "block.minecraft.water");
            tank(g, 130, menu.unsigned(DieselMenu.DATA_TANK2), cap, 0xFFB8E8FF, "fluid.powerfactor.hydrogen");
            energy(g, 156, 12, menu.unsigned(DieselMenu.DATA_STORED) * 10, 20_000, running());
            // The cell: water between two electrodes; bubbles stream off them while it runs.
            int cx = leftPos + 50, cy = topPos + 26, cw = 74, ch = 46;
            g.fill(cx, cy, cx + cw, cy + ch, 0xFF20262B);
            g.fill(cx + 1, cy + 10, cx + cw - 1, cy + ch - 1, 0xFF2D5A9A);
            g.fill(cx + 16, cy + 4, cx + 20, cy + ch - 6, 0xFFB0B8BE);
            g.fill(cx + cw - 20, cy + 4, cx + cw - 16, cy + ch - 6, 0xFF8A6A50);
            g.text(font, "-", cx + 16, cy - 7, 0xFF7FB8FF, false);
            g.text(font, "+", cx + cw - 20, cy - 7, 0xFFFF7F6A, false);
            if (running()) {
                long t = net.minecraft.util.Util.getMillis() / 40;
                for (int k = 0; k < 14; k++) {
                    boolean cathode = k % 3 != 0;   // twice as much hydrogen as oxygen
                    int x = (cathode ? cx + 21 : cx + cw - 23) + (int) ((k * 7 + t / 3) % 3);
                    int y = cy + ch - 8 - (int) ((t + k * 9L) % (ch - 18));
                    g.fill(x, y, x + 2, y + 2, cathode ? 0xFFE0F4FF : 0xFFFFE0D8);
                }
            }
        }

    }

    public static class FuelCell extends Base<DieselMenu.FuelCell> {
        public FuelCell(DieselMenu.FuelCell menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        private boolean online() {
            return (menu.get(DieselMenu.DATA_FLAGS) & 1) != 0;
        }

        private boolean disabled() {
            return (menu.get(DieselMenu.DATA_FLAGS) & com.powerfactor.generator.FuelCellBlockEntity.FLAG_DISABLED) != 0;
        }

        private Component status(boolean compact) {
            String key = disabled() ? "gui.powerfactor.work.disabled"
                    : menu.unsigned(DieselMenu.DATA_TANK2) == 0 ? "gui.powerfactor.fuel_cell.no_hydrogen"
                    : online() ? compact ? "gui.powerfactor.fuel_cell.grid_forming" : "gui.powerfactor.fuel_cell.online"
                    : "gui.powerfactor.fuel_cell.warming";
            return Component.translatable(key);
        }

        private int statusColor() { return disabled() ? 0xFF82929A : online() ? 0xFF7CFF8A : 0xFFFFB020; }

        @Override
        protected Electrical electrical() {
            return new Electrical(online() ? 1.0 : 0, online() ? 60.0 : 0, menu.get(DieselMenu.DATA_FE), 0, 200,
                    status(false), statusColor());
        }

        @Override
        protected void extractMain(GuiGraphicsExtractor g, int mouseX, int mouseY) {
            super.extractMain(g, mouseX, mouseY);
            tank(g, 44, menu.unsigned(DieselMenu.DATA_TANK2), com.powerfactor.generator.FuelCellBlockEntity.TANK,
                    0xFFB8E8FF, "fluid.powerfactor.hydrogen");
            energy(g, 156, 12, menu.unsigned(DieselMenu.DATA_STORED) * 10, 4_000, online());
            Skin.scope(g, leftPos + 8, topPos + 20, 30, 52, menu.get(DieselMenu.DATA_FE) / 200F, online());
            Skin.lcd(g, leftPos + 66, topPos + 20, 84, 52);
            // Warm-up bar under the readouts.
            int x = leftPos + 70, y = topPos + 69, w = 78;
            g.fill(x, y, x + w, y + 4, 0xFF1A1A1A);
            g.fill(x, y, x + w * Math.clamp(menu.get(DieselMenu.DATA_RPM), 0, 100) / 100, y + 4, online() ? 0xFF55FF55 : 0xFFFFB020);
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
            super.extractLabels(g, xm, ym);
            g.text(font, Skin.fit(font, status(true), 76), 70, 23, statusColor(), false);
            g.text(font, menu.get(DieselMenu.DATA_FE) + " FE/t", 70, 34, GuiUtil.FE_COLOR, false);
            g.text(font, Skin.fit(font, Component.translatable("gui.powerfactor.fuel_cell.ee", menu.get(DieselMenu.DATA_EE)), 76), 70, 45, GuiUtil.EE_COLOR, false);
            g.text(font, menu.unsigned(DieselMenu.DATA_TANK2) + " mB H\u2082", 70, 56, Skin.TEXT, false);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            super.extractRenderState(g, mouseX, mouseY, a);
            if (isHovering(70, 45, 76, 9, mouseX, mouseY))
                g.setTooltipForNextFrame(font, Component.translatable("gui.powerfactor.fuel_cell.ee.help", menu.get(DieselMenu.DATA_EE)), mouseX, mouseY);
        }
    }

    public static class Pumpjack extends Base<DieselMenu.Pumpjack> {
        public Pumpjack(DieselMenu.Pumpjack menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        private boolean running() {
            return (menu.get(DieselMenu.DATA_FLAGS) & 1) != 0;
        }

        private boolean dry() {
            return (menu.get(DieselMenu.DATA_FLAGS) & 2) != 0;
        }

        private boolean formed() {
            return (menu.get(DieselMenu.DATA_FLAGS) & 4) != 0;
        }

        @Override
        protected Electrical electrical() {
            return new Electrical(1.0, 60.0, menu.get(DieselMenu.DATA_FE), menu.get(DieselMenu.DATA_EE), 75,
                    Component.translatable(!formed() ? "gui.powerfactor.multiblock.incomplete" : dry() ? "gui.powerfactor.pumpjack.dry" : running() ? "gui.powerfactor.pumpjack.pumping"
                            : "gui.powerfactor.pumpjack.idle"), !formed() || dry() ? 0xFFFF6A5A : running() ? 0xFF55FF55 : 0xFFFFB020);
        }

        @Override
        protected void extractMain(GuiGraphicsExtractor g, int mouseX, int mouseY) {
            super.extractMain(g, mouseX, mouseY);
            preview(g, 8, 20, 52, 52);
            tank(g, 134, menu.unsigned(DieselMenu.DATA_TANK), com.powerfactor.oil.PumpjackBlockEntity.TANK, 0xFF51402D, "fluid.powerfactor.crude_oil");
            energy(g, 156, 12, menu.unsigned(DieselMenu.DATA_STORED) * 10, 12_000, running());
            Skin.lcd(g, leftPos + 64, topPos + 20, 66, 52);
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
            int x = 68;
            g.text(font, Skin.fit(font, Component.translatable(!formed() ? "gui.powerfactor.multiblock.short" : dry() ? "gui.powerfactor.pumpjack.dry" : running() ? "gui.powerfactor.pumpjack.pumping"
                    : "gui.powerfactor.pumpjack.idle"), 58), x, 24, !formed() || dry() ? 0xFFFF6A5A : running() ? Skin.LCD_TEXT : 0xFFFFB020, false);
            g.text(font, menu.get(DieselMenu.DATA_FE) + " FE/t", x, 36, Skin.FE, false);
            g.text(font, Component.translatable("gui.powerfactor.pumpjack.blocks", menu.unsigned(DieselMenu.DATA_TANK2)), x, 48, Skin.LCD_TEXT, false);
            g.text(font, menu.unsigned(DieselMenu.DATA_TANK) + " mB", x, 60, Skin.TEXT, false);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            super.extractRenderState(g, mouseX, mouseY, a);
            if (!formed() && isHovering(8, 20, 122, 52, mouseX, mouseY)) {
                g.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.powerfactor.pumpjack.foundation"),
                        Component.translatable("gui.powerfactor.multiblock.auto_hint")), mouseX, mouseY);
            }
        }
    }

    public static class Refinery extends Base<DieselMenu.Refinery> {
        public Refinery(DieselMenu.Refinery menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        private boolean running() {
            return (menu.get(DieselMenu.DATA_FLAGS) & 1) != 0;
        }

        private boolean formed() {
            return (menu.get(DieselMenu.DATA_FLAGS) & 2) != 0;
        }

        @Override
        protected Electrical electrical() {
            return new Electrical(1.0, 60.0, menu.get(DieselMenu.DATA_FE), menu.get(DieselMenu.DATA_EE), 110,
                    Component.translatable(!formed() ? "gui.powerfactor.multiblock.incomplete"
                            : running() ? "gui.powerfactor.refinery.running" : "gui.powerfactor.refinery.idle"),
                    !formed() ? 0xFFFF6A5A : running() ? 0xFF55FF55 : 0xFFFFB020);
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
            if (!formed()) {
                // Over the scope: what's wrong and how to fix it.
                g.fill(8, 20, 60, 72, 0xD0100808);
                var lines = font.split(Component.translatable("gui.powerfactor.multiblock.hint"), 48);
                for (int i = 0; i < lines.size() && i < 5; i++) {
                    g.text(font, lines.get(i), 10, 23 + i * 10, 0xFFFF8A7A, false);
                }
            }
        }

        @Override
        protected void extractMain(GuiGraphicsExtractor g, int mouseX, int mouseY) {
            super.extractMain(g, mouseX, mouseY);
            Skin.scope(g, leftPos + 8, topPos + 20, 52, 52, menu.get(DieselMenu.DATA_FE) / 100F, running());
            int cap = com.powerfactor.oil.RefineryBlockEntity.TANK;
            tank(g, 68, menu.unsigned(DieselMenu.DATA_TANK), cap, 0xFF51402D, "fluid.powerfactor.crude_oil");
            Skin.chevrons(g, leftPos + 88, topPos + 42, 2, running() ? (net.minecraft.util.Util.getMillis() % 1200) / 1200F : 0, running());
            tank(g, 108, menu.unsigned(DieselMenu.DATA_TANK2), cap, 0xFFC89A32, "fluid.powerfactor.diesel");
            tank(g, 130, menu.unsigned(DieselMenu.DATA_TANK3), cap, 0xFFE6D9A4, "fluid.powerfactor.jet_fuel");
            energy(g, 156, 12, menu.unsigned(DieselMenu.DATA_STORED) * 10, 20_000, running());
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            super.extractRenderState(g, mouseX, mouseY, a);
            if (!formed() && isHovering(8, 20, 52, 52, mouseX, mouseY)) {
                g.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.powerfactor.multiblock.hint"),
                        Component.translatable("gui.powerfactor.multiblock.auto_hint")), mouseX, mouseY);
            }
        }
    }

    private GeneratorScreens() {}
}
