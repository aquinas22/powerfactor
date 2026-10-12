package com.powerfactor.client;

import com.powerfactor.PowerFactor;
import com.powerfactor.generator.BatteryBankBlockEntity;
import com.powerfactor.generator.DieselGeneratorBlockEntity;
import com.powerfactor.generator.DieselMenu;
import com.powerfactor.generator.PowerMeterBlockEntity;
import com.powerfactor.grid.CableTier;
import com.powerfactor.machine.MachineBlockEntity;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** GUIs for the diesel generator, battery bank and power meter. */
public final class GeneratorScreens {
    private abstract static class Base<M extends DieselMenu> extends MachineScreen<M> {
        private final Identifier texture;

        Base(M menu, Inventory inventory, Component title, String texture) {
            super(menu, inventory, title, 166);
            this.texture = PowerFactor.id("textures/gui/" + texture + ".png");
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
            skinBackground(g);
        }
    }

    public static class Diesel extends Base<DieselMenu> {
        public Diesel(DieselMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title, "diesel_generator");
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
            GuiUtil.fluidGauge(g, leftPos + 44, topPos + 20, 16, 52, menu.unsigned(DieselMenu.DATA_TANK), DieselGeneratorBlockEntity.TANK,
                    lava ? 0xFFE0571A : 0xFFC8A030);
            GuiUtil.energyBar(g, leftPos + 156, topPos + 20, 12, 52, menu.unsigned(DieselMenu.DATA_STORED) * 10, 10_000);
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
            super(menu, inventory, title, "battery_bank");
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
            GuiUtil.energyBar(g, leftPos + 76, topPos + 20, 24, 52, menu.unsigned(DieselMenu.DATA_STORED) * 1000, BatteryBankBlockEntity.CAPACITY);
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
            super(menu, inventory, title, "power_meter");
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
            double rating = tier < 0 ? 512 : CableTier.values()[tier].ratingCe;
            return new Electrical(tier < 0 ? 0 : volts(), menu.unsigned(PowerMeterBlockEntity.DATA_HZ) / 100.0,
                    menu.get(PowerMeterBlockEntity.DATA_FE), (short) menu.get(PowerMeterBlockEntity.DATA_EE), rating,
                    Component.translatable("gui.powerfactor.meter.loading", menu.get(PowerMeterBlockEntity.DATA_LOAD) / 10), 0xFFB8F5C0);
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
            super.extractLabels(g, xm, ym);
            int tier = menu.get(PowerMeterBlockEntity.DATA_TIER);
            if (tier < 0) {
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
                g.text(font, lines[i], 8, 18 + i * 10, i == 2 ? Skin.TEXT : Skin.TEXT, false);
            }
        }
    }

    public static class Electrolyzer extends Base<DieselMenu.Electrolyzer> {
        public Electrolyzer(DieselMenu.Electrolyzer menu, Inventory inventory, Component title) {
            super(menu, inventory, title, "electrolyzer");
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
            GuiUtil.fluidGauge(g, leftPos + 26, topPos + 20, 16, 52, menu.unsigned(DieselMenu.DATA_TANK), cap, 0xFF3F76E4);
            GuiUtil.fluidGauge(g, leftPos + 130, topPos + 20, 16, 52, menu.unsigned(DieselMenu.DATA_TANK2), cap, 0xFFB8E8FF);
            GuiUtil.energyBar(g, leftPos + 156, topPos + 20, 12, 52, menu.unsigned(DieselMenu.DATA_STORED) * 10, 20_000);
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
            super(menu, inventory, title, "fuel_cell");
        }

        private boolean online() {
            return (menu.get(DieselMenu.DATA_FLAGS) & 1) != 0;
        }

        @Override
        protected Electrical electrical() {
            return new Electrical(online() ? 1.0 : 0, online() ? 60.0 : 0, menu.get(DieselMenu.DATA_FE), 0, 200,
                    Component.translatable(online() ? "gui.powerfactor.fuel_cell.online" : "gui.powerfactor.fuel_cell.offline"),
                    online() ? 0xFF55FF55 : 0xFFFFB020);
        }

        @Override
        protected void extractMain(GuiGraphicsExtractor g, int mouseX, int mouseY) {
            super.extractMain(g, mouseX, mouseY);
            GuiUtil.fluidGauge(g, leftPos + 44, topPos + 20, 16, 52, menu.unsigned(DieselMenu.DATA_TANK2),
                    com.powerfactor.generator.FuelCellBlockEntity.TANK, 0xFFB8E8FF);
            GuiUtil.energyBar(g, leftPos + 156, topPos + 20, 12, 52, menu.unsigned(DieselMenu.DATA_STORED) * 10, 4_000);
            // Warm-up bar under the readouts.
            int x = leftPos + 70, y = topPos + 64, w = 78;
            g.fill(x, y, x + w, y + 4, 0xFF1A1A1A);
            g.fill(x, y, x + w * Math.clamp(menu.get(DieselMenu.DATA_RPM), 0, 100) / 100, y + 4, online() ? 0xFF55FF55 : 0xFFFFB020);
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
            super.extractLabels(g, xm, ym);
            g.text(font, Component.translatable(online() ? "gui.powerfactor.fuel_cell.grid_forming" : "gui.powerfactor.fuel_cell.warming"), 70, 18,
                    online() ? 0xFF7CFF8A : 0xFFFFB020, false);
            g.text(font, menu.get(DieselMenu.DATA_FE) + " FE/t", 70, 30, GuiUtil.FE_COLOR, false);
            g.text(font, Component.translatable("gui.powerfactor.fuel_cell.ee", menu.get(DieselMenu.DATA_EE)), 70, 41, GuiUtil.EE_COLOR, false);
            g.text(font, menu.unsigned(DieselMenu.DATA_TANK2) + " mB H\u2082", 70, 52, Skin.TEXT, false);
        }
    }

    /** A 3D view of the machine at the left of a classic-layout screen. */
    /** A power scope at the left of a classic-layout screen (the 3D model lives in the Config tab). */
    static void view(GuiGraphicsExtractor g, net.minecraft.client.Minecraft mc, net.minecraft.core.BlockPos pos, int x, int y, int w, int h,
            float load, boolean running) {
        Skin.scope(g, x, y, w, h, load, running);
    }

    public static class Pumpjack extends Base<DieselMenu.Pumpjack> {
        public Pumpjack(DieselMenu.Pumpjack menu, Inventory inventory, Component title) {
            super(menu, inventory, title, "diesel_generator");
        }

        private boolean running() {
            return (menu.get(DieselMenu.DATA_FLAGS) & 1) != 0;
        }

        private boolean dry() {
            return (menu.get(DieselMenu.DATA_FLAGS) & 2) != 0;
        }

        @Override
        protected Electrical electrical() {
            return new Electrical(1.0, 60.0, menu.get(DieselMenu.DATA_FE), menu.get(DieselMenu.DATA_EE), 75,
                    Component.translatable(dry() ? "gui.powerfactor.pumpjack.dry" : running() ? "gui.powerfactor.pumpjack.pumping"
                            : "gui.powerfactor.pumpjack.idle"), dry() ? 0xFFFF6A5A : running() ? 0xFF55FF55 : 0xFFFFB020);
        }

        @Override
        protected void extractMain(GuiGraphicsExtractor g, int mouseX, int mouseY) {
            super.extractMain(g, mouseX, mouseY);
            view(g, minecraft, menu.pos, leftPos + 8, topPos + 20, 52, 52, menu.get(DieselMenu.DATA_FE) / 60F, running());
            GuiUtil.fluidGauge(g, leftPos + 134, topPos + 20, 16, 52, menu.unsigned(DieselMenu.DATA_TANK),
                    com.powerfactor.oil.PumpjackBlockEntity.TANK, 0xFF2A2018);
            GuiUtil.energyBar(g, leftPos + 156, topPos + 20, 12, 52, menu.unsigned(DieselMenu.DATA_STORED) * 10, 12_000);
            Skin.lcd(g, leftPos + 64, topPos + 20, 66, 52);
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
            int x = 68;
            g.text(font, Component.translatable(dry() ? "gui.powerfactor.pumpjack.dry" : running() ? "gui.powerfactor.pumpjack.pumping"
                    : "gui.powerfactor.pumpjack.idle"), x, 24, dry() ? 0xFFFF6A5A : running() ? Skin.LCD_TEXT : 0xFFFFB020, false);
            g.text(font, menu.get(DieselMenu.DATA_FE) + " FE/t", x, 36, Skin.FE, false);
            g.text(font, Component.translatable("gui.powerfactor.pumpjack.blocks", menu.unsigned(DieselMenu.DATA_TANK2)), x, 48, Skin.LCD_TEXT, false);
            g.text(font, menu.unsigned(DieselMenu.DATA_TANK) + " mB", x, 60, Skin.TEXT, false);
        }
    }

    public static class Refinery extends Base<DieselMenu.Refinery> {
        public Refinery(DieselMenu.Refinery menu, Inventory inventory, Component title) {
            super(menu, inventory, title, "diesel_generator");
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
            view(g, minecraft, menu.pos, leftPos + 8, topPos + 20, 52, 52, menu.get(DieselMenu.DATA_FE) / 100F, running());
            int cap = com.powerfactor.oil.RefineryBlockEntity.TANK;
            GuiUtil.fluidGauge(g, leftPos + 68, topPos + 20, 16, 52, menu.unsigned(DieselMenu.DATA_TANK), cap, 0xFF2A2018);
            Skin.chevrons(g, leftPos + 88, topPos + 42, 2, running() ? (net.minecraft.util.Util.getMillis() % 1200) / 1200F : 0, running());
            GuiUtil.fluidGauge(g, leftPos + 108, topPos + 20, 16, 52, menu.unsigned(DieselMenu.DATA_TANK2), cap, 0xFFC89A32);
            GuiUtil.fluidGauge(g, leftPos + 130, topPos + 20, 16, 52, menu.unsigned(DieselMenu.DATA_TANK3), cap, 0xFFE6D9A4);
            GuiUtil.energyBar(g, leftPos + 156, topPos + 20, 12, 52, menu.unsigned(DieselMenu.DATA_STORED) * 10, 20_000);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            super.extractRenderState(g, mouseX, mouseY, a);
            String[] keys = {"fluid.powerfactor.crude_oil", "fluid.powerfactor.diesel", "fluid.powerfactor.jet_fuel"};
            int[] xs = {68, 108, 130}, idx = {DieselMenu.DATA_TANK, DieselMenu.DATA_TANK2, DieselMenu.DATA_TANK3};
            for (int i = 0; i < 3; i++) {
                if (isHovering(xs[i], 20, 16, 52, mouseX, mouseY)) {
                    g.setTooltipForNextFrame(font, Component.translatable("gui.powerfactor.fluid_amount", Component.translatable(keys[i]),
                            String.format("%,d", menu.unsigned(idx[i])), String.format("%,d", com.powerfactor.oil.RefineryBlockEntity.TANK)),
                            mouseX, mouseY);
                }
            }
        }
    }

    private GeneratorScreens() {}
}
