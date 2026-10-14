package com.powerfactor.client.weapon;

import com.powerfactor.PowerFactor;
import com.powerfactor.client.Skin;
import com.powerfactor.weapon.PlasmaRifleItem;
import com.powerfactor.weapon.QuantumDisassemblerItem;
import com.powerfactor.weapon.RailgunItem;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;

/** Client feel for the arsenal: railgun zoom and charge-up, the weapon reticle, and the disassembler's area outline. */
@EventBusSubscriber(modid = PowerFactor.MODID, value = Dist.CLIENT)
public final class WeaponClient {
    private static int lastChargeStep = -1;

    private static float railCharge(Player p) {
        return p.isUsingItem() && p.getUseItem().getItem() instanceof RailgunItem
                ? RailgunItem.charge(p.getTicksUsingItem()) : 0;
    }

    @SubscribeEvent
    static void onFov(ComputeFovModifierEvent event) {
        float c = railCharge(event.getPlayer());
        if (c > 0) event.setNewFovModifier(event.getNewFovModifier() * (1 - 0.5F * c * c));
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || mc.level == null) return;
        float c = railCharge(p);
        if (c <= 0) {
            lastChargeStep = -1;
            return;
        }
        // Sparks crawl in along the rails toward the muzzle; the whine climbs with each charge step.
        Vec3 dir = p.getViewVector(1);
        Vec3 right = dir.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 muzzle = p.getEyePosition().add(dir.scale(1.1)).add(right.scale(0.35)).add(0, -0.25, 0);
        var r = mc.level.getRandom();
        for (int i = 0; i < 1 + (int) (c * 4); i++) {
            Vec3 from = muzzle.add(r.nextGaussian() * 0.5, r.nextGaussian() * 0.5, r.nextGaussian() * 0.5);
            Vec3 v = muzzle.subtract(from).scale(0.25);
            mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, from.x, from.y, from.z, v.x, v.y, v.z);
        }
        int step = (int) (c * 8);
        if (step != lastChargeStep) {
            lastChargeStep = step;
            p.playSound(c >= 1 ? SoundEvents.BEACON_POWER_SELECT : SoundEvents.NOTE_BLOCK_BIT.value(), 0.35F + c * 0.3F, 0.6F + c * 1.4F);
        }
    }

    /** The reticle: charge arcs for the railgun, a heat gauge for the plasma rifle, the mode for the disassembler. */
    public static void renderHud(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null) return;
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        float c = railCharge(p);
        if (c > 0) {
            int segs = 24, lit = Math.round(segs * c);
            for (int i = 0; i < segs; i++) {
                double a = -Math.PI / 2 + i * Math.PI * 2 / segs;
                int x = cx + (int) Math.round(Math.cos(a) * 11), y = cy + (int) Math.round(Math.sin(a) * 11);
                int color = i < lit ? (c >= 1 ? 0xFFFFFFFF : 0xFF6FE3FF) : 0x50FFFFFF;
                g.fill(x, y, x + 2, y + 2, color);
            }
            if (c >= 1) g.centeredText(mc.font, Component.translatable("gui.powerfactor.railgun.ready"), cx, cy + 16, 0xFF8FF4FF);
        }
        ItemStack held = p.getMainHandItem();
        if (held.getItem() instanceof PlasmaRifleItem) {
            int heat = PlasmaRifleItem.heat(held);
            if (heat > 0 || p.getCooldowns().isOnCooldown(held)) {
                int w = 30, x = cx - w / 2, y = cy + 12;
                g.fill(x - 1, y - 1, x + w + 1, y + 4, 0x90000000);
                float f = heat / (float) PlasmaRifleItem.MAX_HEAT;
                int col = f > 0.7F ? 0xFFFF5030 : f > 0.4F ? 0xFFFFB040 : 0xFF7CFF9A;
                g.fill(x, y, x + Math.round(w * f), y + 3, col);
                if (p.getCooldowns().isOnCooldown(held)) {
                    g.centeredText(mc.font, Component.translatable("gui.powerfactor.plasma.vent"), cx, y + 6, 0xFFFF7050);
                }
            }
        } else if (held.getItem() instanceof QuantumDisassemblerItem) {
            Component mode = Component.translatable("gui.powerfactor.disassembler.mode." + QuantumDisassemblerItem.mode(held).name().toLowerCase(java.util.Locale.ROOT));
            g.centeredText(mc.font, mode, cx, cy + 12, 0xC0B18CFF);
        }
    }

    /** Outline every block the disassembler's current mode would take. */
    @SubscribeEvent
    static void onOutline(ExtractBlockOutlineRenderStateEvent event) {
        Player p = Minecraft.getInstance().player;
        if (p == null || p.isShiftKeyDown()) return;
        ItemStack held = p.getMainHandItem();
        if (!(held.getItem() instanceof QuantumDisassemblerItem)) return;
        QuantumDisassemblerItem.Mode mode = QuantumDisassemblerItem.mode(held);
        if (mode == QuantumDisassemblerItem.Mode.SINGLE) return;
        BlockPos origin = event.getBlockPos();
        List<BlockPos> blocks = QuantumDisassemblerItem.positions(event.getLevel(), p, origin, event.getBlockState(), mode);
        Vec3 cam = event.getCamera().position();
        long t = net.minecraft.util.Util.getMillis();
        int color = 0xFF000000 | (int) (0x90 + 0x60 * Mth.sin(t / 220F)) << 16 | 0x8C << 8 | 0xFF;
        event.addCustomRenderer((state, collector, pose, level) -> {
            for (BlockPos b : blocks) {
                pose.pushPose();
                pose.translate(b.getX() - cam.x, b.getY() - cam.y, b.getZ() - cam.z);
                collector.submitShapeOutline(pose, Shapes.block(), RenderTypes.lines(), color, 2.5F, false);
                pose.popPose();
            }
            return true;
        });
    }

    private WeaponClient() {}
}
