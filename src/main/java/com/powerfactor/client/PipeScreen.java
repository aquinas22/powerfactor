package com.powerfactor.client;

import com.powerfactor.logistics.PipeMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Compact routing panel with six world directions, ghost samples and overflow priority. */
public final class PipeScreen extends AbstractContainerScreen<PipeMenu> {
    private final Button[] directions = new Button[6];
    private Button mode, matching, connection, minus, plus;
    public PipeScreen(PipeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 244, 234);
    }
    private Button button(int x, int y, int w, Component label, int action) {
        return addRenderableWidget(Button.builder(label, button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action))
                .bounds(leftPos + x, topPos + y, w, 18).build(ControlButton::new));
    }
    private Component text(String key, Object... args) { return Component.translatable("gui.powerfactor.pipe." + key, args); }
    @Override protected void init() {
        super.init();
        for (Direction direction : Direction.values()) {
            int i = direction.ordinal();
            directions[i] = button(12 + i * 37, 24, 35, text("short." + direction.getSerializedName()), PipeMenu.SELECT_FACE + i);
            ((ControlButton) directions[i]).selectedWhen(() -> direction == menu.selectedSide());
            directions[i].setTooltip(Tooltip.create(Component.translatable("direction.powerfactor." + direction.getSerializedName())));
        }
        connection = button(12, 45, 220, text("connection"), PipeMenu.CONNECTION);
        mode = button(12, 66, 106, Component.empty(), PipeMenu.CYCLE_MODE);
        matching = button(126, 66, 106, Component.empty(), PipeMenu.TOGGLE_EXACT);
        mode.setTooltip(Tooltip.create(text("mode.help")));
        matching.setTooltip(Tooltip.create(text("match.help")));
        var clear = button(180, 109, 52, text("clear"), PipeMenu.CLEAR);
        clear.setTooltip(Tooltip.create(text("clear.help")));
        minus = button(12, 128, 26, Component.literal("−"), PipeMenu.PRIORITY_DOWN);
        plus = button(206, 128, 26, Component.literal("+"), PipeMenu.PRIORITY_UP);
        minus.setTooltip(Tooltip.create(text("priority.help"))); plus.setTooltip(Tooltip.create(text("priority.help")));
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        Skin.panel(g, leftPos, topPos, imageWidth, imageHeight);
        Skin.title(g, font, title, leftPos, topPos, imageWidth);
        Skin.lcd(g, leftPos + 8, topPos + 86, 228, 37);
        Skin.lcd(g, leftPos + 40, topPos + 128, 164, 18);

        for (var slot : menu.slots) Skin.slot(g, leftPos + slot.x - 1, topPos + slot.y - 1, slot.index < 4 ? Skin.TEAL : 0);
        var hint = menu.mode() == 1 && menu.emptyList() ? text("empty_allow")
                : text(menu.kind() == com.powerfactor.logistics.PipeKind.ITEM ? "sample.item" : "sample.fluid");
        g.text(font, Skin.fit(font, hint, 160), leftPos + 12, topPos + 114,
                menu.mode() == 1 && menu.emptyList() ? Skin.FE : Skin.LCD_DIM, false);
        var priority = Skin.fit(font, text("priority", menu.priority()), 160);
        g.text(font, priority, leftPos + (imageWidth - font.width(priority)) / 2, topPos + 133, Skin.LCD_TEXT, false);
        for (Direction side : Direction.values()) directions[side.ordinal()].active = true;
        mode.setMessage(Skin.fit(font, text("mode." + menu.mode()), 98));
        matching.setMessage(Skin.fit(font, text(menu.exact() ? "match.exact" : "match.type"), 98));
        connection.setMessage(Skin.fit(font, text("connection.state", Component.translatable("direction.powerfactor." + menu.selectedSide().getSerializedName()),
                text("connection." + menu.connection().getSerializedName())), 212));
        connection.setTooltip(Tooltip.create(text("endpoint.help")));
        minus.active = menu.priority() > -5; plus.active = menu.priority() < 5;
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {}
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        if (isHovering(42, 86, 160, 21, mouseX, mouseY)) {
            var tooltip = new java.util.ArrayList<Component>();
            if (hoveredSlot != null && hoveredSlot.index < 4 && hoveredSlot.hasItem()) {
                var sample = hoveredSlot.getItem();
                tooltip.add(sample.getHoverName());
                if (menu.kind() != com.powerfactor.logistics.PipeKind.ITEM) {
                    var stored = sample.get(com.powerfactor.registry.ModComponents.FLUID.get());
                    var fluid = stored != null ? stored.copy() : net.neoforged.neoforge.transfer.fluid.FluidUtil.getFirstStackContained(sample);
                    if (!fluid.isEmpty()) tooltip.add(fluid.getHoverName());
                }
            }
            tooltip.add(text("ghost.help")); tooltip.add(text("sample.help"));
            g.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
        }
        if (isHovering(40, 128, 164, 18, mouseX, mouseY))
            g.setTooltipForNextFrame(font, text("priority.help"), mouseX, mouseY);
    }
}
