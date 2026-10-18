package com.powerfactor.client.fission;

import com.powerfactor.client.Skin;
import com.powerfactor.fission.RadiationMeterItem;
import com.powerfactor.fission.RadiationPayload;
import com.powerfactor.registry.ModSounds;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * The Geiger counter clicks faster as the dose rate climbs and shows it on a little dial; the dosimeter shows the
 * dose taken in all. Readings arrive once a second while either is held.
 */
public final class GeigerClient {
    private static float rate, total;
    private static boolean shielded;
    private static long last;

    public static void init() {
        RadiationPayload.clientHandler = p -> {
            rate = p.rate();
            total = p.total();
            shielded = p.shielded();
            last = Util.getMillis();
        };
    }

    private static int held() {
        var p = Minecraft.getInstance().player;
        if (p == null) return 0;
        int k = 0;
        for (var s : new net.minecraft.world.item.ItemStack[] {p.getMainHandItem(), p.getOffhandItem()}) {
            if (s.getItem() instanceof RadiationMeterItem m) k |= m.dosimeter() ? 2 : 1;
        }
        return k;
    }

    private static boolean fresh() {
        return Util.getMillis() - last < 2500;
    }

    public static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || (held() & 1) == 0 || !fresh()) return;
        // Background clicks now and then; a flood of them in a hot zone.
        float chance = Math.min(0.9F, 0.01F + rate * 0.09F);
        if (mc.player.getRandom().nextFloat() < chance) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.GEIGER.get(), 0.9F + mc.player.getRandom().nextFloat() * 0.25F,
                    Math.min(1F, 0.35F + rate * 0.1F)));
        }
    }

    /** Dose rate in mSv/h for display. */
    public static float display(float r) {
        return r * 10;
    }

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        var mc = Minecraft.getInstance();
        speckle(g);
        int k = held();
        if (k == 0 || mc.player == null) return;
        int w = 92, h = (k == 3 ? 34 : 20), x = g.guiWidth() / 2 + 96, y = g.guiHeight() - h - 4;
        Skin.lcd(g, x, y, w, h);
        int ry = y + 4;
        if ((k & 1) != 0) {
            float v = fresh() ? display(rate) : 0;
            float f = Mth.clamp((float) Math.log10(1 + v) / 2.2F, 0, 1);
            int col = f < 0.3F ? 0xFF56E39A : f < 0.65F ? 0xFFFFD84A : 0xFFFF5A4A;
            Skin.icon(g, Skin.Icon.WARNING, x + 4, ry, null);
            String text = shielded ? "—" : v < 0.1F ? "0.0" : String.format(v < 10 ? "%.1f" : "%.0f", v);
            g.text(mc.font, Component.literal(text + " mSv/h"), x + 16, ry + 1, col, false);
            g.fill(x + 4, ry + 11, x + w - 4, ry + 13, 0xFF20303A);
            g.fill(x + 4, ry + 11, x + 4 + (int) ((w - 8) * f), ry + 13, col);
            ry += 15;
        }
        if ((k & 2) != 0) {
            Skin.icon(g, Skin.Icon.PLAYER, x + 4, ry, null);
            g.text(mc.font, Component.translatable("gui.powerfactor.dosimeter", String.format("%.2f", total * 10 / 3600F)), x + 16, ry + 1, Skin.LCD_TEXT, false);
        }
    }

    /**
     * In a strong field the view speckles: single bright pixels and short streaks flashing up at random, as gamma rays
     * strike a camera's sensor, thicker the hotter it is. A full hazmat suit keeps them out.
     */
    private static void speckle(GuiGraphicsExtractor g) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !fresh() || shielded || rate < 0.15F) return;
        var r = mc.player.getRandom();
        int n = (int) Math.min(90, 1 + rate * 12);
        int w = g.guiWidth(), h = g.guiHeight();
        for (int i = 0; i < n; i++) {
            int x = r.nextInt(w), y = r.nextInt(h);
            int a = 120 + r.nextInt(136);
            int col = a << 24 | (r.nextFloat() < 0.25F ? 0xB8F0FF : 0xFFFFFF);
            if (r.nextFloat() < 0.12F) {
                // A track: a few pixels long, at an angle.
                int dx = r.nextInt(3) - 1, dy = r.nextBoolean() ? 1 : -1, len = 2 + r.nextInt(4);
                for (int j = 0; j < len; j++) g.fill(x + dx * j, y + dy * j, x + dx * j + 1, y + dy * j + 1, col);
            } else {
                g.fill(x, y, x + 1, y + 1, col);
            }
        }
    }

    private GeigerClient() {}
}
