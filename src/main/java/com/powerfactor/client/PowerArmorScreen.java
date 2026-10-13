package com.powerfactor.client;

import com.powerfactor.item.DrillModule;
import com.powerfactor.item.JetpackItem;
import com.powerfactor.item.PowerArmorItem;
import com.powerfactor.item.PowerArmorServer;
import com.powerfactor.item.PoweredItem;
import com.powerfactor.item.ToolModules;
import com.powerfactor.item.ToolPayloads;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Live suit battery and module readouts with authoritative settings changes. */
public final class PowerArmorScreen extends Screen {
    private static final int W = 298, H = 220;
    private int left, top;
    private Button assists, visor, hover, glide;
    public PowerArmorScreen() { super(Component.translatable("gui.powerfactor.armor.title")); }
    @Override protected void init() {
        left = (width - W) / 2; top = (height - H) / 2;
        assists = addRenderableWidget(Button.builder(Component.empty(), b -> ClientPacketDistributor.sendToServer(new ToolPayloads.ArmorControl(0)))
                .bounds(left + 10, top + 173, 132, 18).build());
        visor = addRenderableWidget(Button.builder(Component.empty(), b -> ClientPacketDistributor.sendToServer(new ToolPayloads.ArmorControl(1)))
                .bounds(left + 156, top + 173, 132, 18).build());
        hover = addRenderableWidget(Button.builder(Component.empty(), b -> ClientPacketDistributor.sendToServer(new ToolPayloads.JetpackToggle()))
                .bounds(left + 10, top + 196, 88, 18).build());
        glide = addRenderableWidget(Button.builder(Component.empty(), b -> MovementClient.toggleWing()).bounds(left + 105, top + 196, 88, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + 200, top + 196, 88, 18).build());
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        if (minecraft.player == null) { onClose(); return; }
        Skin.panel(g, left, top, W, H);
        g.text(font, title, left + 10, top + 7, Skin.TEAL, false);
        g.text(font, Component.translatable("gui.powerfactor.armor.help"), left + 10, top + 20, Skin.DIM, false);
        boolean enabled = false, hasServos = false;
        int row = 0;
        for (var part : PowerArmorItem.Part.values()) {
            var stack = PowerArmorServer.piece(minecraft.player, part);
            int y = top + 34 + row++ * 33;
            Skin.lcd(g, left + 10, y, W - 20, 29);
            if (stack.getItem() instanceof PowerArmorItem armor) {
                g.item(stack, left + 15, y + 6);
                g.text(font, Skin.fit(font, stack.getHoverName(), 124), left + 38, y + 4, Skin.TEXT, false);
                float fill = (float) PoweredItem.energy(stack) / armor.capacity(stack);
                Skin.bar(g, left + 177, y + 6, 73, fill, fill < 0.15 ? Skin.FE : Skin.TEAL);
                String pct = Math.round(fill * 100) + "%";
                g.text(font, pct, left + W - 16 - font.width(pct), y + 4, Skin.TEXT, false);
                Component detail = detail(part, stack);
                g.text(font, Skin.fit(font, detail, W - 55), left + 38, y + 16, Skin.DIM, false);
                if (mx >= left + 10 && mx < left + W - 10 && my >= y && my < y + 29) {
                    g.setComponentTooltipForNextFrame(font, java.util.List.of(stack.getHoverName(),
                            Component.translatable("tooltip.powerfactor.energy", String.format("%,d", PoweredItem.energy(stack)), String.format("%,d", armor.capacity(stack))),
                            detail), mx, my);
                }
                if (part == PowerArmorItem.Part.LEGS || part == PowerArmorItem.Part.BOOTS) { hasServos = true; enabled |= PowerArmorItem.assists(stack); }
            } else g.text(font, Component.translatable("gui.powerfactor.armor.empty", Component.translatable("gui.powerfactor.armor." + part.name().toLowerCase(java.util.Locale.ROOT))),
                    left + 16, y + 10, Skin.DIM, false);
        }
        var head = PowerArmorServer.piece(minecraft.player, PowerArmorItem.Part.HELMET);
        var chest = minecraft.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
        assists.active = hasServos;
        assists.setMessage(setting("assists", enabled));
        visor.active = !head.isEmpty() && ToolModules.count(head, DrillModule.Type.NIGHT_VISION) > 0;
        visor.setMessage(setting("visor", PowerArmorItem.visor(head)));
        hover.active = JetpackItem.worn(chest);
        hover.setMessage(setting("hover", JetpackItem.hover(chest)));
        glide.active = com.powerfactor.item.WingsuitItem.worn(chest) && (minecraft.player.isFallFlying() || com.powerfactor.movement.WingsuitServer.canDeploy(minecraft.player));
        glide.setMessage(setting("glide", minecraft.player.isFallFlying()));
        super.extractRenderState(g, mx, my, partial);
    }
    private Component setting(String kind, boolean on) { return Component.translatable("gui.powerfactor.armor." + kind,
            Component.translatable(on ? "options.on" : "options.off")); }
    private Component detail(PowerArmorItem.Part part, net.minecraft.world.item.ItemStack stack) {
        return switch (part) {
            case HELMET -> Component.translatable("gui.powerfactor.armor.helmet.modules",
                    ToolModules.count(stack, DrillModule.Type.NIGHT_VISION) > 0 ? "✓" : "—", ToolModules.count(stack, DrillModule.Type.REBREATHER) > 0 ? "✓" : "—");
            case CHEST -> Component.translatable(com.powerfactor.item.WingsuitItem.worn(stack) ? JetpackItem.worn(stack) ? "gui.powerfactor.armor.chest.both"
                    : "gui.powerfactor.armor.chest.wingsuit" : JetpackItem.worn(stack) ? "gui.powerfactor.armor.chest.jetpack" : "gui.powerfactor.armor.chest.socket",
                    JetpackClient.HOVER_KEY.getTranslatedKeyMessage(), MovementClient.GLIDE.getTranslatedKeyMessage());
            case LEGS -> Component.translatable("gui.powerfactor.armor.legs.stats",
                    Math.round(com.powerfactor.power.ArmorPower.speedBonus(ToolModules.count(stack, DrillModule.Type.SPEED)) * 100),
                    String.format(java.util.Locale.ROOT, "+%.2f", com.powerfactor.power.ArmorPower.jumpBonus(ToolModules.count(stack, DrillModule.Type.JUMP))));
            case BOOTS -> Component.translatable("gui.powerfactor.armor.boots.stats", (int) com.powerfactor.power.ArmorPower.landingLimit(ToolModules.count(stack, DrillModule.Type.SHOCK)));
        };
    }
}
