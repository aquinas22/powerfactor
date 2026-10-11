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
            g.blit(RenderPipelines.GUI_TEXTURED, texture, leftPos, topPos, 0, 0, MAIN_W, imageHeight, 256, 256);
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
            GuiUtil.fluidGauge(g, leftPos + 44, topPos + 16, 16, 54, menu.unsigned(DieselMenu.DATA_TANK), DieselGeneratorBlockEntity.TANK,
                    lava ? 0xFFE0571A : 0xFFC8A030);
            GuiUtil.energyBar(g, leftPos + 156, topPos + 16, 12, 54, menu.unsigned(DieselMenu.DATA_STORED) * 10, 10_000);
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
            super.extractLabels(g, xm, ym);
            g.text(font, String.format("%.1f Hz", menu.unsigned(DieselMenu.DATA_RPM) / 60.0), 70, 24, 0xFF404040, false);
            g.text(font, menu.get(DieselMenu.DATA_FE) + " FE/t", 70, 36, GuiUtil.FE_COLOR, false);
            g.text(font, menu.get(DieselMenu.DATA_EE) + " EE/t", 70, 48, GuiUtil.EE_COLOR, false);
            g.text(font, menu.unsigned(DieselMenu.DATA_TANK) + " mB", 70, 60, 0xFF404040, false);
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
            GuiUtil.energyBar(g, leftPos + 76, topPos + 16, 24, 54, menu.unsigned(DieselMenu.DATA_STORED) * 1000, BatteryBankBlockEntity.CAPACITY);
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
            super.extractLabels(g, xm, ym);
            int pct = (int) (100L * menu.unsigned(DieselMenu.DATA_STORED) * 1000 / BatteryBankBlockEntity.CAPACITY);
            g.text(font, pct + "%", 106, 24, 0xFF404040, false);
            g.text(font, "+" + menu.get(DieselMenu.DATA_EE) + " FE/t", 106, 38, 0xFF2F8A3C, false);
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
                g.text(font, Component.translatable("gui.powerfactor.meter.no_grid"), 8, 24, 0xFFB03030, false);
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
                g.text(font, lines[i], 8, 18 + i * 10, i == 2 ? 0xFF404040 : 0xFF303030, false);
            }
        }
    }

    private GeneratorScreens() {}
}
