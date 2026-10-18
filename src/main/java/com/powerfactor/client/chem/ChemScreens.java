package com.powerfactor.client.chem;

import com.powerfactor.chem.ChemicalReactorBlockEntity;
import com.powerfactor.chem.ChemicalReactorMenu;
import com.powerfactor.chem.OreDissolverBlockEntity;
import com.powerfactor.chem.OreDissolverMenu;
import com.powerfactor.chem.OreMetal;
import com.powerfactor.client.GuiUtil;
import com.powerfactor.client.Skin;
import com.powerfactor.client.storage.StorageScreens;
import com.powerfactor.registry.ModChem;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Chemistry consoles: the reaction vessel and the dissolving vat are the heroes, drawn live. */
public final class ChemScreens {
    static final int ACID = 0xFFE4E25A, WATER = 0xFF3F76E4, HYDROGEN = 0xFFB8E8FF, TOP = 20, H = 72;

    /** A status icon for a machine state code: running, waiting for input, output full, no power. */
    static Skin.Icon stateIcon(int state, int running, int noPower, int full) {
        return state == running ? Skin.Icon.OK : state == noPower ? Skin.Icon.ERROR : state == full ? Skin.Icon.WARNING : Skin.Icon.WAIT;
    }

    static void bubbles(GuiGraphicsExtractor g, int x0, int y0, int w, int h, int n, int color, long t, int seed) {
        for (int k = 0; k < n; k++) {
            int bx = x0 + 2 + (int) ((k * 37L + seed) % Math.max(1, w - 4));
            int by = y0 + h - 2 - (int) ((t / 30 + k * 23L) % Math.max(1, h - 3));
            g.fill(bx, by, bx + 2, by + 2, color);
        }
    }

    // ------------------------------------------------------------------ chemical reactor

    public static class Reactor extends StorageScreens.Base<ChemicalReactorMenu> {
        public Reactor(ChemicalReactorMenu menu, Inventory inv, Component title) {
            super(menu, inv, title);
        }

