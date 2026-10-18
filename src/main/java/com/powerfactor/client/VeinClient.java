package com.powerfactor.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.powerfactor.PowerFactor;
import com.powerfactor.item.VeinMiner;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The vein key (` by default, as in FTB Ultimine). While it's held with a fitted tool, every block of the
 * vein under the crosshair is outlined, the outline rippling outward from the aimed block in the order
 * the vein will come away, and the count shows under the crosshair.
 */
@EventBusSubscriber(modid = PowerFactor.MODID, value = Dist.CLIENT)
public final class VeinClient {
    public static final KeyMapping KEY = new KeyMapping("key.powerfactor.vein_mine", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT, JetpackClient.CATEGORY);
    private static boolean sent;
    private static BlockPos cachedAt;
    private static BlockState cachedState;
    private static List<BlockPos> cached = List.of();
    private static long cachedTick;

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;
        boolean down = KEY.isDown();
        if (down != sent) {
            sent = down;
            ClientPacketDistributor.sendToServer(new VeinMiner.KeyState(down));
        }
    }

    static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && KEY.isDown() && VeinMiner.fitted(mc.player.getMainHandItem());
    }

    private static List<BlockPos> vein(BlockPos at, BlockState state) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level.getGameTime();
        if (!at.equals(cachedAt) || state != cachedState || now - cachedTick > 10) {
            cachedAt = at.immutable();
            cachedState = state;
            cachedTick = now;
            cached = VeinMiner.find(mc.level, at, state);
        }
        return cached;
    }

    @SubscribeEvent
    static void onOutline(ExtractBlockOutlineRenderStateEvent event) {
        if (!active()) return;
        List<BlockPos> blocks = vein(event.getBlockPos(), event.getBlockState());
        Vec3 cam = event.getCamera().position();
        float t = net.minecraft.util.Util.getMillis() / 1000F;
        BlockPos origin = event.getBlockPos();
        event.addCustomRenderer((state, collector, pose, level) -> {
            for (BlockPos b : blocks) {
                // A bright front runs outward through the vein, over and over.
                float d = (float) Math.sqrt(b.distSqr(origin));
                float wave = Mth.clamp(1 - Math.abs(((t * 6) % 18) - d) / 2.5F, 0, 1);
                int r = (int) Mth.lerp(wave, 0x4F, 0xFF), g = (int) Mth.lerp(wave, 0xE3, 0xFF), bl = 0xFF;
                pose.pushPose();
                pose.translate(b.getX() - cam.x, b.getY() - cam.y, b.getZ() - cam.z);
                collector.submitShapeOutline(pose, Shapes.block(), RenderTypes.lines(), 0xFF000000 | r << 16 | g << 8 | bl, 2F + 1.5F * wave, false);
                pose.popPose();
            }
            return false;   // keep the vanilla outline on the aimed block
        });
    }

    public static void renderHud(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || !(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)
                || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return;
        BlockState s = mc.level.getBlockState(hit.getBlockPos());
        int n = vein(hit.getBlockPos(), s).size() + 1;
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        g.centeredText(mc.font, Component.translatable("gui.powerfactor.vein.count", n), cx, cy + 10, 0xFF6FE3FF);
    }

    private VeinClient() {}
}
