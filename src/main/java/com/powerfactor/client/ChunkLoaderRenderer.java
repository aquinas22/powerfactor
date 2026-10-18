package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.client.fx.Fx;
import com.powerfactor.util.ChunkLoaderBlockEntity;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Chunk Loader: an eye of ender turning and bobbing over the gold ring, one glowing pip per range card,
 * and, while you sneak nearby, a hologram fence around exactly the chunks it keeps loaded.
 */
public class ChunkLoaderRenderer implements BlockEntityRenderer<ChunkLoaderBlockEntity, ChunkLoaderRenderer.State> {
    public static class State extends BlockEntityRenderState {
        final ItemStackRenderState eye = new ItemStackRenderState();
        float time;
        int cards;
        boolean showArea;
        double minX, minZ, size;
    }

    private final ItemModelResolver items;

    public ChunkLoaderRenderer(BlockEntityRendererProvider.Context ctx) {
        this.items = ctx.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ChunkLoaderBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumble);
        items.updateForTopItem(s.eye, new ItemStack(Items.ENDER_EYE), ItemDisplayContext.FIXED, be.getLevel(), null, 0);
        s.time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 72000L) + partial;
        s.cards = be.cards();
        var player = Minecraft.getInstance().player;
        s.showArea = player != null && player.isShiftKeyDown() && player.blockPosition().distSqr(be.getBlockPos()) < 24 * 24;
        ChunkPos c = ChunkPos.containing(be.getBlockPos());
        int r = be.radius();
        s.minX = (c.x() - r) * 16 - be.getBlockPos().getX();
        s.minZ = (c.z() - r) * 16 - be.getBlockPos().getZ();
        s.size = (2 * r + 1) * 16;
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        float bob = 0.06F * Mth.sin(s.time * 0.08F);
        pose.pushPose();
        pose.translate(0.5, 0.82 + bob, 0.5);
        pose.mulPose(camera.orientation);                       // the eye always looks at you
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.mulPose(Axis.ZP.rotationDegrees(6 * Mth.sin(s.time * 0.05F)));
        pose.scale(0.55F, 0.55F, 0.55F);
        s.eye.submit(pose, c, 0xF000F0, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
        pose.pushPose();
        pose.translate(0.5, 0.82 + bob, 0.5);
        Fx.glow(c, pose, camera.orientation, 0.7F, Fx.alpha(0xFF3AFFB0, 0.45F + 0.15F * Mth.sin(s.time * 0.2F)));
        pose.popPose();
        // Range card pips round the ring.
        for (int k = 0; k < s.cards; k++) {
            double a = k * Math.PI * 2 / 3 + s.time * 0.02;
            pose.pushPose();
            pose.translate(0.5 + Math.cos(a) * 0.36, 0.55, 0.5 + Math.sin(a) * 0.36);
            Fx.glow(c, pose, camera.orientation, 0.16F, 0xFFFFD84A);
            pose.popPose();
        }
        if (s.showArea) {
            // The loaded square: four translucent walls of the chunk boundary, 3 blocks tall around the loader.
            float x0 = (float) s.minX, z0 = (float) s.minZ, x1 = x0 + (float) s.size, z1 = z0 + (float) s.size;
            int col = Fx.alpha(0xFF3AFFB0, 0.35F + 0.1F * Mth.sin(s.time * 0.15F));
            for (float[] e : List.of(new float[] {x0, z0, x1, z0}, new float[] {x1, z0, x1, z1}, new float[] {x1, z1, x0, z1}, new float[] {x0, z1, x0, z0})) {
                Fx.beam(c, pose, new Vec3(e[0], 0.05, e[1]), new Vec3(e[2], 0.05, e[3]), 0.06F, -s.time * 0.05F, col, Fx.alpha(col, 0.5F));
                Fx.beam(c, pose, new Vec3(e[0], 2.5, e[1]), new Vec3(e[2], 2.5, e[3]), 0.04F, s.time * 0.05F, col, Fx.alpha(col, 0.4F));
                Fx.beam(c, pose, new Vec3(e[0], 0.05, e[1]), new Vec3(e[0], 2.5, e[1]), 0.05F, 0, col, Fx.alpha(col, 0.4F));
            }
        }
    }

    @Override
    public AABB getRenderBoundingBox(ChunkLoaderBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(80);
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}