        @Override
        protected void main(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
            long t = Util.getMillis();
            boolean on = menu.state() == ChemicalReactorBlockEntity.STATE_RUNNING;
            int cap = ChemicalReactorBlockEntity.TANK;
            // Inputs (left): sulfur slot under its icon, then water and hydrogen tanks.
            Skin.icon(g, Skin.Icon.ITEM, x + 13, y + 36, Component.translatable("item.powerfactor.sulfur_dust"));
            GuiUtil.fluidGauge(g, x + 28, y + TOP, 16, H, menu.water(), cap, WATER);
            GuiUtil.fluidGauge(g, x + 46, y + TOP, 16, H, menu.hydrogen(), cap, HYDROGEN);
            // Hero: the reaction vessel on the centre line.
            int vx = x + 66, vw = 44, vy = y + TOP;
            Skin.lcd(g, vx, vy, vw, H);
            int level = Math.round((H - 14) * Math.min(1F, 0.25F + 0.75F * menu.acid() / (float) cap));
            int top = vy + H - 8 - level;
            int liquid = com.powerfactor.client.fx.Fx.mix(0xFF2E3C20, ACID, 0.25F + 0.75F * Math.min(1F, menu.acid() / (float) cap));
            g.fill(vx + 3, top, vx + vw - 3, vy + H - 8, liquid);
            g.fill(vx + 3, top, vx + vw - 3, top + 1, 0xC0FFFFFF & ACID | 0x80000000);
            if (on) bubbles(g, vx + 3, top, vw - 6, vy + H - 8 - top, 9, 0xD0FFF6A0, t, 5);
            // Impeller: a shaft from the top and two blades turning (drawn as their projected width).
            int cx = vx + vw / 2;
            g.fill(cx, vy + 3, cx + 1, vy + H - 14, 0xFFB8C4CC);
            float a = on ? t / 120F : 0;
            for (int b = 0; b < 2; b++) {
                int half = Math.round(12 * Math.abs(Mth.cos(a + b * Mth.HALF_PI)));
                int by = vy + H - 16 + b * 4;
                g.fill(cx - half, by, cx + half + 1, by + 2, b == 0 ? 0xFFDDE6EC : 0xFF9DB3C2);
            }
            // Burner under the vessel.
            int flame = on ? 2 + (int) (2 * Math.abs(Math.sin(t / 90.0))) : 0;
            g.fill(vx + 8, vy + H - 6, vx + vw - 8, vy + H - 4, 0xFF4B6271);
            if (on) g.fill(vx + 10, vy + H - 6 - flame, vx + vw - 10, vy + H - 6, 0xFF4FA8FF);
            // Output (right): acid tank, state and progress, FE tube.
            GuiUtil.fluidGauge(g, x + 114, y + TOP, 16, H, menu.acid(), cap, ACID);
            Skin.icon(g, stateIcon(menu.state(), ChemicalReactorBlockEntity.STATE_RUNNING, ChemicalReactorBlockEntity.STATE_NO_POWER,
                    ChemicalReactorBlockEntity.STATE_FULL), x + 137, y + 22, Component.translatable("gui.powerfactor.reactor.state." + menu.state()));
            Skin.icon(g, Skin.Icon.CLOCK, x + 137, y + 34, Component.translatable("gui.powerfactor.console.cycle", Math.round(menu.progress() * 100)));
            Skin.well(g, x + 139, y + 46, 6, 46);
            int p = Math.round(44 * menu.progress());
            if (p > 0) g.fill(x + 140, y + 91 - p, x + 144, y + 91, ACID);
            Skin.energyTube(g, x + 156, y + TOP, 12, H, menu.energy(), ChemicalReactorBlockEntity.CAPACITY, on);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
            super.extractRenderState(g, mx, my, a);
            int cap = ChemicalReactorBlockEntity.TANK;
            if (hover(mx, my, 28, 16)) tip(g, "block.minecraft.water", menu.water(), cap, mx, my);
            else if (hover(mx, my, 46, 16)) tip(g, "fluid.powerfactor.hydrogen", menu.hydrogen(), cap, mx, my);
            else if (hover(mx, my, 114, 16)) tip(g, "fluid.powerfactor.sulfuric_acid", menu.acid(), cap, mx, my);
            else if (hover(mx, my, 156, 12)) GuiUtil.energyTooltip(g, font, menu.energy(), ChemicalReactorBlockEntity.CAPACITY, mx, my);
            else if (hover(mx, my, 66, 44)) g.setTooltipForNextFrame(font, Component.translatable("gui.powerfactor.reactor.recipe"), mx, my);
        }

        boolean hover(int mx, int my, int x, int w) {
            return mx >= leftPos + x && mx < leftPos + x + w && my >= topPos + TOP && my < topPos + TOP + H;
        }

