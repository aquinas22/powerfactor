package com.powerfactor.client;

import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;

/** Machine-themed button retaining vanilla keyboard focus, narration and tooltip handling. */
public final class ControlButton extends Button {
    private BooleanSupplier selected = () -> false;
    public ControlButton(Button.Builder builder) { super(builder); }
    public ControlButton selectedWhen(BooleanSupplier selected) { this.selected = selected; return this; }
    @Override protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        var font = Minecraft.getInstance().font;
        boolean checked = selected.getAsBoolean();
        Skin.control(g, getX(), getY(), getWidth(), getHeight(), checked, active && isHoveredOrFocused());
        var message = Skin.fit(font, getMessage(), Math.max(0, getWidth() - 8));
        g.text(font, message, getX() + (getWidth() - font.width(message)) / 2,
                getY() + (getHeight() - 8) / 2, checked ? Skin.TEAL : active ? Skin.LCD_TEXT : Skin.LCD_DIM, false);
        if (isFocused()) g.outline(getX() + 2, getY() + 2, getWidth() - 4, getHeight() - 4, Skin.LCD_TEXT);
    }
}
