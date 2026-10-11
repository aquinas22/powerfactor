package com.powerfactor.client;

import com.powerfactor.item.ArcSaberItem;
import com.powerfactor.item.SaberSettings;
import com.powerfactor.registry.ModSounds;
import com.powerfactor.scada.ScadaPayloads;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Arc Saber workbench: RGB sliders, color presets, blade style and hit effect, with a live blade preview. */
public class SaberScreen extends Screen {
    private static final int W = 260, H = 176;
    private static final int[] PRESETS = {0x33E0FF, 0x2A6BFF, 0x3CFF4A, 0xFF2A2A, 0xB04AFF, 0xFFE03A, 0xFF8A1A, 0xFFFFFF, 0xFF4AD0, 0x00FFB0};

    private final InteractionHand hand;
    private int r, g, b;
    private SaberSettings.Style style;
    private SaberSettings.Effect effect;
    private int left, top;

    public SaberScreen(InteractionHand hand) {
        super(Component.translatable("gui.powerfactor.saber.title"));
        this.hand = hand;
        SaberSettings s = ArcSaberItem.settings(net.minecraft.client.Minecraft.getInstance().player.getItemInHand(hand));
        r = s.color() >> 16 & 0xFF;
        g = s.color() >> 8 & 0xFF;
        b = s.color() & 0xFF;
        style = s.style();
        effect = s.effect();
    }

    private final class Channel extends AbstractSliderButton {
        private final int shift;

        Channel(int x, int y, int shift, int value) {
            super(x, y, 120, 20, Component.empty(), value / 255.0);
            this.shift = shift;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            String name = shift == 16 ? "R" : shift == 8 ? "G" : "B";
            setMessage(Component.literal(name + ": " + (int) Math.round(value * 255)));
        }

        @Override
        protected void applyValue() {
            int v = (int) Math.round(value * 255);
            switch (shift) {
                case 16 -> r = v;
                case 8 -> g = v;
                default -> b = v;
            }
        }
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        addRenderableWidget(new Channel(left + 10, top + 24, 16, r));
        addRenderableWidget(new Channel(left + 10, top + 48, 8, g));
        addRenderableWidget(new Channel(left + 10, top + 72, 0, b));
        addRenderableWidget(Button.builder(styleLabel(), btn -> {
            style = SaberSettings.Style.values()[(style.ordinal() + 1) % SaberSettings.Style.values().length];
            btn.setMessage(styleLabel());
        }).bounds(left + 10, top + 118, 120, 20).build());
        addRenderableWidget(Button.builder(effectLabel(), btn -> {
            effect = SaberSettings.Effect.values()[(effect.ordinal() + 1) % SaberSettings.Effect.values().length];
            btn.setMessage(effectLabel());
        }).bounds(left + 10, top + 142, 120, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.powerfactor.saber.apply"), btn -> {
            ClientPacketDistributor.sendToServer(new ScadaPayloads.SaberConfig(hand == InteractionHand.MAIN_HAND,
                    new SaberSettings(r << 16 | g << 8 | b, style, effect)));
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.SWORD_IGNITE.get(), 1.0F, 0.5F));
            onClose();
        }).bounds(left + 150, top + 142, 100, 20).build());
    }

    private Component styleLabel() {
        return Component.translatable("gui.powerfactor.saber.style", Component.translatable("gui.powerfactor.saber.style."
                + style.name().toLowerCase(java.util.Locale.ROOT)));
    }

    private Component effectLabel() {
        return Component.translatable("gui.powerfactor.saber.effect", Component.translatable("gui.powerfactor.saber.effect."
                + effect.name().toLowerCase(java.util.Locale.ROOT)));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g2, int mouseX, int mouseY, float a) {
        g2.fill(left - 3, top - 3, left + W + 3, top + H + 3, 0xFF2B3036);
        g2.fill(left - 2, top - 2, left + W + 2, top + H + 2, 0xFF6F767D);
        g2.fill(left, top, left + W, top + H, 0xFF0A100D);
        g2.text(font, title, left + 10, top + 8, 0xFFB8F5C0, false);
        g2.text(font, Component.translatable("gui.powerfactor.saber.presets"), left + 10, top + 98, 0xFF5F8A66, false);
        for (int i = 0; i < PRESETS.length; i++) {
            int x = left + 10 + i * 12, y = top + 106;
            g2.fill(x, y, x + 10, y + 8, 0xFF000000 | PRESETS[i]);
        }
        // Blade preview, glowing in the chosen style.
        int color = style == SaberSettings.Style.RAINBOW ? Mth.hsvToRgb((System.currentTimeMillis() % 3000) / 3000F, 0.85F, 1F) : (r << 16 | g << 8 | b);
        int bx = left + 190, by = top + 18;
        long t = System.currentTimeMillis();
        for (int i = 0; i < 110; i++) {
            float flicker = switch (style) {
                case PLASMA -> 0.85F + 0.15F * Mth.sin((i + t / 40F) * 0.4F);
                case ARC -> ((i * 7 + t / 30) % 13 == 0) ? 1.3F : 0.9F;
                default -> 1F;
            };
            int halo = mix(color, 0x000000, 0.55F / flicker);
            g2.fill(bx - 5, by + i, bx + 6, by + i + 1, 0xFF000000 | halo);
            g2.fill(bx - 2, by + i, bx + 3, by + i + 1, 0xFF000000 | mix(color, 0xFFFFFF, 0.25F));
            g2.fill(bx - 1, by + i, bx + 2, by + i + 1, 0xFFFFFFFF);
        }
        g2.fill(bx - 4, by + 110, bx + 5, by + 136, 0xFF8C949B);
        g2.fill(bx - 5, by + 108, bx + 6, by + 112, 0xFFD4B24A);
        for (int y = by + 115; y < by + 134; y += 3) {
            g2.fill(bx - 4, y, bx + 5, y + 1, 0xFF3A3F44);
        }
        super.extractRenderState(g2, mouseX, mouseY, a);
    }

    private static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0, 1);
        int r = (int) Mth.lerp(t, a >> 16 & 0xFF, b >> 16 & 0xFF);
        int g = (int) Mth.lerp(t, a >> 8 & 0xFF, b >> 8 & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return r << 16 | g << 8 | bl;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (int i = 0; i < PRESETS.length; i++) {
            int x = left + 10 + i * 12, y = top + 106;
            if (event.x() >= x && event.x() < x + 10 && event.y() >= y && event.y() < y + 8) {
                r = PRESETS[i] >> 16 & 0xFF;
                g = PRESETS[i] >> 8 & 0xFF;
                b = PRESETS[i] & 0xFF;
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