        void tip(GuiGraphicsExtractor g, String key, int amount, int cap, int mx, int my) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.powerfactor.fluid_amount", Component.translatable(key),
                    String.format("%,d", amount), String.format("%,d", cap)), mx, my);
        }
    }

    // ------------------------------------------------------------------ ore dissolver

    public static class Dissolver extends StorageScreens.Base<OreDissolverMenu> {
        public Dissolver(OreDissolverMenu menu, Inventory inv, Component title) {
            super(menu, inv, title);
        }

        @Override
        protected void main(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
            long t = Util.getMillis();
            boolean on = menu.state() == OreDissolverBlockEntity.STATE_RUNNING;
            int cap = OreDissolverBlockEntity.TANK;
            float p = menu.progress();
            OreMetal metal = menu.metal() < 0 ? null : OreMetal.values()[menu.metal()];
            GuiUtil.fluidGauge(g, x + 8, y + TOP, 16, H, menu.acid(), cap, ACID);
            // Hero: the vat, centred, with the ore dissolving on the left and crystals growing on the right.
            int vx = x + 52, vw = 72, vy = y + TOP, vh = 50;
            Skin.lcd(g, vx, vy, vw, vh);
            float fill = Math.min(1F, menu.acid() / (float) cap);
            int surface = vy + vh - 4 - Math.round((vh - 12) * (0.3F + 0.7F * fill));
            g.fill(vx + 2, surface, vx + vw - 2, vy + vh - 2, com.powerfactor.client.fx.Fx.mix(0xFF26301A, ACID, 0.25F + 0.55F * fill));
            g.fill(vx + 2, surface, vx + vw - 2, surface + 1, 0xA0FFF8B0);
            if (metal != null) {
                ItemStack ore = menu.getSlot(0).getItem();
                if (!ore.isEmpty()) {
                    // The ore lump shrinks as it dissolves.
                    float s = 1F - 0.7F * p;
                    g.pose().pushMatrix();
                    g.pose().translate(vx + 18, vy + vh - 14);
                    g.pose().scale(s, s);
                    g.pose().translate(-8, -8);
                    g.item(ore, 0, 0);
                    g.pose().popMatrix();
                }
                // Crystals: five shards rising from the floor on the right, taller as the run goes on.
                int crystals = menu.getSlot(1).getItem().is(ModChem.QUANTUM_CATALYST.get()) ? 5 : 4;
                for (int k = 0; k < crystals; k++) {
                    int cxk = vx + 38 + k * 6;
                    int hgt = Math.round((6 + (k * 7) % 9) * Math.min(1F, p * 1.25F - k * 0.05F));
                    if (hgt <= 0) continue;
                    int base = vy + vh - 3;
                    for (int r = 0; r < hgt; r++) {
                        int half = Math.max(0, 2 - r * 2 / Math.max(1, hgt));
                        g.fill(cxk - half, base - r, cxk + half + 1, base - r + 1, r == hgt - 1 ? 0xFFFFFFFF : com.powerfactor.client.fx.Fx.mix(metal.color, 0xFFFFFFFF, r / (float) hgt * 0.4F));
                    }
                }
                if (on) bubbles(g, vx + 4, surface, 26, vy + vh - 2 - surface, 6, 0xC0FFF6A0, t, 3);
            }
            // Under the ore slot: what it is; under the output: the yield.
            Skin.icon(g, stateIcon(menu.state(), OreDissolverBlockEntity.STATE_RUNNING, OreDissolverBlockEntity.STATE_NO_POWER,
                    OreDissolverBlockEntity.STATE_FULL), x + 32, y + 58, Component.translatable("gui.powerfactor.dissolver.state." + menu.state()));
            int crystals = menu.getSlot(1).getItem().is(ModChem.QUANTUM_CATALYST.get()) ? 5 : 4;
            Skin.iconValue(g, font, Skin.Icon.ITEM, Component.literal("×" + crystals), x + 130, y + 59, crystals == 5 ? 0xFF9C74F0 : Skin.TEXT,
                    Component.translatable("gui.powerfactor.dissolver.yield", crystals));
            Skin.icon(g, Skin.Icon.MATTER, x + 69, y + 79, Component.translatable("gui.powerfactor.dissolver.catalyst"));
            Skin.progress(g, x + 52, y + 72, 72, p, ACID);
            Skin.energyTube(g, x + 156, y + TOP, 12, H, menu.energy(), OreDissolverBlockEntity.CAPACITY, on);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
            super.extractRenderState(g, mx, my, a);
            int x = mx - leftPos, y = my - topPos;
            if (x >= 8 && x < 24 && y >= TOP && y < TOP + H) {
                g.setTooltipForNextFrame(font, Component.translatable("gui.powerfactor.fluid_amount", Component.translatable("fluid.powerfactor.sulfuric_acid"),
                        String.format("%,d", menu.acid()), String.format("%,d", OreDissolverBlockEntity.TANK)), mx, my);
            } else if (x >= 156 && x < 168 && y >= TOP && y < TOP + H) {
                GuiUtil.energyTooltip(g, font, menu.energy(), OreDissolverBlockEntity.CAPACITY, mx, my);
            }
        }
    }

    private ChemScreens() {}
}
